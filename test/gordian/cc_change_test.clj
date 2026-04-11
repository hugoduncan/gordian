(ns gordian.cc-change-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.cc-change :as sut]))

;;; ── shared graph fixture ─────────────────────────────────────────────────

(def ^:private graph
  "scan → main structural edge; output has no structural edges."
  {'gordian.scan   #{'gordian.main}
   'gordian.main   #{}
   'gordian.output #{}})

;;; ── shared fixture ───────────────────────────────────────────────────────

(def ^:private commits
  "Four commits over three namespaces:
   a — scan + main          (pair: [main scan])
   b — scan + output        (pair: [output scan])
   c — scan + main + output (pairs: [main scan] [output scan] [main output])
   d — main alone           (no pairs)"
  [{:sha "a" :nss #{'gordian.scan 'gordian.main}}
   {:sha "b" :nss #{'gordian.scan 'gordian.output}}
   {:sha "c" :nss #{'gordian.scan 'gordian.main 'gordian.output}}
   {:sha "d" :nss #{'gordian.main}}])

;;; scan=3 main=3 output=2
;;; [gordian.main gordian.scan]=2  [gordian.output gordian.scan]=2
;;; [gordian.main gordian.output]=1

;;; ── ns-change-counts ─────────────────────────────────────────────────────

(deftest ns-change-counts-test
  (testing "counts commits per namespace"
    (let [counts (sut/ns-change-counts commits)]
      (is (= 3 (get counts 'gordian.scan)))
      (is (= 3 (get counts 'gordian.main)))
      (is (= 2 (get counts 'gordian.output)))))

  (testing "single-namespace commit contributes to count but no pairs"
    ;; commit d touches only main; still counted
    (let [counts (sut/ns-change-counts [{:sha "x" :nss #{'gordian.main}}])]
      (is (= 1 (get counts 'gordian.main)))))

  (testing "empty commits → empty map"
    (is (= {} (sut/ns-change-counts []))))

  (testing "all namespaces in commits are present as keys"
    (let [counts (sut/ns-change-counts commits)]
      (is (= #{'gordian.scan 'gordian.main 'gordian.output}
             (set (keys counts)))))))

;;; ── co-change-counts ─────────────────────────────────────────────────────

(deftest co-change-counts-test
  (testing "correct co-change counts"
    (let [co (sut/co-change-counts commits)]
      (is (= 2 (get co '[gordian.main gordian.scan])))
      (is (= 2 (get co '[gordian.output gordian.scan])))
      (is (= 1 (get co '[gordian.main gordian.output])))))

  (testing "pairs stored in canonical order (lex smaller ns first)"
    ;; 'gordian.main < 'gordian.output < 'gordian.scan alphabetically
    (let [co (sut/co-change-counts commits)]
      (is (every? (fn [[a b]] (neg? (compare (str a) (str b)))) (keys co)))))

  (testing "single-ns commit contributes no pairs"
    (let [co (sut/co-change-counts [{:sha "x" :nss #{'gordian.main}}])]
      (is (empty? co))))

  (testing "3-ns commit emits all 3 pairs"
    (let [co (sut/co-change-counts [{:sha "x"
                                     :nss #{'gordian.scan
                                            'gordian.main
                                            'gordian.output}}])]
      (is (= 3 (count co)))
      (is (= 1 (get co '[gordian.main gordian.scan])))
      (is (= 1 (get co '[gordian.output gordian.scan])))
      (is (= 1 (get co '[gordian.main gordian.output])))))

  (testing "empty commits → empty map"
    (is (= {} (sut/co-change-counts []))))

  (testing "repeated pair across commits accumulates"
    (let [co (sut/co-change-counts
              [{:sha "1" :nss #{'a 'b}}
               {:sha "2" :nss #{'a 'b}}
               {:sha "3" :nss #{'a 'b}}])]
      (is (= 1 (count co)))
      (is (= 3 (get co (sort-by str ['a 'b])))))))
;;; ── change-coupling-pairs ────────────────────────────────────────────────
;;;
;;; Fixture derivation:
;;;   n-changes: scan=3 main=3 output=2
;;;   co-counts: [main scan]=2  [output scan]=2  [main output]=1
;;;
;;;   Jaccard [main scan]:   2/(3+3-2) = 0.50
;;;   Jaccard [output scan]: 2/(2+3-2) = 0.67
;;;   Jaccard [main output]: 1/(3+2-1) = 0.25  → excluded at threshold=0.30
;;;                                              → also excluded at min-co=2
;;;
;;;   structural edges: scan→main (yes), output (none)

(deftest change-coupling-pairs-test
  (testing "returns a map with :pairs and :candidate-count"
    (let [result (sut/change-coupling-pairs commits graph)]
      (is (map? result))
      (is (contains? result :pairs))
      (is (contains? result :candidate-count))
      (is (vector? (:pairs result)))
      (is (pos-int? (:candidate-count result)))))

  (testing "candidate-count >= reported pairs"
    (let [result (sut/change-coupling-pairs commits graph 0.01 1)]
      (is (>= (:candidate-count result) (count (:pairs result))))))

  (testing "each entry has required keys"
    (doseq [p (:pairs (sut/change-coupling-pairs commits graph 0.01 1))]
      (is (contains? p :ns-a))
      (is (contains? p :ns-b))
      (is (contains? p :score))
      (is (contains? p :kind))
      (is (= :change (:kind p)))
      (is (contains? p :confidence-a))
      (is (contains? p :confidence-b))
      (is (contains? p :co-changes))
      (is (contains? p :structural-edge?))))

  (testing "Jaccard coupling values correct"
    (let [pairs (:pairs (sut/change-coupling-pairs commits graph 0.01 1))
          by-ns (into {} (map (fn [p] [#{(:ns-a p) (:ns-b p)} p]) pairs))]
      (is (< (Math/abs (- 0.50 (:score (get by-ns #{'gordian.main 'gordian.scan})))) 1e-9))
      (is (< (Math/abs (- (/ 2.0 3) (:score (get by-ns #{'gordian.output 'gordian.scan})))) 1e-9))
      (is (< (Math/abs (- 0.25 (:score (get by-ns #{'gordian.main 'gordian.output})))) 1e-9))))

  (testing "confidence values correct"
    ;; [output scan]: output changed 2 times, co=2 → conf-a=1.0; scan changed 3 times → conf-b=2/3
    (let [pairs (:pairs (sut/change-coupling-pairs commits graph 0.01 1))
          by-ns (into {} (map (fn [p] [#{(:ns-a p) (:ns-b p)} p]) pairs))
          p     (get by-ns #{'gordian.output 'gordian.scan})]
      (is (= 1.0  (max (:confidence-a p) (:confidence-b p))))
      (is (< (Math/abs (- (/ 2.0 3) (min (:confidence-a p) (:confidence-b p)))) 1e-9))))

  (testing "sorted by coupling descending"
    (let [pairs  (:pairs (sut/change-coupling-pairs commits graph 0.01 1))
          values (map :score pairs)]
      (is (= values (sort > values)))))

  (testing "threshold filters pairs below minimum coupling"
    ;; [main output] Jaccard=0.25 excluded at default threshold=0.30
    (let [pairs (:pairs (sut/change-coupling-pairs commits graph 0.30 1))
          ns-sets (set (map (fn [p] #{(:ns-a p) (:ns-b p)}) pairs))]
      (is (not (contains? ns-sets #{'gordian.main 'gordian.output})))))

  (testing "min-co filters pairs with insufficient raw count"
    ;; [main output] co=1 excluded at min-co=2
    (let [pairs (:pairs (sut/change-coupling-pairs commits graph 0.01 2))
          ns-sets (set (map (fn [p] #{(:ns-a p) (:ns-b p)}) pairs))]
      (is (not (contains? ns-sets #{'gordian.main 'gordian.output})))))

  (testing "structural-edge? true when require edge exists"
    ;; graph has scan → main; canonical pair is [main scan]
    (let [pairs  (:pairs (sut/change-coupling-pairs commits graph 0.01 1))
          by-ns  (into {} (map (fn [p] [#{(:ns-a p) (:ns-b p)} p]) pairs))
          p      (get by-ns #{'gordian.main 'gordian.scan})]
      (is (true? (:structural-edge? p)))))

  (testing "structural-edge? false when no require edge"
    (let [pairs  (:pairs (sut/change-coupling-pairs commits graph 0.01 1))
          by-ns  (into {} (map (fn [p] [#{(:ns-a p) (:ns-b p)} p]) pairs))
          p      (get by-ns #{'gordian.output 'gordian.scan})]
      (is (false? (:structural-edge? p)))))

  (testing "namespaces not in graph are excluded from pairs"
    (let [commits-with-ext [{:sha "x" :nss #{'gordian.scan 'external.lib}}
                            {:sha "y" :nss #{'gordian.scan 'external.lib}}]
          pairs (:pairs (sut/change-coupling-pairs commits-with-ext graph 0.01 1))]
      (is (every? #(and (contains? graph (:ns-a %))
                        (contains? graph (:ns-b %)))
                  pairs))))

  (testing "empty commits → empty pairs"
    (let [result (sut/change-coupling-pairs [] graph)]
      (is (empty? (:pairs result)))
      (is (zero? (:candidate-count result)))))

  (testing "threshold 1.0 excludes all non-identical pairs"
    (is (empty? (:pairs (sut/change-coupling-pairs commits graph 1.0 1))))))
