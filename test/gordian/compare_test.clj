(ns gordian.compare-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.compare :as compare]))

(def compare-health #'gordian.compare/compare-health)
(def compare-nodes #'gordian.compare/compare-nodes)
(def compare-cycles #'gordian.compare/compare-cycles)
(def compare-pairs #'gordian.compare/compare-pairs)
(def compare-findings #'gordian.compare/compare-findings)

;;; ── test data ────────────────────────────────────────────────────────────

(def before-report
  {:gordian/version "0.2.0"
   :gordian/schema  1
   :src-dirs        ["src/"]
   :lenses          {:structural true :conceptual {:enabled true :threshold 0.20}}
   :propagation-cost 0.21
   :cycles          [#{:a.core :b.core}]
   :nodes           [{:ns :a.core :reach 0.30 :fan-in 0.10 :ca 3 :ce 2 :instability 0.40 :role :shared
                      :ca-family 1 :ca-external 2 :ce-family 1 :ce-external 1}
                     {:ns :b.core :reach 0.20 :fan-in 0.15 :ca 2 :ce 3 :instability 0.60 :role :shared
                      :ca-family 0 :ca-external 2 :ce-family 2 :ce-external 1}
                     {:ns :c.util :reach 0.05 :fan-in 0.02 :ca 0 :ce 1 :instability 1.0 :role :peripheral
                      :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 1}]
   :conceptual-pairs [{:ns-a :a.core :ns-b :b.core :score 0.45 :kind :conceptual
                       :structural-edge? true :shared-terms ["core" "process"]}
                      {:ns-a :a.core :ns-b :c.util :score 0.25 :kind :conceptual
                       :structural-edge? false :shared-terms ["util"]}]
   :change-pairs     [{:ns-a :a.core :ns-b :b.core :score 0.55 :kind :change
                       :structural-edge? true :co-changes 8}]
   :findings         [{:severity :high :category :cycle
                       :subject {:members #{:a.core :b.core}}
                       :reason "2-namespace cycle"}
                      {:severity :medium :category :hidden-conceptual
                       :subject {:ns-a :a.core :ns-b :c.util}
                       :reason "hidden conceptual coupling — score=0.25"}]})

(def after-report
  {:gordian/version "0.2.0"
   :gordian/schema  1
   :src-dirs        ["src/"]
   :lenses          {:structural true :conceptual {:enabled true :threshold 0.20}}
   :propagation-cost 0.15
   :cycles          []  ;; cycle removed
   :nodes           [{:ns :a.core :reach 0.25 :fan-in 0.10 :ca 3 :ce 1 :instability 0.25 :role :core
                      :ca-family 1 :ca-external 2 :ce-family 0 :ce-external 1}
                     {:ns :b.core :reach 0.20 :fan-in 0.15 :ca 2 :ce 2 :instability 0.50 :role :shared
                      :ca-family 0 :ca-external 2 :ce-family 1 :ce-external 1}
                     ;; c.util removed, d.new added
                     {:ns :d.new :reach 0.05 :fan-in 0.01 :ca 0 :ce 1 :instability 1.0 :role :peripheral
                      :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 1}]
   :conceptual-pairs [{:ns-a :a.core :ns-b :b.core :score 0.32 :kind :conceptual
                       :structural-edge? true :shared-terms ["process"]}
                       ;; a.core ↔ c.util pair gone (c.util removed)
                       ;; new pair
                      {:ns-a :a.core :ns-b :d.new :score 0.28 :kind :conceptual
                       :structural-edge? false :shared-terms ["data"]}]
   :change-pairs     [{:ns-a :a.core :ns-b :b.core :score 0.50 :kind :change
                       :structural-edge? true :co-changes 7}]
   :findings         [;; cycle finding removed
                      {:severity :medium :category :hidden-conceptual
                       :subject {:ns-a :a.core :ns-b :d.new}
                       :reason "hidden conceptual coupling — score=0.28"}]})

;;; ── health tests ─────────────────────────────────────────────────────────

(deftest compare-health-test
  (testing "computes before, after, and delta"
    (let [result (compare-health before-report after-report)]
      (is (= 0.21 (:propagation-cost (:before result))))
      (is (= 0.15 (:propagation-cost (:after result))))
      (is (< (abs (- -0.06 (:propagation-cost (:delta result)))) 0.001))
      (is (= 1 (:cycle-count (:before result))))
      (is (= 0 (:cycle-count (:after result))))
      (is (= -1 (:cycle-count (:delta result))))
      (is (= 3 (:ns-count (:before result))))
      (is (= 3 (:ns-count (:after result))))
      (is (= 0 (:ns-count (:delta result)))))))

(deftest compare-health-empty-test
  (testing "handles empty reports"
    (let [result (compare-health {} {})]
      (is (= 0.0 (:propagation-cost (:before result))))
      (is (= 0 (:cycle-count (:delta result)))))))

;;; ── node tests ───────────────────────────────────────────────────────────

(deftest compare-nodes-test
  (testing "detects added, removed, and changed nodes"
    (let [result (compare-nodes before-report after-report)]
      ;; d.new added
      (is (= 1 (count (:added result))))
      (is (= :d.new (:ns (first (:added result)))))
      ;; c.util removed
      (is (= 1 (count (:removed result))))
      (is (= :c.util (:ns (first (:removed result)))))
      ;; a.core changed (reach, ce, instability, role all changed)
      (let [changed-nss (set (map :ns (:changed result)))]
        (is (contains? changed-nss :a.core))))))

(deftest compare-nodes-metric-delta-test
  (testing "delta contains correct numeric differences"
    (let [result  (compare-nodes before-report after-report)
          a-diff  (first (filter #(= :a.core (:ns %)) (:changed result)))]
      ;; reach: 0.30 → 0.25 = -0.05
      (is (< (abs (- -0.05 (:reach (:delta a-diff)))) 0.001))
      ;; ce: 2 → 1 = -1
      (is (= -1.0 (:ce (:delta a-diff))))
      ;; role: :shared → :core
      (is (= {:before :shared :after :core} (:role (:delta a-diff)))))))

(deftest compare-nodes-unchanged-test
  (testing "unchanged nodes are not reported"
    (let [same {:nodes [{:ns :x :reach 0.1 :ca 1 :ce 1 :instability 0.5 :role :core
                         :fan-in 0.0 :ca-family 0 :ca-external 1 :ce-family 0 :ce-external 1}]}
          result (compare-nodes same same)]
      (is (empty? (:added result)))
      (is (empty? (:removed result)))
      (is (empty? (:changed result))))))

;;; ── cycle tests ──────────────────────────────────────────────────────────

(deftest compare-cycles-test
  (testing "detects added and removed cycles"
    (let [result (compare-cycles before-report after-report)]
      ;; #{:a.core :b.core} was removed
      (is (= 1 (count (:removed result))))
      (is (= #{:a.core :b.core} (first (:removed result))))
      ;; no new cycles added
      (is (empty? (:added result))))))

(deftest compare-cycles-added-test
  (testing "detects newly added cycles"
    (let [result (compare-cycles
                  {:cycles []}
                  {:cycles [#{:x :y}]})]
      (is (= 1 (count (:added result))))
      (is (empty? (:removed result))))))

;;; ── pair comparison tests ────────────────────────────────────────────────

(deftest compare-conceptual-pairs-test
  (testing "detects added, removed, and changed conceptual pairs"
    (let [result (compare-pairs before-report after-report :conceptual)]
      ;; a.core ↔ d.new is new
      (is (= 1 (count (:added result))))
      (is (= :d.new (:ns-b (first (:added result)))))
      ;; a.core ↔ c.util is gone
      (is (= 1 (count (:removed result))))
      (is (= :c.util (:ns-b (first (:removed result)))))
      ;; a.core ↔ b.core: 0.45 → 0.32 = -0.13
      (is (= 1 (count (:changed result))))
      (let [ch (first (:changed result))]
        (is (= 0.45 (get-in ch [:before :score])))
        (is (= 0.32 (get-in ch [:after :score])))
        (is (< (abs (- -0.13 (get-in ch [:delta :score]))) 0.001))))))

(deftest compare-change-pairs-test
  (testing "change pairs with small delta are not reported"
    (let [result (compare-pairs before-report after-report :change)]
      ;; 0.55 → 0.50 = -0.05, which exceeds 0.01 threshold
      (is (= 1 (count (:changed result)))))))

(deftest compare-pairs-no-change-test
  (testing "identical pairs produce empty diff"
    (let [report {:conceptual-pairs [{:ns-a :a :ns-b :b :score 0.30}]}
          result (compare-pairs report report :conceptual)]
      (is (empty? (:added result)))
      (is (empty? (:removed result)))
      (is (empty? (:changed result))))))

;;; ── finding comparison tests ─────────────────────────────────────────────

(deftest compare-findings-test
  (testing "detects added and removed findings"
    (let [result (compare-findings before-report after-report)]
      ;; cycle finding removed
      (is (= 1 (count (filter #(= :cycle (:category %)) (:removed result)))))
      ;; hidden-conceptual a.core ↔ c.util removed
      (is (some #(= {:ns-a :a.core :ns-b :c.util} (:subject %)) (:removed result)))
      ;; hidden-conceptual a.core ↔ d.new added
      (is (some #(= {:ns-a :a.core :ns-b :d.new} (:subject %)) (:added result))))))

(deftest compare-findings-empty-test
  (testing "no findings in either report"
    (let [result (compare-findings {} {})]
      (is (empty? (:added result)))
      (is (empty? (:removed result))))))

;;; ── composite compare tests ─────────────────────────────────────────────

(deftest compare-reports-test
  (testing "full comparison produces all sections"
    (let [result (compare/compare-reports before-report after-report)]
      (is (= :compare (:gordian/command result)))
      (is (map? (:health result)))
      (is (map? (:nodes result)))
      (is (map? (:cycles result)))
      (is (map? (:conceptual-pairs result)))
      (is (map? (:change-pairs result)))
      (is (map? (:findings result))))))

(deftest compare-reports-metadata-test
  (testing "before/after contain report metadata"
    (let [result (compare/compare-reports before-report after-report)]
      (is (= "0.2.0" (get-in result [:before :gordian/version])))
      (is (= ["src/"] (get-in result [:after :src-dirs]))))))
