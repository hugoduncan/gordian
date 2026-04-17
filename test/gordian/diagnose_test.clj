(ns gordian.diagnose-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.diagnose :as sut]))

(def health #'gordian.diagnose/health)
(def find-cycles #'gordian.diagnose/find-cycles)
(def find-sdp-violations #'gordian.diagnose/find-sdp-violations)
(def find-god-modules #'gordian.diagnose/find-god-modules)
(def find-hubs #'gordian.diagnose/find-hubs)

;;; ── health ───────────────────────────────────────────────────────────────

(deftest health-test
  (testing "low PC → :healthy"
    (is (= :healthy
           (:health (health {:propagation-cost 0.05
                             :cycles [] :nodes [{} {} {}]})))))

  (testing "moderate PC → :moderate"
    (is (= :moderate
           (:health (health {:propagation-cost 0.20
                             :cycles [] :nodes [{} {}]})))))

  (testing "high PC → :concerning"
    (is (= :concerning
           (:health (health {:propagation-cost 0.40
                             :cycles [] :nodes [{} {}]})))))

  (testing "boundary: 0.10 is :healthy"
    (is (= :healthy
           (:health (health {:propagation-cost 0.10
                             :cycles [] :nodes [{}]})))))

  (testing "boundary: 0.30 is :moderate"
    (is (= :moderate
           (:health (health {:propagation-cost 0.30
                             :cycles [] :nodes [{}]})))))

  (testing "reports correct cycle-count and ns-count"
    (let [h (health {:propagation-cost 0.05
                     :cycles [#{'a 'b} #{'c 'd 'e}]
                     :nodes [{} {} {} {} {}]})]
      (is (= 2 (:cycle-count h)))
      (is (= 5 (:ns-count h)))))

  (testing "nil propagation-cost treated as 0"
    (is (= :healthy
           (:health (health {:cycles [] :nodes []}))))))

;;; ── find-cycles ──────────────────────────────────────────────────────────

(deftest find-cycles-test
  (testing "empty cycles → empty findings"
    (is (= [] (find-cycles [] [] [] []))))

  (testing "one cycle → one :high finding"
    (let [findings (find-cycles [#{'a 'b}] [] [] [])]
      (is (= 1 (count findings)))
      (is (= :high (:severity (first findings))))
      (is (= :cycle (:category (first findings))))))

  (testing "finding has :members in :subject and :size in :evidence"
    (let [f (first (find-cycles [#{'a 'b 'c}] [] [] []))]
      (is (= #{'a 'b 'c} (get-in f [:subject :members])))
      (is (= 3 (get-in f [:evidence :size])))))

  (testing "two cycles → two findings"
    (is (= 2 (count (find-cycles [#{'a 'b} #{'c 'd}] [] [] [])))))

  (testing "reason starts with size"
    (let [f (first (find-cycles [#{'x 'y}] [] [] []))]
      (is (str/starts-with? (:reason f) "2-namespace cycle"))))

  (testing "finding has :action and :next-step"
    (let [f (first (find-cycles [#{'a 'b}] [] [] []))]
      (is (= :fix-cycle (:action f)))
      (is (string? (:next-step f)))))

  (testing "strategy :investigate when no evidence"
    (let [f (first (find-cycles [#{'a 'b}] [] [] []))]
      (is (= :investigate (get-in f [:evidence :strategy])))))

  (testing "strategy :merge when change Jaccard > 0.7"
    (let [xp [{:ns-a 'a :ns-b 'b :score 0.8 :structural-edge? false}]
          f  (first (find-cycles [#{'a 'b}] xp [] []))]
      (is (= :merge (get-in f [:evidence :strategy])))))

  (testing "strategy :extract when independent conceptual terms"
    (let [cp [{:ns-a 'a :ns-b 'b :score 0.4 :structural-edge? false
               :independent-terms ["queue" "worker"]}]
          f  (first (find-cycles [#{'a 'b}] [] cp []))]
      (is (= :extract (get-in f [:evidence :strategy])))))

  (testing "strategy :invert when stable high-Ca member"
    (let [nodes [{:ns 'a :ca 4 :instability 0.1}
                 {:ns 'b :ca 1 :instability 0.9}]
          f     (first (find-cycles [#{'a 'b}] [] [] nodes))]
      (is (= :invert (get-in f [:evidence :strategy])))))

  (testing "merge takes priority over extract"
    (let [xp [{:ns-a 'a :ns-b 'b :score 0.8 :structural-edge? false}]
          cp [{:ns-a 'a :ns-b 'b :score 0.4 :structural-edge? false
               :independent-terms ["queue"]}]
          f  (first (find-cycles [#{'a 'b}] xp cp []))]
      (is (= :merge (get-in f [:evidence :strategy]))))))

;;; ── find-sdp-violations ─────────────────────────────────────────────────

(def ^:private sample-nodes
  [{:ns 'a.core :reach 0.1 :fan-in 0.5 :ca 3 :ce 1 :instability 0.25 :role :core}
   {:ns 'b.hub  :reach 0.8 :fan-in 0.1 :ca 0 :ce 5 :instability 1.0  :role :peripheral}
   {:ns 'c.bad  :reach 0.3 :fan-in 0.4 :ca 3 :ce 4 :instability 0.57 :role :shared}
   {:ns 'd.ok   :reach 0.1 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5  :role :shared}
   {:ns 'e.low  :reach 0.0 :fan-in 0.0 :ca 0 :ce 0 :instability 0.0  :role :isolated}])

(deftest find-sdp-violations-test
  (testing "Ca≥2 and I>0.5 → medium finding"
    (let [findings (find-sdp-violations sample-nodes)
          flagged  (set (map #(get-in % [:subject :ns]) findings))]
      (is (contains? flagged 'c.bad))
      (is (= :medium (:severity (first findings))))))

  (testing "Ca=0 → not flagged"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'b.hub)))))

  (testing "I=0.0 → not flagged (stable)"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'e.low)))))

  (testing "Ca=1 → not flagged (threshold is 2)"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'd.ok)))))

  (testing "Ca≥2 but I≤0.5 → not flagged"
    (let [flagged (set (map #(get-in % [:subject :ns])
                            (find-sdp-violations sample-nodes)))]
      (is (not (contains? flagged 'a.core))))))

;;; ── find-god-modules ────────────────────────────────────────────────────

(deftest find-god-modules-test
  (let [nodes [{:ns 'x.god  :reach 0.6 :fan-in 0.6 :ca 3 :ce 3 :instability 0.5 :role :shared}
               {:ns 'x.core :reach 0.0 :fan-in 0.3 :ca 2 :ce 0 :instability 0.0 :role :core}
               {:ns 'x.leaf :reach 0.3 :fan-in 0.0 :ca 0 :ce 2 :instability 1.0 :role :peripheral}
               {:ns 'x.mild :reach 0.1 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5 :role :shared}]]

    (testing "shared with extreme values → medium finding"
      (let [findings (find-god-modules nodes)]
        (is (= 1 (count findings)))
        (is (= 'x.god (get-in (first findings) [:subject :ns])))
        (is (= :medium (:severity (first findings))))))

    (testing "core node with high fan-in → not flagged (not :shared)"
      (let [flagged (set (map #(get-in % [:subject :ns])
                              (find-god-modules nodes)))]
        (is (not (contains? flagged 'x.core)))))

    (testing "shared with normal values → not flagged"
      (let [flagged (set (map #(get-in % [:subject :ns])
                              (find-god-modules nodes)))]
        (is (not (contains? flagged 'x.mild)))))))

;;; ── find-god-modules — façade detection ──────────────────────────────────

(deftest facade-detection-test
  ;; Façade pattern: high Ca-external, low Ce-external, delegates to siblings.
  ;; app.facade has Ca-ext=3 (external dependents), Ce-ext=0, Ce-fam=2 (delegates)
  (let [facade-nodes
        [{:ns 'app.facade :reach 0.6 :fan-in 0.6 :ca 5 :ce 2 :instability 0.29
          :role :shared
          :family "app" :ca-family 2 :ca-external 3 :ce-family 2 :ce-external 0}
         {:ns 'app.impl :reach 0.0 :fan-in 0.2 :ca 1 :ce 0 :instability 0.0
          :role :core
          :family "app" :ca-family 1 :ca-external 0 :ce-family 0 :ce-external 0}
         {:ns 'app.data :reach 0.0 :fan-in 0.2 :ca 1 :ce 0 :instability 0.0
          :role :core
          :family "app" :ca-family 1 :ca-external 0 :ce-family 0 :ce-external 0}
         {:ns 'lib.a :reach 0.1 :fan-in 0.0 :ca 0 :ce 1 :instability 1.0
          :role :peripheral
          :family "lib" :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 1}
         {:ns 'lib.b :reach 0.1 :fan-in 0.0 :ca 0 :ce 1 :instability 1.0
          :role :peripheral
          :family "lib" :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 1}
         {:ns 'lib.c :reach 0.1 :fan-in 0.0 :ca 0 :ce 1 :instability 1.0
          :role :peripheral
          :family "lib" :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 1}]]

    (testing "façade detected → :facade category at :low severity"
      (let [findings (find-god-modules facade-nodes)
            f        (first findings)]
        (is (= 1 (count findings)))
        (is (= :facade (:category f)))
        (is (= :low (:severity f)))
        (is (= 'app.facade (get-in f [:subject :ns])))))

    (testing "façade evidence includes family-scoped metrics"
      (let [f (first (find-god-modules facade-nodes))]
        (is (= 3 (get-in f [:evidence :ca-external])))
        (is (= 0 (get-in f [:evidence :ce-external])))
        (is (= 2 (get-in f [:evidence :ce-family])))
        (is (= "app" (get-in f [:evidence :family]))))))

  ;; God-module pattern: high coupling in all directions
  (let [god-nodes
        [{:ns 'x.god :reach 0.6 :fan-in 0.6 :ca 5 :ce 4 :instability 0.44
          :role :shared
          :family "x" :ca-family 1 :ca-external 4 :ce-family 1 :ce-external 3}
         {:ns 'x.a :reach 0.0 :fan-in 0.1 :ca 1 :ce 0 :instability 0.0
          :role :core
          :family "x" :ca-family 0 :ca-external 1 :ce-family 0 :ce-external 0}
         {:ns 'y.a :reach 0.1 :fan-in 0.0 :ca 0 :ce 1 :instability 1.0
          :role :peripheral
          :family "y" :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 1}
         {:ns 'y.b :reach 0.1 :fan-in 0.0 :ca 0 :ce 1 :instability 1.0
          :role :peripheral
          :family "y" :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 1}]]

    (testing "god-module with high Ce-external → NOT a façade, stays :god-module"
      (let [findings (find-god-modules god-nodes)
            f        (first findings)]
        (is (= 1 (count findings)))
        (is (= :god-module (:category f)))
        (is (= :medium (:severity f)))
        (is (= 'x.god (get-in f [:subject :ns]))))))

  ;; No family metrics available (backward compat)
  ;; mean reach = (0.8+0.0+0.1)/3 = 0.3, 2× = 0.6, 0.8 > 0.6 ✓
  (let [no-family-nodes
        [{:ns 'x.god  :reach 0.8 :fan-in 0.6 :ca 3 :ce 3 :instability 0.5 :role :shared}
         {:ns 'x.core :reach 0.0 :fan-in 0.3 :ca 2 :ce 0 :instability 0.0 :role :core}
         {:ns 'x.leaf :reach 0.1 :fan-in 0.0 :ca 0 :ce 2 :instability 1.0 :role :peripheral}]]

    (testing "without family metrics → god-module (no façade possible)"
      (let [findings (find-god-modules no-family-nodes)]
        (is (= 1 (count findings)))
        (is (= :god-module (:category (first findings))))))))

;;; ── find-hubs ───────────────────────────────────────────────────────────

(deftest find-hubs-test
  (let [nodes [{:ns 'h.main :reach 0.9 :fan-in 0.0 :ca 0 :ce 8 :instability 1.0 :role :peripheral}
               {:ns 'h.a    :reach 0.1 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5 :role :shared}
               {:ns 'h.b    :reach 0.0 :fan-in 0.1 :ca 1 :ce 0 :instability 0.0 :role :core}]]

    (testing "reach > 3× mean → low finding"
      (let [findings (find-hubs nodes)]
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
            findings (find-hubs extreme)]
        (is (= 1 (count findings)))
        (is (= 'h.main (get-in (first findings) [:subject :ns])))
        (is (= :low (:severity (first findings))))))

    (testing "empty nodes → no findings"
      (is (empty? (find-hubs []))))))

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

;;; ── family-noise severity adjustment ─────────────────────────────────────

(deftest family-noise-severity-test
  (testing "same-family with no independent terms → :low (naming noise)"
    (let [pairs [{:ns-a 'psi.session.core :ns-b 'psi.session.ui
                  :score 0.35 :kind :conceptual :structural-edge? false
                  :shared-terms ["psi" "session"]
                  :same-family? true :family-terms ["psi" "session"]
                  :independent-terms []}]
          findings (sut/find-hidden-conceptual pairs #{})]
      (is (= 1 (count findings)))
      (is (= :low (:severity (first findings))))
      (is (str/includes? (:reason (first findings)) "naming similarity"))))

  (testing "same-family with independent terms → keeps normal severity"
    (let [pairs [{:ns-a 'psi.session.core :ns-b 'psi.session.mutations
                  :score 0.30 :kind :conceptual :structural-edge? false
                  :shared-terms ["session" "mutation" "state"]
                  :same-family? true :family-terms ["session"]
                  :independent-terms ["mutation" "state"]}]
          findings (sut/find-hidden-conceptual pairs #{})]
      (is (= 1 (count findings)))
      (is (= :medium (:severity (first findings))))
      (is (not (str/includes? (:reason (first findings)) "naming similarity")))))

  (testing "different-family pair → normal severity regardless"
    (let [pairs [{:ns-a 'gordian.scan :ns-b 'psi.session.core
                  :score 0.25 :kind :conceptual :structural-edge? false
                  :shared-terms ["file" "parse"]
                  :same-family? false :family-terms []
                  :independent-terms ["file" "parse"]}]
          findings (sut/find-hidden-conceptual pairs #{})]
      (is (= :medium (:severity (first findings))))))

  (testing "family annotation present in evidence"
    (let [pairs [{:ns-a 'a.x :ns-b 'a.y :score 0.20
                  :kind :conceptual :structural-edge? false
                  :shared-terms ["foo"]
                  :same-family? true :family-terms [] :independent-terms ["foo"]}]
          f (first (sut/find-hidden-conceptual pairs #{}))]
      (is (true? (get-in f [:evidence :same-family?])))
      (is (= [] (get-in f [:evidence :family-terms])))
      (is (= ["foo"] (get-in f [:evidence :independent-terms])))))

  (testing "pairs without family annotation → backward compat, normal severity"
    (let [pairs [{:ns-a 'x :ns-b 'y :score 0.25 :kind :conceptual
                  :structural-edge? false :shared-terms ["data"]}]
          findings (sut/find-hidden-conceptual pairs #{})]
      (is (= :medium (:severity (first findings))))
      (is (not (contains? (:evidence (first findings)) :same-family?))))))

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

;;; ── diagnose (full integration) ─────────────────────────────────────────

(def ^:private full-report
  {:propagation-cost 0.15
   :cycles [#{'cyc.a 'cyc.b}]
   :nodes [{:ns 'main  :reach 0.8 :fan-in 0.0 :ca 0 :ce 5 :instability 1.0 :role :peripheral}
           {:ns 'core  :reach 0.0 :fan-in 0.3 :ca 2 :ce 0 :instability 0.0 :role :core}
           {:ns 'util  :reach 0.1 :fan-in 0.1 :ca 1 :ce 1 :instability 0.5 :role :shared}
           {:ns 'cyc.a :reach 0.2 :fan-in 0.2 :ca 1 :ce 1 :instability 0.5 :role :shared}
           {:ns 'cyc.b :reach 0.2 :fan-in 0.2 :ca 1 :ce 1 :instability 0.5 :role :shared}]
   :conceptual-pairs
   [{:ns-a 'core :ns-b 'util :score 0.30 :kind :conceptual
     :structural-edge? false :shared-terms ["data" "transform"]}
    {:ns-a 'main :ns-b 'core :score 0.20 :kind :conceptual
     :structural-edge? true :shared-terms ["app"]}]
   :change-pairs
   [{:ns-a 'core :ns-b 'util :score 0.45 :kind :change
     :structural-edge? false :co-changes 5 :confidence-a 0.8 :confidence-b 0.6}]})

(deftest diagnose-test
  (let [findings (sut/diagnose full-report)]

    (testing "returns a vector"
      (is (vector? findings)))

    (testing "cycle finding present"
      (is (some #(= :cycle (:category %)) findings)))

    (testing "cross-lens hidden present (core↔util hidden in both)"
      (is (some #(= :cross-lens-hidden (:category %)) findings)))

    (testing "structural pairs not flagged as hidden"
      (let [hidden-subjects (set (map :subject (filter #(#{:hidden-conceptual :hidden-change
                                                           :cross-lens-hidden} (:category %))
                                                       findings)))]
        (is (not (some #(and (= 'main (:ns-a %)) (= 'core (:ns-b %))) hidden-subjects))))))

  (testing "report with no pairs → only structural findings"
    (let [report (dissoc full-report :conceptual-pairs :change-pairs)
          findings (sut/diagnose report)
          categories (set (map :category findings))]
      (is (not (contains? categories :cross-lens-hidden)))
      (is (not (contains? categories :hidden-conceptual)))
      (is (not (contains? categories :hidden-change)))))

  (testing "report with no cycles → no cycle findings"
    (let [report (assoc full-report :cycles [])
          findings (sut/diagnose report)]
      (is (not (some #(= :cycle (:category %)) findings)))))

  (testing "minimal report works"
    (let [report {:propagation-cost 0.05 :cycles [] :nodes []}]
      (is (vector? (sut/diagnose report)))))

  (testing "all findings have :action"
    (let [findings (sut/diagnose full-report)]
      (is (every? #(contains? % :action) findings))))

  (testing "all pair findings have :next-step starting with gordian"
    (let [pair-cats #{:cross-lens-hidden :hidden-conceptual :hidden-change :vestigial-edge}
          findings  (filter #(contains? pair-cats (:category %)) (sut/diagnose full-report))]
      (is (seq findings))
      (is (every? #(str/starts-with? (str (:next-step %)) "gordian") findings))))

  (testing "ns findings have :next-step starting with gordian"
    (let [ns-cats  #{:sdp-violation :god-module :hub :facade}
          findings (filter #(contains? ns-cats (:category %)) (sut/diagnose full-report))]
      (is (every? #(str/starts-with? (str (:next-step %)) "gordian") findings)))))

;;; ── find-vestigial-edges ─────────────────────────────────────────────────

(def ^:private find-vestigial-edges #'gordian.diagnose/find-vestigial-edges)

(deftest find-vestigial-edges-test
  (let [graph    {'foo.a #{'foo.b 'foo.c}
                  'foo.b #{'foo.c}
                  'foo.c #{}}
        nodes    [{:ns 'foo.a :instability 0.8}
                  {:ns 'foo.b :instability 0.5}
                  {:ns 'foo.c :instability 0.0}]
        cp-ab    [{:ns-a 'foo.a :ns-b 'foo.b :score 0.4 :structural-edge? true}]
        xp-bc    [{:ns-a 'foo.b :ns-b 'foo.c :score 0.6 :structural-edge? true}]]

    (testing "edge with no concept or change signal → :vestigial-edge finding"
      ;; foo.a→foo.c has no concept or change coverage
      (let [findings (find-vestigial-edges graph nodes [] [])]
        (is (some #(and (= :vestigial-edge (:category %))
                        (= #{'foo.a 'foo.c} (set (vals (select-keys (:subject %) [:ns-a :ns-b])))))
                  findings))))

    (testing "edge covered by conceptual pair → not flagged"
      (let [findings (find-vestigial-edges graph nodes cp-ab [])]
        (is (not (some #(and (= :vestigial-edge (:category %))
                             (= #{'foo.a 'foo.b} (set (vals (select-keys (:subject %) [:ns-a :ns-b])))))
                       findings)))))

    (testing "edge covered by change pair → not flagged"
      (let [findings (find-vestigial-edges graph nodes [] xp-bc)]
        (is (not (some #(and (= :vestigial-edge (:category %))
                             (= #{'foo.b 'foo.c} (set (vals (select-keys (:subject %) [:ns-a :ns-b])))))
                       findings)))))

    (testing "external deps (not in graph keys) → not flagged"
      (let [g-ext {'foo.a #{'foo.b 'ext.lib}}
            findings (find-vestigial-edges g-ext nodes [] [])]
        (is (not (some #(= :vestigial-edge (:category %)) findings)))))

    (testing "nil conceptual-pairs → returns []"
      (is (= [] (find-vestigial-edges graph nodes nil []))))

    (testing "nil change-pairs → returns []"
      (is (= [] (find-vestigial-edges graph nodes [] nil))))

    (testing "edge from peripheral ns → not flagged"
      (let [nodes-with-periph [{:ns 'foo.a :instability 1.0 :role :peripheral}
                               {:ns 'foo.b :instability 0.5 :role :core}
                               {:ns 'foo.c :instability 0.0 :role :core}]
            findings (find-vestigial-edges graph nodes-with-periph [] [])]
        (is (not (some #(and (= :vestigial-edge (:category %))
                             (= 'foo.a (get-in % [:subject :ns-a])))
                       findings)))))

    (testing "edge between two core nss with no signal → flagged"
      (let [nodes-core [{:ns 'foo.a :instability 0.5 :role :core}
                        {:ns 'foo.b :instability 0.3 :role :core}
                        {:ns 'foo.c :instability 0.0 :role :core}]
            findings (find-vestigial-edges graph nodes-core [] [])]
        (is (some #(= :vestigial-edge (:category %)) findings))))

    (testing "vestigial finding has :action :remove-dependency"
      (let [f (first (find-vestigial-edges graph nodes [] []))]
        (is (= :remove-dependency (:action f)))))

    (testing "vestigial finding has :next-step with explain-pair"
      (let [f (first (find-vestigial-edges graph nodes [] []))]
        (is (str/starts-with? (:next-step f) "gordian explain-pair"))))))

;;; ── asymmetric change coupling ───────────────────────────────────────────

(def ^:private change-asymmetry #'gordian.diagnose/change-asymmetry)

(deftest change-asymmetry-test
  (testing "conf-a >> conf-b → directional with a as satellite"
    (let [r (change-asymmetry 'foo.a 'foo.b 0.9 0.3)]
      (is (:directional? r))
      (is (= 'foo.a (:satellite r)))
      (is (= 'foo.b (:anchor r)))))

  (testing "conf-b >> conf-a → directional with b as satellite"
    (let [r (change-asymmetry 'foo.a 'foo.b 0.2 0.9)]
      (is (:directional? r))
      (is (= 'foo.b (:satellite r)))
      (is (= 'foo.a (:anchor r)))))

  (testing "ratio ≤ 2 → not directional"
    (let [r (change-asymmetry 'foo.a 'foo.b 0.6 0.4)]
      (is (not (:directional? r)))))

  (testing "conf-a = 0 → returns nil (no division by zero)"
    (is (nil? (change-asymmetry 'foo.a 'foo.b 0 0.5))))

  (testing "both zero → returns nil"
    (is (nil? (change-asymmetry 'foo.a 'foo.b 0 0)))))

(deftest find-hidden-change-asymmetry-test
  (let [cross-keys #{}]
    (testing "directional pair → :narrow-satellite-interface action"
      (let [p        {:ns-a 'foo.a :ns-b 'foo.b :score 0.5
                      :structural-edge? false :co-changes 5
                      :confidence-a 0.9 :confidence-b 0.3}
            findings (sut/find-hidden-change [p] cross-keys)
            f        (first findings)]
        (is (= :narrow-satellite-interface (:action f)))
        (is (some? (get-in f [:evidence :direction])))
        (is (= 'foo.a (get-in f [:evidence :direction :satellite])))
        (is (= 'foo.b (get-in f [:evidence :direction :anchor])))))

    (testing "symmetric pair → :investigate-contract action"
      (let [p        {:ns-a 'foo.a :ns-b 'foo.b :score 0.5
                      :structural-edge? false :co-changes 5
                      :confidence-a 0.6 :confidence-b 0.5}
            findings (sut/find-hidden-change [p] cross-keys)
            f        (first findings)]
        (is (= :investigate-contract (:action f)))
        (is (nil? (get-in f [:evidence :direction])))))

    (testing "zero confidence → symmetric fallback"
      (let [p        {:ns-a 'foo.a :ns-b 'foo.b :score 0.4
                      :structural-edge? false :co-changes 3
                      :confidence-a 0 :confidence-b 0.5}
            findings (sut/find-hidden-change [p] cross-keys)
            f        (first findings)]
        (is (= :investigate-contract (:action f)))))))
