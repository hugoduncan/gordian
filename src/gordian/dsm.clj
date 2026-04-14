(ns gordian.dsm
  (:require [gordian.close :as close]))

(declare dsm-report*)

(def ^:dynamic *profile*
  nil)

(defn- now-nanos []
  (System/nanoTime))

(defn- record-profile!
  [k elapsed-nanos]
  (when *profile*
    (swap! *profile*
           (fn [m]
             (-> m
                 (update-in [k :count] (fnil inc 0))
                 (update-in [k :nanos] (fnil + 0) elapsed-nanos))))))

(defn- profiled
  [k f]
  (let [start (now-nanos)
        ret   (f)
        end   (now-nanos)]
    (record-profile! k (- end start))
    ret))

(defn- profile-summary
  [profile]
  (into {}
        (map (fn [[k {:keys [count nanos]}]]
               [k {:count count
                   :nanos nanos
                   :millis (/ nanos 1000000.0)}]))
        profile))

(defn- index-blocks
  "Assign stable block ids and basic metadata to ordered SCCs."
  [ordered-sccs]
  (mapv (fn [idx members]
          {:id idx
           :members (vec members)
           :size (count members)})
        (range)
        ordered-sccs))

(defn- ns->block-id
  "Return a map from namespace symbol to containing block id."
  [blocks]
  (reduce (fn [m {:keys [id members]}]
            (reduce (fn [m' ns] (assoc m' ns id)) m members))
          {}
          blocks))

(defn- collapsed-edges
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

(defn- internal-edge-count
  "Count namespace-level edges whose endpoints both lie within members."
  [graph members]
  (let [member-set (set members)]
    (reduce (fn [n from]
              (+ n (count (filter member-set (get graph from #{})))))
            0
            members)))

(defn- block-density
  "Directed internal density for a block, excluding self-edges."
  [graph members]
  (let [n (count members)]
    (if (<= n 1)
      0.0
      (double (/ (internal-edge-count graph members)
                 (* n (dec n)))))))

(defn- annotate-blocks
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

(defn- internal-edge-coords
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

(defn- project-graph
  "Restrict graph to project-internal nodes only.
  Drops dependency targets not present as graph keys."
  [graph]
  (let [project-ns (set (keys graph))]
    (into {}
          (map (fn [[ns deps]]
                 [ns (into #{} (filter project-ns) deps)]))
          graph)))

(defn- reverse-graph
  "Reverse all edges in a project graph, preserving all nodes as keys."
  [graph]
  (reduce-kv (fn [rev from deps]
               (reduce (fn [m to]
                         (update m to (fnil conj #{}) from))
                       rev
                       deps))
             (zipmap (keys graph) (repeat #{}))
             graph))

(defn- dfs-topo-order
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

(defn- ordered-nodes
  "Return deterministic project-internal namespace order for DSM layout."
  [graph]
  (-> graph project-graph dfs-topo-order))

(defn- index-of
  "Map ordered namespaces to 0-based positions."
  [ordered]
  (zipmap ordered (range)))

(defn- ordered-edges
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

(def ^:private default-size-penalty-beta
  0.05)

(defn- weak-cohesion-penalty
  "Penalty for multi-namespace blocks that are too sparse to justify their size.
  Measures how far internal edge count falls below a modest target density.
  Zero-internal blocks still receive the stronger global cohesion penalty."
  [block-size internal]
  (if (<= block-size 1)
    0.0
    (let [possible-edges   (* block-size (dec block-size))
          target-internal  (* weak-cohesion-target-density possible-edges)
          missing-internal (max 0.0 (- target-internal internal))]
      missing-internal)))

(defn- interval-endpoints
  [n]
  (for [a (range n)
        b (range a n)]
    [a b]))

(defn- block-costs
  [ordered-edges n beta]
  (let [stats (ordered-edge-stats ordered-edges n)]
    (into {}
          (map (fn [[a b]]
                 (let [{:keys [internal crossing]} (interval-stats stats a b)
                       block-size                  (inc (- b a))
                       size-penalty                (* beta block-size block-size)
                       weak-penalty                (weak-cohesion-penalty block-size internal)]
                   [[a b]
                    (+ crossing
                       size-penalty
                       weak-penalty)])))
          (interval-endpoints n))))

(defn- reconstruct-partition
  "Reconstruct inclusive intervals from backpointer map ending at end-idx."
  [back end-idx]
  (loop [t end-idx
         acc []]
    (if (neg? t)
      (vec (reverse acc))
      (let [a (get back t)]
        (recur (dec a) (conj acc [a t]))))))

(defn- ordering-costs
  [graph ordered beta]
  (profiled :ordering-costs
            (fn []
              (let [edges (ordered-edges graph ordered)
                    n     (count ordered)]
                {:edges edges
                 :n n
                 :costs (block-costs edges n beta)}))))

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

(defn- optimal-partition
  "Return optimal contiguous inclusive interval partition for fixed order.
  Dynamic programming over split points with deterministic leftmost tie-break."
  [graph ordered beta]
  (profiled :optimal-partition
            (fn []
              (optimal-partition-from-costs (ordering-costs graph ordered beta)))))

(defn- block-members
  "Map inclusive interval partitions back to ordered namespace member vectors."
  [ordered intervals]
  (mapv (fn [[a b]] (subvec (vec ordered) a (inc b))) intervals))

(defn- block-swap-valid?
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

(defn- block-edge-counts
  "Return counted inter-block edges for a partition-defined block set."
  [graph blocks]
  (collapsed-edges graph blocks))

(defn- induced-subgraph
  "Return the project-induced subgraph over members, preserving member order as keys.
  Dependency targets outside members are removed."
  [graph members]
  (let [member-set (set members)]
    (into {}
          (map (fn [ns]
                 [ns (into #{} (filter member-set) (get graph ns #{}))]))
          members)))

(defn- block-detail
  "Return one detail record for a block."
  [graph {:keys [id members] :as _block}]
  (let [members' (vec members)]
    {:id id
     :members members'
     :size (count members')
     :internal-edges (internal-edge-coords graph members')
     :internal-edge-count (internal-edge-count graph members')
     :density (block-density graph members')}))

(defn- block-details
  "Return detail records for all blocks in block-id order."
  [graph blocks]
  (mapv (partial block-detail graph) blocks))

(defn- block-summary
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

(defn- partition-cost
  "Return total cost of the optimal partition for ordered namespaces."
  [graph ordered beta]
  (profiled :partition-cost
            (fn []
              (let [{:keys [costs] :as cost-data} (ordering-costs graph ordered beta)
                    parts                         (optimal-partition-from-costs cost-data)]
                (reduce (fn [acc [a b]]
                          (+ acc (get costs [a b])))
                        0.0
                        parts)))))

(defn- valid-adjacent-swap?
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

(defn- edge-length-delta-after-adjacent-swap
  "Cheap local proxy for whether an adjacent swap is promising.
  Computes the exact delta in total edge length Σ|from-to| for edges incident to
  the swapped nodes only. Negative is better; positive is worse."
  [project reverse ordered idx]
  (let [a       (nth ordered idx nil)
        b       (nth ordered (inc idx) nil)
        pos     (index-of ordered)
        before  (fn [x y] (Math/abs ^long (- ^long (pos x) ^long (pos y))))
        after-p (fn [n]
                  (cond
                    (= n a) (inc idx)
                    (= n b) idx
                    :else   (pos n)))
        after   (fn [x y] (Math/abs ^long (- ^long (after-p x) ^long (after-p y))))
        touched (set (concat
                      (map (fn [to] [a to]) (get project a #{}))
                      (map (fn [to] [b to]) (get project b #{}))
                      (map (fn [from] [from a]) (get reverse a #{}))
                      (map (fn [from] [from b]) (get reverse b #{}))))]
    (reduce (fn [delta [from to]]
              (+ delta (- (after from to)
                          (before from to))))
            0
            touched)))

(defn- refine-order-by-node-swaps
  [project closed ordered beta]
  (profiled :refine-node
            (fn []
              (let [reverse (reverse-graph project)]
                (loop [ordered (vec ordered)]
                  (let [base-cost (partition-cost project ordered beta)
                        candidate (first (for [idx (range (dec (count ordered)))
                                               :when (valid-adjacent-swap? project closed ordered idx)
                                               :when (<= (edge-length-delta-after-adjacent-swap
                                                          project reverse ordered idx)
                                                         0)
                                               :let [swapped (swap-adjacent ordered idx)
                                                     cost    (partition-cost project swapped beta)]
                                               :when (< cost base-cost)]
                                           swapped))]
                    (if candidate
                      (recur candidate)
                      ordered)))))))

(defn- block-edge-length-delta-after-swap
  "Cheap local proxy for adjacent block swaps.
  Computes the exact delta in total edge length Σ|from-to| for edges incident to
  either swapped block. Negative is better; positive is worse."
  [project reverse ordered blocks idx]
  (let [left       (nth blocks idx nil)
        right      (nth blocks (inc idx) nil)
        pos        (index-of ordered)
        left-set   (set left)
        right-set  (set right)
        left-start (pos (first left))
        right-start (pos (first right))
        swapped-pos (fn [n]
                      (let [p (pos n)]
                        (cond
                          (left-set n)  (+ right-start (- p left-start))
                          (right-set n) (+ left-start (- p right-start))
                          :else         p)))
        before     (fn [x y] (Math/abs ^long (- ^long (pos x) ^long (pos y))))
        after      (fn [x y] (Math/abs ^long (- ^long (swapped-pos x) ^long (swapped-pos y))))
        touched-ns (concat left right)
        touched    (set (concat
                         (mapcat (fn [n] (map (fn [to] [n to]) (get project n #{}))) touched-ns)
                         (mapcat (fn [n] (map (fn [from] [from n]) (get reverse n #{}))) touched-ns)))]
    (reduce (fn [delta [from to]]
              (+ delta (- (after from to)
                          (before from to))))
            0
            touched)))

(defn- refine-order-by-block-swaps
  [project closed ordered beta]
  (profiled :refine-block
            (fn []
              (let [reverse (reverse-graph project)]
                (loop [ordered (vec ordered)]
                  (let [base-cost (partition-cost project ordered beta)
                        blocks    (block-members ordered (optimal-partition project ordered beta))
                        candidate (first (for [idx (range (dec (count blocks)))
                                               :when (block-swap-valid? project closed blocks idx)
                                               :when (<= (block-edge-length-delta-after-swap
                                                          project reverse ordered blocks idx)
                                                         0)
                                               :let [swapped-blocks (swap-adjacent-blocks (vec blocks) idx)
                                                     swapped-order  (vec (mapcat identity swapped-blocks))
                                                     cost           (partition-cost project swapped-order beta)]
                                               :when (< cost base-cost)]
                                           swapped-order))]
                    (if candidate
                      (recur candidate)
                      ordered)))))))

(defn- refine-order
  "Deterministically improve order by accepting cost-lowering valid adjacent swaps.
  First refine at node granularity, then refine at adjacent block granularity."
  [graph ordered beta]
  (let [project      (project-graph graph)
        closed       (close/close project)
        node-refined (refine-order-by-node-swaps project closed ordered beta)]
    (refine-order-by-block-swaps project closed node-refined beta)))

(def ^:private max-refinement-nodes
  120)

(def ^:private recursive-min-block-size
  5)

(def ^:private max-recursive-depth
  3)

(defn- should-refine-order?
  "Return true when local adjacent-swap refinement should run.
  Refinement is intentionally skipped for larger graphs because it scales
  poorly and can dominate end-to-end DSM runtime without improving the
  canonical partitioning pipeline itself."
  [ordered]
  (<= (count ordered) max-refinement-nodes))

(defn- recursive-subdsm
  [graph {:keys [members size]} depth]
  (profiled :recursive-subdsm
            (fn []
              (when (and (< depth max-recursive-depth)
                         (>= size recursive-min-block-size))
                (let [subgraph (induced-subgraph graph members)
                      report   (dsm-report* subgraph (inc depth))
                      child-count (count (:blocks report))
                      largest-child (apply max 0 (map :size (:blocks report)))]
                  (when (and (> child-count 1)
                             (< largest-child size))
                    report))))))

(defn- attach-recursive-subdsms
  [graph depth blocks]
  (mapv (fn [block]
          (assoc block :subdsm (recursive-subdsm graph block depth)))
        blocks))

(defn dsm-report*
  "Assemble the complete pure DSM payload from a structural graph.
  Internal helper supports bounded recursive decomposition for large blocks."
  [graph depth]
  (profiled :dsm-report
            (fn []
              (let [graph         (project-graph graph)
                    beta          default-size-penalty-beta
                    initial-order (profiled :ordered-nodes #(ordered-nodes graph))
                    refine?       (and (zero? depth)
                                       (should-refine-order? initial-order))
                    ordered       (if refine?
                                    (refine-order graph initial-order beta)
                                    initial-order)
                    parts         (optimal-partition graph ordered beta)
                    blocks        (->> parts
                                       (block-members ordered)
                                       index-blocks
                                       (annotate-blocks graph)
                                       (attach-recursive-subdsms graph depth))
                    edges         (profiled :block-edge-counts #(block-edge-counts graph blocks))
                    details       (profiled :block-details #(block-details graph blocks))
                    summary       (profiled :block-summary #(block-summary blocks edges))]
                {:basis :diagonal-blocks
                 :ordering {:strategy :dfs-topo
                            :refined? (not= initial-order ordered)
                            :alpha 2.0
                            :beta beta
                            :nodes ordered}
                 :blocks blocks
                 :edges edges
                 :summary summary
                 :details details}))))

(defn dsm-report
  "Assemble the complete pure DSM payload from a structural graph."
  [graph]
  (dsm-report* graph 0))

(defn profiled-dsm-report
  "Return DSM payload plus phase timing summary.
  Intended for complexity/runtime investigation, not canonical output wiring."
  [graph]
  (let [profile (atom {})
        report  (binding [*profile* profile]
                  (dsm-report graph))]
    {:report report
     :profile (profile-summary @profile)}))
