(ns gordian.scan
  (:require [babashka.fs :as fs]
            [edamame.core :as e]
            [gordian.conceptual :as conceptual]))

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

(defn deps-from-ns-form
  "Return #{dep-syms} mentioned in (:require ...) clauses of a (ns ...) form."
  [ns-form]
  (let [clauses      (drop 2 ns-form)
        req-clauses  (filter require-clause? clauses)]
    (->> req-clauses
         (mapcat rest)
         (keep extract-dep)
         set)))

;;; ── file parsing ─────────────────────────────────────────────────────────

(def ^:private parse-opts
  "edamame options for parsing .clj source files."
  {:read-cond :allow
   :features  #{:clj}
   :fn        true
   :deref     true
   :regex     true})

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

(defn parse-file
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

(defn scan
  "Recursively scan src-dir for .clj files.
  Returns {ns-sym → #{dep-syms}} for all parseable files."
  [src-dir]
  (->> (fs/glob src-dir "**.clj")
       (keep parse-file)
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

;;; ── full-file term scanning ───────────────────────────────────────────────

(defn- read-all-forms
  "Read all parseable top-level forms from content using edamame's incremental
  reader.  Skips forms that fail to parse rather than aborting — same
  resilience strategy as read-ns-form."
  [content]
  (let [rdr (e/reader content)]
    (loop [forms []]
      (let [form (try (e/parse-next rdr parse-opts)
                      (catch Exception _ ::skip))]
        (cond
          (= ::e/eof form) forms
          (= ::skip  form) (recur forms)
          :else            (recur (conj forms form)))))))

(defn parse-file-terms
  "Read a .clj file and return [ns-sym [term]], or nil on failure.
  Reads the full file body so that def names and docstrings are captured."
  [path]
  (try
    (let [content (slurp (str path))
          forms   (read-all-forms content)
          ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))
          ns-sym  (when ns-form (second ns-form))]
      (when ns-sym
        [ns-sym (conceptual/extract-terms ns-sym forms)]))
    (catch Exception _ nil)))

(defn scan-terms
  "Recursively scan src-dir for .clj files.
  Returns {ns-sym → [term]} for all parseable files."
  [src-dir]
  (->> (fs/glob src-dir "**.clj")
       (keep parse-file-terms)
       (into {})))

(defn scan-terms-dirs
  "Scan multiple src directories and merge their term maps.
  Later directories win on namespace collision."
  [src-dirs]
  (->> src-dirs
       (map scan-terms)
       (apply merge {})))
