(ns puppetlabs.cthun.messaging-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [puppetlabs.cthun.broker.service :refer [broker-service]]
            [puppetlabs.cthun.client :as client]
            [puppetlabs.cthun.message :as message]
            [puppetlabs.trapperkeeper.services.metrics.metrics-service :refer [metrics-service]]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer [webrouting-service]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-test-logging with-test-logging-debug]]
  ))

(def broker-config
  "A broker with ssl and own spool"
  {:webserver {:ssl-host     "127.0.0.1"
               :ssl-port     8081
               :client-auth  "want"
               :ssl-key      "./test-resources/ssl/private_keys/broker.example.com.pem"
               :ssl-cert     "./test-resources/ssl/certs/broker.example.com.pem"
               :ssl-ca-cert  "./test-resources/ssl/ca/ca_crt.pem"
               :ssl-crl-path "./test-resources/ssl/ca/ca_crl.pem"}

   :web-router-service {:puppetlabs.cthun.broker.service/broker-service {:websocket "/pcp"
                                                                         :metrics "/"}}

   :metrics {:enabled true}

   :cthun {:broker-spool "test-resources/tmp/spool"
           :accept-consumers 2
           :delivery-consumers 2}})

(defn default-request-handler
  [conn request]
  (log/debug "Default handler got message" request))

;; connect a controller with handler functions
(defn connect-controller
  [controller-id handler-function]
  (client/connect
    {:server      "wss://localhost:8081/pcp/"
     :cert        (str "test-resources/ssl/certs/" controller-id ".example.com.pem")
     :private-key (str "test-resources/ssl/private_keys/" controller-id ".example.com.pem")
     :cacert      "test-resources/ssl/certs/ca.pem"
     :type        "demo-client"}
    {"example/any_schema"  handler-function
     :default              default-request-handler}))

(deftest send-message-and-assert-received-unchanged-test
  (testing "binary payloads"
    (with-app-with-config
      app
      [broker-service jetty9-service webrouting-service metrics-service]
      broker-config
      (let [expected-data "Hello World!Ѱ$£%^\"\t\r\n(*)"
            message       (-> (message/make-message)
                              (message/set-expiry 3 :seconds)
                              (message/set-data (byte-array (.getBytes expected-data "UTF-8")))
                              (assoc :targets      ["cth://client02.example.com/demo-client"]
                                     :message_type "example/any_schema"))
            received      (promise)
            sender        (connect-controller "client01" (constantly true))
            receiver      (connect-controller "client02"
                                              (fn [conn msg] (deliver received msg)))]
        (client/send! sender message)
        (deref received)
        (client/close receiver) ;; TODO - refactor client so we can with-open it
        (client/close sender)
        (is (= expected-data (String. (message/get-data @received) "UTF-8")))
        (is (= (:id message) (:id @received)))
        (is (= (:message_type message) (:message_type @received)))
        (is (= (:expires message) (:expires @received)))
        (is (= (:targets message) (:targets @received)))
        (is (= "cth://client01.example.com/demo-client" (:sender @received)))))))
