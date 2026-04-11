(ns gordian.filter
  "Namespace exclusion by regex pattern.")

(defn filter-graph
  "Remove namespaces matching any pattern from the dependency graph.
  Removes matched ns from both keys and dependency sets.
  patterns — seq of regex strings. Empty/nil patterns → graph unchanged."
  [graph patterns]
  (if (seq patterns)
    (let [pats     (mapv re-pattern patterns)
          excluded? (fn [ns-sym]
                      (let [s (str ns-sym)]
                        (boolean (some #(re-find % s) pats))))]
      (into {}
            (comp (remove (fn [[k _]] (excluded? k)))
                  (map (fn [[k v]] [k (into #{} (remove excluded?) v)])))
            graph))
    graph))
