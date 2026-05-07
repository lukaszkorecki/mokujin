(ns mokujin.logback.capture
  (:require [mokujin.logback])
  (:import
   [ch.qos.logback.classic.spi ILoggingEvent]
   [ch.qos.logback.core AppenderBase Appender]
   [ch.qos.logback.classic Logger LoggerContext]
   [org.slf4j LoggerFactory]))

(defn ^:private get-root-logger []
  (let [logger-context (LoggerFactory/getILoggerFactory)]
    (LoggerContext/.getLogger logger-context ^String Logger/ROOT_LOGGER_NAME)))

(defn atom-appender [logs]
  (let [appender (proxy [AppenderBase] []
                   (append [^ILoggingEvent ev]
                     (swap! logs conj
                            {:thread-name (.getThreadName ev)
                             :level (get mokujin.logback/level->keyword (.getLevel ev) :off)
                             :message (.getFormattedMessage ev)
                             :logger-name (.getLoggerName ev)
                             :mdc (into {} (.getMDCPropertyMap ev))
                             :timestamp (.getInstant ev)})))]
    (AppenderBase/.setContext appender (LoggerFactory/getILoggerFactory))
    (AppenderBase/.setName appender "AtomAppender")
    appender))

(def ^:dynamic *logs* nil)

(defn do-with-appender [^Appender appender f]
  (let [logger (get-root-logger)]
    (try
      (Appender/.start appender)
      (Logger/.addAppender logger appender)
      (f)
      (finally
        (Logger/.detachAppender logger appender)))))

(defmacro with-captured-logs [& body]
  `(let [logs# (atom [])]
     (binding [*logs* logs#]
       (do-with-appender (atom-appender logs#)
                         (fn* ^:once [] ~@body)))))

(defmacro with-root-log-level [level & body]
  `(let [prev-level# (mokujin.logback/get-level)
         level# ~level]
     (try
       (mokujin.logback/set-level! level#)
       ~@body
       (finally
         (mokujin.logback/set-level! prev-level#)))))

(defn get-logs []
  (if *logs*
    @*logs*
    (throw (ex-info "Call get-logs inside of a with-captured-logs block!" {}))))

