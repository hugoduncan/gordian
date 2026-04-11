(ns gordian.diagnose-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.diagnose :as sut]))

;;; ── pair-key ─────────────────────────────────────────────────────────────

(deftest pair-key-test
  (testing "produces set of two ns symbols"
    (is (= #{'a 'b} (sut/pair-key {:ns-a 'a :ns-b 'b}))))

  (testing "order-independent"
    (is (= (sut/pair-key {:ns-a 'a :ns-b 'b})
           (sut/pair-key {:ns-a 'b :ns-b 'a})))))

;;; ── health ───────────────────────────────────────────────────────────────

(deftest health-test
  (testing "low PC → :healthy"
    (is (= :healthy
           (:health (sut/health {:propagation-cost 0.05
                                 :cycles [] :nodes [{} {} {}]})))))

  (testing "moderate PC → :moderate"
    (is (= :moderate
           (:health (sut/health {:propagation-cost 0.20
                                 :cycles [] :nodes [{} {}]})))))

  (testing "high PC → :concerning"
    (is (= :concerning
           (:health (sut/health {:propagation-cost 0.40
                                 :cycles [] :nodes [{} {}]})))))

  (testing "boundary: 0.10 is :healthy"
    (is (= :healthy
           (:health (sut/health {:propagation-cost 0.10
                                 :cycles [] :nodes [{}]})))))

  (testing "boundary: 0.30 is :moderate"
    (is (= :moderate
           (:health (sut/health {:propagation-cost 0.30
                                 :cycles [] :nodes [{}]})))))

  (testing "reports correct cycle-count and ns-count"
    (let [h (sut/health {:propagation-cost 0.05
                         :cycles [#{'a 'b} #{'c 'd 'e}]
                         :nodes [{} {} {} {} {}]})]
      (is (= 2 (:cycle-count h)))
      (is (= 5 (:ns-count h)))))

  (testing "nil propagation-cost treated as 0"
    (is (= :healthy
           (:health (sut/health {:cycles [] :nodes []}))))))

;;; ── find-cycles ──────────────────────────────────────────────────────────

(deftest find-cycles-test
  (testing "empty cycles → empty findings"
    (is (= [] (sut/find-cycles []))))

  (testing "one cycle → one :high finding"
    (let [findings (sut/find-cycles [#{'a 'b}])]
      (is (= 1 (count findings)))
      (is (= :high (:severity (first findings))))
      (is (= :cycle (:category (first findings))))))

  (testing "finding has :members in :subject and :size in :evidence"
    (let [f (first (sut/find-cycles [#{'a 'b 'c}]))]
      (is (= #{'a 'b 'c} (get-in f [:subject :members])))
      (is (= 3 (get-in f [:evidence :size])))))

  (testing "two cycles → two findings"
    (is (= 2 (count (sut/find-cycles [#{'a 'b} #{'c 'd}])))))

  (testing "reason includes size"
    (let [f (first (sut/find-cycles [#{'x 'y}]))]
      (is (= "2-namespace cycle" (:reason f))))))
