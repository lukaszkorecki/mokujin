(ns mokujin.log
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log])
  (:import
   (org.slf4j MDC)))

(defn format-key* [key]
  (-> (if (qualified-keyword? key)
        (str (namespace key) "_" (name key))
        (name key))
      (str/replace #"[\-\.]" "_")))

(def format-key (memoize format-key*))

(defn valid-key? [key]
  (or (and (string? key) (not (str/blank? key)))
      (keyword? key)))

(defn add-context [ctx]
  (mapv (fn [[k v]]
          (when (valid-key? k)
            (MDC/put (format-key k) (str v))))
        ctx))

(defn remove-context [ctx]
  (mapv (fn [[k _v]]
          (when (valid-key? k)
            (MDC/remove (format-key k))))
        ctx))

(defmacro with-context
  "Wrap a block of code with a context map. The context map is a map of
  anything, but ideally it's keywords and strings."
  [ctx & body]
  `(do
     (add-context ~ctx)
     (try
       (do ~@body)
       (finally
         (remove-context ~ctx)))))

(defmacro with-timing
  "Wrap a block of code with a timing log. Timing is logged using name and info level and added to the context"
  [name & body]
  `(let [start# (System/currentTimeMillis)
         ret# (do ~@body)
         runtime-ms# (- (System/currentTimeMillis) start#)]
     (with-context {:timing_name ~name :runtime_ms runtime-ms#}
       (log/logf :info "%s took %sms" ~name runtime-ms#))
     ret#))

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

(defmacro log-all []
  `(do
     (info {:some "context"} "hello")
     (info "no context")
     (with-context {"some" "ctx"}
       (with-context {:nested "true"}
         (warn "hello")
         (infof "hello %s" "world")
         (info "nested"))

       (error {:error "yes"} (ex-info "oh no" {:exc :data}) "oh no"))
     true))
