(ns gordian.dsm-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.dsm :as sut]))

(def index-blocks #'gordian.dsm/index-blocks)
(def ns->block-id #'gordian.dsm/ns->block-id)
(def collapsed-edges #'gordian.dsm/collapsed-edges)
(def internal-edge-count #'gordian.dsm/internal-edge-count)
(def block-density #'gordian.dsm/block-density)
(def annotate-blocks #'gordian.dsm/annotate-blocks)
(def internal-edge-coords #'gordian.dsm/internal-edge-coords)
(def project-graph #'gordian.dsm/project-graph)
(def reverse-graph #'gordian.dsm/reverse-graph)
(def dfs-topo-order #'gordian.dsm/dfs-topo-order)
(def ordered-nodes #'gordian.dsm/ordered-nodes)
(def index-of #'gordian.dsm/index-of)
(def ordered-edges #'gordian.dsm/ordered-edges)
(def reconstruct-partition #'gordian.dsm/reconstruct-partition)
(def optimal-partition #'gordian.dsm/optimal-partition)
(def block-members #'gordian.dsm/block-members)
(def induced-subgraph #'gordian.dsm/induced-subgraph)
(def block-edge-counts #'gordian.dsm/block-edge-counts)
(def block-summary #'gordian.dsm/block-summary)
(def valid-adjacent-swap? #'gordian.dsm/valid-adjacent-swap?)
(def block-swap-valid? #'gordian.dsm/block-swap-valid?)
(def refine-order #'gordian.dsm/refine-order)
(def partition-cost #'gordian.dsm/partition-cost)
(def should-refine-order? #'gordian.dsm/should-refine-order?)

(deftest index-blocks-test
  (let [blocks (index-blocks [['c] ['a 'b] ['d]])]
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
           (ns->block-id blocks)))))

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
             (collapsed-edges graph blocks)))))

  (testing "multiple namespace edges between same blocks are counted"
    (let [graph  {'a1 #{'c}
                  'a2 #{'c}
                  'c #{}}
          blocks [{:id 0 :members ['c]}
                  {:id 1 :members ['a1 'a2]}]]
      (is (= [{:from 1 :to 0 :edge-count 2}]
             (collapsed-edges graph blocks)))))

  (testing "internal edges inside same SCC are excluded"
    (let [graph  {'a #{'b}
                  'b #{'a 'c}
                  'c #{}}
          blocks [{:id 0 :members ['c]}
                  {:id 1 :members ['a 'b]}]]
      (is (= [{:from 1 :to 0 :edge-count 1}]
             (collapsed-edges graph blocks))))))

(deftest internal-edge-count-test
  (let [graph {'a #{'b 'c}
               'b #{'a}
               'c #{}}
        members ['a 'b]]
    (is (= 2 (internal-edge-count graph members)))))

(deftest block-density-test
  (testing "singleton density is 0.0"
    (is (= 0.0 (block-density {'a #{}} ['a]))))

  (testing "2-node mutual dependency density is 1.0"
    (is (= 1.0 (block-density {'a #{'b}
                               'b #{'a}}
                              ['a 'b]))))

  (testing "sparse 3-node SCC density computed correctly"
    (is (= 0.5 (block-density {'a #{'b}
                               'b #{'c}
                               'c #{'a}}
                              ['a 'b 'c])))))

(deftest annotate-blocks-test
  (let [graph {'a #{'b}
               'b #{'a 'c}
               'c #{}}
        blocks [{:id 0 :members ['c] :size 1}
                {:id 1 :members ['a 'b] :size 2}]
        annotated (annotate-blocks graph blocks)]
    (testing "adds size, cyclic?, internal-edge-count, density"
      (is (= [{:id 0 :members ['c] :size 1 :cyclic? false :internal-edge-count 0 :density 0.0}
              {:id 1 :members ['a 'b] :size 2 :cyclic? true  :internal-edge-count 2 :density 1.0}]
             annotated)))

    (testing "preserves member ordering"
      (is (= [['c] ['a 'b]]
             (mapv :members annotated))))))

(deftest internal-edge-coords-test
  (testing "2-node mutual dep returns both local coordinates"
    (is (= [[0 1] [1 0]]
           (internal-edge-coords {'a #{'b}
                                  'b #{'a}}
                                 ['a 'b]))))

  (testing "omits non-existent edges"
    (is (= [[0 1] [1 2] [2 0]]
           (internal-edge-coords {'a #{'b}
                                  'b #{'c}
                                  'c #{'a}}
                                 ['a 'b 'c])))))

(deftest project-graph-test
  (let [graph {'a #{'b 'ext.lib}
               'b #{'a}
               'c #{'ext.lib}}
        result (project-graph graph)]
    (is (= {'a #{'b}
            'b #{'a}
            'c #{}}
           result))))

(deftest reverse-graph-test
  (is (= {'a #{}
          'b #{'a}
          'c #{'b}}
         (reverse-graph {'a #{'b}
                         'b #{'c}
                         'c #{}}))))

(deftest dfs-topo-order-test
  (testing "returns dependees-first order"
    (is (= ['c 'b 'a]
           (dfs-topo-order {'a #{'b}
                            'b #{'c}
                            'c #{}}))))

  (testing "deterministic under map variation"
    (is (= (dfs-topo-order {'a #{'b}
                            'b #{'c}
                            'c #{}})
           (dfs-topo-order {'c #{}
                            'a #{'b}
                            'b #{'c}}))))

  (testing "lexical tie-break on incomparable nodes"
    (is (= ['myapp.billing.core 'myapp.reporting.core]
           (dfs-topo-order {'myapp.reporting.core #{}
                            'myapp.billing.core #{}})))))

(deftest ordered-nodes-test
  (let [graph {'a #{'b 'ext.lib}
               'b #{'c}
               'c #{}}
        ordered (ordered-nodes graph)]
    (is (= ['c 'b 'a] ordered))
    (is (= #{'a 'b 'c} (set ordered)))
    (is (= 3 (count ordered)))))

(deftest index-of-test
  (is (= {'c 0 'b 1 'a 2}
         (index-of ['c 'b 'a]))))

(deftest ordered-edges-test
  (is (= [[1 0] [2 1]]
         (ordered-edges {'a #{'b}
                         'b #{'c 'ext.lib}
                         'c #{}}
                        ['c 'b 'a]))))

(deftest ordered-edge-stats-test
  (let [edges [[1 0] [2 1] [3 0]]
        stats (#'gordian.dsm/ordered-edge-stats edges 4)]
    (testing "interval stats expose expected counts"
      (is (= 2 (:internal (#'gordian.dsm/interval-stats stats 0 2))))
      (is (= 1 (:internal (#'gordian.dsm/interval-stats stats 0 1))))
      (is (= 0 (:internal (#'gordian.dsm/interval-stats stats 2 3))))
      (is (= 2 (:crossing (#'gordian.dsm/interval-stats stats 0 1))))
      (is (= 1 (:crossing (#'gordian.dsm/interval-stats stats 0 2))))
      (is (= 2 (:crossing (#'gordian.dsm/interval-stats stats 2 3)))))))

(deftest reconstruct-partition-test
  (is (= [[0 1] [2 3]]
         (reconstruct-partition {1 0 3 2} 3)))
  (is (= []
         (reconstruct-partition {} -1))))

(deftest optimal-partition-test
  (testing "empty graph yields empty partition"
    (is (= [] (optimal-partition {} [] 1.5))))

  (testing "singleton graph yields one block"
    (is (= [[0 0]]
           (optimal-partition {'a #{}} ['a] 1.0))))

  (testing "simple chain yields deterministic contiguous partition at low beta"
    (is (= [[0 2]]
           (optimal-partition {'a #{'b}
                               'b #{'c}
                               'c #{}}
                              ['c 'b 'a]
                              0.1))))

  (testing "higher beta splits large weak blocks more aggressively"
    (is (= [[0 0] [1 2]]
           (optimal-partition {'a #{'b}
                               'b #{'c}
                               'c #{}}
                              ['c 'b 'a]
                              1.0))))

  (testing "returned blocks are contiguous, non-overlapping, and cover all nodes"
    (let [part (optimal-partition {'a #{'b}
                                   'b #{'c}
                                   'c #{}
                                   'd #{}}
                                  ['c 'b 'a 'd]
                                  0.1)]
      (is (= [[0 2] [3 3]] part)))))

(deftest block-members-test
  (is (= [['c 'b] ['a]]
         (block-members ['c 'b 'a] [[0 1] [2 2]]))))

(deftest induced-subgraph-test
  (let [graph {'a #{'b 'x}
               'b #{'c}
               'c #{}
               'x #{'a}}]
    (is (= {'a #{'b}
            'b #{'c}
            'c #{}}
           (induced-subgraph graph ['a 'b 'c])))))

(deftest block-edge-counts-test
  (let [graph {'a #{'b}
               'b #{'c}
               'c #{}}
        blocks [{:id 0 :members ['c 'b] :size 2}
                {:id 1 :members ['a] :size 1}]]
    (is (= [{:from 1 :to 0 :edge-count 1}]
           (block-edge-counts graph blocks)))))

(deftest block-summary-test
  (let [blocks [{:id 0 :size 2}
                {:id 1 :size 1}]
        edges [{:from 1 :to 0 :edge-count 1}]
        summary (block-summary blocks edges)]
    (is (= 2 (:block-count summary)))
    (is (= 1 (:singleton-block-count summary)))
    (is (= 2 (:largest-block-size summary)))
    (is (= 1 (:inter-block-edge-count summary)))
    (is (= 0.5 (:density summary)))))

(deftest valid-adjacent-swap-test
  (testing "rejects swap when dependency relation exists"
    (is (false? (valid-adjacent-swap? {'a #{'b}
                                       'b #{'c}
                                       'c #{}}
                                      ['c 'b 'a]
                                      1))))

  (testing "accepts swap for incomparable adjacent nodes"
    (is (true? (valid-adjacent-swap? {'a #{}
                                      'b #{}}
                                     ['a 'b]
                                     0)))))

(deftest block-swap-valid?-test
  (testing "accepts adjacent incomparable blocks"
    (is (true? (block-swap-valid? {'a #{}
                                   'b #{}
                                   'c #{'a}
                                   'd #{}
                                   'e #{'b}
                                   'f #{'a}}
                                  [['a 'c] ['d] ['b 'e] ['f]]
                                  0))))

  (testing "rejects adjacent blocks with dependency relation between members"
    (is (false? (block-swap-valid? {'a #{}
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
          reverse (reverse-graph graph)]
      (is (neg? (#'gordian.dsm/edge-length-delta-after-adjacent-swap
                 graph reverse ordered 1)))))

  (testing "proxy is zero when swapped nodes are structurally irrelevant"
    (let [graph {'a #{}
                 'b #{}
                 'c #{}}
          ordered ['a 'b 'c]
          reverse (reverse-graph graph)]
      (is (= 0 (#'gordian.dsm/edge-length-delta-after-adjacent-swap
                graph reverse ordered 0))))))

(deftest block-edge-length-delta-after-swap-test
  (testing "block proxy prefers swaps that shorten incident edges"
    (let [graph {'a #{}
                 'b #{}
                 'c #{'a}
                 'd #{}
                 'e #{'b}
                 'f #{'a}}
          ordered ['a 'c 'd 'b 'e 'f]
          blocks [['a 'c] ['d] ['b 'e] ['f]]
          reverse (reverse-graph graph)]
      (is (neg? (#'gordian.dsm/block-edge-length-delta-after-swap
                 graph reverse ordered blocks 0)))))

  (testing "block proxy is zero when swapped blocks have no incident edges"
    (let [graph {'a #{}
                 'b #{}
                 'c #{}
                 'd #{}}
          ordered ['a 'b 'c 'd]
          blocks [['a] ['b] ['c] ['d]]
          reverse (reverse-graph graph)]
      (is (= 0 (#'gordian.dsm/block-edge-length-delta-after-swap
                graph reverse ordered blocks 1))))))

(deftest refine-order-test
  (testing "refinement deterministic for same input"
    (let [graph {'a #{} 'b #{} 'c #{}}
          ordered ['a 'b 'c]]
      (is (= (refine-order graph ordered 0.1)
             (refine-order graph ordered 0.1)))))

  (testing "refinement never worsens partition cost"
    (let [graph {'a #{}
                 'b #{}
                 'c #{}}
          ordered ['a 'b 'c]
          refined (refine-order graph ordered 0.1)]
      (is (<= (partition-cost graph refined 0.1)
              (partition-cost graph ordered 0.1)))))

  (testing "block-level refinement can improve order after node refinement stalls"
    (let [graph {'a #{}
                 'b #{}
                 'c #{'a}
                 'd #{}
                 'e #{'b}
                 'f #{'a}}
          ordered ['a 'b 'c 'd 'e 'f]
          refined (refine-order graph ordered 0.1)]
      (is (= ['d 'a 'c 'b 'e 'f] refined))
      (is (< (partition-cost graph refined 0.1)
             (partition-cost graph ['a 'c 'd 'b 'e 'f] 0.1))))))

(deftest should-refine-order-test
  (testing "small graphs are refined"
    (is (true? (should-refine-order? (vec (range 120))))))

  (testing "larger graphs skip refinement for performance"
    (is (false? (should-refine-order? (vec (range 121)))))))

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
