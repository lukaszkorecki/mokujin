(ns mokujin.log
  (:require
   [clojure.tools.logging :as log]
   [mokujin.context.format :as ctx.fmt])
  (:import
   (org.slf4j MDC)))

(set! *warn-on-reflection* true)

(def ^:dynamic *context-formatter* ctx.fmt/stringify)

(def formatters
  {::default ctx.fmt/stringify
   ::stringify ctx.fmt/stringify
   ::flatten ctx.fmt/flatten})

(defn set-context-formatter!
  "Set the context formatter to use for `with-context` and `mdc-put`.
  The formatter should be a function that takes a context map and returns a new map with stringified keys and values."
  [formatter]
  (if-let [fmt (formatters formatter)]
    (alter-var-root #'*context-formatter* (constantly fmt))
    (throw (IllegalArgumentException.
            (str "Unknown context formatter: " formatter " Valid options are: " (keys formatters))))))

;; TODO: use binding for context?

(defn- p! [_ k v] (MDC/put ^String k ^String v))

(defn mdc-put
  "Take a context map and put it into the MDC. Keys and values will be stringified."
  [ctx]
  (reduce-kv p! {} (*context-formatter* ctx)))

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

(defmacro ^:private deflogger
  "This macro is used internally to only define normal namespace-based level loggers."
  [level-sym]
  (let [level-key (keyword level-sym)
        level-doc (str "Similar to clojure.tools.logging/" level-sym ". but with optional MDC context arg.")
        arglists ''([msg] [msg context])]
    `(defmacro ~level-sym
       ~level-doc
       {:arglists ~arglists}
       ([msg#]
        (with-meta
          `(log/log ~~level-key ~msg#)
          ~'(meta &form)))
       ([msg# ctx#]
        (with-meta
          `(with-context ~ctx#
             (log/log ~~level-key ~msg#))
          ~'(meta &form))))))

(declare info warn debug trace error)

(deflogger info)
(deflogger warn)
(deflogger debug)
(deflogger trace)

;; Error is a special case because it handles throwables
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
     `(with-context ~ctx
        (log/log :error ~exc ~msg))
     (meta &form))))

;; <level>f macros are used for formatted messages, they do not support context maps
(declare infof warnf debugf tracef errorf)

(def ^:private f-sym->level-kw
  "Map of format symbols to log levels"
  {'infof :info
   'warnf :warn
   'debugf :debug
   'tracef :trace
   'errorf :error})

(defmacro ^:private defloggerf
  "This macro is used internally to only define normal namespace-based level loggers."
  [level-sym]
  (let [level-key (get f-sym->level-kw level-sym)
        level-doc (str "Same as clojure.tools.logging/" level-sym ".")
        arglists ''([msg & args])]
    `(defmacro ~level-sym
       ~level-doc
       {:arglists ~arglists}
       [& msg#]
       (with-meta
         `(log/logf ~~level-key ~@msg#)
         ~'(meta &form)))))

(defloggerf infof)
(defloggerf warnf)
(defloggerf errorf)
(defloggerf debugf)
(defloggerf tracef)

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
  "Logf wrapper which is helpful if you need to programatically control the log level."
  [level msg & args]
  (with-meta
    `(log/logf ~level ~msg ~@args)
    (meta &form)))
