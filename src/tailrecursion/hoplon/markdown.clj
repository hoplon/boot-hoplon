(ns tailrecursion.hoplon.markdown
  (:require
    [tailrecursion.hoplon               :as hoplon]
    [tailrecursion.boot-hoplon.markdown :as markdown]))

(declare ^:dynamic *references*)
(declare ^:dynamic *abbreviations*)

(def ^:dynamic *node-map*
  (eval '`{:abbreviation-node               abbreviation-node
           :auto-link-node                  auto-link-node
           :block-quote-node                block-quote-node
           :bullet-list-node                bullet-list-node
           :code-node                       code-node
           :definition-list-node            definition-list-node
           :definition-node                 definition-node
           :definition-term-node            definition-term-node
           :exp-image-node                  exp-image-node
           :exp-link-node                   exp-link-node
           :header-node                     header-node
           :html-block-node                 html-block-node
           :inline-html-node                inline-html-node
           :list-item-node                  list-item-node
           :mail-link-node                  mail-link-node
           :ordered-list-node               ordered-list-node
           :para-node                       para-node
           :quoted-node                     quoted-node
           :ref-image-node                  ref-image-node
           :ref-link-node                   ref-link-node
           :reference-node                  reference-node
           :root-node                       root-node
           :simple-node                     simple-node
           :special-text-node               special-text-node
           :strike-node                     strike-node
           :strong-emph-super-node          strong-emph-super-node
           :table-body-node                 table-body-node
           :table-caption-node              table-caption-node
           :table-cell-node                 table-cell-node
           :table-column-node               table-column-node
           :table-header-node               table-header-node
           :table-node                      table-node
           :table-row-node                  table-row-node
           :valid-emph-or-strong-close-node valid-emph-or-strong-close-node
           :verbatim-node                   verbatim-node
           :wiki-link-node                  wiki-link-node}))

(defmacro root-node
  [{:keys [abbreviations references]} kids]
  `(binding [*references*    (merge *references* ~references)
             *abbreviations* (merge *abbreviations* ~abbreviations)]
     ~kids))

(defmacro md
  ([text] (md nil text))
  ([{:keys [references abbreviations]} text]
   `(binding [*references*    (merge *references* ~references)
              *abbreviations* (merge *abbreviations* ~abbreviations)]
      ~(markdown/parse-string *node-map* (eval text)))))

(comment
  
  (prn node-map)
  (use 'clojure.pprint)
  (use '[clojure.walk :only [macroexpand-all]])
  (->> node-map :root-node)

  (->> "README.md" slurp md quote macroexpand pprint)
  )

