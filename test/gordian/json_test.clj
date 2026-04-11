(ns gordian.json-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [gordian.json :as sut]))

(defn- parse [s] (json/parse-string s true))

;;; Hand-crafted fixture — tests json/generate in isolation.
(def fixture-report
  {:src-dirs         ["resources/fixture"]
   :propagation-cost (/ 3.0 9.0)
   :cycles           []
   :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
             :ca 0 :ce 2 :instability 1.0  :role :peripheral}
            {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)
             :ca 1 :ce 1 :instability 0.5  :role :shared}
            {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)
             :ca 2 :ce 0 :instability 0.0  :role :core}]})

;;; ── valid JSON ───────────────────────────────────────────────────────────

(deftest valid-json-test
  (testing "generate produces parseable JSON"
    (is (map? (parse (sut/generate fixture-report)))))

  (testing "empty report produces parseable JSON"
    (is (map? (parse (sut/generate {:src-dirs ["x"] :propagation-cost 0.0
                                    :cycles [] :nodes []}))))))

;;; ── top-level keys ───────────────────────────────────────────────────────

(deftest top-level-keys-test
  (let [parsed (parse (sut/generate fixture-report))]
    (testing "src-dirs present as array"
      (is (= ["resources/fixture"] (:src-dirs parsed))))

    (testing "propagation-cost ≈ 0.333"
      (is (< (Math/abs (- 0.3333 (:propagation-cost parsed))) 0.001)))

    (testing "cycles is a vector"
      (is (vector? (:cycles parsed))))

    (testing "nodes vector has 3 entries"
      (is (= 3 (count (:nodes parsed)))))))

;;; ── node serialisation ───────────────────────────────────────────────────

(deftest node-serialisation-test
  (let [nodes  (:nodes (parse (sut/generate fixture-report)))
        by-ns  (into {} (map (juxt :ns identity) nodes))]
    (testing "ns values are strings"
      (is (every? string? (map :ns nodes))))

    (testing "reach and fan-in are numbers"
      (is (every? number? (map :reach nodes)))
      (is (every? number? (map :fan-in nodes))))

    (testing "instability is a number"
      (is (every? number? (map :instability nodes))))

    (testing "role is a string not a keyword"
      (is (every? string? (map :role nodes))))

    (testing "alpha serialised as core, Ce=0 Ca=2"
      (let [alpha (get by-ns "alpha")]
        (is (= "core" (:role alpha)))
        (is (= 0 (:ce alpha)))
        (is (= 2 (:ca alpha)))))

    (testing "gamma serialised as peripheral, I=1.0"
      (let [gamma (get by-ns "gamma")]
        (is (= "peripheral" (:role gamma)))
        (is (= 1.0 (:instability gamma)))))))

;;; ── cycle serialisation ──────────────────────────────────────────────────

(deftest cycle-serialisation-test
  (testing "no cycles → empty array"
    (is (= [] (:cycles (parse (sut/generate fixture-report))))))

  (testing "cycles serialised as sorted string arrays"
    (let [parsed (parse (sut/generate (assoc fixture-report :cycles [#{'beta 'alpha}])))]
      (is (= [["alpha" "beta"]] (:cycles parsed))))))


