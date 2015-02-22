(ns tailrecursion.boot-hoplon.pygments
  (:import [org.python.util PythonInterpreter])
  (:require [tailrecursion.boot-hoplon.tagsoup :as ts]))

(defn pygmentize [file-ext code]
  (try
    (-> (doto (PythonInterpreter.)
          (.set  "code" code)
          (.set  "file" (str "test." file-ext))
          (.exec "from pygments import highlight\n")
          (.exec "from pygments.lexers import guess_lexer_for_filename\n")
          (.exec "from pygments.formatters import HtmlFormatter\n")
          (.exec "lex = guess_lexer_for_filename(file, code)\n")
          (.exec "fmt = HtmlFormatter(noclasses=True)\n")
          (.exec "result = highlight(code, lex, fmt)"))
        (.get "result" java.lang.String))
    (catch Throwable t
      (throw (ex-info "pygments exception" {:file-ext file-ext :code code} t)))))

(defn qualify [sexp]
  (if-not (and (seq? sexp)
               (symbol? (first sexp))
               (empty? (namespace (first sexp))))
    sexp
    (list* (symbol "tailrecursion.hoplon" (name (first sexp))) (map qualify (rest sexp)))))

(defn hl [file-ext code]
  (qualify (ts/parse-snip (format "<div>%s</div>" (pygmentize file-ext code)))))
