(ns mokujin.log-test
  (:require
   [cheshire.core :as json]
   kaocha.plugin.capture-output
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [mokujin.log :as log])
  (:import
   (org.slf4j MDC)))

(deftest mdc-doesnt-spill-over-when-using-with-context
  (testing "init state"
    (is (nil? (MDC/get "foo"))))

  (testing "with context"
    (log/with-context {:foo "bar"}
      (log/info {:bar "baz"} "ahem")
      (is (= "bar" (MDC/get "foo")))))

  (testing "after"
    (is (nil? (MDC/get "foo")))))

(deftest mds-doesnt-spill-over-on-error-with-context
  (testing "init state"
    (is (nil? (MDC/get "foo"))))
  (try
    (log/with-context {:foo "bar"}
      (throw (Exception. "foo")))
    (catch Exception _err
      (testing "after"
        (is (nil? (MDC/get "foo"))))))

  (testing "after"
    (is (nil? (MDC/get "foo")))))

(deftest mds-doesnt-spill-over-on-error-in-message
  (testing "init state"
    (is (nil? (MDC/get "foo"))))
  (try
    (log/info {:foo "bar"} (format "%s" (throw (Exception. "foo"))))
    (catch Exception _err
      (testing "after catch"
        (is (not= "bar" (MDC/get "foo"))))))

  (testing "after"
    (is (not= "bar" (MDC/get "foo")))
    (is (nil? (MDC/get "foo")))))

(deftest json-log-test
  (log/info "foo")
  (log/with-context {:nested true}
    (log/warn {:foo "bar"} "qux"))
  (log/error "oh no")
  (log/with-context {:foo "bar"}
    (log/error "oh no again"))
  (log/with-context {:foo "bar"}
    (try
      (throw (ex-info "damn" {}))
      (catch Exception e
        (log/error {:fail true} e "oh no again again"))))

  (let [captured-logs (->> (deref kaocha.plugin.capture-output/active-buffers)
                           first
                           kaocha.plugin.capture-output/read-buffer
                           (str/split-lines)
                           (map #(json/parse-string % true))
                           (map #(-> %
                                     (dissoc :timestamp)
                                     (update :stack_trace (fn [st] (when st
                                                                     (count (str/split-lines st))))))))]
    (is (= [{:level "INFO"
             :level_value 20000
             :logger_name "mokujin.log-test"
             :message "foo"
             :stack_trace nil
             :thread_name "main"}
            {:foo "bar"
             :nested "true"
             :level "WARN"
             :level_value 30000
             :logger_name "mokujin.log-test"
             :message "qux"
             :stack_trace nil
             :thread_name "main"}
            {:level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no"
             :stack_trace nil
             :thread_name "main"}
            {:foo "bar"
             :level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no again"
             :stack_trace nil
             :thread_name "main"}
            {:fail "true"
             :foo "bar"
             :level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no again again"
             :stack_trace 118
             :thread_name "main"}]
           captured-logs))))
