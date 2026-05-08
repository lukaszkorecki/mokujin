(ns mokujin.logback.capture
  "Test helpers for capturing logging events in-process.

  Attach an in-memory appender to the root logger for the duration of a
  block via `with-captured-logs`, then assert against the captured events
  with `get-logs`. Each captured event is a plain Clojure map with the
  level, message, MDC, logger name, thread, timestamp, and any logged
  throwable (with a recursive `:cause` chain), so tests can assert on
  structured fields directly instead of parsing rendered log output.

  Use `with-root-log-level` to temporarily lower the root level when the
  events you want to assert on would otherwise be filtered out."
  (:require [mokujin.logback])
  (:import
   [ch.qos.logback.classic.spi ILoggingEvent IThrowableProxy]
   [ch.qos.logback.core AppenderBase Appender]
   [ch.qos.logback.classic Logger LoggerContext]
   [org.slf4j LoggerFactory]))

(set! *warn-on-reflection* true)

(defn ^:private get-root-logger []
  (let [^LoggerContext logger-context (LoggerFactory/getILoggerFactory)]
    (LoggerContext/.getLogger logger-context ^String Logger/ROOT_LOGGER_NAME)))

(defn ^:private throwable-proxy->map [^IThrowableProxy t]
  (when t
    (cond-> {:class-name (.getClassName t)
             :message (.getMessage t)}
      (.getCause t) (assoc :cause (throwable-proxy->map (.getCause t))))))

(defn -atom-appender
  [logs]
  (let [appender (proxy [AppenderBase] []
                   (append [^ILoggingEvent ev]
                     (swap! logs conj
                            {:thread-name (.getThreadName ev)
                             :level (get mokujin.logback/level->keyword (.getLevel ev) :off)
                             :message (.getFormattedMessage ev)
                             :logger-name (.getLoggerName ev)
                             :mdc (into {} (.getMDCPropertyMap ev))
                             :throwable (throwable-proxy->map (.getThrowableProxy ev))
                             :timestamp (.getInstant ev)})))]
    (AppenderBase/.setContext appender ^LoggerContext (LoggerFactory/getILoggerFactory))
    (AppenderBase/.setName appender "AtomAppender")
    appender))

(def ^:dynamic *logs*
  "Atom holding the vector of captured log maps for the current
  `with-captured-logs` block. `nil` outside of a block; `get-logs`
  dereferences this and throws when unbound."
  nil)

(defn -do-with-appender
  [^Appender appender f]
  (let [logger (get-root-logger)]
    (try
      (Appender/.start appender)
      (Logger/.addAppender logger appender)
      (f)
      (finally
        (Logger/.detachAppender logger appender)
        (Appender/.stop appender)))))

(defmacro with-captured-logs
  "Capture log events emitted during `body`. Inside the block,
  `(get-logs)` returns the events captured so far as a vector of maps.

  Captured events are scoped to the dynamic extent of the block.Events
  filtered out by the root logger's level are not captured; use
  `with-root-log-level` to change the log level threshold. "
  [& body]
  `(let [logs# (atom [])]
     (binding [*logs* logs#]
       (-do-with-appender (-atom-appender logs#)
                          (fn* ^:once [] ~@body)))))

(defmacro with-root-log-level
  "Set the root logger's level to `level` (a keyword from
  `mokujin.logback/levels`) for the dynamic extent of `body`, restoring
  the previous level on exit even if `body` throws.

  Useful inside `with-captured-logs` when the events under test sit
  below the configured root level (e.g. asserting on `:debug` output in
  a project whose default level is `:info`)."
  [level & body]
  `(let [prev-level# (mokujin.logback/get-level)
         level# ~level]
     (try
       (mokujin.logback/set-level! level#)
       ~@body
       (finally
         (mokujin.logback/set-level! prev-level#)))))

(defn get-logs
  "Return the vector of log events captured so far in the surrounding
  `with-captured-logs` block."
  []
  (if *logs*
    @*logs*
    (throw (ex-info "Call get-logs inside of a with-captured-logs block!" {}))))

