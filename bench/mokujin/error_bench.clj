(ns mokujin.error-bench
  (:require [criterium.core :as criterium]
            [mokujin.log :as log]))

(set! *warn-on-reflection* true)

(def ^:private msg "oh no")
(def ^:private ctx {:error "yes" :request-id "abc"})

(defn with-context+error []
  (log/with-context {:error "yes" :request-id "abc"}
    (log/error "oh no")))

(defn error-literal []
  (log/error "oh no" {:error "yes" :request-id "abc"}))

(defn error-runtime []
  (log/error msg ctx))

(defn -main []
  (doseq [f [#'with-context+error #'error-literal #'error-runtime]]
    (let [result (criterium/quick-benchmark* f {})]
      (criterium/report-point-estimate (str f) (:mean result))
      (criterium/report-point-estimate-sqrt "  std-dev" (:variance result)))))
