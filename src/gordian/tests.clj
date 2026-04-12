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
