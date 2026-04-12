(ns gordian.glossary
  "Project vocabulary / glossary derivation.
  Pure — aggregates namespace, conceptual-pair, and community evidence into
  ranked glossary entries."
  (:require [clojure.set :as set]))

(def ^:private implementation-terms
  "Generic implementation language that should not dominate the glossary even
  when frequent. Penalized rather than fully suppressed."
  #{"build" "format" "load" "parse" "result" "report" "line"
    "return" "output" "input" "data" "map" "vector" "file"
    "path" "node" "term" "value"})

(defn namespace-term-stats
  "Aggregate namespace-local term evidence.
  ns->terms — {ns → [term]}
  Returns {term {:namespaces #{ns} :namespace-count n}}."
  [ns->terms]
  (reduce-kv
   (fn [acc ns terms]
     (reduce (fn [m term]
               (update m term
                       (fn [cur]
                         (let [namespaces (conj (or (:namespaces cur) #{}) ns)]
                           {:namespaces      namespaces
                            :namespace-count (count namespaces)}))))
             acc
             (distinct terms)))
   {}
   ns->terms))

(defn pair-term-stats
  "Aggregate conceptual-pair term evidence.
  Prefers :independent-terms when present; otherwise falls back to :shared-terms.
  Returns {term {:pair-count n :pair-score s :family-only-count n :pairs [...]}}."
  [pairs]
  (reduce
   (fn [acc {:keys [ns-a ns-b score same-family? independent-terms shared-terms]}]
     (let [family-only? (and same-family? (empty? independent-terms) (seq shared-terms))
           terms        (vec (if family-only?
                               []
                               (or (seq independent-terms) shared-terms [])))
           empty-stat   {:pair-count 0 :pair-score 0.0 :family-only-count 0 :pairs []}]
       (if family-only?
         (reduce (fn [m term]
                   (update m term
                           (fn [cur]
                             (-> (or cur empty-stat)
                                 (update :family-only-count inc)))))
                 acc
                 shared-terms)
         (reduce (fn [m term]
                   (update m term
                           (fn [cur]
                             (-> (or cur empty-stat)
                                 (update :pair-count inc)
                                 (update :pair-score + (double score))
                                 (update :pairs conj {:ns-a ns-a :ns-b ns-b :score score})))))
                 acc
                 terms))))
   {}
   pairs))

(defn community-term-stats
  "Aggregate community vocabulary evidence from community :dominant-terms.
  Returns {term {:community-count n :communities [...]}}."
  [communities]
  (reduce
   (fn [acc {:keys [id size dominant-terms]}]
     (reduce (fn [m term]
               (update m term
                       (fn [cur]
                         (-> (or cur {:community-count 0 :communities []})
                             (update :community-count inc)
                             (update :communities conj {:id id :size size})))))
             acc
             (distinct dominant-terms)))
   {}
   communities))

(defn related-terms
  "Terms that co-occur with `term` in namespace, pair, and community contexts."
  [term ns->terms pairs communities]
  (let [ns-related (->> ns->terms
                        (filter (fn [[_ terms]] (some #{term} terms)))
                        (mapcat (fn [[_ terms]] (distinct terms))))
        pair-related (->> pairs
                          (filter (fn [{:keys [independent-terms shared-terms]}]
                                    (some #{term} (or (seq independent-terms)
                                                      shared-terms
                                                      []))))
                          (mapcat (fn [{:keys [independent-terms shared-terms]}]
                                    (distinct (or (seq independent-terms)
                                                  shared-terms
                                                  [])))))
        community-related (->> communities
                               (filter (fn [{:keys [dominant-terms]}]
                                         (some #{term} dominant-terms)))
                               (mapcat (comp distinct :dominant-terms)))]
    (->> (concat ns-related pair-related community-related)
         frequencies
         (remove (fn [[t _]] (= t term)))
         (sort-by (fn [[t n]] [(- n) t]))
         (map first)
         (take 5)
         vec)))

(defn- classify-kind [{:keys [pair-count community-count namespace-count]} term]
  (cond
    (or (pos? pair-count) (pos? community-count)) :architectural
    (contains? implementation-terms term)         :implementation
    (>= namespace-count 2)                        :domain
    :else                                         :implementation))

(defn- classify-quality [score]
  (cond
    (>= score 6.0) :strong
    (>= score 2.5) :medium
    :else          :weak))

(defn- entry-score [{:keys [namespace-count pair-count pair-score community-count family-only-count]} term]
  (let [ns-score     (* 1.0 namespace-count)
        pair-freq    (* 2.0 pair-count)
        pair-strength (* 4.0 pair-score)
        comm-score   (* 1.5 community-count)
        family-pen   (* 3.0 family-only-count)
        impl-pen     (if (contains? implementation-terms term) 1.0 0.0)]
    (- (+ ns-score pair-freq pair-strength comm-score)
       family-pen
       impl-pen)))

(defn glossary-entries
  "Build ranked glossary entries.
  Inputs:
  - ns->terms: {ns → [term]}
  - conceptual-pairs: [{...}]
  - communities: [{:id ... :dominant-terms [...] }]
  Options:
  - :top       max entries to retain
  - :min-score minimum score required"
  [{:keys [ns->terms conceptual-pairs communities]} {:keys [top min-score]}]
  (let [ns-stats   (namespace-term-stats (or ns->terms {}))
        pair-stats (pair-term-stats (or conceptual-pairs []))
        comm-stats (community-term-stats (or communities []))
        all-terms  (set/union (set (keys ns-stats)) (set (keys pair-stats)) (set (keys comm-stats)))]
    (->> all-terms
         (map (fn [term]
                (let [entry (merge {:term term
                                    :namespace-count 0
                                    :pair-count 0
                                    :pair-score 0.0
                                    :community-count 0
                                    :family-only-count 0
                                    :namespaces #{}
                                    :pairs []
                                    :communities []}
                                   (get ns-stats term)
                                   (get pair-stats term)
                                   (get comm-stats term))
                      score (entry-score entry term)]
                  {:term             term
                   :score            score
                   :kind             (classify-kind entry term)
                   :quality          (classify-quality score)
                   :namespace-count  (:namespace-count entry)
                   :pair-count       (:pair-count entry)
                   :community-count  (:community-count entry)
                   :same-family-noise? (and (pos? (:family-only-count entry))
                                            (zero? (:pair-count entry)))
                   :evidence         {:namespaces    (vec (sort-by str (:namespaces entry)))
                                      :pairs         (->> (:pairs entry)
                                                          (sort-by (fn [{:keys [score ns-a ns-b]}]
                                                                     [(- score) (str ns-a) (str ns-b)]))
                                                          (take 5)
                                                          vec)
                                      :communities   (vec (:communities entry))
                                      :related-terms (related-terms term ns->terms conceptual-pairs communities)}})))
         (filter (fn [{:keys [score]}]
                   (if (some? min-score)
                     (>= score min-score)
                     true)))
         (sort-by (fn [{:keys [score term]}] [(- score) term]))
         (#(if top (take top %) %))
         vec)))

(defn glossary-report
  "Assemble the canonical glossary report.
  data opts -> {:gordian/command :glossary :entries [...] :summary ... :filters ...}"
  [{:keys [ns->terms conceptual-pairs communities]} {:keys [top min-score] :as opts}]
  (let [entries (glossary-entries {:ns->terms ns->terms
                                   :conceptual-pairs conceptual-pairs
                                   :communities communities}
                                  opts)
        source-counts {:namespace-terms (count (set (mapcat identity (vals (or ns->terms {})))))
                       :pair-terms      (count (set (mapcat #(or (seq (:independent-terms %))
                                                                 (:shared-terms %)
                                                                 [])
                                                            (or conceptual-pairs []))))
                       :community-terms (count (set (mapcat :dominant-terms (or communities []))))}
        kind-counts   (frequencies (map :kind entries))]
    {:gordian/command :glossary
     :entries         entries
     :summary         {:entry-count      (count entries)
                       :suppressed-count 0
                       :source-counts    source-counts
                       :kind-counts      kind-counts}
     :filters         {:top top :min-score min-score}}))