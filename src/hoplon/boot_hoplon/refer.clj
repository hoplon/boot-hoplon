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

(defn get-publics* [ns]
  (binding [a/*analyze-deps* false
            a/*cljs-warnings* nil]
    (let [macro? #(boolean (:macro (second %)))
          type? #(some identity ((juxt :type :record) (second %)))
          protocol? #(->> % second :meta
                          ((juxt :protocol :protocol-symbol :protocol-info))
                          (some identity))
          {macros true defs false}
          (ana/with-state st
            (do (ana/analyze-file (nsym->path ns "cljs"))
                (->> (ana/ns-publics ns)
                     (remove protocol?)
                     (remove type?)
                     (group-by macro?))))]
      {:macros (sort (map first macros)) :defs (sort (map first defs))})))

(def get-publics (memoize get-publics*))

(defn exclude [ops exclusions]
  (vec (set/difference (set ops) (set exclusions))))

(defn make-require [ns-sym & [exclusions]]
  [ns-sym :refer (exclude (:defs (get-publics ns-sym)) exclusions)])

(defn make-require-macros [ns-sym & [exclusions]]
  [ns-sym :refer (exclude (:macros (get-publics ns-sym)) exclusions)])
