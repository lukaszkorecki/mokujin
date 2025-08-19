(ns mokujin.context.format-test
  (:require [clojure.test :refer [deftest testing is]]
            [mokujin.context.format :as ctx.fmt]))

(deftest stringify-test
  (testing "simple case"
    (is (= {} (ctx.fmt/stringify {}))))

  (testing "simple case with string keys"
    (is (= {"a" "b"} (ctx.fmt/stringify {"a" "b"}))))

  (testing "keyword handling, including namespaced kws"
    (is (= {"a" "b" "c/d" "e"} (ctx.fmt/stringify {:a :b :c/d :e})))
    (is (= {"a/b" "c/d"} (ctx.fmt/stringify {:a/b :c/d})))
    (is (= {"a.b/c" "d.e/f"} (ctx.fmt/stringify {:a.b/c :d.e/f}))))

  (testing "collection handling"
    (is (= {"a" (pr-str [1 2 3])}
           (ctx.fmt/stringify {:a [1 2 3]}))))

  (testing "other data types"
    (is (= {"a" "1"} (ctx.fmt/stringify {:a 1})))
    (is (= {"a" "true"} (ctx.fmt/stringify {:a true})))
    (is (= {"a" "null"} (ctx.fmt/stringify {:a nil})))
    (is (= {"t" "true" "f" "false"} (ctx.fmt/stringify {:t true :f false })))
    (is (= {"a" "Sun Oct 01 12:00:00 UTC 2023"}
           (ctx.fmt/stringify {:a #inst "2023-10-01T12:00:00Z"})))

    (is (= {"huh" "clojure.lang.ExceptionInfo: woah {:a 1, :b 2}"}
           (ctx.fmt/stringify {:huh (ex-info "woah" {:a 1 :b 2})})))))
