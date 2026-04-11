(ns gordian.explain
  "Drill-down query functions for namespace and pair investigation.
  All functions are pure — they query report data structures.")

;;; ── graph queries ────────────────────────────────────────────────────────

(defn shortest-path
  "BFS with parent tracking from `from` to `to` over directed graph.
  Returns [from ... to] or nil if no path exists.
  Only traverses project-internal edges (keys of graph).
  Returns nil for self-paths (from = to)."
  [graph from to]
  (when (and (not= from to) (contains? graph from) (contains? graph to))
    (let [project-nss (set (keys graph))]
      (loop [queue (into clojure.lang.PersistentQueue/EMPTY
                         (filter project-nss (get graph from #{})))
             visited #{from}
             parents (into {} (map (fn [n] [n from]))
                           (filter project-nss (get graph from #{})))]
        (when (seq queue)
          (let [node (peek queue)]
            (if (= node to)
              ;; reconstruct path
              (loop [path (list to) cur to]
                (let [p (get parents cur)]
                  (if (= p from)
                    (vec (cons from path))
                    (recur (cons p path) p))))
              ;; continue BFS
              (let [new-visited (conj visited node)
                    neighbors   (remove new-visited
                                        (filter project-nss (get graph node #{})))]
                (recur (into (pop queue) neighbors)
                       new-visited
                       (into parents (map (fn [n] [n node]) neighbors)))))))))))

(defn direct-deps
  "Direct dependencies of ns-sym, split into project and external.
  project-nss — set of all project namespace symbols (keys of graph)."
  [graph project-nss ns-sym]
  (let [deps (get graph ns-sym #{})]
    {:project  (vec (sort-by str (filter project-nss deps)))
     :external (vec (sort-by str (remove project-nss deps)))}))

(defn direct-dependents
  "Project namespaces that directly require ns-sym."
  [graph ns-sym]
  (vec (sort-by str
                (keep (fn [[k deps]] (when (contains? deps ns-sym) k))
                      graph))))

(defn ns-pairs
  "Filter pairs to those involving ns-sym (as :ns-a or :ns-b)."
  [pairs ns-sym]
  (filterv (fn [p] (or (= ns-sym (:ns-a p))
                       (= ns-sym (:ns-b p))))
           pairs))
