;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Exploring Clojure with Factorial Computation
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
(defn factorial-using-recur [n]
  (loop [current n
         next (dec current)
         total 1]
    (if (> current 1)
      (recur next (dec next) (* total current))
      total)))

;; `reduce` `*`
;; on a
;; `range`
(defn factorial-using-reduce [n]
  (reduce * (range 1 (inc n))))

;; `apply` `*`
;; to a
;;`range`
(defn factorial-using-apply-range [n]
  (apply * (range 1 (inc n))))

;; `apply` `*`
;;  but `take`-ing from an `iterate`
(defn factorial-using-apply-iterate [n]
  (apply * (take n (iterate inc 1))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## "Code is data, data is code"

;; Higher-order functions FTW.

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

;; Here we illustrate Clojure [homoiconicity](http://en.wikipedia.org/wiki/Homoiconicity)
;; using `eval` `cons` and `'` (quote).
;
;; Our function is building a valid Clojure expression that we then `eval`. Here's
;; how you would do it manually at the REPL:
;;
;;     (def fac5 (cons '* (range 1 6)))
;;     (println fac5)
;;     => (* 1 2 3 4 5)
;;     (eval fac5)
;;     => 120
(defn factorial-using-eval-and-cons [n]
  (eval (cons '* (range 1 (inc n)))))

;;`defmacro` generates Clojure code for a function that will calculate
;; a fixed factorial value on-demand.
;;
;; Similar to the previous function, but note the macro output.
;;
;;      (macroexpand `(factorial-function-macro 5))
;;      => (fn* ([] (clojure.core/* 1 2 3 4 5)))
;;
;; (More information on the mysterious `fn*` [here](http://stackoverflow.com/questions/10767305/what-is-fn-and-how-does-clojure-bootstrap))
(defmacro factorial-function-macro [n]
  `(fn [] (* ~@(range 1 (inc n)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Parallel Computation

;; Using `future` `dosync` and `alter`
;;
;; `partition-all` is just fantastic, by the way.
;;
;; Reminder: `map` is lazy, so force execution with `dorun` (effectively discarding the results).
(defn factorial-using-ref-dosync [n psz]
  (let [result (ref 1)
        parts (partition-all psz (range 1 (inc n)))
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
(defn factorial-using-agent [n psz]
  (let [result (agent 1)
        parts (partition-all psz (range 1 (inc n)))]
    (doseq [p parts]
      (send result #(apply * % p)))
    (await result)
    @result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### pmap to the Rescue

;; If the previous code looked arduous, never fear. Enter: `pmap`

;; Yes, it is `map` executed in parallel (lazily!) .
(defn factorial-using-pmap-reduce [n psz]
  (let [parts (partition-all psz (range 1 (inc n)))
        sub-factorial-fn #(apply * %)]
    (reduce * (pmap sub-factorial-fn parts))))

;; There is also `pvalues`, which evaluates a list of expressions in parallel.
;;
;; Even though this works, it feels wrong having to jump through hoops with `defmacro` like this.
;
; `pmap` is more natural for this use-case.
(defmacro factorial-using-pvalues-reduce [n psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc n)))]
                (cons '* p))]
    `(fn [] (reduce * (pvalues ~@exprs)))))

;; `pvalues` calls a collection of zero-arg functions in parallel to create a lazy sequence.
;; Again, I had to use `defmacro` to accomplish my goal.
(defmacro factorial-using-pcalls-reduce [n psz]
  (let [exprs (for [p (partition-all psz (range 1 (inc n)))]
                `(fn [] (* ~@p)))]
    `(fn [] (reduce * (pcalls ~@exprs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## More Advanced Stuff

;; ### Rolling Your Own Lazy Sequences

;; In simpler times, we used `take` `range` and `iterate` to compute factorials.
;; Under the covers, these functions create "lazy" sequences. It turns out we can
;; cut out the middle-man and compute a lazy sequence of factorials using `cons` and `lazy-seq`
;;
;; I used `letfn` in order to define a private function to kick off our lazy sequence.
;; Another way to define private functions is `defn-`
(defn factorial-using-lazy-seq [n]
  (letfn [(start-lazy-fac-seq [] (lazy-fac-seq 1 1))
           (lazy-fac-seq [n v]
             (let [next-n (inc n)
                   next-v (* n v)]
               (cons v (lazy-seq (lazy-fac-seq next-n next-v)))))]
    (nth (start-lazy-fac-seq) n)))

;; ### Trampoline

;; Here, I've created a factorial function for use with `trampoline`.
;;
;; If you're not familiar with `trampoline`, it basically works like this:
;;
;; 1. `trampoline` calls the function you pass in.
;; 2. If your function returns a value, `trampoline` returns that value.
;; 3. However, if your function returns a function instance, `trampoline`
;;    calls that function.
;; 4. Repeat steps 2 and 3 until we finally get a non-function value.
;;
;; Example usage:
;;
;;     (trampoline (factorial-for-trampoline 5))
;;     => 120
;;
;; A benefit of `trampoline` is that it allows mutual recursion between functions
;; without overflow.
(defn factorial-for-trampoline [n]
  (letfn
    [(next-fac-value [limit current-step previous-value]
       (let [next-value (* current-step previous-value)]
         (if (= limit current-step)
           next-value
           #(next-fac-n limit current-step next-value))))

     (next-fac-n [limit previous-step current-value]
       #(next-fac-value limit (inc previous-step) current-value))]

    (next-fac-value n 1 1)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ### Multimethods
;;
;; I also thought of a way to compute a factorial using multimethods
;; (and some recursion).

;; First define a "struct" that has the fields `n` and `value`
;;
;; (Yes, a tuple in the form of `{:n 1 :value 1}` would also have worked here, but (to be honest) I wanted to use
;; `defrecord` in at least one of these examples)
;;
;; By the way, we actually just defined the Java class `factorials.core.Factorial`
(defrecord Factorial [n value])

;; To define a multimethod, first you define the dispatch function with `defmulti`. Here
;; our function dispatches on just two possible values: `true` or `false`, where "false"
;; means we reached the end of our factorial computation.
;;
;; The function argument `{:keys [n]}` is actually an example of one of Clojures mini-languages,
;; "destructuring." For more on that, see Jay Fields'
;; [terrific blog entry](http://blog.jayfields.com/2010/07/clojure-destructuring.html).
(defmulti factorial-using-multimethods
  (fn ([limit] true)
      ([limit {:keys [n]}] (< n limit))))

;; Our multimethod repeatedly dispatches to this function while `n < limit` (`true`)
;;
;; Note how I had to overload this method to initialize our `Factorial` struct on the first invocation.
(defmethod factorial-using-multimethods true
  ([limit] (factorial-using-multimethods limit (new Factorial 1 1)))
  ([limit fac]
    (let [next-factorial (-> fac (update-in [:n] inc)
                                 (update-in [:value] #(* % (:n fac))))]
      (factorial-using-multimethods limit next-factorial))))

;; We hit the multimethod function for `false` when our `Factorial` struct has the desired `:n` value.
;; It returns the final factorial value after one last computation.
(defmethod factorial-using-multimethods false
  ([limit fac] (* limit (:value fac))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Java Interop

;; Elsewhere we wrote a plain old Java class with a static method that
;; computes factorials.  We can still re-use that legacy code via Clojure's Java interop.
(defn factorial-using-javainterop [n]
  (example.Factorial/calculate n))

;; But what if our Java team read _Effective Java, 2nd Ed._ and decided to use
;; the Builder pattern?

;; We can use the `->` operator (aka the "pipeline operator") to
;; get this under control.
;;
;; And if you're working with `java.util.Map` instances or "JavaBeans" with copious "setters," there's the
;; `doto` macro.
(import 'example.Factorial$Builder)
(defn factorial-using-javainterop-and-pipeline [n]
  (-> (Factorial$Builder.)
      (.factorial n)
      .build
      .compute))

;; ### More on the Pipeline Macro

;; I found `clojure.walk/macroexpand-all` really useful for understanding and debugging the `->` macro:

;; Executing the following at the REPL
(comment
  (clojure.walk/macroexpand-all '(-> (Factorial$Builder.) (.factorial n) .build .compute))
)
;; outputs
(comment
  => (. (. (. (new Factorial$Builder) factorial n) build) compute)
)
;; which is equivalent to
(comment
  => (.compute (.build (.factorial (example.Factorial$Builder.) n)))
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Implementing Java Interfaces

;; Perhaps our Java team, which doesn't use Clojure, needs us to implement one of their API interfaces.

;; Here we use `reify` to generate a Java class that implements the `example.ValueComputer` interface
;; while re-using one of our functions for the implementation.
(defn newFactorialComputer [n]
  (reify example.ValueComputer
    (compute [this] (factorial-using-reduce n))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Finally...

;; Why not just use [Incanter](http://incanter.org/)? (Duh!)
(require '[incanter.core :only factorial])
(defn factorial-from-incanter [n]
  (incanter.core/factorial n))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Epilogue: HALL OF SHAME

;; Here are some functions I wrote that "work" but have hidden defects or are just plain wrong.

;; ### defs Aren't Variables

;; After calling `(factorial-using-do-dotimes 5)` you will have a var named `a` pointing to a value
;; of `120`.  Unless another thread called the function concurrently, in which case who knows
;; what happened?
;;
;; This is because `def` binds to the namespace, not the scope of the function.
(defn factorial-using-do-dotimes [n]
  (do
    (def a 1)
    (dotimes [i n]
      (def a (* a (inc i)))))
  a)

;; This approach using `do` and `while` has the same correctness problem as above.
;; Now you have two vars in your namespace: `a` and `res`.
(defn factorial-using-do-while [n]
  (do
    (def a 0)
    (def res 1)
    (while (< a n)
      (def a (inc a))
      (def res (* res a)))
    res))

;; ### Abusing Atoms

;; An example using Atoms. I suspect one would never (ab)use atoms locally-scoped like this (although
;; they work well for implementing [closures](http://www.nofluffjuststuff.com/blog/stuart_halloway/2009/08/rifle_oriented_programming_with_clojure)).
;;
;; However, perhaps you could contrive an example using the `Factorial` struct from earlier and
;; `compare-and-set!`
(defn factorial-using-atoms-while [n]
  (let [a (atom 0)
        res (atom 1)]
    (while (> n @a)
      (swap! res * (swap! a inc)))
    @res))

;; ### Recursive Agent Race Condition

;; Finally, an attempt to compute a factorial using an `agent` that recursively
;; submits computation tasks to itself.
;;
;; This approach sometimes fails due to a race condition between
;; the recursive (and asychronous) `send-off` and the `await` call.
;;
;;     (for [x (range 10)] (factorial-using-agent-recursive 5))
;;     => (2 4 3 120 2 120 120 120 120 2)
;;     (for [x (range 10)] (factorial-using-agent-recursive 5))
;;     => (2 2 2 3 2 2 3 2 120 2)
(defn factorial-using-agent-recursive [n]
  (let [a (agent 1)]
    (letfn [(calc  [current-n limit total]
               (if (< current-n limit)
                 (let [next-n (inc current-n)]
                   (send-off *agent* calc limit (* total next-n))
                   next-n)
                 total))]
      (await (send-off a calc n 1)))
    @a))
