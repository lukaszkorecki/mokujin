(ns mokujin.logback
  "Configures logback using supplied config or config file.
  Inspired by https://github.com/pyr/unilog by doesn't try to hide any
  of the Logback configuration, it just provides a way to configure it at runtime and optionally with EDN data.
  "
  (:require
   [mokujin.logback.config :as config])
  (:import
   [ch.qos.logback.classic Level Logger LoggerContext]
   [ch.qos.logback.classic.joran JoranConfigurator]
   [java.io ByteArrayInputStream InputStream]
   [org.slf4j LoggerFactory]))

(set! *warn-on-reflection* true)

(defn ^:private str->input-stream [^String s]
  (ByteArrayInputStream. (String/.getBytes s)))

(defn ^:private get-named-logger-and-context [logger-name]
  (let [logger-context (LoggerFactory/getILoggerFactory)
        logger (LoggerContext/.getLogger logger-context ^String logger-name)]
    {:logger logger :logger-context logger-context}))

(defn ^:private initialize-and-configure! [config-stream]
  (let [{:keys [logger logger-context]} (get-named-logger-and-context Logger/ROOT_LOGGER_NAME)
        configurator ^JoranConfigurator (JoranConfigurator.)]
    (.detachAndStopAllAppenders ^Logger logger)
    (.reset ^LoggerContext logger-context)
    (.setContext configurator ^LoggerContext logger-context)
    (.doConfigure configurator ^InputStream config-stream)
    configurator))

(def ^:private presets
  {::json config/json
   ::json-async config/json-async
   ::text config/text})

(defn ^:private config-type->xml-stream
  [{:keys [config logger-filters]}]
  (cond
   (vector? config) (str->input-stream (config/data->xml-str config))
    ;; "raw" XML string
   (string? config) (str->input-stream config)
    ;; io/resource most likely?
   (instance? java.net.URL config) (.openStream ^java.net.URL config)

   ;; one of 'preset' configurations
   (#{::json ::json-async ::text} config) (let [cnf (apply (get presets config)
                                                           (config/logger-filters->logback-loggers logger-filters))]
                                            (str->input-stream cnf))
   :else (throw
          (ex-info "Uknown configuration type" {:config config :class (class config)}))))

(defn configure!
  "Configure logback with the given configuration.
  Arguments are a map of:
  - `:config` - can be either:
    - a string - 'raw' XML configuration for Logback
    - a resource - should point at Logback config in XML format
    - a vector - implies that the vector is a data structure which can be converted to XML using `clojure.data.xml`
                 and that the schema of this data is valid Logback configuration as represented in XML
    - a namespaced keyword - a 'preset' config, valid values:
     - `:mokujin.logback/text` - plain text logger, with MDC - useful for dev/test
     - `:mokujin.logback/json` - basic JSON logger, includes all MDC fields, suitable for most usecases
     - `:mokujin.logback/json-async` - produces JSON logs as the `json` configuration, but uses a buffering async appender to improve performance

  - `:logger-filters` - when `:config` uses one of preset configurations, you can pass a map of `{package.qualifier.string log-level-string}` to control individual packages log level, e.g:

  ```
  :config ::logback/json
  :logger-filters {\"redis.clients.jedis.JedisMonitor\" \"ERROR\"
                   \"org.eclipse.jetty\" \"WARN\"}
  ```



  Note on the config-as-vector input: Mokujin will not do any transformations to the configuration,
  so it needs to be in the correct format recorgnized by Logback,
  internally `clojure.data.xml/sexps-as-fragment` is used to convert the hiccup-style EDN data to XML.
  "
  [{:keys [config logger-filters]
    :or {logger-filters []}}]
  (with-open [conf ^java.io.Closeable (config-type->xml-stream {:config config
                                                                :logger-filters logger-filters})]
    (initialize-and-configure! conf)))

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
