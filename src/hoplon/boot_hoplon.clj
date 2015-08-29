;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns hoplon.boot-hoplon
  {:boot/export-tasks true}
  (:require [boot.core                      :as boot]
            [boot.pod                       :as pod]
            [boot.util                      :as util]
            [clojure.java.io                :as io]))

(def ^:private renderjs
  "
var page = require('webpage').create(),
    sys  = require('system'),
    path = sys.args[1]
    uri  = \"file://\" + path + \"?prerendering=1\";
page.open(uri, function(status) {
  setTimeout(function() {
    var html = page.evaluate(function() {
      return document.documentElement.outerHTML;
    });
    console.log(html);
    phantom.exit();
  }, 0);
});")

(def hoplon-pod
  (delay (pod/make-pod (->> (-> "hoplon/boot_hoplon/pod_deps.edn"
                                io/resource slurp read-string)
                            (update-in pod/env [:dependencies] into)))))

(defn bust-cache
  [path]
  (pod/with-eval-in @hoplon-pod
    (require 'hoplon.core)
    (hoplon.core/bust-cache ~path)))

(defn- by-path
  [paths tmpfiles]
  (boot/by-re (mapv #(re-pattern (str "^\\Q" % "\\E$")) paths) tmpfiles))

(boot/deftask bust-caches
  [p paths PATH #{str} "The set of paths to add cache-busting uuids to."]
  (let [tmp (boot/tmp-dir!)]
    (boot/with-pre-wrap fs
      (let [msg (delay (util/info "Busting cache...\n"))]
        (->> (boot/output-files fs)
             (by-path paths)
             (seq)
             (reduce (fn [fs {:keys [path]}]
                       @msg
                       (util/info "• %s\n" path)
                       (boot/mv fs path (bust-cache path)))
                     fs))))))

(boot/deftask prerender
  [e engine ENGINE str "PhantomJS-compatible engine to use."]
  (let [engine       (or engine "phantomjs")
        pod          (future @hoplon-pod)
        tmp          (boot/tmp-dir!)
        rjs-tmp      (boot/tmp-dir!)
        rjs-path     (.getPath (io/file rjs-tmp "render.js"))]
    (spit rjs-path renderjs)
    (boot/with-pre-wrap fileset
      (let [html (->> fileset
                      boot/output-files
                      (boot/by-ext [".html"])
                      (map (juxt boot/tmp-path (comp (memfn getPath) boot/tmp-file))))]
        (pod/with-call-in @pod
          (hoplon.boot-hoplon.impl/prerender ~engine ~(.getPath tmp) ~rjs-path ~html))
        (-> fileset (boot/add-resource tmp) boot/commit!)))))

(boot/deftask hoplon
  "Build Hoplon web application."
  [p  pretty-print bool "Pretty-print CLJS files created by the Hoplon compiler."
   l  lib          bool "Include produced cljs in the final artifact."
   b  bust-cache   bool "Add cache-busting uuid to JavaScript file name?"]
  (let [prev-fileset (atom nil)
        tmp-cljs     (boot/tmp-dir!)
        tmp-html     (boot/tmp-dir!)
        opts         (dissoc *opts* :lib)
        pod          (future @hoplon-pod)
        add-cljs     (if lib boot/add-resource boot/add-source)]
    (boot/with-pre-wrap fileset
      (let [hl (->> fileset
                    (boot/fileset-diff @prev-fileset)
                    boot/input-files
                    (boot/by-ext [".hl"])
                    (map (juxt boot/tmp-path (comp (memfn getPath) boot/tmp-file))))]
        (reset! prev-fileset fileset)
        (pod/with-call-in @pod
          (hoplon.boot-hoplon.impl/hoplon ~(.getPath tmp-cljs) ~(.getPath tmp-html) ~hl ~opts)))
      (-> fileset (add-cljs tmp-cljs) (boot/add-resource tmp-html) boot/commit!))))

(boot/deftask html2cljs
  "Convert file from html syntax to cljs syntax."
  [f file FILENAME str "File to convert."]
  (let [pod (future @hoplon-pod)]
    (boot/with-pre-wrap fileset
      (print (pod/with-call-in @pod (hoplon.boot-hoplon.impl/html2cljs ~file)))
      fileset)))
