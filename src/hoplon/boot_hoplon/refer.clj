;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns hoplon.boot-hoplon.refer
  (:require
    [cljs.analyzer     :as a]
    [cljs.analyzer.api :as ana]
    [clojure.set       :as set]
    [clojure.string    :as string]))

(defn nsym->path [sym ext]
  (-> (str sym)
      (string/replace "." "/")
      (string/replace "-" "_")
      (str "." ext)))

(def st (ana/empty-state))

(defn analyze [ns]
  (try (ana/analyze-file (nsym->path ns "cljs"))
       (catch Throwable _
         (try (ana/analyze-file (nsym->path ns "cljc"))
              (catch Throwable _
                (ana/analyze-file (nsym->path ns "clj")))))))

(defn get-publics* [ns]
  (binding [a/*analyze-deps* false
            a/*cljs-warnings* nil]
    (let [macro?    #(boolean (:macro (second %)))
          type?     #(some identity ((juxt :type :record) (second %)))
          protocol? #(->> % second :meta
                          ((juxt :protocol :protocol-symbol :protocol-info))
                          (some identity))
          rm-meta   #(with-meta % nil)
          names     #(->> % (map (comp rm-meta first)) sort)
          {macros true defs false}
          (ana/with-state st
            (do (analyze ns)
                (->> (ana/ns-publics ns)
                     (remove protocol?)
                     (remove type?)
                     (group-by macro?))))]
      {:macros (names macros) :defs (names defs)})))

(def get-publics (memoize get-publics*))

(defn exclude [ops exclusions]
  (vec (set/difference (set ops) (set exclusions))))

(defn make-require [ns-sym & [exclusions]]
  [ns-sym :refer (exclude (:defs (get-publics ns-sym)) exclusions)])

(defn make-require-macros [ns-sym & [exclusions]]
  [ns-sym :refer (exclude (:macros (get-publics ns-sym)) exclusions)])

(defn expand-nested [[ns-sym & args :as spec]]
  (letfn [(combine [[ns-sym' & args]]
            `[~(symbol (str ns-sym "." ns-sym')) ~@args])]
    (if-not (and args (every? vector? args))
      [spec]
      (vec (->> (map combine args) (mapcat expand-nested))))))

(defn do-require [xs [ns & mods]]
  (let [{:keys [defs macros]} (get-publics* ns)
        [defs macros] (map set [defs macros])
        mods          (apply hash-map mods)
        [names mods]  ((juxt #(% :refer) #(dissoc % :refer)) mods)
        names         (if (= names :all) (set/union defs macros) (set names))
        refer-defs    (set/intersection defs names)
        refer-macros  (set/intersection macros names)
        refer-errors  (set/difference names (set/union defs macros))
        inset         (fnil into #{})
        xs (if (empty? defs) xs (update-in xs [:require ns] merge mods))
        xs (if (empty? macros) xs (update-in xs [:require-macros ns] merge mods))
        xs (if (empty? refer-defs) xs (update-in xs [:require ns :refer] inset refer-defs))
        xs (if (empty? refer-macros) xs (update-in xs [:require-macros ns :refer] inset refer-macros))]
    (assert
      (empty? refer-errors)
      (format "No such names: %s in namespace %s" (string/join ", " refer-errors) ns))
    xs))

(defn do-use [xs [ns & mods]]
  (let [{:keys [defs macros]} (get-publics* ns)
        [defs macros] (map set [defs macros])
        all-names     (set/union defs macros)
        mods          (apply hash-map mods)
        [only excl mods] ((juxt #(% :only) #(% :exclude) #(dissoc % :only :exclude)) mods)
        names         (cond (seq only) (set only)
                            (seq excl) (set/difference all-names (set excl))
                            :else      all-names)]
    (do-require xs (into [ns] (mapcat identity (assoc mods :refer (vec names)))))))

(defn parse-spec [xs [ns-sym & args]]
  (update-in xs [ns-sym] merge (apply hash-map args)))

(defn parse-clause [xs [k & specs]]
  (case k
    (:require-macros :use-macros)
    (merge-with merge xs {k (reduce parse-spec {} (mapcat expand-nested specs))})
    (assoc xs k (vec specs))))

(defn parse-nsdecl [[tag ns-sym & clauses]]
  (merge {:tag tag :ns-sym ns-sym}
         {:clauses (let [{:keys [require use] :as ret} (reduce parse-clause {} clauses)]
                     (reduce do-use (reduce do-require (dissoc ret :require :use) require) use))}))

(defn emit-spec [[ns-sym mods]]
  (into [ns-sym] (mapcat (fn [[k v]] [k (if (set? v) (vec v) v)]) mods)))

(defn emit-clause [[k specs]]
  (if-not (map? specs)
    (list* k specs)
    (list* k (map emit-spec specs))))

(defn emit-nsdecl [{:keys [tag ns-sym clauses]}]
  (list* tag ns-sym (map emit-clause clauses)))

(defn rewrite-ns [ns-form]
  (emit-nsdecl (parse-nsdecl ns-form)))
