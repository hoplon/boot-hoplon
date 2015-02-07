;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.boot-hoplon
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.pod        :as pod]
            [clojure.java.io :as io]))

(def ^:private renderjs
  "
var page = require('webpage').create(),
    sys  = require('system'),
    path = sys.args[1]
    uri  = \"file://\" + path;
page.open(uri, function(status) {
  setTimeout(function() {
    var html = page.evaluate(function() {
      return document.documentElement.outerHTML;
    });
    console.log(html);
    phantom.exit();
  }, 0);
});")

(def ^:private hoplon-pod
  (delay (pod/make-pod (->> (-> "tailrecursion/boot_hoplon/pod_deps.edn"
                                io/resource slurp read-string)
                            (update-in pod/env [:dependencies] into)))))

(boot/deftask prerender
  [e engine ENGINE str "PhantomJS-compatible engine to use."]
  (let [engine       (or engine "phantomjs")
        pod          (future @hoplon-pod)
        tmp          (boot/temp-dir!)
        prev-fileset (atom nil)
        rjs-tmp      (boot/temp-dir!)
        rjs-path     (.getPath (io/file rjs-tmp "render.js"))]
    (spit rjs-path renderjs)
    (boot/with-pre-wrap fileset
      (let [html (->> fileset
                      (boot/fileset-diff @prev-fileset)
                      boot/output-files
                      (boot/by-ext [".html"])
                      (map (juxt boot/tmppath (comp (memfn getPath) boot/tmpfile))))]
        (reset! prev-fileset fileset)
        (pod/with-call-in @pod
          (tailrecursion.boot-hoplon.impl/prerender ~engine ~(.getPath tmp) ~rjs-path ~html))
        (-> fileset (boot/add-resource tmp) boot/commit!)))))

(boot/deftask hoplon
  "Build Hoplon web application.

  This task accepts an optional map of options to pass to the Hoplon compiler.
  Further ClojureScript compilation rely on another task (e. g. boot-cljs).
  The Hoplon compiler recognizes the following options:

  * :pretty-print  If set to `true` enables pretty-printed output
  in the ClojureScript files created by the Hoplon compiler.

  If you are compiling library, you need to include resulting cljs in target.
  Do it by specifying :lib flag."
  [pp pretty-print bool "Pretty-print CLJS files created by the Hoplon compiler."
   l  lib          bool "Include produced cljs in the final artefact."]
  (let [prev-fileset (atom nil)
        tmp-cljs     (boot/temp-dir!)
        tmp-html     (boot/temp-dir!)
        opts         (dissoc *opts* :lib)
        pod          (future @hoplon-pod)
        add-cljs     (if lib boot/add-resource boot/add-source)]
    (boot/with-pre-wrap fileset
      (boot/empty-dir! tmp-html)
      (let [hl (->> fileset
                    (boot/fileset-diff @prev-fileset)
                    boot/input-files
                    (boot/by-ext [".hl"])
                    (map (juxt boot/tmppath (comp (memfn getPath) boot/tmpfile))))]
        (reset! prev-fileset fileset)
        (pod/with-call-in @pod
          (tailrecursion.boot-hoplon.impl/hoplon ~(.getPath tmp-cljs) ~(.getPath tmp-html) ~hl ~opts)))
      (-> fileset (add-cljs tmp-cljs) (boot/add-resource tmp-html) boot/commit!))))

(boot/deftask html2cljs
  "Convert file from html syntax to cljs syntax."
  [f file FILENAME str "File to convert."]
  (let [pod (future @hoplon-pod)]
    (boot/with-pre-wrap fileset
      (print (pod/with-call-in @pod (tailrecursion.boot-hoplon.impl/html2cljs ~file)))
      fileset)))
