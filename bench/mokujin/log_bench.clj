(ns mokujin.log-bench
  (:require [mokujin.log :as log]
            [clojure.tools.logging :as logging]
            [criterium.core :as criterium]))

(set! *warn-on-reflection* true)

(defn mokujin-log+context []
  (log/info "hello" {:some "context"})
  (log/info "no context")
  (log/with-context {"some" "ctx"}
    (log/with-context {:nested "true"}
      (log/warn "hello")
      (log/infof "hello %s" "world")
      (log/info "nested"))
    (log/error (ex-info "oh no" {:exc :data}) "oh no" {:error "yes"}))
  true)

(defn mokujin-log []
  (log/infof "hello %s=%s" :some "context")
  (log/info "no contgext")
  (log/warnf "hello %s=%s %s=%s" :some "context" :nested "true")
  (log/infof "nested %s=%s" :some "context")
  (log/errorf (ex-info "oh no" {:exc :data}) "oh no %s=%s" :error "yes")
  true)

(defn tools-logging-log []
  (logging/infof "hello %s=%s" :some "context")
  (logging/info "no contgext")
  (logging/warnf "hello %s=%s %s=%s" :some "context" :nested "true")
  (logging/infof "nested %s=%s" :some "context")
  (logging/errorf (ex-info "oh no" {:exc :data}) "oh no %s=%s" :error "yes")
  true)

(defn tools-logging-log+context []
  (log/with-context {:some "ctx"}
    (logging/infof "hello %s=%s" :some "context")
    (logging/info "no contgext")
    (log/with-context {:other "aha"}
      (logging/warnf "hello %s=%s %s=%s" :some "context" :nested "true"))
    (logging/infof "nested %s=%s" :some "context"))
  (logging/errorf (ex-info "oh no" {:exc :data}) "oh no %s=%s" :error "yes")
  true)

(defn -main []
  (doseq [f [#'mokujin-log #'mokujin-log+context #'tools-logging-log #'tools-logging-log+context]]
    (let [result (criterium/quick-benchmark* f {})]
      (criterium/report-point-estimate (str f) (:mean result)))))
