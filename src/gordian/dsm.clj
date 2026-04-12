(ns gordian.dsm
  (:require [gordian.scc :as scc]))

(defn block-label
  "Return a stable label basis for an SCC block.
  Uses the lexicographically smallest member namespace name."
  [members]
  (->> members
       (map str)
       sort
       first))

(defn- condensation-edges
  [graph sccs]
  (let [scc-vec   (vec sccs)
        ns->scc   (reduce-kv (fn [m idx members]
                               (reduce (fn [m' ns] (assoc m' ns idx)) m members))
                             {}
                             scc-vec)]
    (reduce-kv (fn [m from-ns deps]
                 (let [from-id (get ns->scc from-ns)]
                   (reduce (fn [m' to]
                             (let [to-id (get ns->scc to)]
                               (if (and from-id to-id (not= from-id to-id))
                                 (update m' from-id (fnil conj #{}) to-id)
                                 m')))
                           m
                           deps)))
               (zipmap (range (count scc-vec)) (repeat #{}))
               graph)))

(defn- topo-sort-stable
  [adj labels]
  (let [nodes    (sort (keys adj))
        indegree (reduce-kv (fn [m _from tos]
                              (reduce (fn [m' to] (update m' to (fnil inc 0)))
                                      m
                                      tos))
                            (zipmap nodes (repeat 0))
                            adj)]
    (loop [indegree indegree
           ready    (sort-by labels (filter #(zero? (get indegree % 0)) nodes))
           order    []]
      (if-let [n (first ready)]
        (let [ready' (vec (rest ready))
              [indegree' newly-ready]
              (reduce (fn [[m rs] to]
                        (let [m' (update m to dec)]
                          (if (zero? (get m' to))
                            [m' (conj rs to)]
                            [m' rs])))
                      [indegree []]
                      (sort-by labels (get adj n #{})))]
          (recur indegree'
                 (sort-by labels (into ready' newly-ready))
                 (conj order n)))
        order))))

(defn ordered-sccs
  "Return SCCs in deterministic topological order of the condensation graph.
  Includes singleton SCCs.
  Each SCC is returned as a vector in lexicographic member order."
  [graph]
  (let [sccs   (scc/tarjan graph)
        labels (into {} (map-indexed (fn [idx members] [idx (block-label members)]) sccs))
        adj    (condensation-edges graph sccs)
        order  (reverse (topo-sort-stable adj labels))]
    (mapv (fn [idx]
            (->> (get (vec sccs) idx)
                 (sort-by str)
                 vec))
          order)))

(defn index-blocks
  "Assign stable block ids and basic metadata to ordered SCCs."
  [ordered-sccs]
  (mapv (fn [idx members]
          {:id idx
           :members (vec members)
           :size (count members)})
        (range)
        ordered-sccs))
