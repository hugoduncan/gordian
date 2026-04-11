(ns gordian.json-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [gordian.json :as sut]
            [gordian.main :as main]))

(defn- parse [s] (json/parse-string s true))

(def fixture-report (main/build-report "test/fixture"))

;;; ── valid JSON ───────────────────────────────────────────────────────────

(deftest valid-json-test
  (testing "generate produces parseable JSON"
    (is (map? (parse (sut/generate fixture-report)))))

  (testing "empty report produces parseable JSON"
    (let [r (sut/generate {:src-dir "x" :propagation-cost 0.0
                            :cycles [] :nodes []})]
      (is (map? (parse r))))))

;;; ── top-level keys ───────────────────────────────────────────────────────

(deftest top-level-keys-test
  (let [parsed (parse (sut/generate fixture-report))]
    (testing "src-dir present"
      (is (= "test/fixture" (:src-dir parsed))))

    (testing "propagation-cost is a number"
      (is (number? (:propagation-cost parsed))))

    (testing "propagation-cost ≈ 0.333"
      (is (< (Math/abs (- 0.3333 (:propagation-cost parsed))) 0.001)))

    (testing "cycles is a vector"
      (is (vector? (:cycles parsed))))

    (testing "nodes is a vector"
      (is (vector? (:nodes parsed))))

    (testing "node count matches"
      (is (= 3 (count (:nodes parsed)))))))

;;; ── node serialisation ───────────────────────────────────────────────────

(deftest node-serialisation-test
  (let [nodes (:nodes (parse (sut/generate fixture-report)))
        by-ns (into {} (map (juxt :ns identity) nodes))]
    (testing "ns is a string"
      (is (every? string? (map :ns nodes))))

    (testing "reach is a double"
      (is (every? number? (map :reach nodes))))

    (testing "fan-in is a double"
      (is (every? number? (map :fan-in nodes))))

    (testing "instability present as number"
      (is (every? number? (map :instability nodes))))

    (testing "role is a string, not a keyword"
      (is (every? string? (map :role nodes))))

    (testing "alpha is serialised as 'core'"
      (is (= "core" (:role (get by-ns "alpha")))))

    (testing "gamma is serialised as 'peripheral'"
      (is (= "peripheral" (:role (get by-ns "gamma")))))

    (testing "alpha Ce=0 Ca=2"
      (let [alpha (get by-ns "alpha")]
        (is (= 0 (:ce alpha)))
        (is (= 2 (:ca alpha)))))))

;;; ── cycle serialisation ──────────────────────────────────────────────────

(deftest cycle-serialisation-test
  (testing "acyclic report has empty cycles array"
    (is (= [] (:cycles (parse (sut/generate fixture-report))))))

  (testing "cycles serialised as sorted string arrays"
    (let [report (assoc fixture-report :cycles [#{'beta 'alpha}])
          parsed (parse (sut/generate report))]
      (is (= [["alpha" "beta"]] (:cycles parsed))))))
