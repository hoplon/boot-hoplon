# boot-hoplon

[](dependency)
```clojure
[tailrecursion/boot-hoplon "0.1.0-SNAPSHOT"] ;; latest release
```
[](/dependency)

[Boot] task for building [Hoplon] web applications.

## Usage

Add `boot-hoplon` to your `build.boot` dependencies and `require` the namespace.

> **Note:** the boot-hoplon dependency is only needed at when compiling Hoplon
> source files, but hoplon itself is needed both when compiling and at runtime.
> So you should add boot-hoplon with the _test_ scope and hoplon with the
> (default) _compile_ scope.

```clj
(merge-env!
  :dependencies '[[tailrecursion/hoplon "A.B.C"]
                  [tailrecursion/boot-hoplon "X.Y.Z" :scope "test"])
(require '[tailrecursion.boot-hoplon :refer :all])
```

You can see the options available on the command line:

```bash
boot hoplon -h
```

or in the REPL:

```clj
boot.user=> (doc hoplon)
```

## Compile Hoplon Pages

If you only want to compile Hoplon &rarr; ClojureScript+HTML, you can do:

```bash
boot hoplon
```

## License

Copyright © 2014 Alan Dipert and Micha Niskin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[Boot]:                https://github.com/boot-clj/boot
[Hoplon]:              https://github.com/tailrecursion/hoplon
[cider]:               https://github.com/clojure-emacs/cider
[boot-cljs-repl]:      https://github.com/adzerk/boot-cljs-repl
[src-maps]:            https://developer.chrome.com/devtools/docs/javascript-debugging#source-maps
[closure-compiler]:    https://developers.google.com/closure/compiler/
[closure-levels]:      https://developers.google.com/closure/compiler/docs/compilation_levels
[closure-externs]:     https://developers.google.com/closure/compiler/docs/api-tutorial3#externs
[boot-cljs-example]:   https://github.com/adzerk/boot-cljs-example
[cljs-opts]:           https://github.com/clojure/clojurescript/wiki/Compiler-Options
[cljsjs]:              https://github.com/cljsjs/packages
