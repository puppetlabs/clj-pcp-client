;; Clojure script for a trivial agent, to be executed with 'lein exec -p'

(ns example-agent
    (:require [clojure.tools.logging :as log]
      [puppetlabs.cthun.client :as client]
      [puppetlabs.cthun.message :as message]))

(defn associate-session-handler
      [conn msg]
      (log/info "^^^ PCP associate session handler got message" msg))

(defn pcp-error-handler
      [conn msg]
      (log/warn "^^^ PCP error handler got message" msg
                 "\n  Description: " (:description (message/get-json-data msg))))

(defn request-handler
      [conn request]
      (log/info "&&& request handler got message" request)
      (let [request-data (message/get-json-data request)
            requester (:sender request)]
           (if-let [action (:action request-data)]
                   (if (= action "demo")
                     (do
                       (log/info "### sending back DEMO response")
                       (client/send!
                         conn
                         (-> (message/make-message)
                             (message/set-expiry 4 :seconds)
                             (assoc :targets [requester]
                                    :message_type "example/response")
                             (message/set-json-data {:demo "Hey, here's my demo!"}))))
                     (do
                       (log/info "### sending back DEFAULT response")
                       (client/send!
                         conn
                         (-> (message/make-message)
                             (message/set-expiry 4 :seconds)
                             (assoc :targets [requester]
                                    :message_type "example/response")
                             (message/set-json-data {:default "I don't know this action..."})))))
                   (do
                     (log/info "### sending back ERROR message")
                     (client/send!
                       conn
                       (-> (message/make-message)
                           (message/set-expiry 4 :seconds)
                           (assoc :targets [requester]
                                  :message_type "example/error")
                           (message/set-json-data {:error "I need some action :("})))))))

(defn default-msg-handler
      [conn msg]
      (log/info "Default handler got message" msg))

(def agent-params
  {:server      "wss://localhost:8090/pcp/"
   :cert        "test-resources/ssl/certs/client02.example.com.pem"
   :private-key "test-resources/ssl/private_keys/client02.example.com.pem"
   :cacert      "test-resources/ssl/certs/ca.pem"
   :identity    "cth://client02.example.com/agent"
   :type        "agent"})

(def agent-handlers
  {"http://puppetlabs.com/associate_response" associate-session-handler
   "http://puppetlabs.com/error_message" pcp-error-handler
   "example/request" request-handler
   :default default-msg-handler})

(defn start
  "Connect to the broker and wait for requests"
  []
  (log/info "### connecting")
  (let [agent (client/connect agent-params agent-handlers)]
       (log/info "### connected")
       (while (not ((deref (:state agent)) #{:closing :closed}))
              (Thread/sleep 1000))
       (log/info "### connection dropped - terminating")))

(start)
