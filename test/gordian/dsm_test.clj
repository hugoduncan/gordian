(ns gordian.dsm-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.dsm :as sut]))

(deftest block-label-test
  (testing "singleton SCC → member name"
    (is (= "foo.core" (sut/block-label ['foo.core]))))

  (testing "multi-member SCC → lexicographically smallest member"
    (is (= "alpha.beta"
           (sut/block-label ['zeta.core 'alpha.beta 'middle.ns])))))

(deftest ordered-sccs-test
  (testing "acyclic graph → singleton SCCs in topological order"
    (is (= [['c] ['b] ['a]]
           (sut/ordered-sccs
            {'a #{'b}
             'b #{'c}
             'c #{}}))))

  (testing "graph with one cycle → dependees-first order puts prerequisite singleton before cycle block"
    (let [blocks (sut/ordered-sccs
                  {'a #{'b}
                   'b #{'a 'c}
                   'c #{}})]
      (is (= 2 (count blocks)))
      (is (= ['c] (first blocks)))
      (is (= #{'a 'b} (set (second blocks))))))

  (testing "deterministic despite input map key order variation"
    (let [g1 {'a #{'b}
              'b #{'c}
              'c #{}}
          g2 {'c #{}
              'a #{'b}
              'b #{'c}}]
      (is (= (sut/ordered-sccs g1)
             (sut/ordered-sccs g2))))))

(deftest index-blocks-test
  (let [blocks (sut/index-blocks [['c] ['a 'b] ['d]])]
    (testing "assigns consecutive ids starting at 0"
      (is (= [0 1 2] (mapv :id blocks))))

    (testing "preserves ordered block membership"
      (is (= [['c] ['a 'b] ['d]]
             (mapv :members blocks))))

    (testing "marks block size correctly"
      (is (= [1 2 1] (mapv :size blocks))))

    (testing "singleton vs non-singleton distinguishable by size"
      (is (= [true false true]
             (mapv #(= 1 (:size %)) blocks))))))
