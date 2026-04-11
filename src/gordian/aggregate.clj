(ns gordian.aggregate
  (:require [clojure.set :as set]))

;;; ── helpers ──────────────────────────────────────────────────────────────

(defn- project-reachable
  "Intersection of `deps` with `project-nodes`, minus `node` itself.
  Excludes self so a node does not inflate its own reach score."
  [project-nodes node deps]
  (-> deps (set/intersection project-nodes) (disj node)))

(defn- fan-in-counts
  "Given {node → #{project-reach}}, return {node → int} counting how many
  project nodes can transitively reach each node."
  [proj-reach]
  (reduce (fn [acc [_src deps]]
            (reduce #(update %1 %2 (fnil inc 0)) acc deps))
          {}
          proj-reach))

;;; ── public API ───────────────────────────────────────────────────────────

(defn aggregate
  "Compute propagation-cost metrics from a transitively-closed graph.

  `closed-graph` — {node → #{transitively-reachable nodes}}
                    (produced by gordian.close/close)

  Returns:
    {:propagation-cost  double          ; Σ|reach(n)| / N²
     :nodes [{:ns      sym
              :reach   double           ; |project-reach(n)| / N
              :fan-in  double}]}        ; |{m : n ∈ reach(m)}| / N

  Only project nodes (keys of closed-graph) are counted.
  Self-references and external deps are excluded from reach sets."
  [closed-graph]
  (let [project-nodes (set (keys closed-graph))
        N             (count project-nodes)]
    (if (zero? N)
      {:propagation-cost 0.0 :nodes []}
      (let [proj-reach   (into {} (map (fn [[node deps]]
                                         [node (project-reachable project-nodes node deps)])
                                       closed-graph))
            fi-counts    (fan-in-counts proj-reach)
            total-reach  (transduce (map (comp count val)) + proj-reach)
            pc           (/ (double total-reach) (* N N))
            ;; most-coupled first, break ties by ns name
            sorted       (sort-by (fn [n] [(- (count (proj-reach n))) (str n)])
                                  (keys closed-graph))]
        {:propagation-cost pc
         :nodes (mapv (fn [node]
                        {:ns     node
                         :reach  (/ (double (count (proj-reach  node))) N)
                         :fan-in (/ (double (get fi-counts node 0)) N)})
                      sorted)}))))
