(ns mokujin.log-test
  (:require
   [cheshire.core :as json]
   kaocha.plugin.capture-output
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [mokujin.log :as log])
  (:import
   (org.slf4j MDC)))

(set! *warn-on-reflection* true)

(defn- parse-captured-logs
  "Hooks into Kaocha's capture-output plugin to parse the logs. Easier than digging into the clojure.toools.logging internals."
  []
  (->> (deref kaocha.plugin.capture-output/active-buffers)
       first
       kaocha.plugin.capture-output/read-buffer
       (str/split-lines)
       (map #(json/parse-string % true))
       (sort-by :timestamp)
       (map #(-> %
                 (dissoc :timestamp)
                 (update :stack_trace (fn [st] (when st
                                                 {:message (first (str/split-lines st))
                                                  :count (count (str/split-lines st))})))))))

(defn run-in-thread
  ([f]
   (run-in-thread "test" f))
  ([thread-name f]
   (let [t (Thread. ^Runnable f)]
     (.setName t thread-name)
     (.start t)
     (.join t))))

(deftest mdc-doesnt-spill-over-when-using-with-context
  (testing "init state"
    (is (nil? (MDC/get "foo"))))

  (testing "with context"
    (log/with-context {:foo "bar"}
      (log/info {:bar "baz"} "ahem")
      (is (= "bar" (MDC/get "foo")))

      (is (= {"foo" "bar"} (log/current-context)))))

  (testing "after"
    (is (nil? (MDC/get "foo")))))

(deftest mdcs-doesnt-spill-over-on-error-with-context
  (testing "init state"
    (is (nil? (MDC/get "foo"))))
  (try
    (log/with-context {:foo "bar"}
      (throw (Exception. "foo")))
    (catch Exception _err)
    (finally
      (testing "after"
        (is (nil? (MDC/get "foo"))))))

  (testing "after"
    (is (nil? (MDC/get "foo")))))

(deftest mdcs-doesnt-spill-over-on-error-in-message
  (testing "init state"
    (is (nil? (MDC/get "foo"))))
  (try
    (log/info {:foo "bar"} (format "%s" (throw (Exception. "foo"))))
    (catch Exception _err
      (is (= {} (log/current-context)))
      (testing "after catch"
        (is (not= "bar" (MDC/get "foo"))))))

  (testing "after"
    (is (not= "bar" (MDC/get "foo")))
    (is (nil? (MDC/get "foo")))))

(deftest nested-contexts
  (log/with-context {:level-zero "0"}
    (run-in-thread
     (fn nested' []
       (log/with-context {:level-one "yes"}
         (log/with-context {:level-two "yes"}
           (log/info {:level-three "yes"} "ahem"))))))

  (let [captured-logs (parse-captured-logs)]
    (testing "contexts can be nested, but only work within current thread"
      (is (= [{:level "INFO"
               :level_one "yes"
               :level_three "yes"
               :level_two "yes"
               :level_value 20000
               :logger_name "mokujin.log-test"
               :message "ahem"
               :stack_trace nil
               :thread_name "test"}]
             captured-logs)))))

(deftest mdc+qualified-keywords
  (log/with-context {:foo-bar "baz" :qualified.keyword/test "bar"}
    (log/info "ahem")
    (is (= "baz" (MDC/get "foo_bar")))
    (is (= "bar" (MDC/get "test")))))

(deftest ingores-tags-with-no-value
  (log/with-context {:foo nil :aha "" :hello "there"}
    (log/info "ahem")
    (is (nil? (MDC/get "foo")))
    (is (nil? (MDC/get "aha")))
    (is (= "there" (MDC/get "hello")))))

(deftest structured-log-test
  (run-in-thread (fn structured' []
                   (log/info "foo")
                   (log/with-context {:nested true}
                     (log/warn {:foo "bar"} "qux"))
                   (log/error "oh no")
                   (log/with-context {:foo "bar"}
                     (log/infof "oh no %s" "formatted")
                     (log/error "oh no again"))
                   (log/with-context {:foo "bar"}
                     (try
                       (throw (ex-info "this is exception" {}))
                       (catch Exception e
                         (log/error {:fail true} e "oh no again again"))))))
  (let [captured-logs (parse-captured-logs)]
    (is (= [{:level "INFO"
             :level_value 20000
             :logger_name "mokujin.log-test"
             :message "foo"
             :stack_trace nil
             :thread_name "test"}
            {:foo "bar"
             :nested "true"
             :level "WARN"
             :level_value 30000
             :logger_name "mokujin.log-test"
             :message "qux"
             :stack_trace nil
             :thread_name "test"}
            {:level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no"
             :stack_trace nil
             :thread_name "test"}
            {:foo "bar"
             :level "INFO"
             :level_value 20000
             :logger_name "mokujin.log-test"
             :message "oh no formatted"
             :stack_trace nil
             :thread_name "test"}
            {:foo "bar"
             :level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no again"
             :stack_trace nil
             :thread_name "test"}
            {:fail "true"
             :foo "bar"
             :level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no again again"
             :stack_trace {:count 4 :message "clojure.lang.ExceptionInfo: this is exception"}
             :thread_name "test"}]
           captured-logs))))

(deftest verify-nested-mdc
  (count (pmap (fn [f] (f))
               [#(run-in-thread "test-1" (fn []
                                           (log/info "foo")
                                           (log/with-context {:nested true}
                                             (log/info "bar")
                                             (log/with-context {:nested "for real"}
                                               (log/warn {:foo "bar"} "qux")))))

                #(run-in-thread "test-2" (fn []
                                           (log/with-context {:foo "bar2"}
                                             (log/error "oh no")
                                             (try
                                               (assert false)
                                               (catch AssertionError e
                                                 (log/error {:fail true} e "oh no again again"))))))]))

  (let [captured-logs (parse-captured-logs)]
    (is (= [{:level "INFO"
             :level_value 20000
             :logger_name "mokujin.log-test"
             :message "foo"
             :stack_trace nil
             :thread_name "test-1"}

            {:level "INFO"
             :level_value 20000
             :logger_name "mokujin.log-test"
             :message "bar"
             :nested "true"
             :stack_trace nil
             :thread_name "test-1"}
            {:foo "bar"
             :level "WARN"
             :level_value 30000
             :logger_name "mokujin.log-test"
             :message "qux"
             :nested "for real"
             :stack_trace nil
             :thread_name "test-1"}]
           (filter #(= (:thread_name %) "test-1") captured-logs)))

    (is (= [{:foo "bar2"
             :level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no"
             :stack_trace nil
             :thread_name "test-2"}
            {:fail "true"
             :foo "bar2"
             :level "ERROR"
             :level_value 40000
             :logger_name "mokujin.log-test"
             :message "oh no again again"
             :stack_trace {:count 4 :message "java.lang.AssertionError: Assert failed: false"}
             :thread_name "test-2"}]
           (filter #(= (:thread_name %) "test-2") captured-logs)))))
