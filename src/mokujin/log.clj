(ns mokujin.log
  (:require
   [clojure.tools.logging :as log]
   [clojure.tools.logging.impl :as log-impl])
  (:import
   (org.slf4j MDC)))

(defn not-blank? [v] (and v (not (.isBlank ^String v))))

(defn valid-key? [key]
  (let [nk (name key)]
    (when (not-blank? nk)
      nk)))

(def format-key
  (memoize (fn
             [key]
             (when-let [nk (valid-key? key)]
               (.replaceAll ^String nk "-" "_")))))

(defn- add! [[k v]]
  (when-let [k (and (not-blank? (str v)) (valid-key? k))]
    (MDC/put (format-key k) (str v))))

(defn mdc-put [ctx]
  (doall (map add! ctx)))

(defn- remove! [[k _v]]
  (MDC/remove (format-key k)))

(defn mdc-remove [ctx]
  (doall (map remove! ctx)))

(defmacro with-context
  "Set  context map for the form. The context map should use unqualified keywords or strings for keys.
  Values will be stringified."
  [ctx & body]
  `(do
     (mdc-put ~ctx)
     (try
       (do ~@body)
       (finally
         (mdc-remove ~ctx)))))

(defn current-context
  "Returns the current context map, useful if you want to pass it to another process to continue logging with the same context.
  Note that due to how MDC works, this will only return the context map for
  the current thread and map keys will be strings, not keywords."
  []
  (into {} (MDC/getCopyOfContextMap)))


;; IDEA: allow users to define transformation this doesn't have to be handled here
(defn ^:redef transform-ctx [ctx] ctx #_(walk/stringify-keys ctx))


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

(defn timing
  "Returns a map of:
  - start-time-ms
  - get-run-time-ms - returns the time in ms since start-time-ms

  This is useful if you want to instrument a function and return the time it took to run it and inject that into
  the MDC.

  Example:
  (log/with-context {:operation \"do-something\"}
  (let [{:keys [_start-time-ms get-run-time-ms]} (timing)
        result (do-something)]
    (log/info {:run-time-ms (get-run-time-ms)}
      \"do-something completed\")
    result"

  []
  (let [start-time-ms ^long (System/currentTimeMillis)]
    {:start-time-ms start-time-ms
     :get-run-time-ms (fn get-run-time-ms' [] (- (System/currentTimeMillis) ^long start-time-ms))}))
