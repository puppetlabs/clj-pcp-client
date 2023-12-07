(ns puppetlabs.pcp.client-test
  (:require [clojure.test :refer :all]
            [puppetlabs.pcp.client :refer :all :as client]
            [puppetlabs.pcp.message-v2 :as message]
            [schema.test :as st]
            [slingshot.test]))

(use-fixtures :once st/validate-schemas)

(defn make-test-client
  "A dummied up client object"
  ([user-data]
   (let [realized-future (future true)]
     (deref realized-future)                                ; make sure the future is realized
     (map->Client {:server "wss://localhost:8142/pcp/v1"
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

(deftest upgrade-exception-retryable-with-http-status
  (testing "UpgradeException with status code > 0 retries"
    (let [retry-count (atom 0)]
      (with-redefs [create-websocket-session (fn [_websocket-client _client-endpoint uri]
                                               (do
                                                 (when (> @retry-count 0)
                                                   (throw (InterruptedException. "test passed")))
                                                 (swap! retry-count inc)
                                                 (throw (java.util.concurrent.ExecutionException.
                                                         (org.eclipse.jetty.websocket.api.exceptions.UpgradeException. uri 503 "test")))))]
        (is (thrown-with-msg? InterruptedException #"test passed"
                              (-make-connection (make-test-client))))))))

(deftest upgrade-exception-not-retryable
  (testing "UpgradeException with status code 0 does not retry"
    (let [retry-count (atom 0)]
      (with-redefs [create-websocket-session (fn [_websocket-client _client-endpoint uri]
                                               (do
                                                 (when (> @retry-count 0)
                                                   (throw (InterruptedException. "test failed")))
                                                 (swap! retry-count inc)
                                                 (throw (java.util.concurrent.ExecutionException.
                                                         (org.eclipse.jetty.websocket.api.exceptions.UpgradeException. uri 0 "unretryable exception")))))]
        (is (thrown-with-msg? Exception #"unretryable exception"
                              (-make-connection (make-test-client))))))))
