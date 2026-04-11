(ns gordian.envelope-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.envelope :as sut]))

;;; ── fixtures ─────────────────────────────────────────────────────────────

(def ^:private analyze-report
  {:src-dirs              ["src/"]
   :propagation-cost      0.05
   :cycles                []
   :nodes                 [{:ns 'a.core :reach 0.5 :fan-in 0.2}]
   :graph                 {'a.core #{'b.util}}
   :conceptual-pairs      [{:ns-a 'a.core :ns-b 'b.util :score 0.35}]
   :conceptual-threshold  0.20
   :conceptual-candidate-count 42
   :change-pairs          [{:ns-a 'a.core :ns-b 'b.util :score 0.40}]
   :change-threshold      0.30
   :change-candidate-count 15})

(def ^:private opts
  {:src-dirs      ["src/"]
   :conceptual    0.20
   :change        "."
   :change-since  "90 days ago"
   :exclude       ["user"]
   :include-tests true})

;;; ── envelope structure ───────────────────────────────────────────────────

(deftest envelope-keys-test
  (let [env (sut/wrap opts analyze-report :analyze)]
    (testing "envelope metadata keys present"
      (is (= sut/gordian-version (:gordian/version env)))
      (is (= sut/schema-version (:gordian/schema env)))
      (is (= :analyze (:gordian/command env))))

    (testing "src-dirs, excludes, include-tests from opts"
      (is (= ["src/"] (:src-dirs env)))
      (is (= ["user"] (:excludes env)))
      (is (true? (:include-tests env))))

    (testing "lenses section present"
      (is (true? (get-in env [:lenses :structural])))
      (is (map? (get-in env [:lenses :conceptual])))
      (is (map? (get-in env [:lenses :change]))))))

(deftest conceptual-lens-enabled-test
  (let [env (sut/wrap opts analyze-report :analyze)
        cl  (get-in env [:lenses :conceptual])]
    (testing "conceptual lens enabled with threshold"
      (is (true? (:enabled cl)))
      (is (= 0.20 (:threshold cl))))

    (testing "candidate and reported counts"
      (is (= 42 (:candidate-pairs cl)))
      (is (= 1 (:reported-pairs cl))))))

(deftest change-lens-enabled-test
  (let [env (sut/wrap opts analyze-report :analyze)
        ch  (get-in env [:lenses :change])]
    (testing "change lens enabled with threshold"
      (is (true? (:enabled ch)))
      (is (= 0.30 (:threshold ch))))

    (testing "candidate and reported counts"
      (is (= 15 (:candidate-pairs ch)))
      (is (= 1 (:reported-pairs ch))))

    (testing "since and repo-dir from opts"
      (is (= "90 days ago" (:since ch)))
      (is (= "." (:repo-dir ch))))))

(deftest lens-disabled-test
  (let [report (dissoc analyze-report
                       :conceptual-pairs :conceptual-threshold
                       :conceptual-candidate-count
                       :change-pairs :change-threshold
                       :change-candidate-count)
        env    (sut/wrap {} report :analyze)]
    (testing "conceptual lens disabled"
      (is (false? (get-in env [:lenses :conceptual :enabled]))))
    (testing "change lens disabled"
      (is (false? (get-in env [:lenses :change :enabled]))))))

(deftest internal-keys-stripped-test
  (let [env (sut/wrap opts analyze-report :analyze)]
    (testing ":graph stripped from output"
      (is (not (contains? env :graph))))
    (testing "threshold keys stripped (moved to :lenses)"
      (is (not (contains? env :conceptual-threshold)))
      (is (not (contains? env :change-threshold))))
    (testing "candidate-count keys stripped (moved to :lenses)"
      (is (not (contains? env :conceptual-candidate-count)))
      (is (not (contains? env :change-candidate-count))))))

(deftest payload-preserved-test
  (let [env (sut/wrap opts analyze-report :analyze)]
    (testing "propagation-cost preserved"
      (is (= 0.05 (:propagation-cost env))))
    (testing "cycles preserved"
      (is (= [] (:cycles env))))
    (testing "nodes preserved"
      (is (= 1 (count (:nodes env)))))
    (testing "conceptual-pairs preserved"
      (is (= 1 (count (:conceptual-pairs env)))))
    (testing "change-pairs preserved"
      (is (= 1 (count (:change-pairs env)))))))

(deftest command-variants-test
  (testing "diagnose command"
    (let [report (assoc analyze-report :findings [] :health {:propagation-cost 0.05})
          env    (sut/wrap opts report :diagnose)]
      (is (= :diagnose (:gordian/command env)))
      (is (contains? env :findings))
      (is (contains? env :health))))

  (testing "explain command"
    (let [data {:ns 'a.core :metrics {:role :core} :direct-deps {:project [] :external []}}
          env  (sut/wrap opts data :explain)]
      (is (= :explain (:gordian/command env)))
      (is (= 'a.core (:ns env)))))

  (testing "explain-pair command"
    (let [data {:ns-a 'a.core :ns-b 'b.util :structural {:direct-edge? true}}
          env  (sut/wrap opts data :explain-pair)]
      (is (= :explain-pair (:gordian/command env)))
      (is (= 'a.core (:ns-a env))))))

(deftest defaults-when-opts-empty-test
  (let [env (sut/wrap {} analyze-report :analyze)]
    (testing "excludes defaults to []"
      (is (= [] (:excludes env))))
    (testing "include-tests defaults to false"
      (is (false? (:include-tests env))))))
