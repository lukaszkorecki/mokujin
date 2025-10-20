(ns mokujin.logback.config
  (:require
   [clojure.data.xml :as xml]))

(defn data->xml-str [config]
  (->> config
       (xml/sexps-as-fragment)
       ;; NOTE: we can use indent-str here to make it more readable as perf doesn't matter
       (xml/indent-str)))

(defn logger-filters->logback-loggers
  "Converts a map of {package-1-str log-level-str  package-2-str log-level-str} into
   valid ready for xmlifying list of loggers"
  [filters-map]
  (->> filters-map
       (mapv (fn [[package level]]
               [:logger {:name package :level level}]))))

(defn json
  "Creates a logback configuration with JSON output.
  Optionally takes a list of loggers to add to the configuration e.g

  ```clojure
  [:logger {:name \"com.example\" :level \"debug\"}]
  [:logger {:name \"org.eclipse.jetty\" :level \"warn\"}]
  "
  [& loggers]
  (data->xml-str
   [:configuration
    [:statusListener {:class "ch.qos.logback.core.status.NopStatusListener"}]
    [:appender {:name "STDOUT_JSON", :class "ch.qos.logback.core.ConsoleAppender"}

     [:encoder {:class "net.logstash.logback.encoder.LogstashEncoder"}
      [:throwableConverter {:class "net.logstash.logback.stacktrace.ShortenedThrowableConverter"}
       [:shortenedClassNameLength 25]]

      [:fieldNames
       [:timestamp "timestamp"]
       [:version "[ignore]"]
       [:levelValue "[ignore]"]]]]

    loggers

    [:root {:level "INFO"}
     [:appender-ref {:ref "STDOUT_JSON"}]]]))

(defn json-async [& loggers]
  (data->xml-str
   [:configuration
    ;; First, JSON appender logging to STDOUT
    [:appender {:name "STDOUT_JSON", :class "ch.qos.logback.core.ConsoleAppender"}
     [:encoder {:class "net.logstash.logback.encoder.LogstashEncoder"}
      [:throwableConverter {:class "net.logstash.logback.stacktrace.ShortenedThrowableConverter"}
       [:shortenedClassNameLength 25]]

      [:fieldNames
       [:timestamp "timestamp"]
       [:version "[ignore]"]
       [:levelValue "[ignore]"]]]]

    ;; now pipe STDOUT_JSON to the ASYNC appender
    [:appender {:name "ASYNC", :class "ch.qos.logback.classic.AsyncAppender"}
     [:appender-ref {:ref "STDOUT_JSON"}]
     [:queueSize 4096]
     [:discardingThreshold 0]]

    loggers

    [:root {:level "INFO"}
     [:appender-ref {:ref "ASYNC"}]]]))

(defn text
  "Creates a logback configuration with plain text output and MDC.

  Optionally takes a list of loggers to add to the configuration e.g

  ```clojure
  [:logger {:name \"com.example\" :level \"debug\"}]
  [:logger {:name \"org.eclipse.jetty\" :level \"warn\"}]
  ```
  "

  [& loggers]
  (data->xml-str
   [:configuration
    [:appender {:name "PLAIN_TEXT", :class "ch.qos.logback.core.ConsoleAppender"}
     [:encoder
      [:pattern "%date [%thread] [%logger] [%level] %msg %mdc%n"]]]
    loggers

    [:root {:level "INFO"}
     [:appender-ref {:ref "PLAIN_TEXT"}]]]))

(defn json+otel [& loggers]
  (data->xml-str
   [:configuration
    [:statusListener {:class "ch.qos.logback.core.status.NopStatusListener"}]
    [:appender {:name "STDOUT_JSON", :class "ch.qos.logback.core.ConsoleAppender"}

     [:encoder {:class "net.logstash.logback.encoder.LogstashEncoder"}
      [:throwableConverter {:class "net.logstash.logback.stacktrace.ShortenedThrowableConverter"}
       [:shortenedClassNameLength 25]]

      [:fieldNames
       [:timestamp "timestamp"]
       [:version "[ignore]"]
       [:levelValue "[ignore]"]]]]

    [:appender {:class "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"
                :name "OTEL"}
     [:addBaggage true]
     [:captureExperimentalAttributes true]
     [:captureMarkerAttribute true]
     [:captureKeyValuePairAttributes true]
     [:captureLoggerContext true]
     [:captureMdcAttributes "*"]
     [:appender-ref {:ref "STDOUT_JSON"}]]

    loggers

    [:root {:level "INFO"}
     [:appender-ref {:ref "OTEL"}]]]))

(defn text+otel [& loggers]
  (data->xml-str
   [:configuration
    [:statusListener {:class "ch.qos.logback.core.status.NopStatusListener"}]
    [:appender {:name "PLAIN_TEXT", :class "ch.qos.logback.core.ConsoleAppender"}
     [:encoder
      [:pattern "%date [%thread] [%logger] [%level] %msg %mdc%n"]]]

    [:appender {:class "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"
                :name "OTEL"}
     [:addBaggage true]
     [:captureExperimentalAttributes true]
     [:captureMarkerAttribute true]
     [:captureKeyValuePairAttributes true]
     [:captureLoggerContext true]
     [:captureMdcAttributes "*"]
     [:appender-ref {:ref "PLAIN_TEXT"}]
     ]

    loggers

    [:root {:level "INFO"}
     [:appender-ref {:ref "PLAIN_TEXT"}]
     [:appender-ref {:ref "OTEL"}]]]))
