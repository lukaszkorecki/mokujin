(ns mokujin.logback.config-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [mokujin.log :as log]
   [mokujin.logback]
   [mokujin.logback.config :as config])
  (:import
   [java.io File]))

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

;; From Logback's docs, new 1.3 config format:
;; <configuration>
;;   <import class="ch.qos.logback.classic.encoder.PatternLayoutEncoder"/>
;;   <import class="ch.qos.logback.core.FileAppender"/>
;;   <import class="ch.qos.logback.core.ConsoleAppender"/>

;;   <appender name="FILE" class="FileAppender">
;;     <file>myApp.log</file>
;;     <encoder class="PatternLayoutEncoder">
;;       <pattern>%date %level [%thread] %logger{10} [%file:%line] -%kvp- %msg%n</pattern>
;;     </encoder>
;;   </appender>

;;   <appender name="STDOUT" class="ConsoleAppender">
;;     <encoder class="PatternLayoutEncoder">
;;       <pattern>%kvp %msg%n</pattern>
;;     </encoder>
;;   </appender>

;;   <root level="debug">
;;     <appender-ref ref="FILE"/>
;;     <appender-ref ref="STDOUT"/>
;;   </root>
;; </configuration>

(deftest inline-1.3-configuration-test
  (testing "config as data, including 1.3 features"
    (let [tmp-file (File/createTempFile "mokujin." ".log")
          tmp-log-file-path (str (File/.getPath tmp-file))]
      (mokujin.logback/configure! {:config
                                   ;; adapts example from above, but with most things removed
                                   ;; to create a simple log file suitable for testing
                                   [:configuration
                                    [:import {:class "ch.qos.logback.classic.encoder.PatternLayoutEncoder"}]
                                    [:import {:class "ch.qos.logback.core.FileAppender"}]
                                    [:appender {:name "FILE" :class "FileAppender"}
                                     [:file tmp-log-file-path]
                                     [:encoder {:class "PatternLayoutEncoder"}
                                      [:pattern "%level %logger{10} %msg %mdc%n"]]]

                                    [:root {:level "INFO"}
                                     [:appender-ref {:ref "FILE"}]]]})
      (log/with-context {:example true}
        (log/debug "can't see me")
        (log/info "test info")
        (log/warn "test warn" {:extra "aw yeah"})
        (log/error "woah, error"))
      (Thread/sleep 20) ;; just in case

      (is (= ["INFO m.l.config-test test info example=true"
              "WARN m.l.config-test test warn extra=aw yeah, example=true"
              "ERROR m.l.config-test woah, error example=true"]
             (str/split-lines (slurp tmp-file)))))))
