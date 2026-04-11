(ns gordian.family
  "Namespace family detection and family-scoped coupling metrics.
  A family is defined by the parent namespace prefix (drop last segment).
  All functions are pure."
  (:require [clojure.string :as str]))

(defn family-prefix
  "Return the family prefix for a namespace symbol.
  Drops the last dot-separated segment.
  Single-segment namespaces return \"\" (root family)."
  [ns-sym]
  (let [s (str ns-sym)
        i (str/last-index-of s ".")]
    (if i (subs s 0 i) "")))

(defn same-family?
  "True if two namespace symbols share the same family prefix."
  [a b]
  (= (family-prefix a) (family-prefix b)))

(defn family-metrics
  "Compute family-scoped coupling metrics for every node in graph.
  graph — {ns-sym → #{dep-ns-sym}} (direct require edges, project-only keys).
  Returns {ns-sym → {:family         string
                      :ca-family     int
                      :ca-external   int
                      :ce-family     int
                      :ce-external   int}}."
  [graph]
  (let [project (set (keys graph))
        ;; precompute family prefix for all project namespaces
        ns->fam (into {} (map (fn [ns] [ns (family-prefix ns)])) project)
        ;; reverse index: for each ns, who depends on it?
        dependents (reduce-kv
                    (fn [acc ns deps]
                      (reduce (fn [m d]
                                (if (project d)
                                  (update m d (fnil conj []) ns)
                                  m))
                              acc deps))
                    {} graph)]
    (into {}
          (map (fn [ns]
                 (let [fam      (get ns->fam ns)
                       deps     (filter project (get graph ns #{}))
                       dep-of   (get dependents ns [])
                       ce-fam   (count (filter #(= fam (get ns->fam %)) deps))
                       ce-ext   (count (remove #(= fam (get ns->fam %)) deps))
                       ca-fam   (count (filter #(= fam (get ns->fam %)) dep-of))
                       ca-ext   (count (remove #(= fam (get ns->fam %)) dep-of))]
                   [ns {:family      fam
                        :ca-family   ca-fam
                        :ca-external ca-ext
                        :ce-family   ce-fam
                        :ce-external ce-ext}])))
          project)))
