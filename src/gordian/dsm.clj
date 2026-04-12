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

(defn ns->block-id
  "Return a map from namespace symbol to containing block id."
  [blocks]
  (reduce (fn [m {:keys [id members]}]
            (reduce (fn [m' ns] (assoc m' ns id)) m members))
          {}
          blocks))

(defn collapsed-edges
  "Return counted inter-block edges induced by namespace-level graph edges."
  [graph blocks]
  (let [ns->bid (ns->block-id blocks)]
    (->> graph
         (reduce-kv (fn [acc from deps]
                      (let [from-id (get ns->bid from)]
                        (reduce (fn [acc' to]
                                  (let [to-id (get ns->bid to)]
                                    (if (and from-id to-id (not= from-id to-id))
                                      (update acc' [from-id to-id] (fnil inc 0))
                                      acc')))
                                acc
                                deps)))
                    {})
         (sort-by key)
         (mapv (fn [[[from to] edge-count]]
                 {:from from :to to :edge-count edge-count})))))

(defn internal-edge-count
  "Count namespace-level edges whose endpoints both lie within members."
  [graph members]
  (let [member-set (set members)]
    (reduce (fn [n from]
              (+ n (count (filter member-set (get graph from #{})))))
            0
            members)))

(defn block-density
  "Directed internal density for a block, excluding self-edges."
  [graph members]
  (let [n (count members)]
    (if (<= n 1)
      0.0
      (double (/ (internal-edge-count graph members)
                 (* n (dec n)))))))

(defn annotate-blocks
  "Add derived metrics to indexed blocks."
  [graph blocks]
  (mapv (fn [{:keys [members size] :as block}]
          (let [edge-count (internal-edge-count graph members)]
            (assoc block
                   :size size
                   :cyclic? (> size 1)
                   :internal-edge-count edge-count
                   :density (block-density graph members))))
        blocks))

(defn ordered-members
  "Return deterministic local member ordering for an SCC detail matrix."
  [members]
  (->> members (sort-by str) vec))

(defn internal-edge-coords
  "Return local [i j] coordinates for internal edges under ordered-members."
  [graph members]
  (let [members    (vec members)
        index-of   (zipmap members (range))
        member-set (set members)]
    (->> members
         (mapcat (fn [from]
                   (let [i (get index-of from)]
                     (->> (get graph from #{})
                          (filter member-set)
                          (sort-by str)
                          (mapv (fn [to] [i (get index-of to)]))))))
         vec)))

(defn scc-detail
  "Return one detail record for a non-singleton SCC block."
  [graph {:keys [id members] :as _block}]
  (let [members' (ordered-members members)]
    {:id id
     :members members'
     :size (count members')
     :internal-edges (internal-edge-coords graph members')
     :internal-edge-count (internal-edge-count graph members')
     :density (block-density graph members')}))

(defn scc-details
  "Return detail records for all non-singleton SCC blocks in block-id order."
  [graph blocks]
  (->> blocks
       (filter #(< 1 (:size %)))
       (mapv (partial scc-detail graph))))
