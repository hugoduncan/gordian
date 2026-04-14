(ns gordian.prioritize
  "Actionability scoring and ranking for diagnose findings.
  All functions are pure — they take data and return data."
  (:require [gordian.finding :as finding]))

(defn cluster-context
  "Build ranking context from clusters/unclustered findings.
  Returns {:cluster-size-by-finding {finding-key -> n}}."
  [clusters _unclustered]
  {:cluster-size-by-finding
   (into {}
         (mapcat (fn [{:keys [findings]}]
                   (let [n (count findings)]
                     (map (fn [f] [(finding/finding-key f) n]) findings)))
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

(defn- cross-family-weight [{:keys [category evidence]}]
  (if (and (finding/hidden-pair-category? category)
           (false? (:same-family? evidence)))
    3.0
    0.0))

(defn- hidden-weight [category]
  (if (finding/hidden-pair-category? category) 2.0 0.0))

(defn- family-noise-penalty [finding]
  (if (finding/family-noise? finding)
    -4.0
    0.0))

(defn- same-family-hidden-penalty [{:keys [category evidence]}]
  (if (and (= :hidden-conceptual category)
           (:same-family? evidence))
    -1.0
    0.0))

(defn- cluster-weight [finding {:keys [cluster-size-by-finding]}]
  (let [n (get cluster-size-by-finding (finding/finding-key finding) 1)]
    (double (min 3 (max 0 (dec n))))))

(defn- evidence-weight [finding]
  (let [score (double (finding/finding-score finding))]
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
   (- (finding/finding-magnitude f))])

(defn- actionability-sort-key [f]
  [(- (:actionability-score f))
   (severity-rank (:severity f))
   (- (finding/finding-score f))])

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
