(ns mokujin.log
  (:require
   [clojure.tools.logging :as log])
  (:import
   (org.slf4j MDC)))

(defn ->str
  [val] ^String
  (if val
    (if (keyword? val)
      (.replaceAll ^String (.getName ^clojure.lang.Keyword val) "-" "_")
      (.toString ^Object val))
    ""))

(defn format-context [ctx]
  (persistent!
   (reduce-kv (fn [m k v]
                (assoc! m (->str k) (->str v)))
              (transient {})
              ctx)))

(defn mdc-put [ctx]
  (doseq [[k v] (format-context ctx)]
    (MDC/put ^String k ^String v)))

(defmacro with-context
  "Set  context map for the form. The context map should use unqualified keywords or strings for keys.
  Values will be stringified."
  [ctx & body]
  `(let [og# (MDC/getCopyOfContextMap)]
     (mdc-put ~ctx)
     (try
       (do ~@body)
       (finally
         (MDC/setContextMap og#)))))

(defn current-context
  "Returns the current context map, useful if you want to pass it to another process to continue logging with the same context.
  Note that due to how MDC works, this will only return the context map for
  the current thread and map keys will be strings, not keywords."
  []
  (into {} (MDC/getCopyOfContextMap)))

(defmacro info
  "Log an info pass message or ctx+messag"
  ([msg]
   (with-meta
     `(log/log :info ~msg)
     (meta &form)))
  ([ctx msg]
   (with-meta
     `(with-context ~ctx
        (log/log :info ~msg))
     (meta &form))))

(defmacro warn
  "Log a warning pass message or ctx+message"
  ([msg]
   (with-meta
     `(log/log :warn ~msg)
     (meta &form)))
  ([ctx msg]
   (with-meta
     `(with-context ~ctx
        (log/log :warn ~msg))
     (meta &form))))

(defmacro debug
  "Debug log, pass message or ctx+message"
  ([msg]
   (with-meta
     `(log/log :debug ~msg)
     (meta &form)))
  ([ctx msg]
   (with-meta
     `(with-context ~ctx
        (log/log :debug ~msg))
     (meta &form))))

(defmacro error
  "Log an error message.
  [msg]
  [exc msg]
  [ctx exc msg]
  Context map can only be provided when passing exception AND message,
  otherwise, wrap your call in with-context"
  ([msg]
   (with-meta
     `(log/log :error ~msg)
     (meta &form)))
  ([exc msg]
   (with-meta
     `(log/log :error ~exc ~msg)
     (meta &form)))
  ([ctx exc msg]
   (with-meta
     `(with-context ~ctx
        (log/log :error ~exc ~msg))
     (meta &form))))

;; re-export infof/errorf/warnf/debugf for convenience,
;; API is unchanged  - as in, context map is not supported
(defmacro infof [& args]
  (with-meta
    `(log/logf :info ~@args)
    (meta &form)))

(defmacro warnf [& args]
  (with-meta
    `(log/logf :warn ~@args)
    (meta &form)))

(defmacro errorf [& args]
  (with-meta
    `(log/logf :error ~@args)
    (meta &form)))

(defmacro debugf [& args]
  (with-meta
    `(log/logf :debug ~@args)
    (meta &form)))

(defn timer
  "When invoked captures current timestamp in ms, and returns a function
  that when invoked returns the time in ms since the original invocation.

  This is useful if you want to instrument a function and return the time
  it took to run it and inject that into the MDC yourself.

  Example:

  ```clojure
  (log/with-context {:operation \"do-something\"}
    (let [get-run-time-ms (log/timer)
          result (do-something)]
      (log/info {:run-time-ms (get-run-time-ms)} \"do-something completed\")
      result))
  ```
  "

  [] ^long
  (let [start-time-ms (System/currentTimeMillis)]
    (fn ^{:start-time-ms start-time-ms} get-run-time-ms' []
      (- (System/currentTimeMillis) ^long start-time-ms))))
