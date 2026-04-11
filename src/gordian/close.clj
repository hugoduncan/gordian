(ns gordian.close)

(defn- reachable-from
  "BFS from `start` over adjacency map `graph`.
  Returns #{all nodes reachable from start}, which may include start itself
  if there is a cycle back to it.
  Nodes referenced as deps but absent as keys in graph are treated as sinks."
  [graph start]
  (loop [frontier (vec (get graph start #{}))
         visited  #{}]
    (if (empty? frontier)
      visited
      (let [[node & rest-f] frontier
            new-vis  (conj visited node)
            new-nbrs (remove new-vis (get graph node #{}))]
        (recur (into (vec rest-f) new-nbrs) new-vis)))))

(defn close
  "Compute the transitive closure of an adjacency map {node → #{direct-deps}}.
  Returns {node → #{all-transitive-deps}}.

  Only nodes present as keys in `graph` appear as keys in the result.
  External deps (referenced but not a key) are included in reachable sets
  but have no entry of their own."
  [graph]
  (into {} (map (fn [node] [node (reachable-from graph node)]) (keys graph))))
