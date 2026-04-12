(ns gordian.tests
  "Pure helpers for test-architecture analysis."
  (:require [clojure.string :as str]))

(def ^:private support-fragments
  ["support" "helper" "helpers" "fixture" "fixtures"
   "util" "utils" "assert" "assertions"
   "builder" "builders" "generator" "generators"
   "stub" "stubs" "mock" "mocks"])

(def ^:private integration-fragments
  ["integration" "system" "api" "e2e" "acceptance" "smoke"])

(defn support-test-ns?
  "True when the namespace name suggests shared test support.
  Heuristic only: based on common support/helper fragments in the ns name."
  [ns-sym]
  (let [s (str ns-sym)]
    (boolean (some #(str/includes? s %) support-fragments))))

(defn executable-test-ns?
  "True when the namespace does not look like shared test support."
  [ns-sym]
  (not (support-test-ns? ns-sym)))

(defn integration-cue?
  "True when the namespace name suggests an integration/system style test."
  [ns-sym]
  (let [s (str ns-sym)]
    (boolean (some #(str/includes? s %) integration-fragments))))

(defn test-role
  "Classify a test-tree namespace as :support or :executable."
  [ns-sym]
  (if (support-test-ns? ns-sym) :support :executable))

(defn src-only-graph
  "Restrict graph to source namespaces only, preserving only src→src edges."
  [graph origins]
  (let [src-ns (set (keep (fn [[ns-sym kind]] (when (= :src kind) ns-sym)) origins))]
    (into {}
          (map (fn [[ns-sym deps]]
                 [ns-sym (set (filter src-ns deps))]))
          (filter (fn [[ns-sym _]] (contains? src-ns ns-sym)) graph))))

(defn incoming-index
  "Build {ns -> #{incoming-dependents}} from a direct dependency graph."
  [graph]
  (reduce-kv (fn [acc ns-sym deps]
               (reduce (fn [m dep]
                         (update m dep (fnil conj #{}) ns-sym))
                       acc
                       deps))
             {}
             graph))

(defn- mean
  [xs]
  (if (seq xs)
    (/ (reduce + 0.0 xs) (count xs))
    0.0))

(defn test-style
  "Classify one test profile as :support, :unit-ish, :integration-ish, or :mixed.
  Thresholds map accepts :high-reach, :high-ce, :low-reach, :low-ce."
  [{:keys [test-role reach ce integration-cue?]} {:keys [high-reach high-ce low-reach low-ce]}]
  (cond
    (= test-role :support) :support
    (or integration-cue?
        (>= (or reach 0.0) high-reach)
        (>= (or ce 0) high-ce)) :integration-ish
    (and (<= (or reach 0.0) low-reach)
         (<= (or ce 0) low-ce)) :unit-ish
    :else :mixed))

(defn classify-test-styles
  "Attach :test-style to test profiles.
  Thresholds are derived from executable-test distributions with conservative
  floors/caps so small fixtures remain deterministic.

  The integration-ish threshold is intentionally permissive: obviously broad
  tests should classify as integration-ish even in tiny projects where the
  mean itself is inflated by a small sample."
  [profiles]
  (let [execs      (filter #(= :executable (:test-role %)) profiles)
        mean-reach (mean (map :reach execs))
        mean-ce    (mean (map :ce execs))
        thresholds {:high-reach (max 0.20 (* 1.5 mean-reach))
                    :high-ce    (long (Math/ceil (max 4.0 (* 1.5 mean-ce))))
                    :low-reach  (min 0.08 (* 1.25 mean-reach))
                    :low-ce     2}]
    (mapv #(assoc % :test-style (test-style % thresholds)) profiles)))

(defn test-profiles
  "Return per-test namespace profiles from a full structural report + origins.
  Each profile includes structural metrics plus derived :test-role and :test-style."
  [full-report origins]
  (let [test-ns   (set (keep (fn [[ns-sym kind]] (when (= :test kind) ns-sym)) origins))
        nodes     (filter #(contains? test-ns (:ns %)) (:nodes full-report))
        profiles  (mapv (fn [{:keys [ns] :as node}]
                          (assoc (select-keys node [:ns :reach :fan-in :ca :ce :instability :role])
                                 :origin :test
                                 :test-role (test-role ns)
                                 :integration-cue? (integration-cue? ns)))
                        nodes)]
    (classify-test-styles profiles)))
