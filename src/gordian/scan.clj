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

(defn- find-ns-form [forms]
  (first (filter #(and (seq? %) (= 'ns (first %))) forms)))

(defn parse-file
  "Read a .clj file and return {:ns sym :deps #{sym}}, or nil on failure.
  Uses edamame for robust parsing — handles reader conditionals (#?),
  tagged literals, and other Clojure-specific syntax."
  [path]
  (try
    (let [content (slurp (str path))
          forms   (e/parse-string-all content {:read-cond :allow
                                               :features  #{:clj}
                                               :fn        true
                                               :deref     true
                                               :regex     true})
          ns-form (find-ns-form forms)]
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
