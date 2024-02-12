(ns puppetlabs.pcp.client
  (:require [clojure.tools.logging :as log]
            [puppetlabs.pcp.message-v2 :as message :refer [Message]]
            [puppetlabs.trapperkeeper.services.webserver.jetty10-websockets :as jetty10-websockets]
            [puppetlabs.ssl-utils.core :as ssl-utils]
            [schema.core :as s]
            [puppetlabs.i18n.core :as i18n])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:import  (java.net URI)
            (java.nio ByteBuffer)
            (java.util.concurrent CountDownLatch)
            (org.eclipse.jetty.io ClientConnector ClientConnectionFactory$Info)
            (org.eclipse.jetty.client.dynamic HttpClientTransportDynamic)
            (org.eclipse.jetty.websocket.api Session)
            (org.eclipse.jetty.websocket.client WebSocketClient)
            (org.eclipse.jetty.client HttpClient)
            (org.eclipse.jetty.client.http HttpClientConnectionFactory HttpClientConnectionFactory$HTTP11)
            (org.eclipse.jetty.util.component LifeCycle)
            (org.eclipse.jetty.util.ssl SslContextFactory$Client)
            (javax.net.ssl SSLContext)))

(defprotocol ClientInterface
  "client interface - make one with connect"
  (connecting? [client]
    "Returns true if the client is currently connecting to the pcp-broker")
  (connected? [client]
    "Returns true if the client is currently connected to the pcp-broker.
    Propagates any unhandled exception thrown while attempting to connect.")
  (wait-for-connection [client timeout-ms]
    "Wait up to timeout-ms for a connection to be established.
    Returns the client if the connection has been established, else nil.
    Propagates any unhandled exception thrown while attempting to connect.")
  (send! [client message]
    "Send a message across the currently connected client. Will
    raise ::not-associated if the client is not currently associated with the
    pcp-broker, and ::not-connected if the client has become disconnected from
    the pcp-broker.")
  (close [client]
    "Close the connection. Once the client is close you will need a
    new one.

     NOTE: you must invoke this function to properly close the connection,
     otherwise reconnection attempts may happen and the heartbeat thread will
     persist, in case it was previously started."))

(def Handlers
  "schema for handler map. String keys are data_schema handlers,
  keyword keys are special handlers (like :default)"
  {(s/either s/Str s/Keyword) (s/pred fn?)})

;; forward declare implementations of protocol functions. We prefix
;; with the dash so they're not clashing with the versions defined by
;; ClientInterface
(declare -connecting? -connected?
         -wait-for-connection -send! -close)

(s/defrecord Client
  [server :- s/Str
   handlers :- Handlers
   on-close-cb
   on-connect-cb
   retry?
   should-stop ;; promise that when delivered means should stop
   websocket-connection ;; atom of a promise that will be a connection or true
   websocket-client
   certs]
  {(s/optional-key :user-data) s/Any} ;; a field for user data
  ClientInterface
  (connecting? [client] (-connecting? client))
  (connected? [client] (-connected? client))
  (wait-for-connection [client timeout] (-wait-for-connection client timeout))
  (send! [client message] (-send! client message))
  (close [client] (-close client)))

(s/defn ^:private -connecting? :- s/Bool
  [client :- Client]
  (let [{:keys [websocket-connection]} client]
    (not (realized? @websocket-connection))))

(s/defn ^:private -connection-connected? :- s/Bool
  [connection]
  (and (realized? connection) (not (= @connection true))))

(s/defn ^:private -connected? :- s/Bool
  [client :- Client]
  (let [{:keys [websocket-connection]} client]
    ;; if deref returned an exception, we have no connection
    ;; this happens occasionally if the broker is starting up or shutting down
    ;; when we attempt to connect, or if the connection disappears abruptly
    (try
      (-connection-connected? @websocket-connection)
      (catch java.util.concurrent.ExecutionException exception
        (log/debug exception (i18n/trs "exception while establishing a connection; not connected"))
        false))))

(s/defn ^:private fallback-handler
  "The handler to use when no handler matches"
  [client :- Client message :- Message]
  (log/debug (i18n/trs "No handler for {0}" message)))

(s/defn ^:private dispatch-message
  [client :- Client message :- Message]
  (let [message-type (:message_type message)
        handlers (:handlers client)
        handler (or (get handlers message-type)
                    (get handlers :default)
                    fallback-handler)]
    (handler client message)))

(s/defn ^:private heartbeat
  "Provides the WebSocket heartbeat task that sends pings over the
  current set of connections as long as the 'should-stop' promise has
  not been delivered. Will keep a connection alive, or detect a
  stalled connection earlier."
  [^WebSocketClient websocket-client should-stop]
  (while (not (deref should-stop 15000 false))
    (let [sessions (.getOpenSessions websocket-client)]
      (log/debug (i18n/trs "Sending WebSocket ping"))
      (doseq [^Session session sessions]
        ;; sessions in the CLOSING state are in OpenSessions
        (when (.isOpen session)
          (-> session
              .getRemote
              (.sendPing (ByteBuffer/allocate 1)))))
      (log/trace (i18n/trs "done sending WebSocket ping"))))
  (log/debug (i18n/trs "WebSocket heartbeat task is about to finish")))

(s/defn create-websocket-session :- Session
  [^WebSocketClient websocket-client client-endpoint request-path-uri]
   (.get (.connect websocket-client client-endpoint request-path-uri)))

(s/defn -make-connection :- Session
  "Returns a connected org.eclipse.jetty.websocket.api.Session. In case of a SSLHandShakeException
  or ConnectException a further connection attempt will be made by following an
  exponential backoff, whereas other exceptions will be propagated."
  [client :- Client]
  (let [{:keys [server websocket-client should-stop on-close-cb on-connect-cb retry? certs]} client
        initial-sleep 200
        sleep-multiplier 2
        maximum-sleep (* 15 1000)]
    ;; Use a separate promise for ensuring the heartbeat stops when connection is closed.
    ;; If a new connection is established, a new heartbeat thread will start.
    (loop [retry-sleep initial-sleep
           stop-heartbeat (promise)]
      (or (try+
           (try
             (log/debug (i18n/trs "Making connection to {0}" server))
             (let [handlers {:on-connect (fn [ws]
                                           (log/debug (i18n/trs "WebSocket connected, heartbeat task starting."))
                                           (future
                                             (try
                                               (heartbeat websocket-client stop-heartbeat)
                                               (log/debug (i18n/trs "Websocket heartbeat task stopping."))
                                               (catch Exception e
                                                 (log/error e (i18n/trs "Failure during websocket heartbeat.")))))
                                           (when on-connect-cb (on-connect-cb client)))
                             :on-error (fn [ws error]
                                         ;; It may be a bug in jetty but connection errors result in two UpgradeExceptions passed to the
                                         ;; on-error handler on the Listener. gniazdo/connect's Listener captures and returns only the first error
                                         ;; the second is handled off to our handler which should only log it at debug since the catch around excep
                                         ;; will normally deal with it.
                                         (if (instance?  org.eclipse.jetty.websocket.api.exceptions.UpgradeException error)
                                           (log/debug error (i18n/trs "WebSocket error"))
                                           (log/error error (i18n/trs "WebSocket error"))))
                             :on-close (fn [ws code message]
                                         ;; Format error code as a string rather than a localized number, i.e. 1,234.
                                         (log/debug (i18n/trs "WebSocket closed {0} {1}" (str code) message))
                                         (deliver stop-heartbeat true)
                                         (when on-close-cb (on-close-cb client))
                                         (let [{:keys [should-stop websocket-connection]} client]
                                           ;; Ensure disconnect state is immediately registered as connecting.
                                           (log/info (i18n/trs "Sleeping for up to {0} ms to retry" retry-sleep))
                                           (reset! websocket-connection
                                                   (future
                                                     (if (and retry? (not (deref should-stop initial-sleep nil)))
                                                       (-make-connection client)
                                                       true)))))
                             :on-text (fn [ws text]
                                        (log/debug (i18n/trs "Received text message"))
                                        (dispatch-message client (message/decode text)))}
                   closureLatch (CountDownLatch. 1)
                   client-endpoint (jetty10-websockets/proxy-ws-adapter handlers certs server closureLatch)
                   request-path-uri (URI/create server)]
               (create-websocket-session websocket-client client-endpoint request-path-uri))
             (catch java.util.concurrent.ExecutionException exception
               ; ExecutionException is a general exception, rethrow the inner exception
               (throw (.getCause exception))))
           (catch org.eclipse.jetty.websocket.api.exceptions.UpgradeException exception
             (if (> (.getResponseStatusCode exception) 0)
               (do
                 (log/warn exception
                           (i18n/trs
                            "WebSocket Upgrade handshake failed with HTTP {0}. Sleeping for up to {1} ms to retry"
                            (.getResponseStatusCode exception) retry-sleep))
                 (deref should-stop retry-sleep nil))
               (throw exception)))
           (catch javax.net.ssl.SSLHandshakeException exception
             (log/warn exception (i18n/trs "TLS Handshake failed. Sleeping for up to {0} ms to retry" retry-sleep))
             (deref should-stop retry-sleep nil))
           (catch java.net.ConnectException e
             ;; The following will produce "Didn't get connected. ..."
             ;; The apostrophe needs to be duplicated (even in the translations).
             (log/debug e (i18n/trs "Connection failed."))
             (log/info (i18n/trs "Didn''t get connected. Sleeping for up to {0} ms to retry" retry-sleep))
             (deref should-stop retry-sleep nil))
           (catch java.io.IOException e
             (log/debug e (i18n/trs "Connection closed while establishing connection. Sleeping for up to {0} ms to reconnect" retry-sleep))
             (deref should-stop retry-sleep nil))
           (catch Object o
             (log/error (:throwable &throw-context) (i18n/trs "Unexpected error"))
             (throw+)))
          (recur (min maximum-sleep (* retry-sleep sleep-multiplier)) (promise))))))

(s/defn -wait-for-connection :- (s/maybe Client)
  "Waits until a client is connected. If timeout is hit, returns falsey"
  [client :- Client timeout :- s/Num]
  (let [{:keys [websocket-connection]} client]
    (try
      (when (deref @websocket-connection timeout nil)
        client)
      (catch java.util.concurrent.ExecutionException exception
        (log/debug exception (i18n/trs "exception while waiting for a connection; not connected"))))))

(s/defn ^:private -send! :- s/Bool
  "Send a message across the websocket session"
  [client :- Client message :- message/Message]
  (let [{:keys [websocket-connection]} client]
    (if-not (connected? client)
      (do (log/debug (i18n/trs "refusing to send message on unconnected session"))
          (throw+ {:type ::not-connected}))
      (try
        (.sendString (.getRemote @@websocket-connection) (message/encode message))
        (catch java.util.concurrent.ExecutionException exception
          (log/debug exception (i18n/trs "exception on the connection while attempting to send a message"))
          (throw+ {:type ::not-connected}))))
    true))

(s/defn -close :- s/Bool
  "Close the connection. Prevent any reconnection attempt 1) by the concurrent
  'connect' task, in case it's still executing, (NB: the 'connect' function
  operates asynchronously by invoking 'make-connection' in a separate thread)
  or 2) by the :on-close event handler. Stop the heartbeat thread."
  [client :- Client]
  (log/debug (i18n/trs "Closing"))
  (let [{:keys [should-stop websocket-client websocket-connection]} client]
    ;; NOTE:  This true value is also the sentinel for make-connection
    (deliver should-stop true)
    (try
      ;; Make access to websocket-connection atomic, so we don't cause an exception when racing
      ;; during shutdown. The websocket-connection atom could be reset to true between checking
      ;; connected? and dereferencing the atom to close the connection.
      (let [connection @websocket-connection]
        (when (-connection-connected? connection)
          (.close @connection)))
      (catch java.util.concurrent.ExecutionException exception
        (log/debug exception (i18n/trs "exception while closing the connection; connection already closed"))))
    ;; Stop WebSocketClient on a new thread it is not running on
    (future
      (try (LifeCycle/stop websocket-client)
           (catch Exception e
             (log/debug e (i18n/trs "Exception caught when stopping WebSocketClient."))))))
  true)

(def SslFiles
  {:cacert s/Str
   :cert s/Str
   :private-key s/Str})

(def ConnectParams
  "schema for connection parameters"
  {:server s/Str
   :ssl-context (s/either SslFiles SSLContext)
   (s/optional-key :retry?) s/Bool
   (s/optional-key :on-close-cb) Object
   (s/optional-key :on-connect-cb) Object
   (s/optional-key :type) s/Str
   (s/optional-key :user-data) s/Any
   (s/optional-key :max-message-size) s/Int})

(defmulti get-ssl-context class)
(defmethod get-ssl-context SSLContext [context] context)
(defmethod get-ssl-context java.util.Map [ssl-files]
  (let [{:keys [cert private-key cacert]} ssl-files]
    (ssl-utils/pems->ssl-context cert private-key cacert)))

;; public helpers for the ssl/websockets setup, also used by pcp-broker's test client
(s/defn ^:public make-ssl-context :- SslContextFactory$Client
  "Returns an SslContextFactory$Client that does client authentication based on the
  client certificate named."
  [params]
  (let [factory (SslContextFactory$Client.)
        ssl-context (get-ssl-context (:ssl-context params))]
    (.setSslContext factory ssl-context)
    ;;(.setNeedClientAuth factory true) ;; not available with $Client, problem?
    (.setEndpointIdentificationAlgorithm factory "HTTPS")
    factory))

(s/defn ^:public make-websocket-client :- WebSocketClient
  "Returns a WebSocketClient with the correct SSL context."
  [ssl-context :- SslContextFactory$Client max-message-size :- (s/maybe s/Int)]
  (let [client-connector (doto (ClientConnector.)
                           (.setSslContextFactory ssl-context))
        http-client-connection-factory HttpClientConnectionFactory/HTTP11
        client (WebSocketClient.
                (HttpClient.
                 (HttpClientTransportDynamic. client-connector
                                              (into-array ClientConnectionFactory$Info [http-client-connection-factory]))))]
    (when max-message-size
      (.setMaxTextMessageSize client max-message-size))
    (.start client)
    client))

;; private helpers for the ssl/websockets setup
(defn ^:private append-client-type
  "Append client type to a ws connection Uri, accounting for possible trailing
   slash."
  [url type]
  (if (clojure.string/ends-with? url "/")
    (str url type)
    (str url "/" type)))

(s/defn connect :- Client
  "Asyncronously establishes a connection to a pcp-broker named by
  `:server`. Returns a Client.

   The certificate file specified can provide either a single certificate,
   or a certificate chain (with the first entry being the client's certificate)."
  [params :- ConnectParams handlers :- Handlers]
   (let [{:keys [ssl-context type server user-data retry?] :or {retry? true}} params
         defaulted-type (or type "agent")
         ssl-context-factory (make-ssl-context params)
         certs (when (:cert ssl-context)
                 (ssl-utils/pem->certs (:cert ssl-context)))
         client (map->Client {:server (append-client-type server defaulted-type)
                              :websocket-client (make-websocket-client ssl-context-factory
                                                                       (:max-message-size params))
                              :certs certs
                              :websocket-connection (atom (future true))
                              :on-close-cb (:on-close-cb params)
                              :on-connect-cb (:on-connect-cb params)
                              :retry? retry?
                              :handlers handlers
                              :should-stop (promise)
                              :user-data user-data})
         {:keys [websocket-connection]} client]
     (reset! websocket-connection (future (-make-connection client)))
     client))
