(ns core
  (:require [mokujin.log :as log]))

(defn -main
  [& _args]
  (log/info {:some "context"} "hello"))
