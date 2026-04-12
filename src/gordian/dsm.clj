(ns gordian.dsm
  (:require [clojure.string :as str]
            [gordian.scc :as scc]))

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

(declare block-label)

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

(defn collapsed-summary
  "Return top-level summary metrics for the collapsed SCC matrix."
  [blocks edges]
  (let [block-count          (count blocks)
        singleton-block-count (count (filter #(= 1 (:size %)) blocks))
        cyclic-block-count   (count (filter :cyclic? blocks))
        largest-block-size   (apply max 0 (map :size blocks))
        inter-block-edge-count (reduce + 0 (map :edge-count edges))
        density              (if (<= block-count 1)
                               0.0
                               (double (/ inter-block-edge-count
                                          (* block-count (dec block-count)))))]
    {:block-count block-count
     :singleton-block-count singleton-block-count
     :cyclic-block-count cyclic-block-count
     :largest-block-size largest-block-size
     :inter-block-edge-count inter-block-edge-count
     :density density}))

(defn project-graph
  "Restrict graph to project-internal nodes only.
  Drops dependency targets not present as graph keys."
  [graph]
  (let [project-ns (set (keys graph))]
    (into {}
          (map (fn [[ns deps]]
                 [ns (into #{} (filter project-ns) deps)]))
          graph)))

(defn reverse-graph
  "Reverse all edges in a project graph, preserving all nodes as keys."
  [graph]
  (reduce-kv (fn [rev from deps]
               (reduce (fn [m to]
                         (update m to (fnil conj #{}) from))
                       rev
                       deps))
             (zipmap (keys graph) (repeat #{}))
             graph))

(defn topo-order
  "Return a dependees-first topological order for an acyclic graph.
  Stable lexical tie-break by namespace name."
  [graph]
  (let [rev      (reverse-graph graph)
        nodes    (sort-by str (keys rev))
        indegree (reduce-kv (fn [m _from tos]
                              (reduce (fn [m' to] (update m' to (fnil inc 0)))
                                      m
                                      tos))
                            (zipmap nodes (repeat 0))
                            rev)]
    (loop [indegree indegree
           ready    (sort-by str (filter #(zero? (get indegree % 0)) nodes))
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
                      (sort-by str (get rev n #{})))]
          (recur indegree'
                 (sort-by str (into ready' newly-ready))
                 (conj order n)))
        order))))

(defn dfs-topo-order
  "Return a deterministic DFS post-order linearization, reversed into
  dependees-first topological order. Tie-break lexicographically by namespace."
  [graph]
  (let [nodes   (sort-by str (keys graph))
        seen    (atom #{})
        out     (atom [])]
    (letfn [(visit [n]
              (when-not (contains? @seen n)
                (swap! seen conj n)
                (doseq [dep (sort-by str (get graph n #{}))]
                  (visit dep))
                (swap! out conj n)))]
      (doseq [n nodes]
        (visit n))
      (vec @out))))

(defn ordered-nodes
  "Return deterministic project-internal namespace order for DSM layout."
  [graph]
  (-> graph project-graph dfs-topo-order))

(defn common-prefix
  "Return the common dot-prefix of namespace strings, or nil if none."
  [members]
  (let [parts (map #(str/split (str %) #"\.") members)
        prefix (->> (apply map vector parts)
                    (take-while (fn [xs] (apply = xs)))
                    (map first)
                    vec)]
    (when (seq prefix)
      (str/join "." prefix))))

(defn block-label
  "Return a stable representative label basis for a block."
  [members]
  (or (common-prefix members)
      (->> members (map str) sort first)))

(defn index-of
  "Map ordered namespaces to 0-based positions."
  [ordered]
  (zipmap ordered (range)))

(defn ordered-edges
  "Return project-internal edges as sorted index pairs [from to]."
  [graph ordered]
  (let [idx        (index-of ordered)
        project-ns (set ordered)]
    (->> ordered
         (mapcat (fn [from]
                   (for [to (sort-by str (get graph from #{}))
                         :when (project-ns to)]
                     [(idx from) (idx to)])))
         vec)))

(defn interval?
  "True when index i lies within inclusive interval [a,b]."
  [a b i]
  (<= a i b))

(defn interval-internal-edge-count
  "Count ordered edges whose endpoints both lie within inclusive interval [a,b]."
  [ordered-edges a b]
  (count (filter (fn [[from to]]
                   (and (interval? a b from)
                        (interval? a b to)))
                 ordered-edges)))

(defn interval-cross-edge-count
  "Count ordered edges with exactly one endpoint inside inclusive interval [a,b]."
  [ordered-edges a b _n]
  (count (filter (fn [[from to]]
                   (let [from-in? (interval? a b from)
                         to-in?   (interval? a b to)]
                     (not= from-in? to-in?)))
                 ordered-edges)))

(defn pow-cost
  "Return x^alpha as double for DSM block scoring."
  [x alpha]
  (Math/pow (double x) (double alpha)))

(defn block-cost
  "Score inclusive interval block [a,b] using Thebeau-style costs.
  Internal marks cost |B|^alpha; crossing marks cost n^alpha."
  [ordered-edges n alpha a b]
  (let [block-size  (inc (- b a))
        internal    (interval-internal-edge-count ordered-edges a b)
        crossing    (interval-cross-edge-count ordered-edges a b n)]
    (+ (* internal (pow-cost block-size alpha))
       (* crossing (pow-cost n alpha)))))

(defn- interval-endpoints
  [n]
  (for [a (range n)
        b (range a n)]
    [a b]))

(defn- block-costs
  [ordered-edges n alpha]
  (into {}
        (map (fn [[a b]] [[a b] (block-cost ordered-edges n alpha a b)]))
        (interval-endpoints n)))

(defn reconstruct-partition
  "Reconstruct inclusive intervals from backpointer map ending at end-idx."
  [back end-idx]
  (loop [t end-idx
         acc []]
    (if (neg? t)
      (vec (reverse acc))
      (let [a (get back t)]
        (recur (dec a) (conj acc [a t]))))))

(defn optimal-partition
  "Return optimal contiguous inclusive interval partition for fixed order.
  Dynamic programming over split points with deterministic leftmost tie-break."
  [graph ordered alpha]
  (let [ordered-edges (ordered-edges graph ordered)
        n             (count ordered)
        costs         (block-costs ordered-edges n alpha)]
    (if (zero? n)
      []
      (loop [t 0
             dp {}
             back {}]
        (if (= t n)
          (reconstruct-partition back (dec n))
          (let [[best-cost best-a]
                (reduce (fn [[best-cost best-a] a]
                          (let [prev-cost (if (zero? a) 0.0 (get dp (dec a)))
                                total     (+ prev-cost (get costs [a t]))]
                            (if (or (nil? best-cost) (< total best-cost))
                              [total a]
                              [best-cost best-a])))
                        [nil nil]
                        (range (inc t)))]
            (recur (inc t)
                   (assoc dp t best-cost)
                   (assoc back t best-a))))))))

(defn block-members
  "Map inclusive interval partitions back to ordered namespace member vectors."
  [ordered intervals]
  (mapv (fn [[a b]] (subvec (vec ordered) a (inc b))) intervals))

(defn block-edge-counts
  "Return counted inter-block edges for a partition-defined block set."
  [graph blocks]
  (collapsed-edges graph blocks))

(defn block-detail
  "Return one detail record for a block."
  [graph {:keys [id members] :as _block}]
  (let [members' (vec members)]
    {:id id
     :members members'
     :size (count members')
     :internal-edges (internal-edge-coords graph members')
     :internal-edge-count (internal-edge-count graph members')
     :density (block-density graph members')}))

(defn block-details
  "Return detail records for all blocks in block-id order."
  [graph blocks]
  (mapv (partial block-detail graph) blocks))

(defn block-summary
  "Return top-level summary metrics for partitioned DSM blocks."
  [blocks edges]
  (let [block-count            (count blocks)
        singleton-block-count  (count (filter #(= 1 (:size %)) blocks))
        largest-block-size     (apply max 0 (map :size blocks))
        inter-block-edge-count (reduce + 0 (map :edge-count edges))
        density                (if (<= block-count 1)
                                 0.0
                                 (double (/ inter-block-edge-count
                                            (* block-count (dec block-count)))))]
    {:block-count block-count
     :singleton-block-count singleton-block-count
     :largest-block-size largest-block-size
     :inter-block-edge-count inter-block-edge-count
     :density density}))

(defn dsm-report
  "Assemble the complete pure DSM payload from a structural graph."
  [graph]
  (let [graph    (project-graph graph)
        ordered  (ordered-nodes graph)
        alpha    1.5
        parts    (optimal-partition graph ordered alpha)
        blocks   (->> parts
                      (block-members ordered)
                      index-blocks
                      (annotate-blocks graph))
        edges    (block-edge-counts graph blocks)
        details  (block-details graph blocks)
        summary  (block-summary blocks edges)]
    {:basis :diagonal-blocks
     :ordering {:strategy :dfs-topo
                :alpha alpha
                :nodes ordered}
     :blocks blocks
     :edges edges
     :summary summary
     :details details
     ;; compatibility during renderer migration
     :collapsed {:block-count (count blocks)
                 :blocks blocks
                 :edges edges
                 :summary summary}
     :scc-details details}))
