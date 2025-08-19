(ns mokujin.log
  (:require
   [clojure.tools.logging :as log]
   [mokujin.context.format :as ctx.fmt])
  (:import
   (org.slf4j MDC)))

(def ^:dynamic *context-formatter* ctx.fmt/stringify)

;; TODO: use binding for context?
(defn mdc-put
  "Take a context map and put it into the MDC. Keys and values will be stringified."
  [ctx]
  (doseq [[k v] (*context-formatter* ctx)]
    (MDC/put ^String k ^String v)))

(defmacro with-context
  "Set  context map for the form. Ideally, the context map should use unqualified keywords or strings for keys.
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
  ([msg ctx]
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
  ([msg ctx]
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
  ([msg ctx]
   (with-meta
     `(with-context ~ctx
        (log/log :debug ~msg))
     (meta &form))))

(defmacro trace
  "Trace log, pass message or ctx+message"
  ([msg]
   (with-meta
     `(log/log :trace ~msg)
     (meta &form)))
  ([msg ctx]
   (with-meta
     `(with-context ~ctx
        (log/log :trace ~msg))
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
  ([exc msg ctx]
   (with-meta
     `(with-context ~ctx #_(merge ~ctx (ex-data ~exc)) ;; XXX: should we merge ex-data here?
                    (log/log :error ~exc ~msg))
     (meta &form))))

;; TODO: create a deflog macro which reduces duplication above ^^^^

;; See how Cambium did it: https://github.com/cambium-clojure/cambium.core/blob/31a67a6ea2dd54ed9497873af7bb17a8213f8d37/src/cambium/core.clj#L158C1-L171C112

;; re-export infof/errorf/warnf/debugf for convenience,
;; API is unchanged  - as in, context map is not supported
;; TODO: we could destructure last arg to be a context map, but what if somebody passes something that shouldn't be in the context?
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

(defmacro tracef [& args]
  (with-meta
    `(log/logf :trace ~@args)
    (meta &form)))

;; Re-export generic log functions but with context support
;; NOTE: these are SLOWER than dedicated log macros
(defmacro log
  "Log wrapper which is helpful if you need to programatically control the log level"
  ([level msg]
   (with-meta
     `(log/log ~level ~msg)
     (meta &form)))
  ([level msg context?]
   (with-meta
     `(cond
        (and (string? ~msg)
             (map? ~context?)) (with-context ~context?
                                 (log/log ~level ~msg))

        (and (string? ~msg)
             (not (map? ~context?))) (log/log ~level ~msg))
     (meta &form))))

(defmacro logf
  "Log wrapper which is helpful if you need to programatically control the log level."
  [level msg & args]
  (with-meta
    `(log/logf ~level ~msg ~@args)
    (meta &form)))
