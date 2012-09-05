;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; # Exploring Clojure with factorial computation.
;;
;; _"[Fork me on on GitHub](http://github.com/noahlz/factorials)!"_
;;
;; This project demonstrates a variety of of [Clojure](http://clojure.org) language features
;; and library functions using factorial computation as an example.
;;
;; Many Clojure tutorials (and CS textbooks, for that matter) use
; factorial computation to teach recursion.  I implemented such a
; function in Clojure and thought: "why stop there?"
;;
(ns factorials.core)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Basics

;; The classic (and verbose) `loop` + `recur` example.
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## "Code is data, data is code"

;; Higher-order functions FTW.
;;
;; This returns a function that you can later call to compute
;; a factorial value when needed.
;;
;; Usage:
;;
;;     (def fac5 (make-fac-function 5))
;;     (fac5)
;;     => 120
(defn make-fac-function [n]
  (fn [] (reduce * (range 1 (inc n)))))

;;`defmacro` generates Clojure code for a function that will calculate
;; a fixed factorial value on-demand.
;;
;; Similar to the previous function, but note the macro output.
;;
;;      (macroexpand `(factorial-using-macro 10))
;;      => (fn* ([] (clojure.core/* 1 2 3 4 5 6 7 8 9 10)))
;;
(defmacro factorial-function-macro [x]
  `(fn [] (* ~@(range 1 (inc x)))))

;; Here we illustrate Clojure [homoiconicity](http://en.wikipedia.org/wiki/Homoiconicity)
; using `eval` `cons` and `'` (quote)
(defn factorial-using-eval-and-cons [x]
  (eval (cons '* (range 1 (inc x)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Parallel Computation

;; Using `future` `dosync` and `alter`
;;
;; `partition-all` is just fantastic, by the way.
;;
;; Reminder: `map` is lazy, so force execution with `dorun` (effectively discarding the results).
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
;; I found that `(send result * p)` doesn't work here, because of how `*` and `send` are overloaded.
;; Perhaps this is obvious, but I puzzled over it for a while.
;;
;; Also: per Joy of Clojure (§11.3.5) this is not the best use case for Agents (particularly
;; the `await` call at the end).
(defn factorial-using-agent [x psz]
  (let [result (agent 1)
        parts (partition-all psz (range 1 (inc x)))]
    (doseq [p parts]
      (send result #(apply * % p)))
    (await result)
    @result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### pmap to the rescue.

;; If the previous code looked arduous, never fear. Enter: `pmap`
;;
;; Yes, it is `map` executed in parallel (lazily!) .
(defn factorial-using-pmap-reduce [x psz]
  (let [parts (partition-all psz (range 1 (inc x)))
        sub-factorial-fn #(apply * %)]
    (reduce * (pmap sub-factorial-fn parts))))

;; There is also `pvalues`, which evaluates a list of expressions in parallel.
;;
;; Even though this works, it feels wrong having to jump through hoops with `defmacro` like this.
;
; `pmap` is more natural for this use-case.
(defmacro factorial-using-pvalues-reduce [x psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc x)))]
                (cons '* p))]
    `(fn [] (reduce * (pvalues ~@exprs)))))

;; `pvalues` calls a collection of zero-arg functions in parallel to create a lazy sequence.
;; Again, had to use `defmacro` to accomplish my goal.
(defmacro factorial-using-pcalls-reduce [x psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc x)))]
                `(fn [] (* ~@p)))]
    `(fn [] (reduce * (pcalls ~@exprs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## More Advanced Stuff

;; ### Trampoline

;; Here I used `letfn` to create a factorial function for use with `trampoline`.
;; It's not exactly "mutually recursive" - but it works as intended.
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### Multimethods
;;
;; I also thought of a way to compute a factorial using multimethods
;; and some recursion.

;; First define a "struct" (which actually becomes a full-fledged Java class)
;; that has the fields `n` and `value`
;;
;; A tuple in the form of `{:n 1 :value 1}` would also have worked here, but (to be honest) I wanted to use
;; `defrecord` in at least one of these examples.
(defrecord Factorial [n value])

;; Define the root of the multimethod, which dispatches based on "true" or "false," where
;; "false" means we reached the end of our factorial computation.
;;
;; (Highly recommended: Jay Fields' terrific blog entry on
;; [Map Destructuring](http://blog.jayfields.com/2010/07/clojure-destructuring.html))
(defmulti factorial-using-multimethods
  (fn ([limit] true)
      ([limit {:keys [n]}] (< n limit))))

;; Repeatedly dispatch to this method while `n < limit`
;;
;; Note how I had to overload this method to initialize our `Factorial` struct on the first invocation.
(defmethod factorial-using-multimethods true
  ([limit] (factorial-using-multimethods limit (new Factorial 1 1)))
  ([limit fac]
    (let [next-factorial (-> fac (update-in [:n] inc)
                                 (update-in [:value] #(* % (:n fac))))]
      (factorial-using-multimethods limit next-factorial))))

;; We hit this multimethod when our `Factorial` struct has the desired `:n` value.
; It returns the final factorial value with one last computation.
(defmethod factorial-using-multimethods false
  ([limit fac] (* limit (:value fac))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Java Interop

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### Calling a Java Class

;; Elsewhere we wrote a plain old Java class with a static method that
;; computes factorials.  We can still re-use that legacy code via Clojure's Java interop.
(defn factorial-using-javainterop [target]
  (example.Factorial/calculate 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### Taming Java Complexity

;; What if our Java team read _Effective Java, 2nd Ed._ and decided to use
;; the Builder pattern?
;;
;; We can use the `->` operator (aka the "pipeline operator") to
;; get this under control.
;;
;; Note: `doto` works well for "JavaBean" classes having a large number of `void` "setters".
(import 'example.Factorial$Builder)
(defn factorial-using-javainterop-and-pipeline [target]
  (-> (Factorial$Builder.) (.factorial target) .build .compute))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### More on the Pipeline Macro

;; I found `clojure.walk/macroexpand-all` really useful for understanding and debugging the `->` macro:

;; Executing the following at the REPL
(comment
  (clojure.walk/macroexpand-all '(-> (Factorial$Builder.) (.factorial target) .build .compute))
)
;; outputs
(comment
  => (. (. (. (new Factorial$Builder) factorial target) build) compute)
)
;; which is equivalent to
(comment
  => (.compute (.build (.factorial (example.Factorial$Builder.) target)))
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### Implementing Java Interfaces

;; What if our Java team needs us to implement one of their API interfaces? Here
;; we use `reify` to generate a Java class that implements the `example.ValueComputer` interface
;; while re-using one of our functions for the implementation.
(defn newFactorialComputer [x]
  (reify example.ValueComputer
    (compute [this] (factorial-using-reduce x))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Finally...

;; Why not just use [Incanter](http://incanter.org/)? (DUH!)
(require '[incanter.core :only factorial])
(defn factorial-from-incanter [x]
  (incanter.core/factorial x))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; # Epilogue: HALL OF SHAME

;; Here are some functions I wrote that "work" but have hidden defects or are just plain wrong.
;;

;; Note that `def` binds to the namespace, not the scope of the function.
;;
;; After calling `(factorial-using-do-dotimes 5)` you will have a var named `a` pointing to a value
;; of `120`.  Unless another thread called the function concurrently, in which case who knows
;; what happened.
(defn factorial-using-do-dotimes [x]
  (do
    (def a 1)
    (dotimes [i x]
      (def a (* a (inc i)))))
  a)

;; This approach using `do` and `while` has the same correctness problem as above.
;; Now you have two vars in your namespace: `a` and `res`.
(defn factorial-using-do-while [x]
  (do
    (def a 0)
    (def res 1)
    (while (< a x)
      (def a (inc a))
      (def res (* res a)))
    res))

;; An example using Atoms. I suspect one would never (mis-)use atoms locally-scoped like this.
;;
;; However, perhaps you could contrive an example using the `Factorial` struct from earlier and
;; `compare-and-set!`
(defn factorial-using-atoms-while [x]
  (let [a (atom 0)
        res (atom 1)]
    (while (> x @a)
      (swap! res * (swap! a inc)))
    @res))

;; Finally, an attempt to compute a factorial using an `agent` that recursively
;; submits computation tasks to itself
;;
;; This approach sometimes fails due to a race condition between
;; the recursive (and asychronous) `send-off` and the `await` call.
;;
;;     (for [x (range 10)] (factorial-using-agent-recursive 5))
;;     => (2 4 3 120 2 120 120 120 120 2)
;;     (for [x (range 10)] (factorial-using-agent-recursive 5))
;;     => (2 2 2 3 2 2 3 2 120 2)
(defn factorial-using-agent-recursive [x]
  (let [a (agent 1)]
    (letfn [(calc  [n limit total]
               (if (< n limit)
                 (let [next-n (inc n)]
                   (send-off *agent* calc limit (* total next-n))
                   next-n)
                 total))]
      (await (send-off a calc x 1)))
    @a))
