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

(deftest merging-mdc-test
  (is (= {} (MDC/getCopyOfContextMap)))
  (log/with-context {"one" "two" :three :four}
    (is (= {"one" "two" "three" "four"} (MDC/getCopyOfContextMap)))

    (log/with-context {"five" :six}
      (is (= {"one" "two" "three" "four" "five" "six"} (MDC/getCopyOfContextMap))))

    (is (= {"one" "two" "three" "four"} (MDC/getCopyOfContextMap))))
  (is (= {} (MDC/getCopyOfContextMap))))

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

(deftest works-with-tags-with-no-value
  (log/with-context {:foo nil :aha "" :hello "there"}
    (log/info "ahem")
    (is (= {"aha" ""
            "foo" ""
            "hello" "there"}
           (into {} (MDC/getCopyOfContextMap))))))

(deftest structured-log-test
  (run-in-thread (fn structured' []
                   (log/info "one")
                   (log/with-context {:nested true}
                     (log/warn {:foo "bar"} "two"))
                   (log/error "three")
                   (log/with-context {:nested "again"}
                     (log/with-context {:foo "bar"}
                       (log/infof "four %s" "formatted")
                       (log/error "five"))
                     (log/with-context {:foo :bar}
                       (try
                         (throw (ex-info "this is exception" {}))
                         (catch Exception e
                           (log/error {:fail true} e "six")))))
                   (log/info "seven")))
  (let [captured-logs (map #(dissoc % :logger_name :thread_name)
                           (parse-captured-logs))]
    (testing "All messages are captured"
      (is (= ["one" "two" "three" "four formatted" "five" "six" "seven"]
             (map :message captured-logs))))

    (testing "MDC states"
      (is (= [{}
              {:nested "true" :foo "bar"}
              {}
              {:nested "again" :foo "bar"}
              {:nested "again" :foo "bar"}
              {:fail "true" :nested "again" :foo "bar"}
              {}]
             (map #(dissoc % :message :level :stack_trace)
                  captured-logs))))
    (testing "stack trace is included"
      (is (= [{:fail "true"
               :nested "again"
               :foo "bar"
               :level "ERROR"
               :message "six"
               :stack_trace {:count 5
                             :message "clojure.lang.ExceptionInfo: this is exception"}}]
             (filter :stack_trace captured-logs))))))

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
             :logger_name "mokujin.log-test"
             :message "foo"
             :stack_trace nil
             :thread_name "test-1"}

            {:level "INFO"
             :logger_name "mokujin.log-test"
             :message "bar"
             :nested "true"
             :stack_trace nil
             :thread_name "test-1"}
            {:foo "bar"
             :level "WARN"
             :logger_name "mokujin.log-test"
             :message "qux"
             :nested "for real"
             :stack_trace nil
             :thread_name "test-1"}]
           (filter #(= (:thread_name %) "test-1") captured-logs)))

    (is (= [{:foo "bar2"
             :level "ERROR"
             :logger_name "mokujin.log-test"
             :message "oh no"
             :stack_trace nil
             :thread_name "test-2"}
            {:fail "true"
             :foo "bar2"
             :level "ERROR"
             :logger_name "mokujin.log-test"
             :message "oh no again again"
             :stack_trace {:count 4 :message "java.lang.AssertionError: Assert failed: false"}
             :thread_name "test-2"}]
           (filter #(= (:thread_name %) "test-2") captured-logs)))))
