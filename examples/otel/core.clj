(ns core
  (:require [mokujin.log :as log]
            [mokujin.logback :as lb]
            [clojure.pprint]))

(lb/configure! {:config ::lb/text})

(defn -main []
  (log/with-context {:app "otel-log-example"}

    (let [release "ZERO DEP"
          otel-context (->> (System/getenv)
                            (reduce-kv (fn [m k v]
                                         (if (re-find #"OTEL" k)
                                           (assoc m k v)
                                           m))

                                       {}))]
      (log/info "Application started" otel-context)

      (while true
        (log/info (str "Heartbeat " release) {:status (rand-nth ["alive" "pending" "foobar" "test"])
                                              :some-number (rand-int 100)})
        (Thread/sleep 1000)))))
