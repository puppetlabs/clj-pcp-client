(ns puppetlabs.cthun.client-test
  (:require [clojure.test :refer :all]
            [puppetlabs.cthun.client :refer :all]
            [puppetlabs.cthun.message :as message]))

;; aliases to 'private' functions
(def session-association-message #'puppetlabs.cthun.client/session-association-message)

(defn make-test-client
  "A dummied up client object"
  []
  {:server ""
   :cacert ""
   :cert ""
   :private-key ""
   :identity ""
   :type ""
   :conn ""
   :handlers {}
   :state (atom :initialized)
   :websocket ""
   :heartbeat ""
   :heartbeat-stop (promise)})

(deftest session-association-message-test
  (let [message (session-association-message (make-test-client))]
    (testing "it yields a message"
      (is (map? message)))
    (testing "message with the correct type"
      (is (= "http://puppetlabs.com/associate_request"
             (:message_type message))))))

(deftest dispatch-message-test
  (with-redefs [puppetlabs.cthun.client/fallback-handler (fn [c m] "fallback")]
    (testing "should fall back to fallback-handler with no :default"
      (is (= "fallback"
             (dispatch-message {:handlers {}} {:message_type "foo"})))))

  (let [client {:handlers {"foo" (fn [c m] "foo")
                           :default (fn [c m] "default")}}]

    (testing "default handler should match when supplied"
      (is (= "foo"
             (dispatch-message client {:message_type "foo"})))
      (is (= "default"
             (dispatch-message client {:message_type "bar"}))))))


(def make-identity #'puppetlabs.cthun.client/make-identity)

(deftest make-identity-test
  (is (= "cth://cthun-server/test"
         (make-identity "test-resources/ssl/certs/cthun-server.pem" "test"))))
