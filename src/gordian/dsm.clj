(ns gordian.dsm
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [gordian.close :as close]
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

(defn- prefix-sums
  [xs]
  (let [arr (long-array (inc (count xs)))]
    (loop [i 0
           acc 0]
      (if (= i (count xs))
        arr
        (let [acc' (+ acc (long (nth xs i)))]
          (aset-long arr (inc i) acc')
          (recur (inc i) acc'))))))

(defn- prefix-range-sum
  [prefix a b]
  (- (aget prefix (inc b))
     (aget prefix a)))

(defn- matrix-prefix-sums
  [ordered-edges n]
  (let [prefix (make-array Long/TYPE (inc n) (inc n))]
    (doseq [[from to] ordered-edges]
      (aset-long prefix (inc from) (inc to)
                 (inc (aget prefix (inc from) (inc to)))))
    (doseq [i (range 1 (inc n))
            j (range 1 (inc n))]
      (aset-long prefix i j
                 (+ (aget prefix i j)
                    (aget prefix (dec i) j)
                    (aget prefix i (dec j))
                    (- (aget prefix (dec i) (dec j))))))
    prefix))

(defn- matrix-range-sum
  [prefix row-a row-b col-a col-b]
  (- (+ (aget prefix (inc row-b) (inc col-b))
        (aget prefix row-a col-a))
     (aget prefix row-a (inc col-b))
     (aget prefix (inc row-b) col-a)))

(defn- ordered-edge-stats
  [ordered-edges n]
  (let [row-totals (long-array n)
        col-totals (long-array n)]
    (doseq [[from to] ordered-edges]
      (aset-long row-totals from (inc (aget row-totals from)))
      (aset-long col-totals to (inc (aget col-totals to))))
    {:n n
     :row-prefix (prefix-sums (vec row-totals))
     :col-prefix (prefix-sums (vec col-totals))
     :matrix-prefix (matrix-prefix-sums ordered-edges n)}))

(defn- interval-stats
  [{:keys [row-prefix col-prefix matrix-prefix]} a b]
  (let [internal (matrix-range-sum matrix-prefix a b a b)
        outgoing (prefix-range-sum row-prefix a b)
        incoming (prefix-range-sum col-prefix a b)
        crossing (- (+ outgoing incoming) (* 2 internal))]
    {:internal internal
     :crossing crossing}))

(def ^:private weak-cohesion-target-density
  0.10)

(defn- weak-cohesion-penalty
  "Penalty for multi-namespace blocks that are too sparse to justify their size.
  Measures how far internal edge count falls below a modest target density.
  Zero-internal blocks still receive the stronger global cohesion penalty."
  [block-size internal alpha]
  (if (<= block-size 1)
    0.0
    (let [possible-edges      (* block-size (dec block-size))
          target-internal     (* weak-cohesion-target-density possible-edges)
          missing-internal    (max 0.0 (- target-internal internal))]
      (* missing-internal (pow-cost block-size alpha)))))

(defn block-cost
  "Score inclusive interval block [a,b] using Thebeau-style costs.
  Internal marks cost |B|^alpha; crossing marks cost n^alpha.
  Multi-namespace blocks with zero internal edges incur an extra cohesion penalty.
  Weakly cohesive multi-namespace blocks also incur a size-scaled sparsity penalty."
  [ordered-edges n alpha a b]
  (let [{:keys [internal crossing]} (interval-stats (ordered-edge-stats ordered-edges n) a b)
        block-size                  (inc (- b a))
        cohesion-penalty            (if (and (> block-size 1) (zero? internal))
                                      (pow-cost n alpha)
                                      0.0)
        weak-penalty                (weak-cohesion-penalty block-size internal alpha)]
    (+ (* internal (pow-cost block-size alpha))
       (* crossing (pow-cost n alpha))
       cohesion-penalty
       weak-penalty)))

(defn- interval-endpoints
  [n]
  (for [a (range n)
        b (range a n)]
    [a b]))

(defn- block-costs
  [ordered-edges n alpha]
  (let [stats           (ordered-edge-stats ordered-edges n)
        n-alpha         (pow-cost n alpha)
        size-cost-cache (into {} (map (fn [size] [size (pow-cost size alpha)])) (range 1 (inc n)))]
    (into {}
          (map (fn [[a b]]
                 (let [{:keys [internal crossing]} (interval-stats stats a b)
                       block-size                  (inc (- b a))
                       cohesion-penalty            (if (and (> block-size 1) (zero? internal))
                                                     n-alpha
                                                     0.0)
                       weak-penalty                (weak-cohesion-penalty block-size internal alpha)]
                   [[a b]
                    (+ (* internal (get size-cost-cache block-size))
                       (* crossing n-alpha)
                       cohesion-penalty
                       weak-penalty)])))
          (interval-endpoints n))))

(defn reconstruct-partition
  "Reconstruct inclusive intervals from backpointer map ending at end-idx."
  [back end-idx]
  (loop [t end-idx
         acc []]
    (if (neg? t)
      (vec (reverse acc))
      (let [a (get back t)]
        (recur (dec a) (conj acc [a t]))))))

(defn- ordering-costs
  [graph ordered alpha]
  (let [edges (ordered-edges graph ordered)
        n     (count ordered)]
    {:edges edges
     :n n
     :costs (block-costs edges n alpha)}))

(defn- optimal-partition-from-costs
  [{:keys [n costs]}]
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
                 (assoc back t best-a)))))))

(defn optimal-partition
  "Return optimal contiguous inclusive interval partition for fixed order.
  Dynamic programming over split points with deterministic leftmost tie-break."
  [graph ordered alpha]
  (optimal-partition-from-costs (ordering-costs graph ordered alpha)))

(defn transitive-consumers
  "Return {ns -> #{project namespaces that transitively depend on ns}}."
  [graph]
  (let [project   (project-graph graph)
        reversed  (reverse-graph project)
        closed    (close/close reversed)]
    (into {}
          (map (fn [ns]
                 [ns (disj (get closed ns #{}) ns)]))
          (keys project))))

(defn jaccard
  "Jaccard similarity of two sets. Empty/empty = 1.0."
  [a b]
  (let [u (count (set/union a b))]
    (if (zero? u)
      1.0
      (/ (count (set/intersection a b))
         (double u)))))

(defn co-usage-similarity
  "Return transitive-consumer Jaccard similarity for namespace pair."
  [obs a b]
  (jaccard (get obs a #{}) (get obs b #{})))

(defn block-members
  "Map inclusive interval partitions back to ordered namespace member vectors."
  [ordered intervals]
  (mapv (fn [[a b]] (subvec (vec ordered) a (inc b))) intervals))

(defn block-swap-valid?
  "True when adjacent partition blocks are pairwise incomparable in the dependency graph."
  ([graph blocks idx]
   (let [project-graph' (project-graph graph)]
     (block-swap-valid? project-graph' (close/close project-graph') blocks idx)))
  ([_project closed blocks idx]
   (let [a (nth blocks idx nil)
         b (nth blocks (inc idx) nil)]
     (and a b
          (every? true?
                  (for [x a
                        y b]
                    (and (not (contains? (get closed x #{}) y))
                         (not (contains? (get closed y #{}) x)))))))))

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

(defn partition-cost
  "Return total cost of the optimal partition for ordered namespaces."
  [graph ordered alpha]
  (let [{:keys [costs] :as cost-data} (ordering-costs graph ordered alpha)
        parts                         (optimal-partition-from-costs cost-data)]
    (reduce (fn [acc [a b]]
              (+ acc (get costs [a b])))
            0.0
            parts)))

(defn valid-adjacent-swap?
  "True when adjacent nodes at idx and idx+1 are incomparable in the dependency graph."
  ([graph ordered idx]
   (let [project (project-graph graph)]
     (valid-adjacent-swap? project (close/close project) ordered idx)))
  ([project closed ordered idx]
   (let [a (nth ordered idx nil)
         b (nth ordered (inc idx) nil)]
     (and a b
          (contains? project a)
          (contains? project b)
          (not (contains? (get closed a #{}) b))
          (not (contains? (get closed b #{}) a))))))

(defn- swap-adjacent
  [v idx]
  (assoc v idx (v (inc idx)) (inc idx) (v idx)))

(defn- swap-adjacent-blocks
  [blocks idx]
  (assoc blocks idx (blocks (inc idx)) (inc idx) (blocks idx)))

(defn- refine-order-by-node-swaps
  [project closed ordered alpha]
  (loop [ordered (vec ordered)]
    (let [base-cost (partition-cost project ordered alpha)
          candidate (first (for [idx (range (dec (count ordered)))
                                 :when (valid-adjacent-swap? project closed ordered idx)
                                 :let [swapped (swap-adjacent ordered idx)
                                       cost    (partition-cost project swapped alpha)]
                                 :when (< cost base-cost)]
                             swapped))]
      (if candidate
        (recur candidate)
        ordered))))

(defn- refine-order-by-block-swaps
  [project closed ordered alpha]
  (loop [ordered (vec ordered)]
    (let [base-cost (partition-cost project ordered alpha)
          blocks    (block-members ordered (optimal-partition project ordered alpha))
          candidate (first (for [idx (range (dec (count blocks)))
                                 :when (block-swap-valid? project closed blocks idx)
                                 :let [swapped-blocks (swap-adjacent-blocks (vec blocks) idx)
                                       swapped-order  (vec (mapcat identity swapped-blocks))
                                       cost           (partition-cost project swapped-order alpha)]
                                 :when (< cost base-cost)]
                             swapped-order))]
      (if candidate
        (recur candidate)
        ordered))))

(defn refine-order
  "Deterministically improve order by accepting cost-lowering valid adjacent swaps.
  First refine at node granularity, then refine at adjacent block granularity."
  [graph ordered alpha]
  (let [project      (project-graph graph)
        closed       (close/close project)
        node-refined (refine-order-by-node-swaps project closed ordered alpha)]
    (refine-order-by-block-swaps project closed node-refined alpha)))

(def ^:private max-refinement-nodes
  120)

(defn should-refine-order?
  "Return true when local adjacent-swap refinement should run.
  Refinement is intentionally skipped for larger graphs because it scales
  poorly and can dominate end-to-end DSM runtime without improving the
  canonical partitioning pipeline itself."
  [ordered]
  (<= (count ordered) max-refinement-nodes))

(defn dsm-report
  "Assemble the complete pure DSM payload from a structural graph."
  [graph]
  (let [graph         (project-graph graph)
        alpha         1.5
        initial-order (ordered-nodes graph)
        ordered       (if (should-refine-order? initial-order)
                        (refine-order graph initial-order alpha)
                        initial-order)
        parts         (optimal-partition graph ordered alpha)
        blocks        (->> parts
                           (block-members ordered)
                           index-blocks
                           (annotate-blocks graph))
        edges         (block-edge-counts graph blocks)
        details       (block-details graph blocks)
        summary       (block-summary blocks edges)]
    {:basis :diagonal-blocks
     :ordering {:strategy :dfs-topo
                :refined? (not= initial-order ordered)
                :alpha alpha
                :nodes ordered}
     :blocks blocks
     :edges edges
     :summary summary
     :details details}))
