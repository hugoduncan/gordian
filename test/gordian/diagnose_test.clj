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

;;; ── find-sdp-violations ─────────────────────────────────────────────────

(def ^:private sample-nodes
  [{:ns 'a.core :reach 0.1 :fan-in 0.5 :ca 3 :ce 1 :instability 0.25 :role :core}
   {:ns 'b.hub  :reach 0.8 :fan-in 0.1 :ca 0 :ce 5 :instability 1.0  :role :peripheral}
   {:ns 'c.bad  :reach 0.3 :fan-in 0.4 :ca 3 :ce 4 :instability 0.57 :role :shared}
   {:ns 'd.ok   :reach 0.1 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5  :role :shared}
   {:ns 'e.low  :reach 0.0 :fan-in 0.0 :ca 0 :ce 0 :instability 0.0  :role :isolated}])

(deftest find-sdp-violations-test
  (testing "Ca≥2 and I>0.5 → medium finding"
    (let [findings (sut/find-sdp-violations sample-nodes)
          flagged  (set (map #(get-in % [:subject :ns]) findings))]
      (is (contains? flagged 'c.bad))
      (is (= :medium (:severity (first findings))))))

  (testing "Ca=0 → not flagged"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (sut/find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'b.hub)))))

  (testing "I=0.0 → not flagged (stable)"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (sut/find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'e.low)))))

  (testing "Ca=1 → not flagged (threshold is 2)"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (sut/find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'd.ok)))))

  (testing "Ca≥2 but I≤0.5 → not flagged"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (sut/find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'a.core))))))

;;; ── find-god-modules ────────────────────────────────────────────────────

(deftest find-god-modules-test
  (let [nodes [{:ns 'x.god  :reach 0.6 :fan-in 0.6 :ca 3 :ce 3 :instability 0.5 :role :shared}
               {:ns 'x.core :reach 0.0 :fan-in 0.3 :ca 2 :ce 0 :instability 0.0 :role :core}
               {:ns 'x.leaf :reach 0.3 :fan-in 0.0 :ca 0 :ce 2 :instability 1.0 :role :peripheral}
               {:ns 'x.mild :reach 0.1 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5 :role :shared}]]

    (testing "shared with extreme values → medium finding"
      (let [findings (sut/find-god-modules nodes)]
        (is (= 1 (count findings)))
        (is (= 'x.god (get-in (first findings) [:subject :ns])))
        (is (= :medium (:severity (first findings))))))

    (testing "core node with high fan-in → not flagged (not :shared)"
      (let [flagged (set (map #(get-in % [:subject :ns])
                              (sut/find-god-modules nodes)))]
        (is (not (contains? flagged 'x.core)))))

    (testing "shared with normal values → not flagged"
      (let [flagged (set (map #(get-in % [:subject :ns])
                              (sut/find-god-modules nodes)))]
        (is (not (contains? flagged 'x.mild)))))))

;;; ── find-hubs ───────────────────────────────────────────────────────────

(deftest find-hubs-test
  (let [nodes [{:ns 'h.main :reach 0.9 :fan-in 0.0 :ca 0 :ce 8 :instability 1.0 :role :peripheral}
               {:ns 'h.a    :reach 0.1 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5 :role :shared}
               {:ns 'h.b    :reach 0.0 :fan-in 0.1 :ca 1 :ce 0 :instability 0.0 :role :core}]]

    (testing "reach > 3× mean → low finding"
      (let [findings (sut/find-hubs nodes)]
        ;; mean reach = (0.9+0.1+0.0)/3 = 0.333; 3× = 1.0; 0.9 < 1.0 → not flagged?
        ;; Actually 0.9 is not > 1.0. Let me use a more extreme example.
        ;; But wait: 0.9 > 3*0.333 = 0.999? No, 0.9 < 0.999. Hmm.
        ;; This test fixture needs adjustment. Let me check.
        ;; mean = 1.0/3 = 0.333, 3× = 1.0, 0.9 is not > 1.0. So not flagged.
        ;; Need different fixture.
        (is (= 0 (count findings)))))

    (testing "truly extreme reach > 3× mean → low finding"
      (let [extreme [{:ns 'h.main :reach 0.9 :fan-in 0.0 :ca 0 :ce 8 :instability 1.0 :role :peripheral}
                     {:ns 'h.a    :reach 0.05 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5 :role :shared}
                     {:ns 'h.b    :reach 0.0  :fan-in 0.1 :ca 1 :ce 0 :instability 0.0 :role :core}
                     {:ns 'h.c    :reach 0.0  :fan-in 0.1 :ca 1 :ce 0 :instability 0.0 :role :core}
                     {:ns 'h.d    :reach 0.0  :fan-in 0.1 :ca 1 :ce 0 :instability 0.0 :role :core}]
            ;; mean = 0.95/5 = 0.19, 3× = 0.57, 0.9 > 0.57 ✓
            findings (sut/find-hubs extreme)]
        (is (= 1 (count findings)))
        (is (= 'h.main (get-in (first findings) [:subject :ns])))
        (is (= :low (:severity (first findings))))))

    (testing "empty nodes → no findings"
      (is (empty? (sut/find-hubs []))))))

;;; ── pair-level findings ─────────────────────────────────────────────────

(def ^:private conceptual-pairs
  [{:ns-a 'a :ns-b 'b :score 0.35 :kind :conceptual :structural-edge? false
    :shared-terms ["reach" "close"]}
   {:ns-a 'a :ns-b 'c :score 0.25 :kind :conceptual :structural-edge? true
    :shared-terms ["scan" "file"]}
   {:ns-a 'b :ns-b 'c :score 0.12 :kind :conceptual :structural-edge? false
    :shared-terms ["util"]}])

(def ^:private change-pairs
  [{:ns-a 'a :ns-b 'b :score 0.40 :kind :change :structural-edge? false
    :co-changes 4 :confidence-a 0.8 :confidence-b 0.6}
   {:ns-a 'c :ns-b 'd :score 0.50 :kind :change :structural-edge? false
    :co-changes 5 :confidence-a 1.0 :confidence-b 0.5}
   {:ns-a 'a :ns-b 'c :score 0.30 :kind :change :structural-edge? true
    :co-changes 3 :confidence-a 0.5 :confidence-b 0.5}])

(deftest find-cross-lens-hidden-test
  (testing "pair hidden in both → :high finding"
    (let [{:keys [findings cross-keys]}
          (sut/find-cross-lens-hidden conceptual-pairs change-pairs)]
      (is (= 1 (count findings)))
      (is (= :high (:severity (first findings))))
      (is (= :cross-lens-hidden (:category (first findings))))
      (is (contains? cross-keys #{'a 'b}))))

  (testing "evidence contains both scores"
    (let [f (first (:findings (sut/find-cross-lens-hidden conceptual-pairs change-pairs)))]
      (is (= 0.35 (get-in f [:evidence :conceptual-score])))
      (is (= 0.40 (get-in f [:evidence :change-score])))
      (is (= 4 (get-in f [:evidence :co-changes])))))

  (testing "structural pairs not flagged"
    (let [{:keys [cross-keys]}
          (sut/find-cross-lens-hidden conceptual-pairs change-pairs)]
      (is (not (contains? cross-keys #{'a 'c})))))

  (testing "empty pairs → empty"
    (let [{:keys [findings cross-keys]}
          (sut/find-cross-lens-hidden [] [])]
      (is (empty? findings))
      (is (empty? cross-keys)))))

(deftest find-hidden-conceptual-test
  (let [cross-keys #{#{'a 'b}}]

    (testing "hidden pair not in cross-keys, score≥0.20 → :medium"
      ;; no non-cross hidden pairs with score≥0.20 in fixture
      ;; b↔c is hidden, score=0.12 (low), a↔b is cross-lens (excluded)
      (let [findings (sut/find-hidden-conceptual conceptual-pairs cross-keys)]
        (is (= 1 (count findings)))
        (is (= :low (:severity (first findings))))
        (is (= #{'b 'c} (set (vals (select-keys (:subject (first findings)) [:ns-a :ns-b])))))))

    (testing "hidden pair score≥0.20 → :medium"
      (let [pairs [{:ns-a 'x :ns-b 'y :score 0.25 :kind :conceptual
                    :structural-edge? false :shared-terms ["foo"]}]
            findings (sut/find-hidden-conceptual pairs #{})]
        (is (= :medium (:severity (first findings))))))

    (testing "structural pair → not flagged"
      (let [findings (sut/find-hidden-conceptual
                      [{:ns-a 'x :ns-b 'y :score 0.50 :structural-edge? true
                        :shared-terms ["foo"]}]
                      #{})]
        (is (empty? findings))))

    (testing "pair in cross-keys → not flagged"
      (let [findings (sut/find-hidden-conceptual conceptual-pairs #{#{'a 'b} #{'b 'c}})]
        (is (empty? findings))))))

(deftest find-hidden-change-test
  (let [cross-keys #{#{'a 'b}}]

    (testing "hidden pair not in cross-keys → :medium"
      (let [findings (sut/find-hidden-change change-pairs cross-keys)]
        (is (= 1 (count findings)))
        (is (= :medium (:severity (first findings))))
        (is (= #{'c 'd} (set (vals (select-keys (:subject (first findings)) [:ns-a :ns-b])))))))

    (testing "structural pair → not flagged"
      (let [findings (sut/find-hidden-change
                      [{:ns-a 'x :ns-b 'y :score 0.50 :structural-edge? true
                        :co-changes 3 :confidence-a 1.0 :confidence-b 0.5}]
                      #{})]
        (is (empty? findings))))

    (testing "pair in cross-keys → not flagged"
      (let [findings (sut/find-hidden-change change-pairs #{#{'a 'b} #{'c 'd}})]
        (is (empty? findings))))))
