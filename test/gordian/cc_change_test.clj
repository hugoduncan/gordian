(ns gordian.cc-change-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.cc-change :as sut]))

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
