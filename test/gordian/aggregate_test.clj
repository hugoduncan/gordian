(ns gordian.aggregate-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.aggregate :as sut]))

;;; ── helpers ──────────────────────────────────────────────────────────────

(defn- approx= [a b] (< (Math/abs (- a b)) 1e-9))

(defn- pc [m]   (:propagation-cost m))
(defn- nodes [m] (:nodes m))
(defn- node [m ns-sym] (first (filter #(= ns-sym (:ns %)) (nodes m))))

;;; ── edge cases ───────────────────────────────────────────────────────────

(deftest empty-graph-test
  (testing "empty graph yields zero cost and no nodes"
    (let [r (sut/aggregate {})]
      (is (= 0.0 (pc r)))
      (is (= [] (nodes r))))))

(deftest single-node-test
  (testing "single isolated node"
    ;; N=1, reach=0, PC = 0/1 = 0.0
    (let [r (sut/aggregate {'A #{}})]
      (is (= 0.0 (pc r)))
      (is (= 1 (count (nodes r))))
      (is (= 0.0 (:reach  (node r 'A))))
      (is (= 0.0 (:fan-in (node r 'A)))))))

;;; ── fixture graph ────────────────────────────────────────────────────────

(deftest fixture-graph-test
  ;; closed graph:  alpha→#{}, beta→#{alpha}, gamma→#{alpha beta}
  ;; N=3
  ;; proj-reach (excl self, project only):
  ;;   alpha → #{}          |reach|=0
  ;;   beta  → #{alpha}     |reach|=1
  ;;   gamma → #{alpha beta}|reach|=2
  ;; PC = (0+1+2) / 9 = 1/3
  ;; fan-in: alpha←{beta,gamma}=2, beta←{gamma}=1, gamma←{}=0
  (let [closed {'alpha #{}
                'beta  '#{alpha}
                'gamma '#{alpha beta}}
        r      (sut/aggregate closed)
        N      3.0]
    (testing "global propagation cost"
      (is (approx= (/ 3.0 9.0) (pc r))))

    (testing "reach per node"
      (is (approx= (/ 0.0 N) (:reach (node r 'alpha))))
      (is (approx= (/ 1.0 N) (:reach (node r 'beta))))
      (is (approx= (/ 2.0 N) (:reach (node r 'gamma)))))

    (testing "fan-in per node"
      (is (approx= (/ 2.0 N) (:fan-in (node r 'alpha))))
      (is (approx= (/ 1.0 N) (:fan-in (node r 'beta))))
      (is (approx= (/ 0.0 N) (:fan-in (node r 'gamma)))))

    (testing "nodes sorted most-coupled first"
      (is (= '[gamma beta alpha] (mapv :ns (nodes r)))))))

;;; ── cycle case ───────────────────────────────────────────────────────────

(deftest cycle-test
  ;; A→B→A  →  close gives A→#{A B}, B→#{A B}
  ;; proj-reach excl self: A→#{B}, B→#{A}
  ;; N=2, PC = (1+1)/4 = 0.5
  (let [closed {'A '#{A B}
                'B '#{A B}}
        r      (sut/aggregate closed)
        N      2.0]
    (testing "propagation cost for fully cyclic pair"
      (is (approx= 0.5 (pc r))))

    (testing "reach symmetric"
      (is (approx= (/ 1.0 N) (:reach (node r 'A))))
      (is (approx= (/ 1.0 N) (:reach (node r 'B)))))

    (testing "fan-in symmetric"
      (is (approx= (/ 1.0 N) (:fan-in (node r 'A))))
      (is (approx= (/ 1.0 N) (:fan-in (node r 'B)))))))

;;; ── external dep excluded ────────────────────────────────────────────────

(deftest external-dep-excluded-test
  ;; A depends on ext (external, not a key); ext must not inflate PC
  ;; proj-reach(A) = #{} (ext is not a project node)
  ;; N=1, PC = 0/1 = 0.0
  (let [r (sut/aggregate {'A '#{ext}})]
    (is (= 0.0 (pc r)))
    (is (= 0.0 (:reach (node r 'A))))))
