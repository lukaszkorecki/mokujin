(ns mokujin.logback-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [mokujin.logback :as logback]))

(deftest get-level-test
  (testing "returns the root logger level when called with no args"
    (let [original (logback/get-level)]
      (try
        (logback/set-level! :warn)
        (is (= :warn (logback/get-level)))
        (finally
          (logback/set-level! original)))))

  (testing "named logger inherits its parent level when none is set directly"
    (let [logger-name "mokujin.logback-test.inherits"
          original-root (logback/get-level)]
      (try
        (logback/set-level! :warn)
        (is (= :warn (logback/get-level logger-name))
            "no direct level → effective level walks up to root")
        (logback/set-level! logger-name :debug)
        (is (= :debug (logback/get-level logger-name))
            "direct level wins over inherited")
        (finally
          (logback/set-level! original-root))))))
