(ns hooks.log-lint
  (:require [clj-kondo.hooks-api :as api]))

(defn register! [node msg context]
  (api/reg-finding! (-> (meta node)
                        (assoc :message msg)
                        (merge context))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var :redundant-ignore]}
(defn error-log-args
  "Verifies that log/error can be only called with one or two args:
  (log/error exc msg)
  (log/error msg)"
  [{:keys [node]}]
  (let [sexpr (api/sexpr node)
        arg-count (count (rest sexpr))
        [arg1 arg2 & _] (rest sexpr)]
    (cond
      ;; error only accepts [throwable msg] or [msg] as args, so anything more than that is not valid
      (>= arg-count 3)
      (register! node "too many arguments passed to log/error" {:type :mokujin.log/invalid-arg-count})

      ;; we have two args but first is a string, indicates that 2nd arg is a context
      (and (= arg-count 2)
           (string? arg1))
      (register! node "log/error doesn't accept context maps as 2nd argument!" {:type :mokujin.log/error-log-context-not-supported})

      (or (map? arg1) (map? arg2))
      (register! node "log/error doesn't accept maps as log statements" {:type :mokujin.log/error-log-map-args}))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var :redundant-ignore]}
(defn log-args
  "generic log dispatch for info, warn, debug log calls which accept the following:
  (log/info msg-str)
  (log/info msg-str context-map)"
  [{:keys [node]}]
  (let [sexpr (rest (api/sexpr node))
        [msg? ctx? & _rest?] sexpr]
    (cond
      (>= (count sexpr) 3) (register! node "too many arguments" {:type :mokujin.log/invalid-arg-count})
      (not (string? msg?)) (register! node "log message is not a string" {:type :mokujin.log/log-message-not-string})
      (and (= 2 (count sexpr)) (not (map? ctx?))) (register! node "log context is not a map" {:type :mokujin.log/log-context-not-map}))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var :redundant-ignore]}
(defn logf-args
  "Verifies that *f variants never accepts a context map as 2nd argument and have varargs"
  [{:keys [node]}]

  (let [sexpr (api/sexpr node)
        arg-count (count (rest sexpr))
        [arg1 _arg2 & _] (rest sexpr)]
    (cond
      (= arg-count 1)
      (register! node "too few arguments passed to logf" {:type :mokujin.log/invalid-arg-count})

      (not (string? arg1))
      (register! node "log message is not a string" {:type :mokujin.log/log-message-not-string}))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn log-errof-args
  "like `logf-args` but issues a warning if the first argument is not a string"
  [{:keys [node]}]
  (let [sexpr (api/sexpr node)
        arg-count (count (rest sexpr))
        [arg1 arg2 & _] (rest sexpr)]
    (cond
      (= arg-count 1)
      (register! node "too few arguments passed to errorf" {:type :mokujin.log/invalid-arg-count})

      (and
        (= arg-count 2)
        (not (string? arg1)))
      (register! node "too few arguments passed to errorf" {:type :mokujin.log/invalid-arg-count})

      (and (>= arg-count 3)
           (not (string? arg1))
           (not (string? arg2)))
      (register! node (format "log message is not a string %s" [arg1 arg2]) {:type :mokujin.log/log-message-not-string}))))
