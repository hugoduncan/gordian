(ns gordian.classify-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.classify :as sut]))

;;; ── classify-node ────────────────────────────────────────────────────────

(deftest classify-node-test
  (testing "low reach + high fan-in → :core"
    (is (= :core (sut/classify-node {:reach 0.1 :fan-in 0.9} 0.5 0.5))))

  (testing "high reach + low fan-in → :peripheral"
    (is (= :peripheral (sut/classify-node {:reach 0.9 :fan-in 0.1} 0.5 0.5))))

  (testing "high reach + high fan-in → :shared"
    (is (= :shared (sut/classify-node {:reach 0.9 :fan-in 0.9} 0.5 0.5))))

  (testing "low reach + low fan-in → :isolated"
    (is (= :isolated (sut/classify-node {:reach 0.1 :fan-in 0.1} 0.5 0.5))))

  (testing "exactly at threshold: reach≥ and fan-in≥ → :shared"
    (is (= :shared (sut/classify-node {:reach 0.5 :fan-in 0.5} 0.5 0.5))))

  (testing "exactly at reach threshold, below fan-in → :peripheral"
    (is (= :peripheral (sut/classify-node {:reach 0.5 :fan-in 0.0} 0.5 0.5)))))

;;; ── classify ─────────────────────────────────────────────────────────────

(deftest classify-empty-test
  (testing "empty nodes → empty"
    (is (= [] (sut/classify [])))))

(deftest classify-fixture-test
  ;; fixture: alpha(reach=0, fan-in=2/3), beta(reach=1/3, fan-in=1/3), gamma(reach=2/3, fan-in=0)
  ;; mean reach = (0 + 1/3 + 2/3)/3 = 1/3
  ;; mean fan-in = (2/3 + 1/3 + 0)/3 = 1/3
  ;; alpha: reach=0 < 1/3, fan-in=2/3 ≥ 1/3 → :core
  ;; beta:  reach=1/3 ≥ 1/3, fan-in=1/3 ≥ 1/3 → :shared
  ;; gamma: reach=2/3 ≥ 1/3, fan-in=0 < 1/3 → :peripheral
  (let [nodes  [{:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)}
                {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)}
                {:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0}]
        result (sut/classify nodes)]
    (testing "all nodes retain existing keys"
      (is (every? #(contains? % :ns) result)))

    (testing "alpha → :core"
      (is (= :core      (:role (first (filter #(= 'alpha (:ns %)) result))))))

    (testing "beta → :shared (at the mean on both dimensions)"
      (is (= :shared    (:role (first (filter #(= 'beta  (:ns %)) result))))))

    (testing "gamma → :peripheral"
      (is (= :peripheral (:role (first (filter #(= 'gamma (:ns %)) result))))))))

(deftest classify-isolated-test
  ;; single node with no connections — reach=0, fan-in=0
  ;; mean reach=0, mean fan-in=0 → reach ≥ mean (0≥0) and fan-in ≥ mean (0≥0) → :shared
  ;; (edge case: degenerate single-node graph)
  (let [[n] (sut/classify [{:ns 'A :reach 0.0 :fan-in 0.0}])]
    (testing "isolated single node is :shared (degenerate — both at mean=0)"
      (is (= :shared (:role n)))))

  ;; two isolated nodes — both below each other's mean  would be 0 still
  (let [result (sut/classify [{:ns 'A :reach 0.0 :fan-in 0.0}
                              {:ns 'B :reach 0.0 :fan-in 0.0}])]
    (testing "two equally-zero nodes → both :shared"
      (is (every? #(= :shared (:role %)) result)))))

(deftest classify-all-four-roles-test
  ;; craft nodes that clearly land in all four quadrants
  (let [nodes  [{:ns 'c :reach 0.1 :fan-in 0.9}   ; core
                {:ns 'p :reach 0.9 :fan-in 0.1}   ; peripheral
                {:ns 's :reach 0.9 :fan-in 0.9}   ; shared
                {:ns 'i :reach 0.1 :fan-in 0.1}]  ; isolated
        result (sut/classify nodes)
        role   (fn [ns-sym] (:role (first (filter #(= ns-sym (:ns %)) result))))]
    (is (= :core       (role 'c)))
    (is (= :peripheral (role 'p)))
    (is (= :shared     (role 's)))
    (is (= :isolated   (role 'i)))))
