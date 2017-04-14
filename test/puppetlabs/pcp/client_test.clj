(ns puppetlabs.pcp.client-test
  (:require [clojure.test :refer :all]
            [puppetlabs.pcp.client :refer :all :as client]
            [puppetlabs.pcp.message-v2 :as message]
            [slingshot.test]
            [schema.test :as st]))

(defn make-test-client
  "A dummied up client object"
  ([user-data]
   (let [realized-future (future true)]
     (deref realized-future)                                ; make sure the future is realized
     (map->Client {:server "wss://localhost:8142/pcp/v1"
                   :websocket-client ""
                   :websocket-connection (atom realized-future)
                   :handlers {}
                   :should-stop (promise)
                   :user-data user-data})))
  ([]
   (make-test-client nil)))

(deftest client-with-user-data-test
  (let [client (make-test-client "foo")]
    (testing "client includes the user data"
      (is (= (:user-data client) "foo")))))

(deftest state-checkers-test
  (let [client (make-test-client)]
    (testing "successfully returns negative"
      (is (not (connected? client))))
    (testing "successfully returns negative"
      (is (not (connecting? client))))))

(def dispatch-message #'puppetlabs.pcp.client/dispatch-message)
(deftest dispatch-message-test
  (with-redefs [puppetlabs.pcp.client/fallback-handler (fn [c m] "fallback")]
    (testing "should fall back to fallback-handler with no :default"
      (is (= "fallback"
             (dispatch-message (assoc (make-test-client) :handlers {})
                               (message/make-message :message_type "foo"))))))

  (let [client (assoc (make-test-client)
                      :handlers {"foo" (fn [c m] "foo")
                                 :default (fn [c m] "default")})]

    (testing "default handler should match when supplied"
      (is (= "foo"
             (dispatch-message client (message/make-message :message_type "foo"))))
      (is (= "default"
             (dispatch-message client (message/make-message :message_type "bar")))))))

(deftest make-connection-test
  (with-redefs [gniazdo.core/connect (constantly "awesome")]
    (is (= "awesome"
           (-make-connection (make-test-client))))))

(deftest wait-for-connection-test
  (testing "when connected"
    (let [connection (atom (future "yes"))
          connected (assoc (make-test-client) :websocket-connection connection)]
      (is (= connected
             (wait-for-connection connected 1000)))))
  (testing "when connecting slowly"
    (let [connection (atom (future (Thread/sleep 10000) "slowly"))
          connected-later (assoc (make-test-client) :websocket-connection connection)]
      (is (= nil
             (wait-for-connection connected-later 1000))))))
