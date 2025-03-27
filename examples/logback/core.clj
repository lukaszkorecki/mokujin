(ns core
  (:require [mokujin.log :as log]
            [mokujin.logback :as logback]))

(defn -main
  [& _args]
  (log/info "hello" {:some "context"})
  (log/info "no context")
  (log/with-context {"some" "ctx"}
    (log/with-context {:nested "true"}
      (log/warn "hello")
      (log/infof "hello %s" "world")
      (log/info "nested"))

    (log/error (ex-info "oh no" {:exc :data}) "oh no" {:error "yes"}))
  true)