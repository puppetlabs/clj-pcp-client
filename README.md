## clj-pcp-client

PCP client

https://github.com/puppetlabs/pcp-specifications

## Installation

Releases of this project are distributed via clojars, to use it:

[![Clojars Project](http://clojars.org/puppetlabs/pcp-client/latest-version.svg)](http://clojars.org/puppetlabs/pcp-client)

## Usage example

```clojure
(ns example-client
  (:require [clojure.tools.logging :as log]
            [puppetlabs.pcp.client :as client]
            [puppetlabs.pcp.message-v2 :as message]))

(defn cnc-request-handler
  [conn request]
  (log/info "cnc handler got message" request)
  (let [response (-> (message/make-message)
                     (assoc :target (:sender request)
                            :message_type "example/cnc_response")
                     (message/set-data {:response "Hello world"
                                        :request (:id request)}))]
    (client/send! conn response))
  (log/info "cnc handler sent response"))

(defn default-request-handler
  [conn request]
  (log/info "Default handler got message" request))

;; connecting with handlers; allows an optional callback that's called when the
;; connection closes that passes the Client object as an argument
(def conn (client/connect
           {:server "wss://localhost:8142/pcp/"
            :ssl-context
            {:cert "test-resources/ssl/certs/0001_controller.pem"
             :private-key "test-resources/ssl/private_keys/0001_controller.pem"
             :cacert "test-resources/ssl/certs/ca.pem"}
            :on-close-cb (fn [client] (println client))}
           {"example/cnc_request" cnc-request-handler
            :default default-request-handler}))

;; ensuring that the underlying WebSocket connection persists with a heartbeat task
(client/start-heartbeat-thread conn)

;; sending messages
(client/send! conn
              (-> (message/make-message)
                  (assoc :target "pcp://*/demo-client"
                         :message_type "example/any_schema")))

(client/send! conn
              (-> (message/make-message)
                  (assoc :target "pcp://*/demo-client"
                         :message_type "example/cnc_request")
                  (message/set-data {:action "demo"})))

;; wait 5 seconds for things to resolve
(Thread/sleep (* 5 1000))

;; closing the connection and terminating the heartbeat task
(client/close conn)
```

## Maintenance

Maintainers: Alessandro Parisi <alessandro@puppet.com>, Michael Smith
<michael.smith@puppet.com>, Michal Ruzicka <michal.ruzicka@puppet.com>.

Contributing: Please refer to [this][contributing] document.

Tickets: File bug tickets at https://tickets.puppet.com/browse/PCP and add the
`clj-pcp-client` component to the ticket.

[contributing]: CONTRIBUTING.md
