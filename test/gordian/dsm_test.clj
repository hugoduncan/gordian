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

(deftest ns->block-id-test
  (let [blocks [{:id 0 :members ['c]}
                {:id 1 :members ['a 'b]}
                {:id 2 :members ['d]}]]
    (is (= {'c 0 'a 1 'b 1 'd 2}
           (sut/ns->block-id blocks)))))

(deftest collapsed-edges-test
  (testing "simple chain gives two block edges"
    (let [graph  {'a #{'b}
                  'b #{'c}
                  'c #{}}
          blocks [{:id 0 :members ['c]}
                  {:id 1 :members ['b]}
                  {:id 2 :members ['a]}]]
      (is (= [{:from 1 :to 0 :edge-count 1}
              {:from 2 :to 1 :edge-count 1}]
             (sut/collapsed-edges graph blocks)))))

  (testing "multiple namespace edges between same blocks are counted"
    (let [graph  {'a1 #{'c}
                  'a2 #{'c}
                  'c #{}}
          blocks [{:id 0 :members ['c]}
                  {:id 1 :members ['a1 'a2]}]]
      (is (= [{:from 1 :to 0 :edge-count 2}]
             (sut/collapsed-edges graph blocks)))))

  (testing "internal edges inside same SCC are excluded"
    (let [graph  {'a #{'b}
                  'b #{'a 'c}
                  'c #{}}
          blocks [{:id 0 :members ['c]}
                  {:id 1 :members ['a 'b]}]]
      (is (= [{:from 1 :to 0 :edge-count 1}]
             (sut/collapsed-edges graph blocks))))))

(deftest internal-edge-count-test
  (let [graph {'a #{'b 'c}
               'b #{'a}
               'c #{}}
        members ['a 'b]]
    (is (= 2 (sut/internal-edge-count graph members)))))

(deftest block-density-test
  (testing "singleton density is 0.0"
    (is (= 0.0 (sut/block-density {'a #{}} ['a]))))

  (testing "2-node mutual dependency density is 1.0"
    (is (= 1.0 (sut/block-density {'a #{'b}
                                   'b #{'a}}
                                  ['a 'b]))))

  (testing "sparse 3-node SCC density computed correctly"
    (is (= 0.5 (sut/block-density {'a #{'b}
                                   'b #{'c}
                                   'c #{'a}}
                                  ['a 'b 'c])))))

(deftest annotate-blocks-test
  (let [graph {'a #{'b}
               'b #{'a 'c}
               'c #{}}
        blocks [{:id 0 :members ['c] :size 1}
                {:id 1 :members ['a 'b] :size 2}]
        annotated (sut/annotate-blocks graph blocks)]
    (testing "adds size, cyclic?, internal-edge-count, density"
      (is (= [{:id 0 :members ['c] :size 1 :cyclic? false :internal-edge-count 0 :density 0.0}
              {:id 1 :members ['a 'b] :size 2 :cyclic? true  :internal-edge-count 2 :density 1.0}]
             annotated)))

    (testing "preserves member ordering"
      (is (= [['c] ['a 'b]]
             (mapv :members annotated))))))
