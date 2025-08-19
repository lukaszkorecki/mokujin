(ns mokujin.log-test
  (:require
   [cheshire.core :as json]
   kaocha.plugin.capture-output
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [mokujin.log :as log]
   [mokujin.context.format :as ctx.fmt]
   [clojure.test :refer [deftest is testing]]
   [matcher-combinators.test]
   [matcher-combinators.core :refer [match?]]
   [mokujin.log :as log])
  (:import
   (org.slf4j MDC)))

(set! *warn-on-reflection* true)

(use-fixtures :each
  (fn [test]
    (log/set-context-formatter! ::log/stringify)
    (test)))

(defn- parse-captured-logs
  "Hooks into Kaocha's capture-output plugin to parse the logs. Easier than digging into the clojure.toools.logging internals."
  []
  (->> (deref kaocha.plugin.capture-output/active-buffers)
       first
       kaocha.plugin.capture-output/read-buffer
       (str/split-lines)
       (map #(json/parse-string % true))
       (sort-by :timestamp)
       (map #(update % :stack_trace (fn [st] (when st
                                               {:message (first (str/split-lines st))
                                                :count (count (str/split-lines st))}))))))

(defn run-in-thread
  ([f]
   (run-in-thread "test" f))
  ([thread-name f]
   (let [t (Thread. ^Runnable f)]
     (.setName t thread-name)
     (.start t)
     (.join t))))

(deftest merging-mdc-test
  (is (= {} (log/current-context)))
  (log/with-context {"one" "two" :three :four}
    (is (= {"one" "two" "three" "four"} (log/current-context)))

    (log/with-context {"five" :six}
      (is (= {"one" "two" "three" "four" "five" "six"} (log/current-context))))

    (is (= {"one" "two" "three" "four"} (log/current-context))))
  (is (= {} (log/current-context))))

(deftest mdc-doesnt-spill-over-when-using-with-context
  (testing "init state"
    (is (nil? (MDC/get "foo"))))

  (testing "with context"
    (log/with-context {:foo "bar"}
      (log/info "ahem" {:bar "baz"})
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

(deftest mdc-doesnt-persist-after-error-in-log-statement
  (testing "init state"
    (is (nil? (MDC/get "foo"))))
  (try
    #_{:clj-kondo/ignore [:mokujin.log/log-message-not-string]}
    (log/info (format "%s" (throw (Exception. "foo"))) {:foo "bar"})
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
           (log/info "ahem" {:level-three "yes"}))))))

  (let [captured-logs (parse-captured-logs)]
    (testing "contexts can be nested, but only work within current thread"
      (is (match? [{:level "INFO"
                    :level-one "yes"
                    :level-three "yes"
                    :level-two "yes"
                    :logger_name "mokujin.log-test"
                    :message "ahem"
                    :stack_trace nil
                    :thread_name "test"}]
                  captured-logs)))))

(deftest mdc+qualified-keywords
  (log/with-context {"one" "two"
                     :foo-bar "baz"
                     :qualified.keyword.is-here/test "bar"}
    (log/info "ahem")

    (is (= {"foo-bar" "baz"
            "one" "two"
            "qualified.keyword.is-here/test" "bar"}
           (log/current-context)))))

(deftest works-with-tags-with-no-value
  (log/with-context {:foo nil :aha "" :hello "there"}
    (log/info "ahem")
    (is (= {"aha" ""
            "foo" "null"
            "hello" "there"}
           (log/current-context)))))

(deftest structured-log-test
  (run-in-thread (fn structured' []
                   (log/info "one")
                   (log/with-context {:nested true}
                     (log/warn "two" {:foo "bar"}))
                   (log/error "three")
                   (log/with-context {:nested "again"}
                     (log/with-context {:foo "bar"}
                       (log/infof "four %s" "formatted")
                       (log/error "five"))
                     (log/with-context {:foo :bar}
                       (try
                         (throw (ex-info "this is exception" {}))
                         (catch Exception e
                           (log/error e "six" {:fail true})))))
                   (log/info "seven")))
  (let [captured-logs (parse-captured-logs)]
    (testing "All messages are captured"
      (is (= ["one" "two" "three" "four formatted" "five" "six" "seven"]
             (map :message captured-logs))))

    (testing "MDC states"
      (is (match? [{}
                   {:nested "true" :foo "bar"}
                   {}
                   {:nested "again" :foo "bar"}
                   {:nested "again" :foo "bar"}
                   {:fail "true" :nested "again" :foo "bar"}
                   {}]
                  captured-logs #_(map #(dissoc % :message :level :stack_trace)
                                       captured-logs))))
    (testing "stack trace is included"
      (is (match? [{:fail "true"
                    :nested "again"
                    :foo "bar"
                    :level "ERROR"
                    :message "six"
                    :stack_trace {:count 5
                                  :message "clojure.lang.ExceptionInfo: this is exception"}}]
                  (filter :stack_trace captured-logs))))))

(deftest verify-nested-mdc
  ;; just so we have some more threads-in-threads
  (is (= 2 (count (pmap (fn [f] (f))
                        [#(run-in-thread "test-1" (fn []
                                                    (log/info "foo")
                                                    (log/with-context {:nested true}
                                                      (log/info "bar")
                                                      (log/with-context {:nested "for real"}
                                                        (log/warn "qux" {:foo "bar"})))))

                         #(run-in-thread "test-2" (fn []
                                                    (log/with-context {:foo "bar2"}
                                                      (log/error "oh no")
                                                      (try
                                                        (assert false)
                                                        (catch AssertionError e
                                                          (log/error e "oh no again again" {:fail true}))))))]))))

  (let [captured-logs (parse-captured-logs)]
    (is (match? [{:level "INFO"
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

    (is (match? [{:foo "bar2"
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

(deftest generic-log-marco-test
  (testing "`log` macro"
    (log/log :info "one" {:ctx true})
    (log/log :info "two" {:ctx true :more :true})
    (log/with-context {:extra "extra"}
      (log/log :warn "three" {:ctx true}))
    (log/log :warn "four")

    (let [logs (parse-captured-logs)]
      (is (match? [{:ctx "true" :level "INFO" :message "one"}
                   {:ctx "true" :level "INFO" :message "two" :more "true"}
                   {:ctx "true" :extra "extra" :level "WARN" :message "three"}
                   {:level "WARN" :message "four"}]
                  logs)))))

(deftest generic-logf-macro-test
  (testing "`logf` macro"
    (log/logf :info "one %s" 1)
    (log/logf :info "two %s %s" 1 2)
    (log/with-context {:extra "extra"}
      (log/logf :warn "three" {:ctx true})
      (log/logf :warn "3.5 %s" {:uh "oh"}))
    (log/logf :warn "four"))

  (let [logs (parse-captured-logs)]
    (is (match? [{:level "INFO" :message "one 1"}
                 {:level "INFO" :message "two 1 2"}
                 {:level "WARN" :message "three" :extra "extra"}
                 {:level "WARN" :message "3.5 {:uh \"oh\"}" :extra "extra"}
                 {:level "WARN" :message "four"}]
                logs))))

(deftest rebinding-context-formatter
  (log/set-context-formatter! ::log/flatten)
  (log/info "hello" {:some "context"})
  (log/info "no context")
  (log/with-context {"some" "ctx" :x [{:foo "bar"} {:bar "baz"}]}
    (log/with-context {:nested "true"}
      (log/warn "hello")
      (log/infof "hello %s" "world")
      (log/info "nested"))
    (log/error (ex-info "oh no" {:exc :data}) "oh no" {:error "yes"}))
  (let [logs (map #(dissoc % :logger_name :stack_trace :thread_name) (parse-captured-logs))]
    (is (= [{:level "INFO" :message "hello" :some "context"}
            {:level "INFO" :message "no context"}
            {:level "WARN" :message "hello" :nested "true" :some "ctx" :x.0.foo "bar" :x.1.bar "baz"}
            {:level "INFO"
             :message "hello world"
             :nested "true"
             :some "ctx"
             :x.0.foo "bar"
             :x.1.bar "baz"}
            {:level "INFO" :message "nested" :nested "true" :some "ctx" :x.0.foo "bar" :x.1.bar "baz"}
            {:error "yes" :level "ERROR" :message "oh no" :some "ctx" :x.0.foo "bar" :x.1.bar "baz"}]
           logs))))
