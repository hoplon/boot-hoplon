(ns tailrecursion.boot-hoplon.haml
  (:require [clojure.string :as string]))

(defn read-str [x & [all?]]
  (try (read-string (if-not all? x (str "(" x ")")))
       (catch Throwable e
         (throw (ex-info "unreadable form" {:form x} e)))))

(defn join        [x] (string/join "\n" x))
(defn split       [x] (string/split x #"\n"))
(defn indent      [x] (count (re-find #"^ *" (or x ""))))
(defn triml       [x] #(.replaceAll % (format "^ {%d}" x) ""))
(defn tag?        [x] (and (string? x) (= \% (first x))))
(defn clj?        [x] (= x "%clj"))
(defn comment?    [x] (.startsWith (or x "") "-#"))
(defn indent?  [op x] #(or (string/blank? %) (op (indent %) x)))
(defn splay    [& fs] (fn [xs] (mapv #(%1 %2) (concat fs (repeat identity)) xs)))
(defn outdent     [x] #(->> % (split-with (indent? >= x)) ((splay (partial map (triml x))))))
(defn end-blanks  [x] (->> x reverse (split-with string/blank?) reverse ((splay reverse))))
(defn reverse-str [x] (->> x str reverse (apply str)))
(defn parse-op    [x] (-> x reverse-str (string/replace-first "." "/") reverse-str symbol))
(defn parse-tag   [x] (let [[tag attr] (read-str (subs x 1) true)] (list (parse-op tag) (or attr {}))))

(declare parse)

(defn parse-block [x xs]
  (let [block         (parse-tag x)
        [blank1 more] (split-with string/blank? xs)
        i             (indent (first more))]
    (if (= i 0)
      [[block] xs]
      (let [[[kids blank2] more] (->> more ((outdent i)) ((splay end-blanks)))]
        (if (clj? x)
          [(read-str (join kids) true) (concat blank2 more)]
          [[(concat block blank1 (parse kids))] (concat blank2 more)])))))

(defn parse [x]
  (loop [ret [], [x & xs] (remove comment? x)]
    (let [nojoin? (or (empty? ret)
                      (not (string? x))
                      (not (string? (peek ret))))]
      (cond (not x)  ret
            (tag? x) (let [[x xs] (parse-block x xs)]
                       (recur (into ret x) xs))
            nojoin?  (recur (conj ret x) xs)
            :else    (let [[ret' r] ((juxt pop peek) ret)]
                       (recur (conj ret' (join [r x])) xs))))))

(defn parse-string [x] (remove string? (parse (split x))))
