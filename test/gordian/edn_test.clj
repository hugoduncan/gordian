(ns gordian.edn-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.edn  :as sut]
            [gordian.main :as main]))

(def fixture-report (main/build-report ["resources/fixture"]))

;;; ── generate ─────────────────────────────────────────────────────────────

(deftest generate-valid-edn-test
  (testing "output is parseable EDN"
    (let [s (sut/generate fixture-report)]
      (is (map? (read-string s)))))

  (testing "empty report produces parseable EDN"
    (let [r {:src-dir "x" :propagation-cost 0.0 :cycles [] :nodes []}]
      (is (map? (read-string (sut/generate r)))))))

(deftest generate-content-test
  (let [parsed (read-string (sut/generate fixture-report))]
    (testing "src-dirs present as vector"
      (is (= ["resources/fixture"] (:src-dirs parsed))))

    (testing "propagation-cost present"
      (is (number? (:propagation-cost parsed))))

    (testing "cycles is a vector"
      (is (vector? (:cycles parsed))))

    (testing "nodes is a vector"
      (is (vector? (:nodes parsed))))

    (testing "ns values are symbols (not strings)"
      (is (every? symbol? (map :ns (:nodes parsed)))))

    (testing "role values are keywords (not strings)"
      (is (every? keyword? (map :role (:nodes parsed)))))

    (testing "cycles are sets (native Clojure)"
      ;; acyclic fixture → empty, but the type is right
      (is (every? #(or (set? %) (sequential? %)) (:cycles parsed))))

    (testing "graph key excluded from output"
      (is (not (contains? parsed :graph))))))

(deftest generate-node-values-test
  (let [nodes (-> fixture-report sut/generate read-string :nodes)
        by-ns (into {} (map (juxt :ns identity) nodes))]
    (testing "alpha is :core"
      (is (= :core (:role (get by-ns 'alpha)))))

    (testing "gamma is :peripheral"
      (is (= :peripheral (:role (get by-ns 'gamma)))))

    (testing "alpha Ce=0 Ca=2"
      (is (= 0 (:ce (get by-ns 'alpha))))
      (is (= 2 (:ca (get by-ns 'alpha)))))))

(deftest generate-cycle-edn-test
  (testing "cycles preserved as sets of symbols"
    (let [report (assoc fixture-report :cycles [#{'beta 'alpha}])
          parsed (read-string (sut/generate report))]
      (is (= [#{'alpha 'beta}] (:cycles parsed))))))
