(ns gordian.dsm-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.dsm :as sut]))

(deftest block-label-test
  (testing "singleton block → member name"
    (is (= "foo.core" (sut/block-label ['foo.core]))))

  (testing "multi-member block → common prefix when present"
    (is (= "alpha"
           (sut/block-label ['alpha.beta 'alpha.gamma 'alpha.delta]))))

  (testing "falls back to lexicographically smallest member when no common prefix"
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

(deftest ordered-members-test
  (is (= ['alpha.core 'middle.ns 'zeta.core]
         (sut/ordered-members ['zeta.core 'alpha.core 'middle.ns]))))

(deftest internal-edge-coords-test
  (testing "2-node mutual dep returns both local coordinates"
    (is (= [[0 1] [1 0]]
           (sut/internal-edge-coords {'a #{'b}
                                      'b #{'a}}
                                     ['a 'b]))))

  (testing "omits non-existent edges"
    (is (= [[0 1] [1 2] [2 0]]
           (sut/internal-edge-coords {'a #{'b}
                                      'b #{'c}
                                      'c #{'a}}
                                     ['a 'b 'c])))))

(deftest scc-detail-test
  (let [graph {'a #{'b}
               'b #{'a 'c}
               'c #{}}
        block {:id 1 :members ['b 'a] :size 2}]
    (is (= {:id 1
            :members ['a 'b]
            :size 2
            :internal-edges [[0 1] [1 0]]
            :internal-edge-count 2
            :density 1.0}
           (sut/scc-detail graph block)))))

(deftest scc-details-test
  (let [graph {'a #{'b}
               'b #{'a 'c}
               'c #{}
               'x #{'y}
               'y #{'x}}
        blocks [{:id 0 :members ['c] :size 1}
                {:id 1 :members ['a 'b] :size 2}
                {:id 2 :members ['x 'y] :size 2}]
        details (sut/scc-details graph blocks)]
    (testing "excludes singleton SCCs"
      (is (= [1 2] (mapv :id details))))

    (testing "includes multiple cyclic SCCs in block-id order"
      (is (= [1 2] (mapv :id details))))))

(deftest collapsed-summary-test
  (testing "reports block and singleton/cyclic counts"
    (let [blocks [{:id 0 :size 1 :cyclic? false}
                  {:id 1 :size 2 :cyclic? true}
                  {:id 2 :size 1 :cyclic? false}]
          edges [{:from 2 :to 1 :edge-count 1}
                 {:from 1 :to 0 :edge-count 1}]
          summary (sut/collapsed-summary blocks edges)]
      (is (= 3 (:block-count summary)))
      (is (= 2 (:singleton-block-count summary)))
      (is (= 1 (:cyclic-block-count summary)))
      (is (= 2 (:largest-block-size summary)))
      (is (= 2 (:inter-block-edge-count summary)))
      (is (= (/ 2.0 6.0) (:density summary)))))

  (testing "density is 0.0 for zero or one block"
    (is (= 0.0 (:density (sut/collapsed-summary [] []))))
    (is (= 0.0 (:density (sut/collapsed-summary [{:id 0 :size 1 :cyclic? false}] []))))))

(deftest project-graph-test
  (let [graph {'a #{'b 'ext.lib}
               'b #{'a}
               'c #{'ext.lib}}
        result (sut/project-graph graph)]
    (is (= {'a #{'b}
            'b #{'a}
            'c #{}}
           result))))

(deftest reverse-graph-test
  (is (= {'a #{}
          'b #{'a}
          'c #{'b}}
         (sut/reverse-graph {'a #{'b}
                             'b #{'c}
                             'c #{}}))))

(deftest topo-order-test
  (is (= ['c 'b 'a]
         (sut/topo-order {'a #{'b}
                          'b #{'c}
                          'c #{}}))))

(deftest dfs-topo-order-test
  (testing "returns dependees-first order"
    (is (= ['c 'b 'a]
           (sut/dfs-topo-order {'a #{'b}
                                'b #{'c}
                                'c #{}}))))

  (testing "deterministic under map variation"
    (is (= (sut/dfs-topo-order {'a #{'b}
                                'b #{'c}
                                'c #{}})
           (sut/dfs-topo-order {'c #{}
                                'a #{'b}
                                'b #{'c}}))))

  (testing "lexical tie-break on incomparable nodes"
    (is (= ['myapp.billing.core 'myapp.reporting.core]
           (sut/dfs-topo-order {'myapp.reporting.core #{}
                                'myapp.billing.core #{}})))))

(deftest ordered-nodes-test
  (let [graph {'a #{'b 'ext.lib}
               'b #{'c}
               'c #{}}
        ordered (sut/ordered-nodes graph)]
    (is (= ['c 'b 'a] ordered))
    (is (= #{'a 'b 'c} (set ordered)))
    (is (= 3 (count ordered)))))

(deftest index-of-test
  (is (= {'c 0 'b 1 'a 2}
         (sut/index-of ['c 'b 'a]))))

(deftest ordered-edges-test
  (is (= [[1 0] [2 1]]
         (sut/ordered-edges {'a #{'b}
                             'b #{'c 'ext.lib}
                             'c #{}}
                            ['c 'b 'a]))))

(deftest interval-test
  (is (true? (sut/interval? 1 3 1)))
  (is (true? (sut/interval? 1 3 2)))
  (is (true? (sut/interval? 1 3 3)))
  (is (false? (sut/interval? 1 3 0)))
  (is (false? (sut/interval? 1 3 4))))

(deftest interval-internal-edge-count-test
  (let [edges [[1 0] [2 1] [3 0]]]
    (is (= 2 (sut/interval-internal-edge-count edges 0 2)))
    (is (= 1 (sut/interval-internal-edge-count edges 0 1)))
    (is (= 0 (sut/interval-internal-edge-count edges 2 3)))))

(deftest interval-cross-edge-count-test
  (let [edges [[1 0] [2 1] [3 0]]]
    (is (= 2 (sut/interval-cross-edge-count edges 0 1 4)))
    (is (= 1 (sut/interval-cross-edge-count edges 0 2 4)))
    (is (= 2 (sut/interval-cross-edge-count edges 2 3 4)))
    (is (= 0 (sut/interval-cross-edge-count [] 0 0 1)))))

(deftest dsm-report-test
  (let [graph {'a #{'b}
               'b #{'a 'c}
               'c #{'ext.lib}}
        report (sut/dsm-report graph)]
    (testing "returns collapsed, ordering, and scc-details"
      (is (contains? report :collapsed))
      (is (contains? report :ordering))
      (is (contains? report :scc-details)))

    (testing "ordering contains project-only ordered nodes"
      (is (= ['c 'b 'a]
             (get-in report [:ordering :nodes]))))

    (testing "includes all project SCCs in collapsed blocks"
      (is (= 2 (count (get-in report [:collapsed :blocks])))))

    (testing "excludes external-only dependency nodes from blocks"
      (is (= #{'a 'b 'c}
             (set (mapcat :members (get-in report [:collapsed :blocks]))))))

    (testing "includes only non-singleton SCCs in scc-details"
      (is (= [1] (mapv :id (:scc-details report)))))

    (testing "is deterministic for same input graph"
      (is (= report (sut/dsm-report graph))))))
