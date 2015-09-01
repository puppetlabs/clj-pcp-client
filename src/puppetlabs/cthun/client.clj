(ns puppetlabs.cthun.client
  (:require [clojure.tools.logging :as log]
            [gniazdo.core :as ws]
            [puppetlabs.cthun.message :as message :refer [Message]]
            [puppetlabs.ssl-utils.core :as ssl-utils]
            [schema.core :as s])
  (:import  (java.nio ByteBuffer)
            (org.eclipse.jetty.websocket.client WebSocketClient)
            (org.eclipse.jetty.util.ssl SslContextFactory)))

;; WebSocket connection state values

(def ws-states #{:initialized
                 :connecting
                 :open
                 :closing
                 :closed})

(defn ws-state? [x] (contains? ws-states x))

;; schemas

(def ConnectParams
  "schema for connection parameters"
  {:server s/Str
   :cacert s/Str
   :cert s/Str
   :private-key s/Str
   :type s/Str})

(def WSState
  "schema for an atom referring to a WebSocket connection state"
  (s/pred (comp ws-state? deref)))

(def Handlers
  "schema for handler map.  String keys are data_schema handlers,
  keyword keys are special handlers (like :default)"
  {(s/either s/Str s/Keyword) (s/pred fn?)})

(def Client
  "schema for a client"
  (merge ConnectParams
         {:conn Object
          :state WSState
          :websocket Object
          :handlers Handlers
          :heartbeat Object}))

;; private helpers for the ssl/websockets setup

(defn- make-ssl-context
  "Returns an SslContextFactory that does client authentication based on the
  client certificate named"
  ^SslContextFactory
  [params]
  (let [factory (SslContextFactory.)
        {:keys [cert private-key cacert]} params
        ssl-context (ssl-utils/pems->ssl-context cert private-key cacert)]
    (.setSslContext factory ssl-context)
    (.setNeedClientAuth factory true)
    factory))

(defn- make-websocket-client
  "Returns a WebSocketClient with the correct SSL context"
  ^WebSocketClient
  [params]
  (let [client (WebSocketClient. (make-ssl-context params))]
    (.start client)
    client))

(s/defn ^:always-validate ^:private session-association-message :- Message
  [client :- Client]
  (-> (message/make-message :message_type "http://puppetlabs.com/associate_request"
                            :targets ["cth:///server"])
      (message/set-expiry 3 :seconds)))

;; TODO(ale): use WebSocket ping / pong via Jetty WS; now, the ping response
;; should be displayed by the fallback-handler

(s/defn ^:always-validate ^:private ping-message :- Message
  [client :- Client]
  (let [id (:identity client)]
    (-> (message/make-message :message_type "http://puppetlabs.com/ping"
                              :targets [id])
        (message/set-expiry 3 :seconds))))


(defn fallback-handler
  "The handler to use when no handler matches"
  [client message]
  (log/debug "no handler for " message))

(defn dispatch-message
  [client message]
  (let [message-type (:message_type message)
        handlers (:handlers client)
        handler (or (get handlers message-type)
                    (get handlers :default)
                    fallback-handler)]
    (handler client message)))

;; synchornous interface

(s/defn ^:always-validate send! :- s/Bool
  [client :- Client message :- message/Message]
  (ws/send-msg (:conn client)
               (message/encode (assoc message :sender (:identity client))))
  true)

<<<<<<< HEAD
(s/defn ^:always-validate ^:private make-identity :- p/Uri
  [certificate type]
  (let [x509     (ssl-utils/pem->cert certificate)
        cn       (ssl-utils/get-cn-from-x509-certificate x509)
        identity (format "cth://%s/%s" cn type)]
    identity))

(s/defn ^:always-validate connect :- Client
  [params :- ConnectParams handlers :- Handlers]
  (let [cert (:cert params)
        type (:type params)
        identity (make-identity cert type)
        client (promise)
=======
(defn- ping!
  "ping the broker"
  [client-ref]
  (log/debug "sending heartbeat ping")
  (send! @client-ref (ping-message client-ref)))

(defn- heartbeat
  "Starts the WebSocket heartbeat task that keep the current connection alive
  as long as it's in 'open' state, by pinging the broker every 15 s.
  Returns a reference to the task."
  [client-ref]
  (log/debug "WebSocket heartbeat task is about to start")
  (let [counter 0]
    (future
      (loop [idx 30]
            (when (= (deref (:state @client-ref)) :open)
              (do
                (Thread/sleep 500)
                (if (neg? idx)
                  (do
                    (ping! client-ref)
                    (recur 30))
                  (recur (dec idx))))))
      (log/debug "WebSocket heartbeat task is about to finish"))))

;; TODO(richardc): the identity should be derived from the client
;; certificate and the connection type.

(s/defn ^:always-validate connect :- Client
  [params :- ConnectParams handlers :- Handlers]
  (let [client-ref (promise)
>>>>>>> (CTH-305) First cut of heartbeat task,  WS state, controller/agent example pair
        websocket (make-websocket-client params)
        conn (ws/connect (:server params)
                         :client websocket
                         :on-connect (fn [session]
                                       (log/debug "WebSocket connected")
                                       (reset! (:state @client-ref) :open)
                                       (send! @client-ref (session-association-message @client-ref))
                                       (log/debug "sent associate session request")
                                       (reset! (:heartbeat @client-ref) (heartbeat client-ref)))
                         :on-error (fn [error]
                                       (log/error "WebSocket error" error))
                         :on-close (fn [code message]
                                     (log/debug "WebSocket closed" code message)
                                       (reset! (:state @client-ref) :closed))
                         :on-receive (fn [text]
                                       (log/debug "received text message")
                                       (dispatch-message @client-ref (message/decode (message/string->bytes text))))
                         :on-binary (fn [buffer offset count]
<<<<<<< HEAD
                                      (log/debug "bytes message" offset count)
                                      (log/debug "got " (message/decode buffer))
                                      (dispatch-message @client (message/decode buffer))))]
    (deliver client (merge params
                           {:identity identity
                            :conn conn
                            :websocket websocket
                            :handlers handlers}))
    @client))
=======
                                      (log/debug "received bin message - offset/bytes:" offset count
                                                 "- chunk descriptors:" (message/decode buffer))
                                      (dispatch-message @client-ref (message/decode buffer))))]
    (deliver client-ref (assoc params :conn conn
                                      :state (atom :connecting)
                                      :websocket websocket
                                      :handlers handlers
                                      :heartbeat (atom (future))))
    @client-ref))
>>>>>>> (CTH-305) First cut of heartbeat task,  WS state, controller/agent example pair

(s/defn ^:always-validate close :- s/Bool
  "Close the connection"
  [client :- Client]
  (if (not (future-done? (deref (:heartbeat client))))
        (do
          ;; TODO(ale): check this; it's meant to prevent the 1 min delay (see
          ;; clj docs --> future), but it could interact with other libs
          (log/debug "shutting down agents to avoid delays")
          (shutdown-agents))
        (log/debug "heartbeat task already finished"))
  (when ((deref (:state client)) #{:opening :open})
        (log/debug "Closing")
        (reset! (:state client) :closing)
        (.stop (:websocket client))
        (ws/close (:conn client)))
  true)

;; TODO(ale): consider moving the heartbeat pings into a separate monitor task
