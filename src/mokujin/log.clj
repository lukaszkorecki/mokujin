(ns mokujin.log
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log])
  (:import
   (org.slf4j MDC)))

(defn add-context [ctx]
  (mapv (fn [[k v]]
          (when-not (str/blank? (str k))
            (MDC/put (name k) (str (if (keyword? v) (name v) v)))))
        ctx))

(defn remove-context [ctx]
  (mapv (fn [[k _v]]
          (when-not (str/blank? (str k))
            (MDC/remove (name k))))
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

(defmacro showcase []
  `(do
     (info {:some "context"} "hello")
     (info "no context")
     (with-context {"some" "ctx"}
       (with-context {:nested "true"}
         (warn "hello")
         (info "nested"))

       (error {:error "yes"} (ex-info "oh no" {:exc :data}) "oh no"))))
