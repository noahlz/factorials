(ns factorials.core)

;; How many ways can we compute a factorial?

(defn factorial-using-recur [x]
  (loop [current x
         next (dec current)
         total 1]
    (if (> current 1)
      (recur next (dec next) (* total current))
      total)))

(defn factorial-using-reduce [x]
  (reduce * (range 1 (inc x))))

(defn factorial-using-apply-range [x]
  (apply * (range 1 (inc x))))

(defn factorial-using-apply-iterate [x]
  (apply * (take x (iterate inc 1))))

(defn make-fac-function [n]
  (fn [] (reduce * (range 1 (inc n)))))

; produces a function that will calculate the factorial on demand
(defmacro factorial-function-macro [x]
  `(fn [] (* ~@(range 1 (inc x)))))
;; (macroexpand `(factorial-using-macro 10))
;; => (fn* ([] (clojure.core/* 1 2 3 4 5 6 7 8 9 10)))

(defn factorial-using-eval-and-cons [x]
  (eval (cons '* (range 1 (inc x)))))

;; Ugh...really? PROTIP: DON'T DO THIS!
;; See: http://stackoverflow.com/q/12099329/7507
;; Note that def binds to the namespace, not the scope of the function.
(defn factorial-using-do-dotimes [x]
  (do
    (def a 1)
    (dotimes [i x]
      (def a (* a (inc i)))))
  a)
;; Brrr...

;; More awfulness...
;; Same correctness problem as above! def'd vars are bound to the namespace.
(defn factorial-using-do-while [x]
  (do
    (def a 0)
    (def res 1)
    (while (< a x)
      (def a (inc a))
      (def res (* res a)))
    res))

;; Note: you would never use atoms locally-scoped like this.
;; (TODO: come up with a example that uses actual concurrency)
(defn factorial-using-atoms-while [x]
  (let [a (atom 0)
        res (atom 1)]
    (while (> x @a)
      (swap! res * (swap! a inc)))
    @res))

(defn factorial-using-ref-dosync [x psz]
  (let [result (ref 1)
        parts (partition-all psz (range 1 (inc x)))
        tasks (for [p parts]
      (future
        (Thread/sleep (rand-int 10))
        (dosync (alter result #(apply * % p)))))]
    (dorun (map deref tasks))  ;; reminder: map is lazy, so force execution (but discard results)
    @result))

(defn factorial-using-agent [x psz]
  (let [result (agent 1)
        parts (partition-all psz (range 1 (inc x)))]
    (doseq [p parts]
      (send result #(apply * % p)))
    (await result)
    @result))
;; NOTE: (send result * p) didn't work above because of how * and send are overloaded
;; No (future) used as (send) updates the agent in a separate thread of execution.
;; Also: per Joy of Clojure (11.3.5) this is not the best use case for Agents (particularly
;; the (await) call at the end.

;; Note: I suspect there's a better way of doing this...
;; See: http://codereview.stackexchange.com/q/15160/9032
(defn factorial-using-pmap-reduce [x psz]
  (let [parts (partition-all psz (range 1 (inc x)))
        sub-factorial-fn #(apply * %)]
    (reduce * (pmap sub-factorial-fn parts))))

;; Again, feels wrong - contortions to use pvalues. pmap seems more natural
(defmacro factorial-using-pvalues-reduce [x psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc x)))]
    (cons '* p))]
    `(fn [] (reduce * (pvalues ~@exprs)))))
;; Also, since the range of integers is finite, probably want to cache the returned function
;; (otherwise the perm gen will grow)

;; Once more using pcalls - again, blah.
(defmacro factorial-using-pcalls-reduce [x psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc x)))]
    `(fn [] (* ~@p)))]
    `(fn [] (reduce * (pcalls ~@exprs)))))

(defn factorial-for-trampoline [target]
  (letfn
    [(next-fac-fn-or-value [target previous-step previous-value]
       (let [current-step (inc previous-step)
             current-value (* current-step previous-value)]
         (if (= target current-step)
           current-value
           #(next-fac-fn-or-value target current-step current-value))))]
    (next-fac-fn-or-value target 1 1)))

;; Multimethods dispatching on a Factorial record structure
(defrecord Factorial [n value])

;; Map destructuring: http://blog.jayfields.com/2010/07/clojure-destructuring.html
(defmulti factorial-using-multimethods
  (fn ([limit] true)
      ([limit {:keys [n]}] (< n limit))))

(defmethod factorial-using-multimethods true
  ([limit] (factorial-using-multimethods limit (new Factorial 1 1)))
  ([limit fac]
    (let [next-factorial (-> fac (update-in [:n] inc)
                                  (update-in [:value] #(* % (:n fac))))]
       (factorial-using-multimethods limit next-factorial))))

(defmethod factorial-using-multimethods false
  ([limit fac] (* limit (:value fac))))


(defn factorial-using-javainterop [target]
  (example.Factorial/calculate 5))

(import 'example.Factorial$Builder)
(defn factorial-using-javainterop-and-pipeline [target]
  (-> (Factorial$Builder.) (.factorial target) .build .compute))
;; (clojure.walk/macroexpand-all '(-> (Factorial$Builder.) (.factorial target) .build .compute))
;; => (. (. (. (new Factorial$Builder) factorial target) build) compute)
;; Also equivalent to:
;; (.compute (.build (.factorial (example.Factorial$Builder.) 5)))

;; Re-use one of our functions in Java-compatible class
(defn newFactorialComputer [x]
  (reify example.ValueComputer
    (compute [this] (factorial-using-reduce x))))

;; Finally...why not just use Incanter?
(require '[incanter.core :only factorial])
(defn factorial-from-incanter [x]
  (incanter.core/factorial x))

