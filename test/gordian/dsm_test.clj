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

(deftest pow-cost-test
  (is (= 4.0 (sut/pow-cost 2 2.0)))
  (is (= 3.0 (sut/pow-cost 3 1.0))))

(deftest block-cost-test
  (let [edges [[1 0] [2 1] [3 0]]]
    (testing "singleton block cost is finite and deterministic"
      (is (= 3.0 (sut/block-cost edges 4 1.0 0 0)))
      (is (= 3.0 (sut/block-cost edges 4 1.0 0 0))))

    (testing "larger blocks pay quadratic size penalty"
      (is (< (sut/block-cost edges 4 1.0 0 1)
             (sut/block-cost edges 4 1.0 0 2))))

    (testing "weakly cohesive sparse blocks pay more than denser ones of same size"
      (let [sparse [[3 0]]
            dense  [[0 1] [1 0] [3 0]]]
        (is (> (sut/block-cost sparse 4 1.0 0 1)
               (sut/block-cost dense 4 1.0 0 1)))))

    (testing "larger beta increases same interval cost when size > 1"
      (is (< (sut/block-cost edges 4 0.5 0 1)
             (sut/block-cost edges 4 2.0 0 1))))))

(deftest ordered-edge-stats-test
  (let [edges [[1 0] [2 1] [3 0]]
        stats (#'gordian.dsm/ordered-edge-stats edges 4)]
    (testing "interval stats match counted helper behavior"
      (doseq [[a b] [[0 0] [0 1] [0 2] [2 3]]]
        (is (= (sut/interval-internal-edge-count edges a b)
               (:internal (#'gordian.dsm/interval-stats stats a b))))
        (is (= (sut/interval-cross-edge-count edges a b 4)
               (:crossing (#'gordian.dsm/interval-stats stats a b))))))))

(deftest reconstruct-partition-test
  (is (= [[0 1] [2 3]]
         (sut/reconstruct-partition {1 0 3 2} 3)))
  (is (= []
         (sut/reconstruct-partition {} -1))))

(deftest optimal-partition-test
  (testing "empty graph yields empty partition"
    (is (= [] (sut/optimal-partition {} [] 1.5))))

  (testing "singleton graph yields one block"
    (is (= [[0 0]]
           (sut/optimal-partition {'a #{}} ['a] 1.0))))

  (testing "simple chain yields deterministic contiguous partition at low beta"
    (is (= [[0 2]]
           (sut/optimal-partition {'a #{'b}
                                   'b #{'c}
                                   'c #{}}
                                  ['c 'b 'a]
                                  0.1))))

  (testing "higher beta splits large weak blocks more aggressively"
    (is (= [[0 0] [1 2]]
           (sut/optimal-partition {'a #{'b}
                                   'b #{'c}
                                   'c #{}}
                                  ['c 'b 'a]
                                  1.0))))

  (testing "returned blocks are contiguous, non-overlapping, and cover all nodes"
    (let [part (sut/optimal-partition {'a #{'b}
                                       'b #{'c}
                                       'c #{}
                                       'd #{}}
                                      ['c 'b 'a 'd]
                                      0.1)]
      (is (= [[0 2] [3 3]] part)))))

(deftest block-members-test
  (is (= [['c 'b] ['a]]
         (sut/block-members ['c 'b 'a] [[0 1] [2 2]]))))

(deftest induced-subgraph-test
  (let [graph {'a #{'b 'x}
               'b #{'c}
               'c #{}
               'x #{'a}}]
    (is (= {'a #{'b}
            'b #{'c}
            'c #{}}
           (sut/induced-subgraph graph ['a 'b 'c])))))

(deftest block-edge-counts-test
  (let [graph {'a #{'b}
               'b #{'c}
               'c #{}}
        blocks [{:id 0 :members ['c 'b] :size 2}
                {:id 1 :members ['a] :size 1}]]
    (is (= [{:from 1 :to 0 :edge-count 1}]
           (sut/block-edge-counts graph blocks)))))

(deftest block-summary-test
  (let [blocks [{:id 0 :size 2}
                {:id 1 :size 1}]
        edges [{:from 1 :to 0 :edge-count 1}]
        summary (sut/block-summary blocks edges)]
    (is (= 2 (:block-count summary)))
    (is (= 1 (:singleton-block-count summary)))
    (is (= 2 (:largest-block-size summary)))
    (is (= 1 (:inter-block-edge-count summary)))
    (is (= 0.5 (:density summary)))))

(deftest transitive-consumers-test
  (let [graph {'a #{'b}
               'b #{'c}
               'c #{}}
        obs   (sut/transitive-consumers graph)]
    (is (= #{'a 'b} (get obs 'c)))
    (is (= #{'a} (get obs 'b)))
    (is (= #{} (get obs 'a)))))

(deftest jaccard-test
  (is (= 1.0 (sut/jaccard #{} #{})))
  (is (= 1.0 (sut/jaccard #{1 2} #{1 2})))
  (is (= 0.0 (sut/jaccard #{1} #{2})))
  (is (= 0.3333333333333333 (sut/jaccard #{1 2} #{2 3}))))

(deftest co-usage-similarity-test
  (let [obs {'a #{'x 'y}
             'b #{'x 'y}
             'c #{'y 'z}}]
    (is (= 1.0 (sut/co-usage-similarity obs 'a 'b)))
    (is (= 0.3333333333333333 (sut/co-usage-similarity obs 'a 'c)))))

(deftest valid-adjacent-swap-test
  (testing "rejects swap when dependency relation exists"
    (is (false? (sut/valid-adjacent-swap? {'a #{'b}
                                           'b #{'c}
                                           'c #{}}
                                          ['c 'b 'a]
                                          1))))

  (testing "accepts swap for incomparable adjacent nodes"
    (is (true? (sut/valid-adjacent-swap? {'a #{}
                                          'b #{}}
                                         ['a 'b]
                                         0)))))

(deftest block-swap-valid?-test
  (testing "accepts adjacent incomparable blocks"
    (is (true? (sut/block-swap-valid? {'a #{}
                                       'b #{}
                                       'c #{'a}
                                       'd #{}
                                       'e #{'b}
                                       'f #{'a}}
                                      [['a 'c] ['d] ['b 'e] ['f]]
                                      0))))

  (testing "rejects adjacent blocks with dependency relation between members"
    (is (false? (sut/block-swap-valid? {'a #{}
                                        'b #{'a}
                                        'c #{}}
                                       [['a] ['b 'c]]
                                       0)))))

(deftest edge-length-delta-after-adjacent-swap-test
  (testing "proxy prefers swaps that shorten incident edges"
    (let [graph {'a #{}
                 'b #{}
                 'c #{'a}
                 'd #{}}
          ordered ['a 'b 'c 'd]
          reverse (sut/reverse-graph graph)]
      (is (neg? (#'gordian.dsm/edge-length-delta-after-adjacent-swap
                 graph reverse ordered 1)))))

  (testing "proxy is zero when swapped nodes are structurally irrelevant"
    (let [graph {'a #{}
                 'b #{}
                 'c #{}}
          ordered ['a 'b 'c]
          reverse (sut/reverse-graph graph)]
      (is (= 0 (#'gordian.dsm/edge-length-delta-after-adjacent-swap
                graph reverse ordered 0))))))

(deftest refine-order-test
  (testing "refinement deterministic for same input"
    (let [graph {'a #{} 'b #{} 'c #{}}
          ordered ['a 'b 'c]]
      (is (= (sut/refine-order graph ordered 0.1)
             (sut/refine-order graph ordered 0.1)))))

  (testing "refinement never worsens partition cost"
    (let [graph {'a #{}
                 'b #{}
                 'c #{}}
          ordered ['a 'b 'c]
          refined (sut/refine-order graph ordered 0.1)]
      (is (<= (sut/partition-cost graph refined 0.1)
              (sut/partition-cost graph ordered 0.1)))))

  (testing "block-level refinement can improve order after node refinement stalls"
    (let [graph {'a #{}
                 'b #{}
                 'c #{'a}
                 'd #{}
                 'e #{'b}
                 'f #{'a}}
          ordered ['a 'b 'c 'd 'e 'f]
          refined (sut/refine-order graph ordered 0.1)]
      (is (= ['d 'a 'c 'b 'e 'f] refined))
      (is (< (sut/partition-cost graph refined 0.1)
             (sut/partition-cost graph ['a 'c 'd 'b 'e 'f] 0.1))))))

(deftest should-refine-order-test
  (testing "small graphs are refined"
    (is (true? (sut/should-refine-order? (vec (range 120))))))

  (testing "larger graphs skip refinement for performance"
    (is (false? (sut/should-refine-order? (vec (range 121)))))))

(deftest dsm-report-test
  (let [graph {'a #{'b}
               'b #{'a 'c}
               'c #{'ext.lib}}
        report (sut/dsm-report graph)]
    (testing "returns basis, ordering, blocks, edges, summary, and details"
      (is (= :diagonal-blocks (:basis report)))
      (is (contains? report :ordering))
      (is (contains? report :blocks))
      (is (contains? report :edges))
      (is (contains? report :summary))
      (is (contains? report :details)))

    (testing "ordering contains project-only ordered nodes"
      (is (= #{'a 'b 'c}
             (set (get-in report [:ordering :nodes]))))
      (is (= 3 (count (get-in report [:ordering :nodes])))))

    (testing "ordering exposes refinement metadata"
      (is (contains? (:ordering report) :refined?)))

    (testing "blocks cover all project namespaces exactly once"
      (is (= #{'a 'b 'c}
             (set (mapcat :members (:blocks report))))))

    (testing "details correspond to block ids"
      (is (= (mapv :id (:blocks report))
             (mapv :id (:details report)))))

    (testing "each block exposes optional recursive decomposition slot"
      (is (every? #(contains? % :subdsm) (:blocks report))))

    (testing "is deterministic for same input graph"
      (is (= report (sut/dsm-report graph))))))

(deftest dsm-report-recursive-test
  (let [graph {'a #{}
               'b #{'a}
               'c #{}
               'd #{'a}
               'e #{'a}
               'f #{}
               'g #{}}
        report (sut/dsm-report graph)]
    (testing "quadratic size penalty with low beta can still keep a coarse sparse block"
      (is (= [['a 'b 'c 'd 'e] ['f] ['g]]
             (mapv :members (:blocks report)))))

    (testing "small sparse blocks do not recurse further"
      (is (every? nil? (map :subdsm (:blocks report)))))))

(deftest profiled-dsm-report-test
  (let [graph {'a #{'b}
               'b #{'c}
               'c #{}}
        {:keys [report profile]} (sut/profiled-dsm-report graph)]
    (is (= :diagonal-blocks (:basis report)))
    (is (map? profile))
    (is (contains? profile :dsm-report))
    (is (contains? profile :ordered-nodes))
    (is (contains? profile :optimal-partition))
    (is (contains? profile :ordering-costs))
    (is (every? #(contains? (val %) :millis) profile))))

(deftest recursive-levels-skip-refinement-test
  (let [graph {'a #{}
               'b #{'a}
               'c #{'a}
               'd #{'b}
               'e #{'b}
               'f #{'c}
               'g #{'c}}
        report (sut/dsm-report graph)]
    (is (every? #(or (nil? (:subdsm %))
                     (false? (get-in % [:subdsm :ordering :refined?])))
                (:blocks report)))))
