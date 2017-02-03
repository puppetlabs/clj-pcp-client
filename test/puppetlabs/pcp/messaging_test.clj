(ns puppetlabs.pcp.messaging-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [puppetlabs.pcp.broker.service :refer [broker-service]]
            [puppetlabs.pcp.client :as client]
            [puppetlabs.pcp.message-v2 :as message]
            [puppetlabs.trapperkeeper.services.authorization.authorization-service :refer [authorization-service]]
            [puppetlabs.trapperkeeper.services.metrics.metrics-service :refer [metrics-service]]
            [puppetlabs.trapperkeeper.services.status.status-service :refer [status-service]]
            [puppetlabs.trapperkeeper.services.scheduler.scheduler-service :refer [scheduler-service]]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer [webrouting-service]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-log-level with-logging-to-atom with-log-suppressed-unless-notable]]
            [slingshot.test]
            [schema.test :as st]))

(def broker-config
  "A broker with ssl and own spool"
  {:authorization {:version 1
                   :rules [{:name "allow all"
                            :match-request {:type "regex"
                                            :path "^/.*$"}
                            :allow-unauthenticated true
                            :sort-order 1}]}

   :webserver {:ssl-host     "127.0.0.1"
               ;; Default port is 8142.  Use 8143 here so we don't clash.
               :ssl-port     8143
               :client-auth  "want"
               :ssl-key      "./test-resources/ssl/private_keys/localhost.pem"
               :ssl-cert     "./test-resources/ssl/certs/localhost.pem"
               :ssl-ca-cert  "./test-resources/ssl/ca/ca_crt.pem"
               :ssl-crl-path "./test-resources/ssl/ca/ca_crl.pem"}

   :web-router-service
   {:puppetlabs.pcp.broker.service/broker-service {:v1 "/pcp1"
                                                   :v2 "/pcp"
                                                   :metrics "/"}
    :puppetlabs.trapperkeeper.services.status.status-service/status-service "/status"}

   :metrics {:enabled true
             :server-id "localhost"}

   :pcp-broker {:broker-spool "test-resources/tmp/spool"
                :accept-consumers 2
                :delivery-consumers 2}})

(defn default-request-handler
  [conn request]
  (log/debug "Default handler got message" request))

(defn client-config
  "returns a client config for a given cn against the test-resource/ssl ca"
  [cn]
  {:server      "wss://localhost:8143/pcp/"
   :cert        (format "test-resources/ssl/certs/%s.example.com.pem" cn)
   :private-key (format "test-resources/ssl/private_keys/%s.example.com.pem" cn)
   :cacert      "test-resources/ssl/certs/ca.pem"})

(defn connect-client-config
  "connect a client with a handler function"
  [config handler-function]
  (client/connect config
   {"example/any_schema"  handler-function
    :default              default-request-handler}))

(defn connect-client
  "connect a client with a handler function, uses default configuration strategy"
  [cn handler-fn]
  (connect-client-config (client-config cn) handler-fn))

(defmacro eventually-logged?
  [logger-id level pred & body]
  `(with-log-level ~logger-id ~level
     (with-log-suppressed-unless-notable (constantly false)
       (let [log# (atom [])]
         (with-logging-to-atom ~logger-id log#
           (do ~@body)
           (loop [n# 15] ;; loop up to 15 times to see if predicate is satisfied
             (let [log-text# (map (fn [x#] (.getMessage x#)) (deref log#))]
               (cond
                 (neg? n#) (is (some ~pred log-text#))
                 (some ~pred log-text#) (is (some ~pred log-text#))
                 :else (do (Thread/sleep 100)
                           (recur (dec n#)))))))))))

(def broker-services
  [authorization-service
   broker-service
   jetty9-service
   webrouting-service
   metrics-service
   status-service
   scheduler-service])

(deftest message-roundtrip-test
  (doseq [target-type ["agent" "controller"]]
    (testing (format "payloads with target type '%s'" target-type)
      (with-app-with-config app broker-services broker-config
        (let [expected-data "Hello World!Ѱ$£%^\"\t\r\n(*)"
              message (-> (message/make-message)
                          (message/set-data expected-data)
                          (assoc :target (str "pcp://client02.example.com/" target-type)
                                 :message_type "example/any_schema"))
              received (promise)]
          (with-open [sender (connect-client "client01" (constantly true))
                      receiver (-> (client-config "client02")
                                   (assoc :type target-type)
                                   (connect-client-config (fn [conn msg] (deliver received msg))))]
            (client/wait-for-connection sender (* 40 1000))
            (client/wait-for-connection receiver (* 40 1000))
            (client/send! sender message)
            (deref received)
            (is (= expected-data (message/get-data @received)))
            (is (= (:id message) (:id @received)))
            (is (= (:message_type message) (:message_type @received)))
            (is (= (:target message) (:target @received)))
            (is (= "pcp://client01.example.com/agent" (:sender @received)))))))))

;; Test that the client allows specifying a sender and it won't be overwritten.
;; The existing pcp-broker implementation will still reject the message, so we
;; just check that the message is passed through unmodified.
(deftest sender-spoof-test
  (with-app-with-config app broker-services broker-config
    (let [message (-> (message/make-message)
                      (assoc :target "pcp://client01.example.com/agent"
                             :sender "pcp://client02.example.com/agent"
                             :message_type "example/any_schema"))
          received (promise)]
      (with-open [sender (connect-client "client01" (constantly true))]
        (client/wait-for-connection sender (* 40 1000))
        (eventually-logged?
          "puppetlabs.pcp.broker.pcp_access" :warn
          (partial re-find #"AUTHENTICATION_FAILURE")
          (client/send! sender message))))))

(deftest connect-to-a-down-broker-test
  (with-open [client (connect-client "client01" (constantly true))]
    (is (not (client/connected? client)) "Should not be connected yet")
    (with-app-with-config app broker-services broker-config
      (client/wait-for-connection client (* 40 1000))
      (is (client/connected? client) "Should now be connected"))
    ;; Allow time for the websocket connection to close, but not enough to attempt reconnecting
    (Thread/sleep 500)
    (is (not (client/connected? client)) "Should be disconnected")))

(deftest connect-to-a-broker-with-the-wrong-name-test
  (with-app-with-config app broker-services
    (update-in broker-config [:webserver] merge
               {:ssl-key "./test-resources/ssl/private_keys/client01.example.com.pem"
                :ssl-cert "./test-resources/ssl/certs/client01.example.com.pem"})
    (eventually-logged? "puppetlabs.pcp.client" :warn
                        (partial re-find #"TLS Handshake failed\. Sleeping for up to 200 ms to retry")
                        (with-open [client (connect-client "client01" (constantly true))]
                          (client/wait-for-connection client (* 4 1000))
                          (is (not (client/connected? client)))))))

(deftest ssl-ca-cert-permutation-test
  ;; This test checks that ssl verification is happening
  ;; against the expected certificate chain.  We do this using an
  ;; alternate signing authority (test-resources/ssl-alt), and then
  ;; permute the ssl-ca-cert configured for client and broker.
  (doseq [[broker-ca client-ca retries]
          [["ssl" "ssl-alt"] ;; client should reject server cert
           ["ssl-alt" "ssl-alt"]]]
    (testing (str "broker-ca: " broker-ca " client-ca: " client-ca)
      (with-app-with-config app broker-services
        (assoc-in broker-config [:webserver :ssl-ca-cert]
                  (str "./test-resources/" broker-ca "/ca/ca_crt.pem"))
          (eventually-logged?
            "puppetlabs.pcp.client" :warn
            (partial re-find #"TLS Handshake failed\. Sleeping for up to 200 ms to retry")
            (with-open [client (connect-client-config
                                 (assoc (client-config "client01")
                                        :cacert (str "test-resources/" client-ca "/certs/ca.pem"))
                                 (constantly true))]
              (client/wait-for-connection client (* 4 1000))))))))

(deftest send-when-not-connected-test
  (with-open [client (connect-client "client01" (constantly true))]
    (is (thrown+? [:type :puppetlabs.pcp.client/not-connected]
                  (client/send! client (message/make-message))))))

(deftest connect-to-a-down-up-down-up-broker-test
  (with-open [client (connect-client "client01" (constantly true))]
    (is (not (client/connected? client)) "Should not be connected yet")
    (with-app-with-config app broker-services broker-config
      (is (= client (client/wait-for-connection client (* 40 1000))))
      (is (client/connected? client) "Should now be connected"))
    ;; Allow time for the websocket connection to close, but not enough to attempt reconnecting
    (Thread/sleep 500)
    (is (not (client/connected? client)) "Should be disconnected")
    (with-app-with-config app broker-services broker-config
      (is (= client (client/wait-for-connection client (* 40 1000))))
      (is (client/connected? client) "Should be reconnected"))))

(deftest connect-with-too-small-message-size
  (with-app-with-config app broker-services broker-config
    (let [received (promise)]
      (with-open [client (connect-client-config (assoc (client-config "client01")
                                                       :max-message-size 128)
                                                (constantly true))]
        (let [data (-> (message/make-message)
                       (message/set-data "foobar")
                       (assoc :target "pcp://client01.example.com/agent"
                              :message_type "example/any_schema"))]
          (deliver (:should-stop client) true)
          (eventually-logged?
            "puppetlabs.pcp.client" :debug
            (partial re-find #"WebSocket closed 1009 Text message size \[185\] exceeds maximum size \[128\]")
            (let [connected (client/wait-for-connection client 4000)]
              (client/send! client data)
              (is connected))))))))
