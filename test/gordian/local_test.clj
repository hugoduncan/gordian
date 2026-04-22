(ns gordian.local-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.local.units :as units]
            [gordian.local.evidence :as evidence]
            [gordian.local.burden :as burden]
            [gordian.local.findings :as findings]
            [gordian.local.report :as report]
            [gordian.scan :as scan]))

(deftest analyzable-units-test
  (let [tmp (java.io.File/createTempFile "gordian-local" ".clj")
        source "(ns sample.local)\n(defn a [x] (let [y (inc x)] y))\n(defn b\n  ([x] (if x {:ok true} nil))\n  ([x y] (-> x (assoc :y y))))\n(defmethod render :html [x] (case x :a {:kind :a} {:kind :other}))\n"
        _ (spit tmp source)
        file (assoc (scan/parse-file-all-forms (.getPath tmp)) :origin :src)
        extracted (units/analyzable-units file)]
    (is (= 4 (count extracted)))
    (is (= 2 (count (filter #(= 'b (:var %)) extracted))))
    (is (= 1 (count (filter #(= :defmethod (:kind %)) extracted))))
    (is (= :html (:dispatch (first (filter #(= :defmethod (:kind %)) extracted)))))
    (is (every? :line extracted))
    (.delete tmp)))

(deftest evidence-extraction-test
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[x]
              :body '[(let [{:keys [a b c d]} x
                         y (assoc {:a a} :b b)
                         y (if (and a (not (nil? b)))
                             (assoc y :c c)
                             nil)]
                     (println y)
                     (swap! state assoc :value y)
                     (if-let [z (helper y)]
                       (case (:kind z)
                         :ok {:result z}
                         {:error true})
                       {:error false}))]} 
        ev (:evidence (evidence/extract-evidence unit))]
    (is (pos? (get-in ev [:flow :branch])))
    (is (pos? (get-in ev [:flow :logic])))
    (is (pos? (get-in ev [:state :mutation-sites])))
    (is (pos? (get-in ev [:shape :destructure])))
    (is (pos? (get-in ev [:dependency :helpers])))
    (is (seq (:program-points ev)))))

(deftest burden-scoring-test
  (let [scored (burden/score-unit
                {:evidence {:flow {:branch 3 :nest 2 :interrupt 1 :recursion 1 :logic 1.5 :max-depth 3}
                            :state {:mutation-sites 1 :rebindings 2 :temporal-dependencies 1 :effect-reads 1 :effect-writes 1}
                            :shape {:transitions 3 :destructure 1.0 :variant 1 :nesting 1 :sentinel 1}
                            :abstraction {:levels [:domain :mechanism :data-shaping :domain]
                                          :distinct-levels #{:domain :mechanism :data-shaping}
                                          :incidental 1}
                            :dependency {:helpers 2 :opaque-stages 4 :inversion 1 :semantic-jumps 3}
                            :program-points [{:bindings #{'x 'y}
                                              :shape-bindings 1
                                              :mutable-count 1
                                              :opaque-count 1
                                              :active-predicates 1}
                                             {:bindings #{'x 'y 'z}
                                              :shape-bindings 2
                                              :mutable-count 1
                                              :opaque-count 2
                                              :active-predicates 2}]}})]
    (is (= 8.5 (:flow-burden scored)))
    (is (= 8 (:state-burden scored)))
    (is (= 8.0 (:shape-burden scored)))
    (is (= 7 (:abstraction-burden scored)))
    (is (= 8 (:dependency-burden scored)))
    (is (= 10 (:peak (:working-set scored))))
    (is (pos? (:lcc-total scored)))
    (is (= :very-high (get-in scored [:lcc-severity :level])))))

(deftest findings-test
  (let [unit {:flow-burden 8.5
              :state-burden 8
              :shape-burden 8.0
              :abstraction-burden 7
              :dependency-burden 6
              :working-set {:peak 8 :avg 6.0 :burden 5.5}
              :evidence {:flow {:max-depth 3}
                         :state {:mutation-sites 1 :temporal-dependencies 1}
                         :shape {:transitions 3 :variant 1}
                         :abstraction {:distinct-levels #{:domain :mechanism :data-shaping}
                                       :levels [:domain :mechanism :data-shaping :domain :mechanism]}
                         :dependency {:opaque-stages 3 :helpers 2 :semantic-jumps 3}}}
        kinds (set (map :kind (findings/findings-for-unit unit)))]
    (is (contains? kinds :deep-control-nesting))
    (is (contains? kinds :mutable-state-tracking))
    (is (contains? kinds :temporal-coupling))
    (is (contains? kinds :shape-churn))
    (is (contains? kinds :conditional-return-shape))
    (is (contains? kinds :abstraction-mix))
    (is (contains? kinds :opaque-pipeline))
    (is (contains? kinds :helper-chasing))
    (is (contains? kinds :working-set-overload))))

(deftest sort-filter-and-rollup-test
  (let [units [{:ns 'b.core :var 'z :kind :defn-arity :arity 1
                :flow-burden 1 :state-burden 0 :shape-burden 0 :abstraction-burden 1 :dependency-burden 0
                :working-set {:peak 3 :avg 2.0 :burden 0.0} :lcc-total 2.4 :findings []}
               {:ns 'a.core :var 'x :kind :defn-arity :arity 1
                :flow-burden 3 :state-burden 1 :shape-burden 1 :abstraction-burden 2 :dependency-burden 1
                :working-set {:peak 5 :avg 3.5 :burden 1.25} :lcc-total 10.0 :findings []}
               {:ns 'a.core :var 'y :kind :defmethod :dispatch :json
                :flow-burden 2 :state-burden 0 :shape-burden 3 :abstraction-burden 4 :dependency-burden 3
                :working-set {:peak 7 :avg 4.0 :burden 3.5} :lcc-total 18.0 :findings []}]
        mins {:total 10 :abstraction 2}
        rollups (report/namespace-rollups units)]
    (is (= ['a.core 'a.core 'b.core] (mapv :ns (report/sort-units units :ns))))
    (is (= ['y 'x 'z] (mapv :var (report/sort-units units :total))))
    (is (= :total (report/effective-bar-metric {})))
    (is (= :shape (report/effective-bar-metric {:sort :shape})))
    (is (= :working-set (report/effective-bar-metric {:bar :working-set})))
    (is (= [:total 12] (report/parse-min-expression "total=12")))
    (is (= [:abstraction 4] (report/parse-min-expression "abstraction=4")))
    (is (nil? (report/parse-min-expression "bogus=10")))
    (is (nil? (report/parse-min-expression "total=0")))
    (is (= ['x 'y] (mapv :var (report/filter-units-by-mins units mins))))
    (is (= 2 (count (report/truncate-section units 2))))
    (is (= ['a.core 'b.core] (mapv :ns rollups)))
    (is (= 3 (get-in (report/project-rollup units rollups) [:unit-count])))))

(deftest rollup-test
  (let [tmp (java.io.File/createTempFile "gordian-local-rollup" ".clj")
        source "(ns sample.local)\n(defn simple [x] x)\n(defn branchy [x]\n  (let [y (assoc {:x x} :ready true)]\n    (if (helper y)\n      (println y)\n      (swap! state assoc :y y))))\n(defmethod render :html [x] (case x :ok {:kind :ok} {:kind :error}))\n"
        _ (spit tmp source)
        file (assoc (scan/parse-file-all-forms (.getPath tmp)) :origin :src)
        result (report/rollup [file])]
    (is (= :local (:gordian/command result)))
    (is (= :local-comprehension-complexity (:metric result)))
    (is (= 3 (count (:units result))))
    (is (every? :lcc-total (:units result)))
    (is (every? :working-set (:units result)))
    (is (= ['sample.local] (mapv :ns (:namespace-rollups result))))
    (is (= 3 (get-in result [:project-rollup :unit-count])))
    (is (= 1 (get-in result [:project-rollup :namespace-count])))
    (is (pos? (get-in result [:project-rollup :total-lcc])))
    (is (:max-unit result))
    (.delete tmp)))
