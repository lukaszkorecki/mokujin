(ns mokujin.log-bench
  (:require [mokujin.log :as log]
            [clojure.tools.logging :as logging]
            [criterium.core :as bench]))

(defmacro tools-logging-log-all []
  `(do
     (logging/infof "hello %s=%s" :some "context")
     (logging/info "no contgext")
     (logging/warnf "hello %s=%s %s=%s" :some "context" :nested "true")
     (logging/infof "nested %s=%s" :some "context")
     (logging/errorf (ex-info "oh no" {:exc :data}) "oh no %s=%s" :error "yes")
     true))

(defn -main []

  (println "clojure.tools.logging")
  (bench/quick-bench (tools-logging-log-all))

  (println "mokujin.log")
  (bench/quick-bench (log/log-all)))
