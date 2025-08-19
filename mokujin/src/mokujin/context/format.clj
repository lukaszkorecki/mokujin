(ns mokujin.context.format
  (:refer-clojure :exclude [flatten])
  (:require [clojure.string :as str]))
(set! *warn-on-reflection* true)

(def ^:private l {nil "null" false "false"})

(defn- ->str
  [val] ^String
  (case val
    nil "null"
    false "false"
    #_else (if (keyword? val)
             ;; all of these are quite slow
             #_(.replaceAll ^String (.getName ^clojure.lang.Keyword val) "-" "_")
             #_(.replaceAll ^String (str (symbol val)) "-" "_")
             #_(.getName ^clojure.lang.Keyword val)
             ;; fastest way to get a fully-quallified keyword as a string
             (str (symbol val))
             (Object/.toString val))))

(defn stringify
  "Given context map `ctx`, returns a new map with all keys and values converted to strings,
  this also means that any netsed data structures will be stringified as well, and most likely very
  hard to parse when using structured data appender like JSON."
  [ctx]
  (persistent!
   (reduce-kv (fn [m k v]
                (assoc! m (->str k) (->str v)))
              (transient {})
              ctx)))

(defn flatten
  "Given a context map it will 'flatten' it, i.e. convert all nested maps and collections into a single map and
  keys will be strings with dot notation, e.g. `:a.b.c` for nested map `{:a {:b {:c 1}}}`.

  Collections will be addressed by key paths with indexes eg `:a.0.b.c` for `{:a [{:b {:c 1}}]}`.

  NOTE: this is quite slower than default `stringify` formatter. Use it only if you can take the perf hit and
  still have addressable context map keys. "
  [ctx]

  (letfn [(flatten-impl [m prefix]
            (reduce-kv (fn [acc k v]
                         (let [new-key (if prefix
                                         (str prefix "." (->str k))
                                         (->str k))]
                           (cond
                             (map? v) (merge acc (flatten-impl v new-key))
                             (coll? v) (reduce-kv
                                        (fn [a i e] (merge a {(str new-key "." i) e}))
                                        acc v)
                             :else (assoc acc new-key v))))
                       {}
                       m))]
    (flatten-impl ctx nil)))
