(ns gordian.scc)

;;; Tarjan's strongly-connected-components algorithm.
;;;
;;; Time:  O(V + E)
;;; Space: O(V)
;;;
;;; A strongly connected component (SCC) in the require graph is a set of
;;; namespaces that all mutually reach each other — i.e. a dependency cycle.
;;; Tarjan produces SCCs in reverse topological order.

(defn- tarjan
  "Return all SCCs of `graph` as a vector of sets.
  `graph` — adjacency map {node → #{direct-deps}}.
  Nodes referenced as deps but absent as keys are ignored as sinks."
  [graph]
  (let [idx     (atom 0)
        stack   (atom [])
        on-stk  (atom #{})
        indices (atom {})
        lowlink (atom {})
        result  (atom [])]
    (letfn [(visit [v]
              (let [i @idx]
                (swap! indices assoc v i)
                (swap! lowlink assoc v i)
                (swap! idx inc)
                (swap! stack conj v)
                (swap! on-stk conj v)
                (doseq [w (get graph v #{})]
                  (cond
                    ;; w not yet visited
                    (not (contains? @indices w))
                    (do (visit w)
                        (swap! lowlink update v min (get @lowlink w)))
                    ;; w is on the stack — back edge
                    (contains? @on-stk w)
                    (swap! lowlink update v min (get @indices w))))
                ;; v is the root of an SCC
                (when (= (get @lowlink v) (get @indices v))
                  (let [scc (loop [acc #{}]
                              (let [w (peek @stack)]
                                (swap! stack  pop)
                                (swap! on-stk disj w)
                                (let [acc' (conj acc w)]
                                  (if (= w v) acc' (recur acc')))))]
                    (swap! result conj scc)))))]
      (doseq [v (keys graph)]
        (when-not (contains? @indices v)
          (visit v))))
    @result))

(defn- cycles
  "Filter `sccs` to only non-trivial SCCs — those that represent real cycles.
  A trivial SCC is a single node with no self-loop in `graph`."
  [graph sccs]
  (->> sccs
       (filter (fn [scc]
                 (or (> (count scc) 1)
                     (contains? (get graph (first scc) #{}) (first scc)))))
       (sort-by (comp - count))
       vec))

(defn find-cycles
  "Convenience: tarjan then filter to cycles only.
  Returns vector of #{ns-sym} sets, largest first."
  [graph]
  (cycles graph (tarjan graph)))
