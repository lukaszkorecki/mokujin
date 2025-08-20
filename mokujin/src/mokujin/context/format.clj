(ns mokujin.context.format
  (:refer-clojure :exclude [flatten])
  (:require [clojure.string :as str]))
(set! *warn-on-reflection* true)

(defn- ->str
  [val] ^String

  (if val
    (if (keyword? val)
      ;; all of these are quite slow
      #_(.replaceAll ^String (.getName ^clojure.lang.Keyword val) "-" "_")
      #_(.replaceAll ^String (str (symbol val)) "-" "_")
      #_(.getName ^clojure.lang.Keyword val)
      ;; fastest way to get a fully-quallified keyword as a string
      (str (symbol val))
      ;; otherwise just use toString
      (Object/.toString val))
    (case val
      nil "null"
      false "false")))

(defn stringify
  "Given context map `ctx`, returns a new map with all keys and values converted to strings,
  this also means that any netsed data structures will be stringified as well, and most likely very
  hard to parse when using structured data appender like JSON."
  [ctx]
  (->> ctx
       (reduce-kv (fn [m k v]
                    (assoc! m (->str k) (->str v)))
                  (transient {}))
       persistent!))

;;;;;

(defn- path->str [segments]
  (->> segments
       (map ->str) ; ensure all segments are strings
       (str/join ".")))

(defn flatten
  "Given a context map it will 'flatten' it, i.e. convert all nested maps and collections into a single map and
  keys will be strings with dot notation, e.g. `:a.b.c` for nested map `{:a {:b {:c 1}}}`.

  Collections will be addressed by key paths with indexes eg `:a.0.b.c` for `{:a [{:b {:c 1}}]}`.

  NOTE: this is quite slower than default `stringify` formatter. Use it only if you can take the perf hit and
  still have addressable context map keys.

  Values will be stringified just like in `stringify` formatter.

  Example:
  { :a { :b 1
         :c [2 3]
         :d {:e 4} }
    :f \"string\"  => { \"a.b\" \"1\"
                       \"a.c.0\" \"2\"
                       \"a.c.1\" \"3\"
                       \"a.d.e\" \"4\"
                       \"f\" \"string\" }
  "
  [ctx]
  (loop [stack (list [[] ctx]) ; [path-vec node]
         acc (transient {})]
    (if (empty? stack)
      (-> (persistent! acc)
          (update-keys path->str)
          (update-vals ->str))
      (let [[path node] (peek stack)
            stack' (pop stack)]
        (cond
          (map? node) (let [next-stack (reduce-kv
                                        (fn [st k v] (conj st [(conj path k) v]))
                                        stack'
                                        node)]
                        (recur next-stack acc))

          (sequential? node) (let [vnode (vec node)
                                   next-stack (reduce-kv
                                               (fn [st i v] (conj st [(conj path i) v]))
                                               stack'
                                               vnode)]
                               (recur next-stack acc))

          :else
          (recur stack' (assoc! acc path node)))))))
