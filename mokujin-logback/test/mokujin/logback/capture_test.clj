(ns mokujin.logback.capture-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [matcher-combinators.test :refer [match?]]
   [mokujin.log :as log]
   [mokujin.logback]
   [mokujin.logback.capture :as capture]))

(deftest capture-logs-test
  (testing "captures logs from mokujin"
    (capture/with-captured-logs
      (capture/with-root-log-level :debug
        (log/info "This is a test")
        (log/debug "Oh, hello"))
      (is (match? [{:level :info
                    :message "This is a test"
                    :logger-name "mokujin.logback.capture-test"
                    :thread-name string?
                    :mdc {}
                    :timestamp inst?}
                   {:level :debug
                    :message "Oh, hello"
                    :logger-name "mokujin.logback.capture-test"
                    :thread-name string?
                    :mdc {}
                    :timestamp inst?}]
                  (capture/get-logs)))))
  (testing "captures MDC context"
    (capture/with-captured-logs
      (log/with-context {:context-thing :stringified}
        (log/warn "This is a test"))
      (is (match? [{:level :warn
                    :message "This is a test"
                    :logger-name "mokujin.logback.capture-test"
                    :thread-name string?
                    :mdc {"context-thing" "stringified"}
                    :timestamp inst?}]
                  (capture/get-logs)))))
  (testing "captures throwables logged via log/error"
    (capture/with-captured-logs
      (let [cause (ex-info "underlying" {:why :testing})
            wrapped (ex-info "boom" {:fail true} cause)]
        (log/error wrapped "something failed" {:request-id "abc"}))
      (is (match? [{:level :error
                    :message "something failed"
                    :mdc {"request-id" "abc"}
                    :throwable {:class-name "clojure.lang.ExceptionInfo"
                                :message "boom"
                                :cause {:class-name "clojure.lang.ExceptionInfo"
                                        :message "underlying"}}}]
                  (capture/get-logs)))))
  (testing "throwable is nil when no exception was logged"
    (capture/with-captured-logs
      (log/info "plain message")
      (is (match? [{:throwable nil}] (capture/get-logs)))))
  (testing "warns with calling get-logs outside of block"
    (is (thrown-with-msg? Exception #"Call get-logs inside of a with-captured-logs block!"
                          (capture/get-logs))))
  (testing "Only captures logs that are within the current level"
    (capture/with-captured-logs
      (capture/with-root-log-level :warn
        (log/info "This should be missing")
        (log/warn "This should be present"))
      (log/info "This should also be present")
      (is (match? [{:level :warn
                    :message "This should be present"}
                   {:level :info
                    :message "This should also be present"}]
                  (capture/get-logs))))))
