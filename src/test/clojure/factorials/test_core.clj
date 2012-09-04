(ns factorials.test-core
  (:use clojure.test
         factorials.core))

(deftest test-it-all
  (testing "Everything from factorials.core"
    (let [fac (* 1 2 3 4 5)]
      (is (== fac (factorial-using-recur 5)))
      (is (== fac (factorial-using-reduce 5)))
      (is (== fac (factorial-using-apply-range 5)))
      (is (== fac (factorial-using-apply-iterate 5)))
      (is (== fac ((make-fac-function 5))))
      (is (== fac ((factorial-function-macro 5))))
      (is (== fac (factorial-using-eval-and-cons 5)))
      (is (== fac (factorial-using-do-dotimes 5)))
      (is (== fac (factorial-using-do-while 5)))
      (is (== fac (factorial-using-atoms-while 5)))
      (is (== fac (factorial-using-ref-dosync 5 2)))
      (is (== fac (factorial-using-agent 5 2)))
      (is (== fac (factorial-using-pmap-reduce 5 2)))
      (is (== fac ((factorial-using-pvalues-reduce 5 2)))) ;; macro
      (is (== fac ((factorial-using-pcalls-reduce 5 2)))) ;; macro
      (is (== fac (trampoline (factorial-for-trampoline 5))))
      (is (== fac (factorial-using-multimethods 5)))
      (is (== fac (factorial-using-javainterop 5)))
      (is (== fac (factorial-using-javainterop-and-pipeline 5)))
      (is (== fac (-> (newFactorialComputer 5) .compute)))
      (is (== fac (factorial-from-incanter 5))))))
