(ns mokujin.logback.config-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [mokujin.logback.config :as config]))

(def split-lines str/split-lines)

(defn read-fixture [p]
  (slurp (io/resource p)))

(deftest generating-logback-xml-configs-test
  (testing "'plain text' preset"
    (is (= (read-fixture "fixtures/logback-plain.xml")
           (config/text))))

  (testing "'json' preset with custom filters"
    (is (= (split-lines (read-fixture "fixtures/logback-json-with-filters.xml"))
           (split-lines (config/json
                         [:logger {:name "org.test" :level "OFF"}]
                         [:logger {:name "org.elasticsearch" :level "WARN"}]))))))
