;; Clojure script for a trivial controller, to be executed with 'lein exec -p'

;; TODO: configure log level and avoid logging at error lv

(ns example-controller
    (:require
      [clojure.tools.logging    :as log]
      [puppetlabs.cthun.client  :as client]
      [puppetlabs.cthun.message :as message]))

(defn associate-session-handler
  [conn msg]
  (log/fatal "^^^ PCP associate session handler got message" msg))

(defn pcp-error-handler
  [conn msg]
  (log/fatal "^^^ PCP error handler got message" msg
             "\n  Description: " (:description (message/get-json-data msg))))

(defn inventory-handler
  [conn msg]
  (log/fatal "^^^ PCP inventory handler got message" msg
             "\n  URIs: " (:uris (message/get-json-data msg))))

(defn response-handler
  [conn msg]
      (log/fatal "&&& response handler got message" msg
                 "\n  &&& &&& RESPONSE:" (message/get-json-data msg)))

(defn agent-error-handler
  [conn msg]
      (log/fatal "&&& error handler got message" msg))

(defn default-msg-handler
  [conn msg]
  (log/fatal "&&& Default handler got message" msg))

(def controller-params
  {:server      "wss://localhost:8090/cthun/"
   :cert        "examples/controller_certs/crt.pem"
   :private-key "examples/controller_certs/key.pem"
   :cacert      "examples/controller_certs/ca_crt.pem"
   :identity    "cth://0000_controller/example_controller"
   :type        "controller"})

(def controller-handlers
  {"http://puppetlabs.com/associate_response" associate-session-handler
   "http://puppetlabs.com/inventory_response" inventory-handler
   "http://puppetlabs.com/error_message" pcp-error-handler
   "example/response" response-handler
   "example/error" agent-error-handler
   :default default-msg-handler})

(defn start
  "Connect to the broker and send a request to the agent"
  []
  (log/fatal "### connecting")
  (let [cl (client/connect controller-params controller-handlers)]
       (log/fatal "### sending inventory request")
       (client/send!
         cl
         (-> (message/make-message)
             (message/set-expiry 4 :seconds)
             (assoc :targets ["cth:///server"]
                    :message_type "http://puppetlabs.com/inventory_request")
             (message/set-json-data {:query ["cth://*/agent"]})))

       (log/fatal "### sending agent request")
       (client/send!
         cl
         (-> (message/make-message)
             (message/set-expiry 4 :seconds)
             (assoc :targets ["cth://*/agent"]
                    :message_type "example/request")
             (message/set-json-data {:action "demo"})))
       (log/fatal "### waiting for 60 s")
       (Thread/sleep 60000)
       (client/close cl)))

(time (start))
