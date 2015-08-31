;; Clojure script for a trivial agent, to be executed with 'lein exec -p'

;; TODO: configure log level and avoid logging at error lv

(ns example-agent
    (:require [clojure.tools.logging :as log]
      [puppetlabs.cthun.client :as client]
      [puppetlabs.cthun.message :as message]))

(defn associate-session-handler
      [conn msg]
      (log/fatal "^^^ PCP associate session handler got message" msg))

(defn pcp-error-handler
      [conn msg]
      (log/fatal "^^^ PCP error handler got message" msg
                 "\n  Description: " (:description (message/get-json-data msg))))

(defn request-handler
      [conn request]
      (log/fatal "&&& request handler got message" request)
      (let [request-data (message/get-json-data request)
            requester (:sender request)]
           (if-let [action (:action request-data)]
                   (if (= action "demo")
                     (do
                       (log/fatal "### sending back DEMO response")
                       (client/send!
                         conn
                         (-> (message/make-message)
                             (message/set-expiry 4 :seconds)
                             (assoc :targets [requester]
                                    :message_type "example/response")
                             (message/set-json-data {:demo "Hey, here's my demo!"}))))
                     (do
                       (log/fatal "### sending back DEFAULT response")
                       (client/send!
                         conn
                         (-> (message/make-message)
                             (message/set-expiry 4 :seconds)
                             (assoc :targets [requester]
                                    :message_type "example/response")
                             (message/set-json-data {:default "I don't know this action..."})))))
                   (do
                     (log/fatal "### sending back ERROR message")
                     (client/send!
                       conn
                       (-> (message/make-message)
                           (message/set-expiry 4 :seconds)
                           (assoc :targets [requester]
                                  :message_type "example/error")
                           (message/set-json-data {:error "I need some action :("})))))))

(defn default-msg-handler
      [conn msg]
      (log/fatal "Default handler got message" msg))

(def agent-params
  {:server      "wss://localhost:8090/cthun/"
   :cert        "examples/agent_certs/crt.pem"
   :private-key "examples/agent_certs/key.pem"
   :cacert      "examples/agent_certs/ca_crt.pem"
   :identity    "cth://0000_agent/agent"
   :type        "agent"})

(def agent-handlers
  {"http://puppetlabs.com/associate_response" associate-session-handler
   "http://puppetlabs.com/error_message" pcp-error-handler
   "example/request" request-handler
   :default default-msg-handler})

(defn start
  "Connect to the broker and wait for requests"
  []
  (log/fatal "### connecting")
  (let [agent (client/connect agent-params agent-handlers)]
       (log/fatal "### connected")
       (while (not ((deref (:state agent)) #{:closing :closed}))
              (Thread/sleep 1000))
       (log/fatal "### connection dropped - terminating")))

(start)
