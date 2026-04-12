(ns gordian.prioritize
  "Actionability scoring and ranking for diagnose findings.
  All functions are pure — they take data and return data.")

(defn finding-key
  "Identity key for a finding — category + subject."
  [finding]
  [(:category finding) (:subject finding)])

(defn cluster-context
  "Build ranking context from clusters/unclustered findings.
  Returns {:cluster-size-by-finding {finding-key -> n}}."
  [clusters _unclustered]
  {:cluster-size-by-finding
   (into {}
         (mapcat (fn [{:keys [findings]}]
                   (let [n (count findings)]
                     (map (fn [f] [(finding-key f) n]) findings)))
                 clusters))})

(defn- severity-weight [severity]
  (case severity
    :high 3.0
    :medium 2.0
    :low 1.0
    0.0))

(defn- category-weight [category]
  (case category
    :cross-lens-hidden 4.0
    :hidden-change 2.0
    :hidden-conceptual 2.0
    :cycle 2.0
    :sdp-violation 1.5
    :god-module 1.0
    :facade -2.0
    :hub -1.0
    0.0))

(defn- hidden-pair-category? [category]
  (contains? #{:cross-lens-hidden :hidden-conceptual :hidden-change} category))

(defn- cross-family-weight [{:keys [category evidence]}]
  (if (and (hidden-pair-category? category)
           (false? (:same-family? evidence)))
    3.0
    0.0))

(defn- hidden-weight [category]
  (if (hidden-pair-category? category) 2.0 0.0))

(defn- family-noise-penalty [{:keys [evidence]}]
  (if (and (:same-family? evidence)
           (empty? (:independent-terms evidence)))
    -4.0
    0.0))

(defn- same-family-hidden-penalty [{:keys [category evidence]}]
  (if (and (= :hidden-conceptual category)
           (:same-family? evidence))
    -1.0
    0.0))

(defn- cluster-weight [finding {:keys [cluster-size-by-finding]}]
  (let [n (get cluster-size-by-finding (finding-key finding) 1)]
    (double (min 3 (max 0 (dec n))))))

(defn- evidence-weight [{:keys [evidence]}]
  (let [score (double (or (:score evidence)
                          (:conceptual-score evidence)
                          0.0))]
    (* 2.0 score)))

(defn actionability-score
  "Compute relative actionability score for a finding.
  Higher means more likely to be high-leverage to investigate or fix."
  [finding context]
  (+ (severity-weight (:severity finding))
     (category-weight (:category finding))
     (cross-family-weight finding)
     (hidden-weight (:category finding))
     (cluster-weight finding context)
     (evidence-weight finding)
     (family-noise-penalty finding)
     (same-family-hidden-penalty finding)))

(defn annotate-actionability
  "Attach :actionability-score to each finding."
  [findings context]
  (mapv (fn [f]
          (assoc f :actionability-score (actionability-score f context)))
        findings))

(defn- severity-rank [s]
  (case s :high 0 :medium 1 :low 2 3))

(defn- severity-sort-key [f]
  [(severity-rank (:severity f))
   (- (or (get-in f [:evidence :score])
          (get-in f [:evidence :conceptual-score])
          (get-in f [:evidence :reach])
          (get-in f [:evidence :size])
          (get-in f [:evidence :instability])
          0))])

(defn- actionability-sort-key [f]
  [(- (:actionability-score f))
   (severity-rank (:severity f))
   (- (or (get-in f [:evidence :score])
          (get-in f [:evidence :conceptual-score])
          0))])

(defn rank-findings
  "Rank findings by mode.
  mode — :severity (default) | :actionability."
  [findings mode context]
  (let [annotated (annotate-actionability findings context)]
    (vec
     (sort-by (case mode
                :actionability actionability-sort-key
                severity-sort-key)
              annotated))))
