(ns gordian.subgraph
  "Subgraph/family views over a gordian report.
  All functions are pure — they take data and return data."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [gordian.close :as close]
            [gordian.aggregate :as aggregate]
            [gordian.scc :as scc]
            [gordian.cluster :as cluster]))

(defn- match-prefix?
  "True when ns-sym matches prefix exactly or as a dotted child namespace."
  [prefix ns-sym]
  (let [s (str ns-sym)]
    (or (= s prefix)
        (str/starts-with? s (str prefix ".")))))

(defn- members-by-prefix
  "Return sorted vector of namespaces in graph matching prefix."
  [graph prefix]
  (->> (keys graph)
       (filter #(match-prefix? prefix %))
       (sort-by str)
       vec))

(defn- induced-graph
  "Return graph induced by members: only member nodes with internal edges."
  [graph members]
  (let [members (set members)]
    (into {}
          (map (fn [n] [n (set/intersection members (get graph n #{}))]))
          members)))

(defn- graph-density
  "Directed graph density in [0,1]. 0.0 when fewer than 2 nodes."
  [graph]
  (let [n (count graph)
        m (reduce + 0 (map count (vals graph)))]
    (if (< n 2)
      0.0
      (/ (double m) (* n (dec n))))))

(defn- boundary-edges
  "Compute incoming/outgoing edges crossing the member boundary."
  [graph members]
  (let [members   (set members)
        outgoing  (->> members
                       (mapcat (fn [from]
                                 (for [to (get graph from #{})
                                       :when (not (contains? members to))]
                                   {:from from :to to})))
                       (sort-by (juxt (comp str :from) (comp str :to)))
                       vec)
        incoming  (->> graph
                       (mapcat (fn [[from deps]]
                                 (for [to deps
                                       :when (and (not (contains? members from))
                                                  (contains? members to))]
                                   {:from from :to to})))
                       (sort-by (juxt (comp str :from) (comp str :to)))
                       vec)
        external-deps (->> outgoing (map :to) distinct (sort-by str) vec)
        dependents    (->> incoming (map :from) distinct (sort-by str) vec)]
    {:incoming incoming
     :outgoing outgoing
     :incoming-count (count incoming)
     :outgoing-count (count outgoing)
     :external-deps external-deps
     :dependents dependents}))

(defn- pair-membership
  "Classify pair wrt members: :internal, :touching, or nil."
  [{:keys [ns-a ns-b]} members]
  (let [members (set members)
        a? (contains? members ns-a)
        b? (contains? members ns-b)]
    (cond
      (and a? b?) :internal
      (or a? b?)  :touching
      :else       nil)))

(defn- filter-pairs
  "Filter pairs by membership class.
  membership — :internal | :touching"
  [pairs members membership]
  (->> pairs
       (filter (fn [p]
                 (case membership
                   :internal (= :internal (pair-membership p members))
                   :touching (contains? #{:internal :touching} (pair-membership p members))
                   false)))
       (sort-by (juxt (comp str :ns-a) (comp str :ns-b)))
       vec))

(defn- finding-touches-members?
  "True if finding subject mentions any member namespace."
  [{:keys [subject]} members]
  (let [members (set members)]
    (cond
      (:members subject) (boolean (seq (set/intersection members (:members subject))))
      (:ns subject)      (contains? members (:ns subject))
      (and (:ns-a subject) (:ns-b subject))
      (or (contains? members (:ns-a subject))
          (contains? members (:ns-b subject)))
      :else false)))

(defn- filter-findings
  "Filter findings to those touching any member."
  [findings members]
  (->> findings
       (filter #(finding-touches-members? % members))
       vec))

(defn- summary-counts
  "Build summary counts for filtered findings/pairs and boundary."
  [findings conceptual-internal conceptual-touching change-internal change-touching boundary]
  {:finding-counts (into {} (map (fn [[k v]] [k (count v)])) (group-by :category findings))
   :pair-counts {:internal-conceptual (count conceptual-internal)
                 :touching-conceptual (count conceptual-touching)
                 :internal-change     (count change-internal)
                 :touching-change     (count change-touching)}
   :boundary {:incoming-count (:incoming-count boundary)
              :outgoing-count (:outgoing-count boundary)}})

(defn subgraph-summary
  "Assemble a full subgraph view from an enriched diagnose-style report.
  Returns subgraph map or {:error ...}."
  [{:keys [graph nodes conceptual-pairs change-pairs findings rank-by] :as _report} prefix]
  (let [members (members-by-prefix graph prefix)]
    (if (empty? members)
      {:error (str "no namespaces found for prefix '" prefix "'")
       :available (vec (sort-by str (keys graph)))}
      (let [members-set          (set members)
            igraph               (induced-graph graph members-set)
            internal-report      (-> igraph
                                     close/close
                                     aggregate/aggregate)
            boundary             (boundary-edges graph members-set)
            member-nodes         (->> nodes
                                      (filter #(contains? members-set (:ns %)))
                                      (sort-by (comp str :ns))
                                      vec)
            conceptual-internal  (filter-pairs (or conceptual-pairs []) members-set :internal)
            conceptual-touching  (filter-pairs (or conceptual-pairs []) members-set :touching)
            change-internal      (filter-pairs (or change-pairs []) members-set :internal)
            change-touching      (filter-pairs (or change-pairs []) members-set :touching)
            touching-findings    (filter-findings (or findings []) members-set)
            local-clusters       (cluster/cluster-findings touching-findings)]
        {:gordian/command :subgraph
         :prefix          prefix
         :rank-by         (or rank-by :severity)
         :members         members
         :internal        {:node-count        (count members)
                           :edge-count        (reduce + 0 (map count (vals igraph)))
                           :density           (graph-density igraph)
                           :propagation-cost  (:propagation-cost internal-report)
                           :cycles            (scc/find-cycles igraph)
                           :nodes             member-nodes}
         :boundary        boundary
         :pairs           {:conceptual {:internal conceptual-internal
                                        :touching conceptual-touching}
                           :change     {:internal change-internal
                                        :touching change-touching}}
         :findings        touching-findings
         :clusters        (:clusters local-clusters)
         :unclustered     (:unclustered local-clusters)
         :summary         (summary-counts touching-findings
                                          conceptual-internal conceptual-touching
                                          change-internal change-touching
                                          boundary)}))))
