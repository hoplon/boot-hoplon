(set-env!
  ;; using the sonatype repo is sometimes useful when testing Clojurescript
  ;; versions that not yet propagated to Clojars
  ;; :repositories #(conj % '["sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"}])
  :dependencies '[[org.clojure/clojure       "1.7.0"          :scope "provided"]
                  [org.clojure/clojurescript "1.7.228"        :scope "provided"]
                  [hoplon                    "6.0.0-alpha16"  :scope "provided"]
                  [hoplon/brew               "0.2.0-SNAPSHOT" :scope "provided"]
                  [adzerk/bootlaces          "0.1.13"         :scope "test"]])

(require
  '[clojure.java.io :as io]
  '[adzerk.bootlaces :refer :all])

(def +version+ "0.3.0-SNAPSHOT")

(bootlaces! +version+ :dev-dependencies "hoplon/boot_hoplon/pod_deps.edn")

(require '[hoplon.boot-hoplon :refer :all])

(task-options!
  pom  {:project     'hoplon/boot-hoplon
        :version     +version+
        :description "Boot task for the Hoplon web development environment."
        :url         "https://github.com/hoplon/boot-hoplon"
        :scm         {:url "https://github.com/hoplon/boot-hoplon"}
        :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask develop []
  (comp (watch) (speak) (build-jar)))
