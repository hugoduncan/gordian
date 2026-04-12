(ns gordian.subgraph-test
  (:require [clojure.test :refer [deftest is]]
            [gordian.subgraph :as sut]))

(def graph
  {'gordian.scan  #{'gordian.close 'clojure.set}
   'gordian.close #{'gordian.scan}
   'gordian.main  #{'gordian.scan 'app.main}
   'other.ns      #{'gordian.main}})

(deftest match-prefix-test
  (is (true? (sut/match-prefix? "gordian" 'gordian)))
  (is (true? (sut/match-prefix? "gordian" 'gordian.scan)))
  (is (true? (sut/match-prefix? "psi.agent-session" 'psi.agent-session.db.query)))
  (is (false? (sut/match-prefix? "psi.agent" 'psi.agentx.session)))
  (is (false? (sut/match-prefix? "foo.bar" 'baz.qux))))

(deftest members-by-prefix-test
  (is (= ['gordian.close 'gordian.main 'gordian.scan]
         (sut/members-by-prefix graph "gordian")))
  (is (= ['gordian.scan]
         (sut/members-by-prefix graph "gordian.scan")))
  (is (= [] (sut/members-by-prefix graph "missing"))))

(deftest induced-graph-test
  (is (= {'gordian.scan #{'gordian.close}
          'gordian.close #{'gordian.scan}}
         (sut/induced-graph graph #{'gordian.scan 'gordian.close}))))

(deftest graph-density-test
  (is (= 0.0 (sut/graph-density {})))
  (is (= 0.0 (sut/graph-density {'a #{}})))
  (is (= 0.5 (sut/graph-density {'a #{'b} 'b #{}})))
  (is (= 1.0 (sut/graph-density {'a #{'b} 'b #{'a}}))))

(deftest boundary-edges-test
  (let [b (sut/boundary-edges graph #{'gordian.scan 'gordian.close})]
    (is (= 1 (:incoming-count b)))
    (is (= 1 (:outgoing-count b)))
    (is (= [{:from 'other.ns :to 'gordian.main}]
           (:incoming (sut/boundary-edges graph #{'gordian.main}))))
    (is (= ['clojure.set] (:external-deps b)))))

(deftest pair-membership-test
  (let [m #{'a 'b}]
    (is (= :internal (sut/pair-membership {:ns-a 'a :ns-b 'b} m)))
    (is (= :touching (sut/pair-membership {:ns-a 'a :ns-b 'x} m)))
    (is (nil? (sut/pair-membership {:ns-a 'x :ns-b 'y} m)))))

(deftest filter-pairs-test
  (let [pairs [{:ns-a 'a :ns-b 'b}
               {:ns-a 'a :ns-b 'x}
               {:ns-a 'x :ns-b 'y}]
        m #{'a 'b}]
    (is (= [{:ns-a 'a :ns-b 'b}]
           (sut/filter-pairs pairs m :internal)))
    (is (= [{:ns-a 'a :ns-b 'b}
            {:ns-a 'a :ns-b 'x}]
           (sut/filter-pairs pairs m :touching)))))

(deftest finding-touches-members-test
  (let [m #{'a 'b}]
    (is (true? (sut/finding-touches-members? {:subject {:ns 'a}} m)))
    (is (false? (sut/finding-touches-members? {:subject {:ns 'x}} m)))
    (is (true? (sut/finding-touches-members? {:subject {:ns-a 'a :ns-b 'x}} m)))
    (is (false? (sut/finding-touches-members? {:subject {:ns-a 'x :ns-b 'y}} m)))
    (is (true? (sut/finding-touches-members? {:subject {:members #{'x 'a 'y}}} m)))
    (is (false? (sut/finding-touches-members? {:subject {:members #{'x 'y}}} m)))))

(deftest summary-counts-test
  (let [summary (sut/summary-counts [{:category :hidden-conceptual}
                                     {:category :hidden-conceptual}
                                     {:category :hub}]
                                    [1 2 3] [1 2 3 4 5]
                                    [1] [1 2]
                                    {:incoming-count 2 :outgoing-count 4})]
    (is (= 2 (get-in summary [:finding-counts :hidden-conceptual])))
    (is (= 1 (get-in summary [:finding-counts :hub])))
    (is (= 3 (get-in summary [:pair-counts :internal-conceptual])))
    (is (= 5 (get-in summary [:pair-counts :touching-conceptual])))
    (is (= 2 (get-in summary [:boundary :incoming-count])))))

(deftest subgraph-summary-test
  (let [report {:graph graph
                :nodes [{:ns 'gordian.scan :reach 0.5}
                        {:ns 'gordian.close :reach 0.5}
                        {:ns 'gordian.main :reach 0.9}
                        {:ns 'other.ns :reach 0.2}]
                :conceptual-pairs [{:ns-a 'gordian.scan :ns-b 'gordian.close :score 0.3}
                                   {:ns-a 'gordian.main :ns-b 'other.ns :score 0.2}]
                :change-pairs [{:ns-a 'gordian.main :ns-b 'other.ns :score 0.4}]
                :findings [{:severity :medium :category :hidden-conceptual
                            :subject {:ns-a 'gordian.scan :ns-b 'gordian.close}}
                           {:severity :low :category :hub
                            :subject {:ns 'gordian.main}}
                           {:severity :low :category :hub
                            :subject {:ns 'other.ns}}]
                :rank-by :actionability}
        sg (sut/subgraph-summary report "gordian")]
    (is (= :subgraph (:gordian/command sg)))
    (is (= "gordian" (:prefix sg)))
    (is (= 3 (count (:members sg))))
    (is (= 3 (get-in sg [:internal :node-count])))
    (is (= 3 (count (get-in sg [:internal :nodes]))))
    (is (= 2 (count (get-in sg [:pairs :conceptual :touching]))))
    (is (= 1 (count (get-in sg [:pairs :conceptual :internal]))))
    (is (= 2 (count (:findings sg))))
    (is (= :actionability (:rank-by sg)))))

(deftest subgraph-summary-empty-test
  (let [sg (sut/subgraph-summary {:graph graph} "missing")]
    (is (contains? sg :error))))
