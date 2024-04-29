(ns mokujin.logback
  "Configures logback using supplied config or config file.
  Inspired by https://github.com/pyr/unilog by doesn't try to hide any
  of the Logback configuration, it just provides a way to configure it at runtime and optionally with EDN data.
  "
  (:require
   [clojure.data.xml :as xml])
  (:import
   [ch.qos.logback.classic Logger LoggerContext Level]
   [ch.qos.logback.classic.joran JoranConfigurator]
   [java.io ByteArrayInputStream InputStream]
   [org.slf4j LoggerFactory]))

(defn config-data->xml-str [config]
  (->> config
       (xml/sexps-as-fragment)
       ;; NOTE: we can use indent-str here to make it more readable as perf doesn't matter
       (xml/indent-str)))

(defn str->input-stream [^String s]
  (ByteArrayInputStream. (.getBytes s)))

(defn- get-named-logger-and-context [logger-name]
  (let [logger-context ^LoggerContext (LoggerFactory/getILoggerFactory)
        logger (.getLogger logger-context ^String logger-name)]
    {:logger logger :logger-context logger-context}))

(defn- initialize-and-configure! [config-stream]
  (let [{:keys [logger logger-context]} (get-named-logger-and-context Logger/ROOT_LOGGER_NAME)
        configurator ^JoranConfigurator (JoranConfigurator.)]
    (.detachAndStopAllAppenders ^Logger logger)
    (.reset ^LoggerContext logger-context)
    (.setContext configurator ^LoggerContext logger-context)
    (.doConfigure configurator ^InputStream config-stream)))

(defn configure!
  "Configure logback with the given configuration.
  One of the keys nees to be present:
  `xml-config-path` - path or resource to the logback configuration file, it will
                      override any loaded loback configuration found
                      in <classpath>/logback.xml or <classpath>/logback-test.xml
  `config` - a map with logback configuration, as hiccup-style EDN data eg.

  ```clojure
  [:configuration
     [:statusListener {:class \"ch.qos.logback.core.status.NopStatusListener\"}]
     [:appender {:name \"STDOUT\", :class \"ch.qos.logback.core.ConsoleAppender\"}
       [:encoder
         [:pattern \"%date [%thread] [%logger] [%level] %msg %mdc%n\"]]]
     [:appender {:name \"STDOUT_JSON\", :class \"ch.qos.logback.core.ConsoleAppender\"}
       [:encoder {:class \"net.logstash.logback.encoder.LogstashEncoder\"}
         [:fieldNames
           [:timestamp \"timestamp\"]
           [:version \"[ignore]\"]
           [:levelValue \"[ignore]\"]]]]
     [:root {:level \"info\"}
       [:appender-ref {:ref \"STDOUT_JSON\"}]]]

  ```
  Note that it will not do any transformations to the configuration, so it needs to be in the correct format,
  internally `clojure.data.xml/sexps-as-fragment` is used to convert the hiccup-style EDN data to XML.
  "
  [{:keys [config]}]
  (cond
    ;; clj data -> xml
    (vector? config) (with-open [conf ^java.io.Closeable (str->input-stream (config-data->xml-str config))]
                       (initialize-and-configure! conf))
    ;; "raw" XML string
    (string? config) (with-open [conf ^java.io.Closeable (str->input-stream config)]
                       (initialize-and-configure! conf))
    ;; io/resource most likely?
    (instance? java.net.URL config) (with-open [conf ^java.io.Closeable (.openStream ^java.net.URL config)]
                                      (initialize-and-configure! conf))
    :else (ex-info "Uknown configuration type" {:config config :class (class config)})))

(def levels
  {:all Level/ALL
   :off Level/OFF
   :info Level/INFO
   :debug Level/DEBUG
   :warn Level/WARN
   :error Level/ERROR
   :trace Level/TRACE})

(defn set-level!
  "Set logging level dynamically - can be used to change logging level at runtime
  If only level (a kewyord of the levels map) is provided, it will set the level for the root logger
  if name and level are provided, it will set the level for the named logger.
  "
  ([level]
   (set-level! Logger/ROOT_LOGGER_NAME level))
  ([name level]
   (let [{:keys [logger]} (get-named-logger-and-context name)]
     (.setLevel ^Logger logger ^Level (get levels level :info)))))
