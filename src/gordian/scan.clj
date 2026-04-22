(ns gordian.scan
  (:require [babashka.fs :as fs]
            [edamame.core :as e]))

;;; ── require-entry extraction ─────────────────────────────────────────────

(defn- extract-dep
  "Given one entry inside a (:require ...) clause, return the dep namespace
  symbol or nil.  Handles:
    [ns-sym opts...]  → ns-sym
    ns-sym            → ns-sym  (bare symbol)
  Prefix vectors are not supported and silently skipped."
  [entry]
  (cond
    (symbol? entry)                           entry
    (and (vector? entry)
         (symbol? (first entry)))             (first entry)
    :else                                     nil))

(defn- require-clause? [clause]
  (and (seq? clause) (= :require (first clause))))

(defn- deps-from-ns-form
  "Return #{dep-syms} mentioned in (:require ...) clauses of a (ns ...) form."
  [ns-form]
  (let [req-clauses (filter require-clause? (drop 2 ns-form))]
    (into #{} (comp (mapcat rest) (keep extract-dep)) req-clauses)))

;;; ── file parsing ─────────────────────────────────────────────────────────

(def ^:private parse-opts
  "edamame options for parsing .clj source files.
  :syntax-quote true — required for files containing defmacro bodies that use
  backtick syntax-quote (e.g. `(do ~@body)).  Without this, edamame throws
  on every sub-token inside the syntax-quoted form and the ::skip handler
  retries from the same reader position, producing an infinite exception loop."
  {:read-cond    :allow
   :features     #{:clj}
   :fn           true
   :deref        true
   :regex        true
   :syntax-quote true})

(defn- read-ns-form
  "Incrementally read forms from `content`, returning the first (ns ...)
  form found, or nil.  Stops reading as soon as the ns form is located —
  never parses the file body — so syntax in function definitions (e.g.
  quoted sets like '#{...}) cannot cause failures."
  [content]
  (let [rdr (e/reader content)]
    (loop []
      (let [form (try (e/parse-next rdr parse-opts)
                      (catch Exception _ ::skip))]
        (cond
          (= ::e/eof  form) nil
          (= ::skip   form) (recur)
          (and (seq? form)
               (= 'ns (first form))) form
          :else               (recur))))))

(defn- parse-file
  "Read a .clj file and return {:ns sym :deps #{sym}}, or nil on failure.
  Uses edamame's incremental reader so only the ns form is parsed;
  the file body (which may contain reader syntax edamame mishandles,
  such as quoted sets) is never evaluated."
  [path]
  (try
    (let [ns-form (read-ns-form (slurp (str path)))]
      (when ns-form
        {:ns   (second ns-form)
         :deps (deps-from-ns-form ns-form)}))
    (catch Exception _ nil)))

;;; ── directory scan ───────────────────────────────────────────────────────

(defn- scan
  "Recursively scan src-dir for .clj files.
  Returns {ns-sym → #{dep-syms}} for all parseable files.
  Files are parsed in parallel (pmap)."
  [src-dir]
  (->> (fs/glob src-dir "**.clj")
       (pmap parse-file)
       (keep identity)
       (into {} (map (juxt :ns :deps)))))

(defn scan-dirs
  "Scan multiple src directories and merge their namespace graphs.
  If the same namespace appears in more than one directory the last
  directory's version wins (consistent with Clojure's own classpath
  semantics)."
  [src-dirs]
  (->> src-dirs
       (map scan)
       (apply merge {})))

(defn- scan-path
  "Scan one typed path entry {:dir string :kind :src|:test}.
  Returns {:graph {ns → #{deps}} :origins {ns → kind}}."
  [{:keys [dir kind]}]
  (let [graph (scan dir)]
    {:graph   graph
     :origins (into {} (map (fn [ns-sym] [ns-sym kind]) (keys graph)))}))

(defn scan-paths
  "Scan multiple typed path entries and merge results.
  Later entries win on namespace collisions for both graph and origin."
  [paths]
  (->> paths
       (map scan-path)
       (reduce (fn [a b]
                 {:graph   (merge (:graph a) (:graph b))
                  :origins (merge (:origins a) (:origins b))})
               {:graph {} :origins {}})))

;;; ── single-pass full-file scanning ───────────────────────────────────────
;;;
;;; Functions in this section accept a `terms-fn` as their first argument.
;;; `terms-fn` is called as (terms-fn ns-sym all-forms) → [term] for each
;;; file parsed. The caller supplies the extraction logic; scan supplies
;;; only the IO and parsing.

(defn- read-all-forms
  "Read all parseable top-level forms from content with source locations.
  Uses edamame's whole-string parser so top-level forms carry row/end-row metadata."
  [content]
  (try
    (e/parse-string-all content parse-opts)
    (catch Exception _ [])))

(defn parse-file-all-forms
  "Read a .clj file and return {:file string :ns sym :forms [form] :source string}, or nil on failure.
  Reads the full file body so that def names, docstrings, and source spans are captured."
  [path]
  (try
    (let [content (slurp (str path))
          forms   (read-all-forms content)
          ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))
          ns-sym  (when ns-form (second ns-form))]
      (when ns-sym
        {:file (str path)
         :ns ns-sym
         :forms forms
         :source content}))
    (catch Exception _ nil)))

;;; ── combined single-pass scan ────────────────────────────────────────────

(defn- parse-file-all
  "Read a .clj file once and return {:ns sym :deps #{sym} :terms [term]}, or nil.
  `terms-fn` extracts terms from the fully-parsed file (see section comment).
  Single-pass: reads and parses the file once, extracting both the structural
  dependency graph (deps) and the term list in one shot."
  [terms-fn path]
  (when-let [{:keys [ns forms]} (parse-file-all-forms path)]
    (let [ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))]
      {:ns    ns
       :deps  (deps-from-ns-form ns-form)
       :terms (terms-fn ns forms)})))

(defn- scan-all
  "Recursively scan src-dir for .clj files in a single pass per file.
  `terms-fn` extracts terms per file (see section comment).
  Returns {:graph {ns → #{deps}} :ns->terms {ns → [term]}}.
  Files are parsed in parallel (pmap); results reduced sequentially."
  [terms-fn src-dir]
  (->> (fs/glob src-dir "**.clj")
       (pmap (partial parse-file-all terms-fn))
       (keep identity)
       (reduce (fn [acc {:keys [ns deps terms]}]
                 (-> acc
                     (assoc-in [:graph    ns] deps)
                     (assoc-in [:ns->terms ns] terms)))
               {:graph {} :ns->terms {}})))

(defn scan-all-dirs
  "Scan multiple src directories in a single pass per file.
  `terms-fn` extracts terms per file (see section comment).
  Returns {:graph {ns → #{deps}} :ns->terms {ns → [term]}}.
  Later directories win on namespace collision."
  [terms-fn src-dirs]
  (->> src-dirs
       (map (partial scan-all terms-fn))
       (reduce (fn [a b]
                 {:graph     (merge (:graph a) (:graph b))
                  :ns->terms (merge (:ns->terms a) (:ns->terms b))})
               {:graph {} :ns->terms {}})))
