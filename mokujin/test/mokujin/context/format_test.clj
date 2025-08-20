(ns mokujin.context.format-test
  (:require [clojure.test :refer [deftest testing is]]
            [mokujin.context.format :as ctx.fmt]))

(deftest stringify-test
  (testing "simple case"
    (is (= {} (ctx.fmt/stringify {}))))

  (testing "simple case with string keys"
    (is (= {"a" "b"} (ctx.fmt/stringify {"a" "b"}))))

  (testing "keyword handling including namespaced kws"
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
    (is (= {"t" "true" "f" "false"} (ctx.fmt/stringify {:t true :f false})))
    (is (= {"a" "Sun Oct 01 12:00:00 UTC 2023"}
           (ctx.fmt/stringify {:a #inst "2023-10-01T12:00:00Z"})))

    (is (= {"huh" "clojure.lang.ExceptionInfo: woah {:a 1, :b 2}"}
           (ctx.fmt/stringify {:huh (ex-info "woah" {:a 1 :b 2})})))))

(deftest flatten-test
  (testing "works just like stringify for simple maps"
    (is (= {"a" "b"} (ctx.fmt/flatten {:a "b"})))
    (is (= {} (ctx.fmt/flatten {})))

    (is (= {"q" "false" "r" "string" "x" "1" "y" "true" "z" "null"}
           (ctx.fmt/stringify {:x 1 :y true :z nil :q false :r "string"})
           (ctx.fmt/flatten {:x 1 :y true :z nil :q false :r "string"}))))

  (testing "nested maps"

    (is (= {"a.b" "c" "a.d.e" "f"}
           (ctx.fmt/flatten {:a {:b "c" :d {:e "f"}}})))

    (is (= {"a.b.c" "d" "a.e.f.g" "h"}
           (ctx.fmt/flatten {:a {:b {:c "d"} :e {:f {:g "h"}}}}))))

  (testing "nested collections"
    (is (= {"a.0.b" "c" "a.1.d.e" "f"}
           (ctx.fmt/flatten {:a [{:b "c"} {:d {:e "f"}}]})))

    (is (= {"a.0.b.c" "d" "a.1.e.f.g" "h"}
           (ctx.fmt/flatten {:a [{:b {:c "d"}} {:e {:f {:g "h"}}}]})))

    (is (= {"x.0.foo" "bar"
            "x.1.bar" "baz"
            "woop.0.x" "1"
            "woop.1.y" "2"
            "woop.2.z.0" "3"
            "woop.2.z.1" "4"}
           (ctx.fmt/flatten {:x [{:foo "bar"} {:bar "baz"}]
                             :woop [{:x 1} {:y 2} {:z [3 4]}]}))))

  (testing "edge case with sets - these won't be handled"
    (is (= {"a" (pr-str #{1 2 3})}
           (ctx.fmt/flatten {:a #{1 2 3}})))))
