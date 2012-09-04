(ns factorials.core)

;; # How many ways can we compute a factorial?
;; This project demonstrates a variety of of Clojure language features and
;; library functions using factorial computation as an example.
;;
;; Many Clojure tutorials (and CS textbooks, for that matter) use factorial computation
;; to demonstrate recursion.  I implemented such a function in Clojure and thought: "why stop here?"

;; ## Part 1: The Basics

;; The classic (and verbose) `loop` + `recur`
(defn factorial-using-recur [x]
  (loop [current x
         next (dec current)
         total 1]
    (if (> current 1)
      (recur next (dec next) (* total current))
      total)))

;; `reduce` `*`
;; on a
;; `range`
(defn factorial-using-reduce [x]
  (reduce * (range 1 (inc x))))

;; `apply` `*`
;; to a
;;`range`
(defn factorial-using-apply-range [x]
  (apply * (range 1 (inc x))))

;; `apply` `*`
;;  but `take`-ing from an `iterate`
(defn factorial-using-apply-iterate [x]
  (apply * (take x (iterate inc 1))))

;; ## "Code is data, data is code"

;; Higher-order functions FTW
(defn make-fac-function [n]
  (fn [] (reduce * (range 1 (inc n)))))

;;`defmacro` - produces a function that will calculate a factorial value on-demand
;;
;;    (macroexpand `(factorial-using-macro 10))
;;     => (fn* ([] (clojure.core/* 1 2 3 4 5 6 7 8 9 10)))
(defmacro factorial-function-macro [x]
  `(fn [] (* ~@(range 1 (inc x)))))

;; Clojure as an homiconic language
;; using `eval` `cons` and the `'` operator.
(defn factorial-using-eval-and-cons [x]
  (eval (cons '* (range 1 (inc x)))))

;; ## Parallel Computation

;; Using `future` `dosync` and `alter`
;;
;; Reminder: `map` is lazy, so force execution with `dorun` (but discard results)
(defn factorial-using-ref-dosync [x psz]
  (let [result (ref 1)
        parts (partition-all psz (range 1 (inc x)))
        tasks (for [p parts]
      (future
        (Thread/sleep (rand-int 10))
        (dosync (alter result #(apply * % p)))))]
    (dorun (map deref tasks))
    @result))

;; Using `agent` `send` and `await`
;;
;; NOTE: `(send result * p)` didn't work above because of how `*` and `send` are overloaded
;; (perhaps this is obvious, but I puzzled over it for while).
;;
;; Also: per Joy of Clojure (11.3.5) this is not the best use case for Agents (particularly
;; the `await` call at the end.
(defn factorial-using-agent [x psz]
  (let [result (agent 1)
        parts (partition-all psz (range 1 (inc x)))]
    (doseq [p parts]
      (send result #(apply * % p)))
    (await result)
    @result))

;; ### `pmap` to the rescue.
;; If the previous code looked arduous, never fear. Enter: `pmap`
;;
;; Yes, it is `map` executed in parallel - lazily.
(defn factorial-using-pmap-reduce [x psz]
  (let [parts (partition-all psz (range 1 (inc x)))
        sub-factorial-fn #(apply * %)]
    (reduce * (pmap sub-factorial-fn parts))))

;; There is also `pvalues`, which evaluates a list of expressions in parallel.
;;
;; Even though this works, it feels wrong - jumping through hoops with `defmacro`. `pmap`
;; feels more natural.
(defmacro factorial-using-pvalues-reduce [x psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc x)))]
                (cons '* p))]
    `(fn [] (reduce * (pvalues ~@exprs)))))

;; `pvalues` calls a collection of zero-arg functions in parallel to create a lazy sequence.
;; Again, had to jump through hoops to get it to work.
(defmacro factorial-using-pcalls-reduce [x psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc x)))]
    `(fn [] (* ~@p)))]
    `(fn [] (reduce * (pcalls ~@exprs)))))

;; ## More Advanced Stuff

;; ### Trampoline
;; Using `letfn` to write a factorial function that can be
;; used with `trampoline`.  Not exactly "mutually recursive" but works.
;;
;;     (trampoline (factorial-for-trampoline 5))
;;     => 120
(defn factorial-for-trampoline [target]
  (letfn
    [(next-fac-fn-or-value [target previous-step previous-value]
       (let [current-step (inc previous-step)
             current-value (* current-step previous-value)]
         (if (= target current-step)
           current-value
           #(next-fac-fn-or-value target current-step current-value))))]
    (next-fac-fn-or-value target 1 1)))

;; ### Multimethods

;; Defines a "struct" (which is actually a full-fledged Java class)
;; That has fields `n` and `value`
(defrecord Factorial [n value])

;; Define the root of the multimethod, which dispatches based on "true" or "false," where
;; "false" means we reached the end of our factorial computation.
;;
;; (Highly recommended: Jay Fields' terrific blog entry on [Map Destructuring](http://blog.jayfields.com/2010/07/clojure-destructuring.html))
(defmulti factorial-using-multimethods
  (fn ([limit] true)
      ([limit {:keys [n]}] (< n limit))))

;; Compute the next step of the factorial computation...
(defmethod factorial-using-multimethods true
  ([limit] (factorial-using-multimethods limit (new Factorial 1 1)))
  ([limit fac]
    (let [next-factorial (-> fac (update-in [:n] inc)
                                 (update-in [:value] #(* % (:n fac))))]
      (factorial-using-multimethods limit next-factorial))))

;; And when we have a Factorial record with `{:n}` of our target iteration,
;; return the result (with one last computation).
(defmethod factorial-using-multimethods false
  ([limit fac] (* limit (:value fac))))

;; ## Java Interop

;; Elsewhere we wrote a plain old Java class that computes a factorial.  We can
;; still use it via Clojure's Java interop.
(defn factorial-using-javainterop [target]
  (example.Factorial/calculate 5))

;; What if our Java team read _Effective Java, 2nd Ed._ and decided to implement
;; the Builder Pattern? We can use the `->` operator (aka "pipeline operator") to
;; get this under control. (Note: `doto` works well for classes that have copious void "setters").
;;
;;
;;
(import 'example.Factorial$Builder)
(defn factorial-using-javainterop-and-pipeline [target]
  (-> (Factorial$Builder.) (.factorial target) .build .compute))
(comment
  (clojure.walk/macroexpand-all '(-> (Factorial$Builder.) (.factorial target) .build .compute))
  => (. (. (. (new Factorial$Builder) factorial target) build) compute)
)
;; Note: thanks to `->`, the above is equivalent to
(comment
  (.compute (.build (.factorial (example.Factorial$Builder.) 5)))
)

;; Re-use one of our functions in Java-compatible class
(defn newFactorialComputer [x]
  (reify example.ValueComputer
    (compute [this] (factorial-using-reduce x))))

;; ## Finally...

;; Why not just use [Incanter](http://incanter.org/)? (DUH!)
(require '[incanter.core :only factorial])
(defn factorial-from-incanter [x]
  (incanter.core/factorial x))

;; # Epilogue: The Hall of Shame

;; Here are some functions I wrote that "work" but have hidden defects are are just plain wrong.
;; Don't do these!

;; Note that `def` binds to the namespace, not the scope of the function.
;;
;; See [my StackOverflow question](http://stackoverflow.com/q/12099329/7507)
(defn factorial-using-do-dotimes [x]
  (do
    (def a 1)
    (dotimes [i x]
      (def a (* a (inc i)))))
  a)

;; Brrr... More awfulness...
;; Has the same correctness problem as above! `def`'d vars are bound to the namespace.
(defn factorial-using-do-while [x]
  (do
    (def a 0)
    (def res 1)
    (while (< a x)
      (def a (inc a))
      (def res (* res a)))
    res))

;; An example using Atoms. I don't think you would never use atoms locally-scoped like this.
;;
;; Perhaps you could contrive an example using the `Factorial` example from earlier and
;; `compare-and-set!`
(defn factorial-using-atoms-while [x]
  (let [a (atom 0)
        res (atom 1)]
    (while (> x @a)
      (swap! res * (swap! a inc)))
    @res))

