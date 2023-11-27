(ns core
  (:require [mokujin.log :as log]))

(defn -main
  [& _args]
  (log/info {:some "context"} "hello")
  (log/info "no context")
  (log/with-context {"some" "ctx"}
    (log/with-context {:nested "true"}
      (log/warn "hello")
      (log/infof "hello %s" "world")
      (log/info "nested"))

    (log/error {:error "yes"} (ex-info "oh no" {:exc :data}) "oh no"))
  true)
