(ns gordian.communities
  "Community detection over gordian reports.
  All functions are pure — they take data and return data."
  (:require [clojure.set :as set]))

(defn- canonical-edge
  "Canonical undirected edge key/order for two namespaces."
  [a b]
  (if (neg? (compare (str a) (str b)))
    {:a a :b b}
    {:a b :b a}))

(defn- undirected-structural-edges
  "Convert directed require graph to unique undirected structural edges."
  [graph]
  (->> graph
       (mapcat (fn [[from deps]]
                 (for [to deps
                       :when (contains? graph to)]
                   (merge (canonical-edge from to)
                          {:weight 1.0 :sources #{:structural}}))))
       (remove #(= (:a %) (:b %)))
       (reduce (fn [m {:keys [a b] :as e}]
                 (update m [a b]
                         (fn [cur]
                           (if cur
                             (-> cur
                                 (update :weight max (:weight e))
                                 (update :sources set/union (:sources e)))
                             e))))
               {})
       vals
       (sort-by (juxt (comp str :a) (comp str :b)))
       vec))

(defn- conceptual-edges
  "Convert conceptual pairs above threshold to undirected weighted edges."
  [pairs threshold]
  (->> pairs
       (filter #(>= (:score %) threshold))
       (map (fn [{:keys [ns-a ns-b score]}]
              (merge (canonical-edge ns-a ns-b)
                     {:weight (double score)
                      :sources #{:conceptual}})))
       (sort-by (juxt (comp str :a) (comp str :b)))
       vec))

(defn- change-edges
  "Convert change pairs above threshold to undirected weighted edges."
  [pairs threshold]
  (->> pairs
       (filter #(>= (:score %) threshold))
       (map (fn [{:keys [ns-a ns-b score]}]
              (merge (canonical-edge ns-a ns-b)
                     {:weight (double score)
                      :sources #{:change}})))
       (sort-by (juxt (comp str :a) (comp str :b)))
       vec))

(defn- combined-edges
  "Merge structural, conceptual, and change edges into one weighted graph."
  [{:keys [graph conceptual-pairs change-pairs]} {:keys [conceptual-threshold change-threshold]}]
  (let [all-edges (concat (undirected-structural-edges (or graph {}))
                          (conceptual-edges (or conceptual-pairs []) (or conceptual-threshold 0.15))
                          (change-edges (or change-pairs []) (or change-threshold 0.30)))]
    (->> all-edges
         (reduce (fn [m {:keys [a b weight sources]}]
                   (update m [a b]
                           (fn [cur]
                             (if cur
                               (-> cur
                                   (update :weight + weight)
                                   (update :sources set/union sources))
                               {:a a :b b :weight weight :sources sources}))))
                 {})
         vals
         (sort-by (juxt (comp str :a) (comp str :b)))
         vec)))

(defn- threshold-edges
  "Keep edges with weight >= threshold."
  [edges threshold]
  (->> edges
       (filter #(>= (:weight %) threshold))
       vec))

(defn- adjacency-map
  "Build undirected adjacency map from edges."
  [edges]
  (reduce (fn [adj {:keys [a b]}]
            (-> adj
                (update a (fnil conj #{}) b)
                (update b (fnil conj #{}) a)))
          {}
          edges))

(defn- connected-components
  "Connected components over undirected adjacency. Include singleton nodes from all-nodes."
  [adjacency all-nodes]
  (let [all-nodes (set all-nodes)]
    (loop [remaining all-nodes
           comps []]
      (if-let [start (first remaining)]
        (let [comp (loop [stack [start] visited #{}]
                     (if-let [n (peek stack)]
                       (if (contains? visited n)
                         (recur (pop stack) visited)
                         (recur (into (pop stack) (get adjacency n #{}))
                                (conj visited n)))
                       visited))]
          (recur (set/difference remaining comp)
                 (conj comps comp)))
        (vec (sort-by (fn [members] [(- (count members)) (str (first (sort-by str members)))]) comps))))))

(defn- community-edge-count
  "Count internal edges for a community."
  [members edges]
  (let [members (set members)]
    (count (filter (fn [{:keys [a b]}]
                     (and (contains? members a) (contains? members b)))
                   edges))))

(defn- community-density
  "Unweighted undirected density for a community."
  [members edges]
  (let [n (count members)
        m (community-edge-count members edges)]
    (if (< n 2)
      0.0
      (/ (double m) (/ (* n (dec n)) 2.0)))))

(defn- internal-boundary-weight
  "Return {:internal-weight x :boundary-weight y} for a community."
  [members edges]
  (let [members (set members)]
    (reduce (fn [{:keys [internal-weight boundary-weight] :as acc} {:keys [a b weight]}]
              (let [a? (contains? members a)
                    b? (contains? members b)]
                (cond
                  (and a? b?) (assoc acc :internal-weight (+ internal-weight weight))
                  (or a? b?)  (assoc acc :boundary-weight (+ boundary-weight weight))
                  :else acc)))
            {:internal-weight 0.0 :boundary-weight 0.0}
            edges)))

(defn- bridge-namespaces
  "Top bridge namespaces by summed external edge weight."
  [members edges]
  (let [members (set members)
        scores (reduce (fn [m {:keys [a b weight]}]
                         (let [a? (contains? members a)
                               b? (contains? members b)]
                           (cond
                             (and a? (not b?)) (update m a (fnil + 0.0) weight)
                             (and b? (not a?)) (update m b (fnil + 0.0) weight)
                             :else m)))
                       {}
                       edges)]
    (->> scores
         (sort-by (fn [[n w]] [(- w) (str n)]))
         (map first)
         (take 3)
         vec)))

(defn- dominant-terms
  "Top dominant terms from internal conceptual pairs' shared terms."
  [{:keys [conceptual-pairs]} members]
  (let [members (set members)
        freqs (->> conceptual-pairs
                   (filter (fn [{:keys [ns-a ns-b]}]
                             (and (contains? members ns-a)
                                  (contains? members ns-b))))
                   (mapcat :shared-terms)
                   frequencies)]
    (->> freqs
         (sort-by (fn [[t n]] [(- n) t]))
         (map first)
         (take 5)
         vec)))

(defn- threshold-for-lens [{:keys [lens threshold]}]
  (or threshold
      (case lens
        :conceptual 0.15
        :change 0.30
        :combined 0.75
        nil)))

(defn- build-edges [report {:keys [lens] :as opts}]
  (case lens
    :structural (undirected-structural-edges (:graph report))
    :conceptual (conceptual-edges (:conceptual-pairs report) (threshold-for-lens opts))
    :change     (change-edges (:change-pairs report) (threshold-for-lens opts))
    :combined   (combined-edges report {:conceptual-threshold 0.15 :change-threshold 0.30})
    (combined-edges report {:conceptual-threshold 0.15 :change-threshold 0.30})))

(defn community-report
  "Assemble a full communities report from an enriched report and opts.
  opts: {:lens :combined|:structural|:conceptual|:change :threshold ...}"
  [report {:keys [lens] :as opts}]
  (let [lens        (or lens :combined)
        threshold   (threshold-for-lens (assoc opts :lens lens))
        all-nodes    (map :ns (:nodes report))
        built-edges  (build-edges report {:lens lens :threshold threshold})
        edges        (if (= lens :structural)
                       built-edges
                       (threshold-edges built-edges threshold))
        adjacency    (adjacency-map edges)
        components   (connected-components adjacency all-nodes)
        communities  (->> components
                          (map-indexed
                           (fn [i members]
                             (let [members  (vec (sort-by str members))
                                   weights  (internal-boundary-weight members edges)]
                               {:id                (inc i)
                                :members           members
                                :size              (count members)
                                :edge-count        (community-edge-count members edges)
                                :density           (community-density members edges)
                                :internal-weight   (:internal-weight weights)
                                :boundary-weight   (:boundary-weight weights)
                                :dominant-terms    (dominant-terms report members)
                                :bridge-namespaces (bridge-namespaces members edges)
                                :edges             (vec (filter (fn [{:keys [a b]}]
                                                                  (and (contains? (set members) a)
                                                                       (contains? (set members) b)))
                                                                edges))})))
                          vec)]
    {:gordian/command :communities
     :lens            lens
     :threshold       threshold
     :communities     communities
     :summary         {:community-count (count communities)
                       :largest-size    (apply max 0 (map :size communities))
                       :singleton-count (count (filter #(= 1 (:size %)) communities))}}))
