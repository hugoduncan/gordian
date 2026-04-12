(ns gordian.communities-test
  (:require [clojure.test :refer [deftest is]]
            [gordian.communities :as sut]))

(def graph
  {'a #{'b}
   'b #{'c}
   'c #{}
   'd #{'e}
   'e #{}})

(def report
  {:graph graph
   :nodes [{:ns 'a} {:ns 'b} {:ns 'c} {:ns 'd} {:ns 'e} {:ns 'solo}]
   :conceptual-pairs [{:ns-a 'a :ns-b 'c :score 0.3 :shared-terms ["x" "y"]}
                      {:ns-a 'd :ns-b 'e :score 0.2 :shared-terms ["z"]}]
   :change-pairs [{:ns-a 'a :ns-b 'b :score 0.4}
                  {:ns-a 'd :ns-b 'e :score 0.35}]})

(deftest canonical-edge-test
  (is (= {:a 'a :b 'b} (sut/canonical-edge 'a 'b)))
  (is (= {:a 'a :b 'b} (sut/canonical-edge 'b 'a))))

(deftest undirected-structural-edges-test
  (let [edges (sut/undirected-structural-edges graph)]
    (is (= 3 (count edges)))
    (is (= #{:structural} (:sources (first edges))))
    (is (= 1.0 (:weight (first edges))))))

(deftest conceptual-edges-test
  (let [edges (sut/conceptual-edges (:conceptual-pairs report) 0.25)]
    (is (= 1 (count edges)))
    (is (= #{:conceptual} (:sources (first edges))))))

(deftest change-edges-test
  (let [edges (sut/change-edges (:change-pairs report) 0.30)]
    (is (= 2 (count edges)))
    (is (= #{:change} (:sources (first edges))))))

(deftest combined-edges-test
  (let [edges (sut/combined-edges report {:conceptual-threshold 0.15 :change-threshold 0.30})
        ab    (first (filter #(and (= 'a (:a %)) (= 'b (:b %))) edges))]
    (is ab)
    (is (> (:weight ab) 1.0))
    (is (= #{:structural :change} (:sources ab)))))

(deftest threshold-edges-test
  (is (= 1 (count (sut/threshold-edges [{:weight 0.2} {:weight 0.8}] 0.5)))))

(deftest adjacency-map-test
  (let [adj (sut/adjacency-map [{:a 'a :b 'b} {:a 'b :b 'c}])]
    (is (= #{'b} (get adj 'a)))
    (is (= #{'a 'c} (get adj 'b)))))

(deftest connected-components-test
  (let [adj {'a #{'b} 'b #{'a} 'd #{'e} 'e #{'d}}
        comps (sut/connected-components adj ['a 'b 'd 'e 'solo])]
    (is (= 3 (count comps)))
    (is (some #(= #{'a 'b} %) comps))
    (is (some #(= #{'d 'e} %) comps))
    (is (some #(= #{'solo} %) comps))))

(deftest community-metrics-test
  (let [edges [{:a 'a :b 'b :weight 1.0}
               {:a 'b :b 'c :weight 1.0}
               {:a 'a :b 'x :weight 0.5}]
        members ['a 'b 'c]
        w (sut/internal-boundary-weight members edges)]
    (is (= 2 (sut/community-edge-count members edges)))
    (is (= 2.0 (:internal-weight w)))
    (is (= 0.5 (:boundary-weight w)))
    (is (= 0.6666666666666666 (sut/community-density members edges)))))

(deftest bridge-namespaces-test
  (let [edges [{:a 'a :b 'x :weight 0.2}
               {:a 'a :b 'y :weight 0.7}
               {:a 'b :b 'z :weight 0.4}]
        bridges (sut/bridge-namespaces ['a 'b] edges)]
    (is (= ['a 'b] bridges))))

(deftest dominant-terms-test
  (is (= ["x" "y"] (sut/dominant-terms report ['a 'c]))))

(deftest community-report-test
  (let [r (sut/community-report report {:lens :structural})]
    (is (= :communities (:gordian/command r)))
    (is (= :structural (:lens r)))
    (is (= 3 (:community-count (:summary r))))
    (is (= 3 (:largest-size (:summary r))))))

(deftest community-report-combined-test
  (let [r (sut/community-report report {:lens :combined})]
    (is (= :combined (:lens r)))
    (is (number? (:threshold r)))
    (is (seq (:communities r)))
    (is (every? #(contains? % :members) (:communities r)))))
