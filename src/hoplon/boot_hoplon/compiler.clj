;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns hoplon.boot-hoplon.compiler
  (:require
    [clojure.pprint             :as pp]
    [clojure.java.io            :as io]
    [clojure.string             :as string]
    [hoplon.core                :as hl]
    [hoplon.boot-hoplon.tagsoup :as tags]
    [hoplon.boot-hoplon.refer   :as refer])
  (:import
    [clojure.lang LineNumberingPushbackReader]
    [java.io PushbackReader BufferedReader StringReader]))

(def ^:dynamic *printer* prn)

(defn munge-page-name [x]
  (-> (str "_" (name x)) (string/replace #"\." "_DOT_") munge))

(defn munge-page [ns]
  (let [ap "hoplon.app-pages."]
    (if (symbol? ns) ns (symbol (str ap (munge-page-name ns))))))

(defn read-string-1
  [x]
  (when x
    (with-open [r (LineNumberingPushbackReader. (StringReader. x))]
      [(read r false nil)
       (apply str (concat (repeat (dec (.getLineNumber r)) "\n") [(slurp r)]))])))

(defn up-parents [path name]
  (let [[f & dirs] (string/split path #"/")]
    (->> [name] (concat (repeat (count dirs) "../")) (apply str))))

(defn inline-code [s process]
  (let [lines (string/split s #"\n")
        start #";;\{\{\s*$"
        end #"^\s*;;\}\}\s*$"
        pad #"^\s*"
        unpad #(string/replace %1 (re-pattern (format "^\\s{0,%d}" %2)) "")]
    (loop [txt nil, i 0, [line & lines] lines, out []]
      (if-not line
        (string/join "\n" out)
        (if-not txt
          (if (re-find start line)
            (recur [] i lines out)
            (recur txt i lines (conj out line)))
          (if (re-find end line)
            (let [s (process (string/trim (string/join "\n" txt)))]
              (recur nil 0 (rest lines) (conj (pop out) (str (peek out) s (first lines)))))
            (let [i (if-not (empty? txt) i (count (re-find pad line)))]
              (recur (conj txt (unpad line i)) i lines out))))))))

(defn ->cljs-str [s]
  (if (not= \< (first (string/trim s)))
    s
    (tags/parse-string (inline-code s tags/html-escape))))

(defn output-path [forms] (-> forms first second str))
(defn output-path-for [path] (-> path slurp ->cljs-str output-path))

(defn make-nsdecl [[_ ns-sym & forms] {:keys [refers]}]
  (let [ns-sym    (symbol ns-sym)
        refers    (into '#{hoplon.core javelin.core} refers)
        rm?       #(or (contains? refers %) (and (seq %) (contains? refers (first %))))
        mk-req    #(concat (remove rm? %2) (map %1 refers (repeat %3)))
        clauses   (->> (tree-seq list? seq forms) (filter list?) (group-by first))
        exclude   (when-let [e (:refer-hoplon clauses)] (nth (first e) 2))
        combine   #(mapcat (partial drop 1) (% clauses))
        req       (combine :require)
        reqm      (combine :require-macros)
        reqs      `(:require ~@(mk-req refer/make-require req exclude))
        macros    `(:require-macros ~@(mk-req refer/make-require-macros reqm exclude))
        other?    #(-> #{:require :require-macros :refer-hoplon}
                       ((comp not contains?) (first %)))
        others    (->> forms (filter list?) (filter other?))]
    `(~'ns ~ns-sym ~@others ~reqs ~macros)))

(defn forms-str [ns-form body]
  (str (binding [*print-meta* true] (pr-str ns-form)) body))

(defn ns->path [ns]
  (-> ns munge (string/replace \. \/) (str ".cljs")))

(defn compile-forms [nsdecl body {:keys [bust-cache refers] :as opts}]
  (case (first nsdecl)
    ns   {:cljs (forms-str (make-nsdecl nsdecl opts) body) :ns (second nsdecl)}
    page (let [[_ page & _] nsdecl
               outpath     (output-path [nsdecl])
               page-ns     (munge-page page)
               cljsstr     (let [[h _ & t] (make-nsdecl nsdecl opts)]
                             (forms-str (list* h page-ns t) body)) 
               js-out      (if-not bust-cache outpath (hl/bust-cache outpath))
               js-uri      (-> js-out (string/split #"/") last (str ".js"))
               script-src  #(list 'script {:type "text/javascript" :src (str %)})
               s-html      `(~'html {}
                                    (~'head {}
                                            (~'meta {:charset "utf-8"}))
                                    (~'body {}
                                            ~(script-src js-uri)))
               htmlstr     (tags/print-page "html" s-html)
               edn         {:require  [(symbol page-ns)]}
               ednstr      (pr-str edn)]
           {:html htmlstr :edn ednstr :cljs cljsstr :ns page-ns :file outpath :js-file js-out})))

(defn pp [form] (pp/write form :dispatch pp/code-dispatch))

(defn- write [f s]
  (when (and f s)
    (doto f io/make-parents (spit s))))

(defn compile-string
  [forms-str cljsdir htmldir & {:keys [opts]}]
  (let [[ns-form body] (read-string-1 (->cljs-str forms-str))
        {:keys [cljs ns html edn file js-file]}
        (compile-forms ns-form body opts)
        cljs-out (io/file cljsdir (ns->path ns))]
    (write cljs-out cljs)
    (when file
      (let [html-out (io/file htmldir file)
            edn-out  (io/file cljsdir (str js-file ".cljs.edn"))]
        (write edn-out edn)
        (write html-out html)))))

(defn compile-file [f & args]
  (apply compile-string (slurp f) args))
