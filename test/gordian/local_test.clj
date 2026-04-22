(ns gordian.local-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.local.units :as units]
            [gordian.local.evidence :as evidence]
            [gordian.local.burden :as burden]
            [gordian.local.findings :as findings]
            [gordian.local.report :as report]
            [gordian.local.steps :as steps]
            [gordian.local.dependency :as dependency]
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
    (is (seq (:main-steps ev)))
    (is (seq (:branch-regions ev)))
    (is (pos? (get-in ev [:shape :destructure])))
    (is (pos? (get-in ev [:dependency :semantic-jumps])))
    (is (seq (:program-points ev)))))

(deftest abstraction-oscillation-follows-main-path-steps
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[x]
              :body '[(helper x)
                      (assoc {} :x x)
                      (println x)
                      (helper-2 x)]}
        ev (:evidence (evidence/extract-evidence unit))]
    (is (= [:domain :data-shaping :mechanism :domain]
           (get-in ev [:abstraction :levels])))
    (is (= 7 (burden/abstraction-burden (:abstraction ev))))))

(deftest shape-variant-comes-from-branch-outcome-differences
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[x]
              :body '[(if x {:ok true} nil)
                      (case x :a {:kind :a} {:error true})]}
        ev (:evidence (evidence/extract-evidence unit))]
    (is (= 2 (get-in ev [:shape :variant])))
    (is (= 4 (-> ev :shape :variant (* 2))))))

(deftest working-set-uses-v1-program-point-model
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[input]
              :body '[(let [a {:x 1}
                         b (helper a)
                         c (atom [])]
                     (when (valid? b)
                       (swap! c conj b)
                       (-> b
                           helper-2
                           helper-3)
                       @c))]}
        ev (:evidence (evidence/extract-evidence unit))
        points (:program-points ev)]
    (is (seq points))
    (is (= #{:binding-group :branch-entry}
           (set (map :kind points))))
    (is (every? #(contains? % :live-bindings) points))
    (is (every? #(contains? % :active-predicates) points))
    (is (every? #(contains? % :mutable-entities) points))
    (is (every? #(contains? % :shape-assumptions) points))
    (is (every? #(contains? % :unresolved-semantics) points))))

(deftest dependency-does-not-double-count-opaque-pipeline-stages-as-helpers
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[x]
              :body '[(-> x helper-a helper-b helper-c)]}
        ev (:evidence (evidence/extract-evidence unit))]
    (is (= 3 (get-in ev [:dependency :opaque-stages])))
    (is (= 0 (get-in ev [:dependency :helpers])))
    (is (= 3 (get-in ev [:dependency :semantic-jumps])))
    (is (= 3 (burden/dependency-burden (:dependency ev))))))

(deftest top-level-pipeline-stages-still-produce-main-path-program-points
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[x]
              :body '[(-> x helper-a helper-b helper-c)]}
        ev (:evidence (evidence/extract-evidence unit))]
    (is (= #{:pipeline-stage :main-path-step}
           (set (map :kind (:program-points ev)))))))

(deftest state-distinguishes-local-mutation-from-external-effects
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[]
              :body '[(let [state (atom [])]
                        (swap! state conj :x)
                        @state
                        (swap! global-state conj :y)
                        (println state))]}
        ev (:evidence (evidence/extract-evidence unit))]
    (is (= 1 (get-in ev [:state :mutation-sites])))
    (is (= 2 (get-in ev [:state :effect-writes])))
    (is (= 1 (get-in ev [:state :temporal-dependencies])))))

(deftest branch-variant-edge-cases-test
  (testing "cond counts one variant when branch outcomes differ by nilability"
    (let [unit {:ns 'sample.local
                :var 'f
                :kind :defn-arity
                :arity 1
                :args '[x]
                :body '[(cond
                          (= x :a) {:kind :a}
                          (= x :b) nil
                          :else {:kind :other})]}
          ev (:evidence (evidence/extract-evidence unit))]
      (is (= 1 (get-in ev [:shape :variant])))))

  (testing "condp includes default branch in variant detection"
    (let [unit {:ns 'sample.local
                :var 'f
                :kind :defn-arity
                :arity 1
                :args '[x]
                :body '[(condp = x
                          :a {:kind :a}
                          :b {:kind :b}
                          nil)]}
          ev (:evidence (evidence/extract-evidence unit))]
      (is (= 1 (get-in ev [:shape :variant])))))

  (testing "case detects map keyset differences as one variant region"
    (let [unit {:ns 'sample.local
                :var 'f
                :kind :defn-arity
                :arity 1
                :args '[x]
                :body '[(case x
                          :a {:kind :a :ok true}
                          :b {:kind :b}
                          {:kind :other})]}
          ev (:evidence (evidence/extract-evidence unit))]
      (is (= 1 (get-in ev [:shape :variant]))))))

(deftest sentinel-and-abstraction-edge-cases-test
  (testing "sentinel burden counts sentinel-return branches and direct sentinel comparisons"
    (let [unit {:ns 'sample.local
                :var 'f
                :kind :defn-arity
                :arity 1
                :args '[x]
                :body '[(if (= x :ok) :ok :error)
                        (= x :none)]}
          ev (:evidence (evidence/extract-evidence unit))]
      (is (= 2 (get-in ev [:shape :sentinel])))))

  (testing "branch forms do not count as sentinel-bearing when sentinels appear only in predicates"
    (let [unit {:ns 'sample.local
                :var 'f
                :kind :defn-arity
                :arity 1
                :args '[x]
                :body '[(if (= x :ok)
                          {:status :ready}
                          {:status :waiting})]}
          ev (:evidence (evidence/extract-evidence unit))]
      (is (= 1 (get-in ev [:shape :sentinel])))))

  (testing "shape ops classify as data-shaping while opaque helpers remain domain"
    (let [unit {:ns 'sample.local
                :var 'f
                :kind :defn-arity
                :arity 1
                :args '[x]
                :body '[(assoc {} :x x)
                        (helper x)]}
          ev (:evidence (evidence/extract-evidence unit))]
      (is (= [:data-shaping :domain]
             (get-in ev [:abstraction :levels]))))))

(deftest working-set-point-kinds-and-opaque-boundary-test
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[x]
              :body '[(let [y (helper x)]
                        (cond
                          (valid? y) (-> y helper-a helper-b)
                          :else (helper-c y))) ]}
        ev (:evidence (evidence/extract-evidence unit))
        points (:program-points ev)]
    (is (= #{:binding-group :branch-entry :clause-entry}
           (set (map :kind points))))
    (is (= 0 (get-in ev [:dependency :opaque-stages])))
    (is (= 3 (get-in ev [:dependency :helpers])))))

(deftest branch-local-opaque-chains-count-as-helpers-not-main-path-pipeline-stages
  (let [unit {:ns 'sample.local
              :var 'f
              :kind :defn-arity
              :arity 1
              :args '[x]
              :body '[(if (ready? x)
                        (-> x helper-a helper-b)
                        (helper-c x))]}
        ev (:evidence (evidence/extract-evidence unit))]
    (is (= 0 (get-in ev [:dependency :opaque-stages])))
    (is (= 2 (get-in ev [:dependency :helpers])))
    (is (= 2 (count (filter #(= :pipeline-stage (:kind %))
                            (get-in ev [:main-steps])))))
    (is (= #{:branch-entry}
           (set (map :kind (:program-points ev)))))))

(deftest main-path-steps-carry-explicit-branch-locality
  (let [steps (steps/main-path-steps '[(let [y (helper x)]
                                         (if (ready? y)
                                           (-> y helper-a helper-b)
                                           (helper-c y)))])]
    (is (= [false false true true true]
           (mapv :branch-local? steps)))
    (is (= [0 0 1 1 1]
           (mapv :active-predicates steps)))
    (is (= [false false true true true]
           (mapv dependency/branch-local-step? (assoc steps 0 (assoc (first steps) :active-predicates 99)))))))

(deftest burden-scoring-test
  (let [scored (burden/score-unit
                {:evidence {:flow {:branch 3 :nest 2 :interrupt 1 :recursion 1 :logic 1.5 :max-depth 3}
                            :state {:mutation-sites 1 :rebindings 2 :temporal-dependencies 1 :effect-reads 1 :effect-writes 1 :mutable-entities 1}
                            :shape {:transitions 3 :destructure 1.0 :variant 1 :nesting 1 :sentinel 1}
                            :abstraction {:levels [:domain :mechanism :data-shaping :domain]
                                          :distinct-levels #{:domain :mechanism :data-shaping}
                                          :incidental 1}
                            :dependency {:helpers 2 :opaque-stages 4 :inversion 1 :semantic-jumps 3}
                            :program-points [{:live-bindings #{'x 'y}
                                              :shape-assumptions 1
                                              :mutable-entities 1
                                              :unresolved-semantics 1
                                              :active-predicates 1}
                                             {:live-bindings #{'x 'y 'z}
                                              :shape-assumptions 2
                                              :mutable-entities 1
                                              :unresolved-semantics 2
                                              :active-predicates 2}]}})
        calibration {:transform :log1p-over-scale
                     :weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                     :families {:flow {:scale 4.0}
                                :state {:scale 4.0}
                                :shape {:scale 4.0}
                                :abstraction {:scale 4.0}
                                :dependency {:scale 4.0}
                                :working-set {:scale 4.0}}}
        applied (burden/apply-calibration calibration scored)]
    (is (= 8.5 (:flow-burden scored)))
    (is (= 8 (:state-burden scored)))
    (is (= 8.0 (:shape-burden scored)))
    (is (= 7 (:abstraction-burden scored)))
    (is (= 8 (:dependency-burden scored)))
    (is (= 10 (:peak (:working-set scored))))
    (is (pos? (:lcc-total applied)))
    (is (= #{:flow :state :shape :abstraction :dependency :working-set}
           (set (keys (:normalized-burdens applied)))))
    (is (= :high (get-in applied [:lcc-severity :level])))))

(deftest findings-test
  (let [unit {:flow-burden 8.5
              :state-burden 8
              :shape-burden 8.0
              :abstraction-burden 7
              :dependency-burden 6
              :working-set {:peak 8 :avg 6.0 :burden 5.5}
              :evidence {:flow {:max-depth 3 :logic 2.0}
                         :state {:mutation-sites 1 :temporal-dependencies 1 :mutable-entities 1}
                         :shape {:transitions 3 :variant 1}
                         :abstraction {:distinct-levels #{:domain :mechanism :data-shaping}
                                       :levels [:domain :mechanism :data-shaping :domain :mechanism]}
                         :dependency {:opaque-stages 3 :helpers 2 :semantic-jumps 3}}}
        kinds (set (map :kind (findings/findings-for-unit unit)))]
    (is (contains? kinds :deep-control-nesting))
    (is (contains? kinds :predicate-density))
    (is (contains? kinds :mutable-state-tracking))
    (is (contains? kinds :temporal-coupling))
    (is (contains? kinds :shape-churn))
    (is (contains? kinds :conditional-return-shape))
    (is (contains? kinds :abstraction-mix))
    (is (contains? kinds :opaque-pipeline))
    (is (contains? kinds :helper-chasing))
    (is (contains? kinds :working-set-overload))))

(deftest sort-filter-and-rollup-test
  (let [units [{:ns 'b.core :var 'z :kind :defn-arity :arity 1 :line 10
                :flow-burden 1 :state-burden 0 :shape-burden 0 :abstraction-burden 1 :dependency-burden 0
                :working-set {:peak 3 :avg 2.0 :burden 0.0}
                :normalized-burdens {:flow 0.69 :state 0.0 :shape 0.0 :abstraction 0.69 :dependency 0.0 :working-set 0.0}
                :lcc-total 2.4 :findings []}
               {:ns 'a.core :var 'x :kind :defn-arity :arity 1 :line 20
                :flow-burden 3 :state-burden 1 :shape-burden 1 :abstraction-burden 2 :dependency-burden 1
                :working-set {:peak 5 :avg 3.5 :burden 1.25}
                :normalized-burdens {:flow 1.38 :state 0.69 :shape 0.69 :abstraction 1.10 :dependency 0.69 :working-set 0.81}
                :lcc-total 10.0 :findings []}
               {:ns 'a.core :var 'y :kind :defmethod :dispatch :json :line 30
                :flow-burden 2 :state-burden 0 :shape-burden 3 :abstraction-burden 4 :dependency-burden 3
                :working-set {:peak 7 :avg 4.0 :burden 3.5}
                :normalized-burdens {:flow 1.10 :state 0.0 :shape 1.38 :abstraction 1.61 :dependency 1.38 :working-set 1.50}
                :lcc-total 18.0 :findings []}]
        mins {:total 10 :abstraction 2}
        options (report/local-options {:namespace-rollup true :project-rollup false})
        rollups (report/namespace-rollups units)]
    (is (= ['a.core 'a.core 'b.core] (mapv :ns (report/sort-units units :ns))))
    (is (= ['y 'x 'z] (mapv :var (report/sort-units units :total))))
    (is (= ['y 'x 'z] (mapv :var (report/sort-units units :working-set.peak))))
    (is (= :total (report/effective-bar-metric {})))
    (is (= :shape (report/effective-bar-metric {:sort :shape})))
    (is (= :working-set (report/effective-bar-metric {:bar :working-set})))
    (is (= :working-set.peak (report/effective-bar-metric {:bar :working-set.peak})))
    (is (= [:total 12] (report/parse-min-expression "total=12")))
    (is (= [:abstraction 4] (report/parse-min-expression "abstraction=4")))
    (is (= [:working-set.peak 7] (report/parse-min-expression "working-set.peak=7")))
    (is (nil? (report/parse-min-expression "bogus=10")))
    (is (nil? (report/parse-min-expression "ns=10")))
    (is (nil? (report/parse-min-expression "total=0")))
    (is (= 7.0 (report/metric-value (last units) :working-set.peak)))
    (is (= 4.0 (report/metric-value (last units) :working-set.avg)))
    (is (= 1.5 (report/metric-value (last units) :normalized-burdens.working-set)))
    (is (= ['x 'y] (mapv :var (report/filter-units-by-mins units mins))))
    (is (= ['y] (mapv :var (report/filter-units-by-mins units {:working-set.peak 6}))))
    (is (= {:sort nil :top nil :bar nil :namespace-rollup true :project-rollup false :mins nil} options))
    (is (= 2 (count (report/truncate-section units 2))))
    (is (= ['a.core 'b.core] (mapv :ns rollups)))
    (is (= 3 (get-in (report/project-rollup units rollups) [:unit-count])))
    (is (report/valid-sort-key? :working-set.peak))
    (is (report/valid-sort-key? :normalized-burdens.working-set))
    (is (not (report/valid-sort-key? :bogus)))
    (is (report/valid-bar-metric? :working-set.avg))
    (is (not (report/valid-bar-metric? :ns)))))

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
    (is (every? :normalized-burdens (:units result)))
    (is (= :log1p-over-scale (get-in result [:calibration :transform])))
    (is (= ['sample.local] (mapv :ns (:namespace-rollups result))))
    (is (= 3 (get-in result [:project-rollup :unit-count])))
    (is (= 1 (get-in result [:project-rollup :namespace-count])))
    (is (pos? (get-in result [:project-rollup :total-lcc])))
    (is (:max-unit result))
    (.delete tmp)))

(deftest finalize-report-separates-canonical-and-display-bases
  (let [units [{:ns 'b.core :var 'z :kind :defn-arity :arity 1
                :flow-burden 1 :state-burden 0 :shape-burden 0 :abstraction-burden 1 :dependency-burden 0
                :working-set {:peak 3 :avg 2.0 :burden 0.0}
                :normalized-burdens {:flow 0.69 :state 0.0 :shape 0.0 :abstraction 0.69 :dependency 0.0 :working-set 0.0}
                :lcc-total 1.38 :findings []}
               {:ns 'a.core :var 'x :kind :defn-arity :arity 1
                :flow-burden 3 :state-burden 1 :shape-burden 1 :abstraction-burden 2 :dependency-burden 1
                :working-set {:peak 5 :avg 3.5 :burden 1.25}
                :normalized-burdens {:flow 1.38 :state 0.69 :shape 0.69 :abstraction 1.10 :dependency 0.69 :working-set 0.81}
                :lcc-total 5.36 :findings []}
               {:ns 'a.core :var 'y :kind :defmethod :dispatch :json
                :flow-burden 2 :state-burden 0 :shape-burden 3 :abstraction-burden 4 :dependency-burden 3
                :working-set {:peak 7 :avg 4.0 :burden 3.5}
                :normalized-burdens {:flow 1.10 :state 0.0 :shape 1.38 :abstraction 1.61 :dependency 1.38 :working-set 1.50}
                :lcc-total 6.97 :findings []}]
        report {:gordian/command :local
                :metric :local-comprehension-complexity
                :calibration {:transform :log1p-over-scale
                              :weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                              :families {:flow {:scale 1.0} :state {:scale 1.0} :shape {:scale 1.0}
                                         :abstraction {:scale 1.0} :dependency {:scale 1.0} :working-set {:scale 1.0}}}
                :units units
                :namespace-rollups (report/namespace-rollups units)
                :project-rollup (report/project-rollup units (report/namespace-rollups units))
                :max-unit (report/max-unit units)}
        finalized (report/finalize-report report
                                          :explicit
                                          [{:dir "src" :kind :src}]
                                          {:sort :working-set.peak :top 1 :min ["working-set.avg=4"] :namespace-rollup true})]
    (is (= ['b.core 'a.core 'a.core] (mapv :ns (:units finalized)))
        "canonical units remain intact")
    (is (= ['a.core] (mapv :ns (get-in finalized [:display :units]))))
    (is (= ['y] (mapv :var (get-in finalized [:display :units]))))
    (is (= ['a.core] (mapv :ns (get-in finalized [:display :namespace-rollups]))))
    (is (nil? (:project-rollup finalized)))
    (is (= 'y (get-in finalized [:max-unit :var])))
    (is (= {:working-set.avg 4} (get-in finalized [:options :mins])))
    (is (= :working-set.peak (get-in finalized [:options :sort])))
    (is (= :working-set.peak (:bar-metric finalized)))
    (is (true? (get-in finalized [:options :namespace-rollup])))
    (is (false? (get-in finalized [:options :project-rollup])))))
