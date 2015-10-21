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
            [puppetlabs.pcp.message :as message]))

(defn cnc-request-handler
  [conn request]
  (log/info "cnc handler got message" request)
  (let [response (-> (message/make-message)
                     (assoc :targets [(:sender request)]
                            :message_type "example/cnc_response")
                     (message/set-expiry 3 :seconds)
                     (message/set-json-data {:response "Hello world"
                                             :request (:id request)}))]
    (client/send! conn response))
  (log/info "cnc handler sent response"))

(defn default-request-handler
  [conn request]
  (log/info "Default handler got message" request))

;; connecting with handlers
(def conn (client/connect
           {:server "wss://localhost:8142/pcp/"
            :cert "test-resources/ssl/certs/0001_controller.pem"
            :private-key "test-resources/ssl/private_keys/0001_controller.pem"
            :cacert "test-resources/ssl/certs/ca.pem"
            :type "demo-client"}
           {"example/cnc_request" cnc-request-handler
            :default default-request-handler}))

;; sending messages
(client/send! conn
              (-> (message/make-message)
                  (message/set-expiry 3 :seconds)
                  (assoc :targets ["pcp://*/demo-client"]
                         :message_type "example/any_schema")))

(client/send! conn
              (-> (message/make-message)
                  (message/set-expiry 3 :seconds)
                  (assoc :targets ["pcp://*/demo-client"]
                         :message_type "example/cnc_request")
                  (message/set-json-data {:action "demo"})))

;; wait 5 seconds for things to resolve
(Thread/sleep (* 5 1000))

(client/close conn)
```

## Support

We use the [Puppet Communications Protocol project on JIRA](https://tickets.puppetlabs.com/browse/PCP)
with the component `clj-pcp-client` for tickets on `puppetlabs/pcp-client`.
