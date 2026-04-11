(ns gordian.scc-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [gordian.scc :as sut]))

;;; ── tarjan ───────────────────────────────────────────────────────────────

(deftest tarjan-empty-test
  (testing "empty graph"
    (is (= [] (sut/tarjan {})))))

(deftest tarjan-no-cycles-test
  (testing "linear chain A→B→C — three trivial SCCs"
    (let [sccs (sut/tarjan {'A '#{B} 'B '#{C} 'C #{}})]
      (is (= 3 (count sccs)))
      (is (every? #(= 1 (count %)) sccs))
      (is (= #{'A 'B 'C} (apply set/union sccs)))))

  (testing "fixture graph — three trivial SCCs"
    (let [sccs (sut/tarjan {'alpha #{}
                            'beta  '#{alpha}
                            'gamma '#{alpha beta}})]
      (is (= 3 (count sccs)))
      (is (every? #(= 1 (count %)) sccs)))))

(deftest tarjan-self-loop-test
  (testing "A→A — single SCC containing A"
    (let [sccs (sut/tarjan {'A '#{A}})]
      (is (= 1 (count sccs)))
      (is (= #{'A} (first sccs))))))

(deftest tarjan-mutual-cycle-test
  (testing "A→B→A — one SCC of size 2"
    (let [sccs (sut/tarjan {'A '#{B} 'B '#{A}})]
      (is (= 1 (count sccs)))
      (is (= #{'A 'B} (first sccs))))))

(deftest tarjan-three-cycle-test
  (testing "A→B→C→A — one SCC of size 3"
    (let [sccs (sut/tarjan {'A '#{B} 'B '#{C} 'C '#{A}})]
      (is (= 1 (count sccs)))
      (is (= #{'A 'B 'C} (first sccs))))))

(deftest tarjan-multiple-cycles-test
  (testing "two independent cycles: {A B} and {C D}"
    (let [sccs  (sut/tarjan {'A '#{B} 'B '#{A} 'C '#{D} 'D '#{C}})
          cycle-sets (set (map set sccs))]
      (is (= 2 (count sccs)))
      (is (contains? cycle-sets #{'A 'B}))
      (is (contains? cycle-sets #{'C 'D}))))

  (testing "cycle with attached tail: {A B} and isolated C→A"
    ;; C is not part of the cycle but points into it
    (let [sccs (sut/tarjan {'A '#{B} 'B '#{A} 'C '#{A}})]
      (is (= 2 (count sccs)))
      (is (some #(= #{'A 'B} %) sccs))
      (is (some #(= #{'C} %) sccs)))))

;;; ── cycles ───────────────────────────────────────────────────────────────

(deftest cycles-test
  (testing "no non-trivial SCCs → empty"
    (let [g {'A '#{B} 'B #{}}]
      (is (= [] (sut/cycles g (sut/tarjan g))))))

  (testing "self-loop is a cycle"
    (let [g {'A '#{A}}]
      (is (= [#{'A}] (sut/cycles g (sut/tarjan g))))))

  (testing "size-2 cycle"
    (let [g {'A '#{B} 'B '#{A}}]
      (is (= [#{'A 'B}] (sut/cycles g (sut/tarjan g))))))

  (testing "sorted largest first"
    (let [g    {'A '#{B} 'B '#{C} 'C '#{A}   ; 3-cycle
                'D '#{E} 'E '#{D}}             ; 2-cycle
          cycs (sut/cycles g (sut/tarjan g))]
      (is (= 2 (count cycs)))
      (is (= 3 (count (first  cycs))))
      (is (= 2 (count (second cycs)))))))

;;; ── find-cycles convenience ──────────────────────────────────────────────

(deftest find-cycles-test
  (testing "acyclic graph → []"
    (is (= [] (sut/find-cycles {'A '#{B} 'B #{}}))))

  (testing "returns cycles"
    (is (= [#{'A 'B}]
           (sut/find-cycles {'A '#{B} 'B '#{A}})))))
