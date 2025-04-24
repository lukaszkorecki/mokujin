#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns linting
  (:require [mokujin.log :as log]))

(log/info "don't run me :-)")
(System/exit 0)

;; valid
(log/info "foobar" {:test 1})

;; invalid
(log/info {:msg "test"} "bananas")

;;  invalid
(log/error 'foo "bar" {:test 1})


(try
  (/ 1 0)
  (catch Exception err
    ;; valid
    (log/errorf err "woah %s" "bananas")))

;; invalid

(log/errorf {:foo "bar"} "bananas")


;; valid
(log/error "oh no")
