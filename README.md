# Exploring Clojure with Factorial Computation

This project demonstrates a variety of of [Clojure](http://clojure.org) language features
and library functions using factorial computation as an example.

Many Clojure tutorials (and CS textbooks, for that matter) use
factorial computation to teach recursion.  I implemented such a
function in Clojure and thought: "why stop there?"

## Inventory

Approaches used to calculate factorials:

1. Using `loop` and `recur`
1. Using `reduce` and `range`
1. Using `apply` and `range`
1. Using `apply` `take` and `iterate`
1. Using `reduce` and `range` but returning a `fn` (a "higher-order" factorial function)
1. Using `defmacro` to create a function that always calculates the same factorial.
1. Using `cons` `range` and `eval`
1. Parallel computation using `future` `dosync` and `alter`
1. Parallel computation using `ref` `agent` `send` and `await`
1. Parallel computation using `reduce` and `pmap`
1. Extremely convoluted parallel computation using `reduce` and `pvalues` (requiring a macro)
1. Yet more convoluted parallel computation using `reduce` and `pcalls` (also requiring a macro)
1. By getting the `nth` value from a `lazy-seq` of factorials.
1. Using `trampoline` and mutually-recursive functions defined with `letfn`.
1. Using `defmethod` `defmulti` and `defrecord` (plus `update-in` and `->`) for recursive computation.
1. Using a Java primitive array and `areduce`
1. Using Java interop to call a Java class.
1. Using Java interop, but with `->` to tame a goofy "Builder" pattern.
1. Using `reify` to implement a pre-existing Java interface using one of our previous functions.
1. Using `incanter.core/factorial` (duh!)

### Hall of Shame

Buggy or just wrong functions that still "work."

1. (Mis-)using `def` `do` and `dotimes` (defective code)
1. (Mis-)using `def` `do` and `while` (yet more defective code)
1. (Mis-)using `while` `atom` and `swap` (not precisesly defective, but not a good use of Atoms).
1. An attempt with `agent` and recursive `send-off` calls, which often fails due to a race condition with `await`

# Building

This project builds with [Leiningen 2](https://github.com/technomancy/leiningen). Note that it includes some Java sources (for the interop examples).

Running `lein test` or `lein repl` will compile said Java sources.

Also, the source code comments are formatted for [Marginalia](http://fogus.me/fun/marginalia/). Configure your
profile to use the lein-marginalia plugin and run `lein marg` to generate documentation.

# GitHub Page

This project also has a GitHub page hosting the generated Marginalia documentation (with some
manual tweaks). Point your browser to

[http://noahlz.github.io/factorials](http://noahlz.github.io/factorials)

# Continuous Integration

![Build Status](https://travis-ci.org/noahlz/factorials.png?branch=master)

CI is hosted by [travis-ci.org](http://travis-ci.org)

# License

Creative Commons CC0 1.0 Universal 

See: http://creativecommons.org/publicdomain/zero/1.0/legalcode

Noah Zucker (nzucker at gmail.com / [@noahlz](http://twitter.com/noahlz))

