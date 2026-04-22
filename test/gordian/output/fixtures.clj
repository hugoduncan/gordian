(ns gordian.output.fixtures)

(def fixture-report
  "Unified report matching the fixture graph (alpha←beta←gamma, no cycles)."
  {:src-dirs         ["resources/fixture"]
   :propagation-cost (/ 3.0 9.0)
   :cycles           []
   :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
            :ca 0 :ce 2 :instability 1.0  :role :peripheral}
           {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)
            :ca 1 :ce 1 :instability 0.5  :role :shared}
           {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)
            :ca 2 :ce 0 :instability 0.0  :role :core}]})

(def sample-pairs
  [{:ns-a 'gordian.output  :ns-b 'gordian.json   :score 0.54 :kind :conceptual
    :structural-edge? false :shared-terms ["report" "format" "lines"]}
   {:ns-a 'gordian.classify :ns-b 'gordian.scc   :score 0.48 :kind :conceptual
    :structural-edge? true  :shared-terms ["cycle" "node"]}])

(def change-pairs
  [{:ns-a 'gordian.output :ns-b 'gordian.main
    :score 0.6667 :kind :change :co-changes 2
    :confidence-a 0.6667 :confidence-b 1.0
    :structural-edge? true}
   {:ns-a 'gordian.scan :ns-b 'gordian.output
    :score 0.5 :kind :change :co-changes 2
    :confidence-a 0.6667 :confidence-b 0.6667
    :structural-edge? false}])

(def dsm-data
  {:src-dirs ["resources/fixture"]
   :ordering {:strategy :dfs-topo :refined? false :alpha 2.0 :beta 0.05 :nodes ['c 'a 'b]}
   :blocks [{:id 0 :members ['c] :size 1 :internal-edge-count 0 :density 0.0}
            {:id 1 :members ['a 'b] :size 2 :internal-edge-count 2 :density 1.0}]
   :edges [{:from 1 :to 0 :edge-count 1}]
   :summary {:block-count 2
             :singleton-block-count 1
             :largest-block-size 2
             :inter-block-edge-count 1
             :density 0.5}
   :details [{:id 1
              :members ['a 'b]
              :size 2
              :internal-edges [[0 1] [1 0]]
              :internal-edge-count 2
              :density 1.0}]})

(def diagnose-health
  {:propagation-cost 0.055 :health :healthy :cycle-count 0 :ns-count 14})

(def diagnose-findings
  [{:severity :high
    :category :cross-lens-hidden
    :subject {:ns-a 'a.core :ns-b 'b.util}
    :reason "hidden in 2 lenses — conceptual=0.35 change=0.40"
    :evidence {:conceptual-score 0.35 :shared-terms ["reach" "close"]
               :change-score 0.40 :co-changes 4
               :confidence-a 0.8 :confidence-b 0.6}}
   {:severity :medium
    :category :hidden-conceptual
    :subject {:ns-a 'c.scan :ns-b 'd.git}
    :reason "hidden conceptual coupling — score=0.25"
    :evidence {:score 0.25 :shared-terms ["file" "path"]}}
   {:severity :low
    :category :hub
    :subject {:ns 'e.main}
    :reason "high-reach hub — 92.9% of project reachable"
    :evidence {:reach 0.929 :ce 13 :instability 1.0 :role :peripheral}}])

(def explain-ns-data
  {:ns 'gordian.scan
   :metrics {:reach 0.0 :fan-in 0.06 :ca 1 :ce 0 :instability 0.0 :role :core}
   :direct-deps {:project [] :external ['babashka.fs 'edamame.core]}
   :direct-dependents ['gordian.main]
   :conceptual-pairs
   [{:ns-a 'gordian.conceptual :ns-b 'gordian.scan :score 0.30
     :kind :conceptual :structural-edge? false :shared-terms ["term" "extract"]}]
   :change-pairs []
   :cycles []})

(def explain-pair-data
  {:ns-a 'gordian.aggregate :ns-b 'gordian.close
   :structural {:direct-edge? false :direction nil :shortest-path nil}
   :conceptual {:ns-a 'gordian.aggregate :ns-b 'gordian.close :score 0.37
                :kind :conceptual :structural-edge? false
                :shared-terms ["reach" "transitive" "node"]}
   :change nil
   :finding {:severity :medium :category :hidden-conceptual
             :reason "hidden conceptual coupling — score=0.37"}})

(def compare-diff
  {:gordian/command  :compare
   :before           {:gordian/version "0.2.0" :src-dirs ["src/"]}
   :after            {:gordian/version "0.2.0" :src-dirs ["src/"]}
   :health           {:before {:propagation-cost 0.21 :cycle-count 2 :ns-count 10}
                      :after  {:propagation-cost 0.15 :cycle-count 0 :ns-count 11}
                      :delta  {:propagation-cost -0.06 :cycle-count -2 :ns-count 1}}
   :nodes            {:added   [{:ns 'd.new :metrics {:reach 0.05 :role :peripheral}}]
                      :removed [{:ns 'c.old :metrics {:reach 0.10 :role :core}}]
                      :changed [{:ns 'a.core
                                 :before {:reach 0.30 :instability 0.40 :role :shared}
                                 :after  {:reach 0.25 :instability 0.33 :role :core}
                                 :delta  {:reach -0.05 :instability -0.07
                                          :role {:before :shared :after :core}}}]}
   :cycles           {:added [] :removed [#{:x :y}]}
   :conceptual-pairs {:added   [{:ns-a 'a.core :ns-b 'd.new :score 0.28}]
                      :removed [{:ns-a 'a.core :ns-b 'c.old :score 0.35}]
                      :changed [{:ns-a 'a.core :ns-b 'b.core
                                 :before {:score 0.45} :after {:score 0.32}
                                 :delta {:score -0.13}}]}
   :change-pairs     {:added [] :removed [] :changed []}
   :findings         {:added   [{:severity :medium :category :hidden-conceptual
                                 :subject {:ns-a 'a.core :ns-b 'd.new}
                                 :reason "hidden conceptual coupling — score=0.28"}]
                      :removed [{:severity :high :category :cycle
                                 :subject {:members #{:x :y}}
                                 :reason "2-namespace cycle"}]}})

(def gate-result-data
  {:gordian/command :gate
   :baseline-file "baseline.edn"
   :result :fail
   :src-dirs ["src/"]
   :checks [{:name :pc-delta :status :pass :actual 0.004 :limit 0.01}
            {:name :new-cycles :status :fail :actual 1 :limit 0}
            {:name :new-high-findings :status :pass :actual 0 :limit 0}]
   :summary {:passed 2 :failed 1 :total 3}
   :warnings [{:kind :src-dirs-mismatch :baseline ["src/"] :current ["src/" "test/"]}]})

(def subgraph-data
  {:gordian/command :subgraph
   :prefix "gordian"
   :rank-by :actionability
   :members ['gordian.close 'gordian.main 'gordian.scan]
   :internal {:node-count 3
              :edge-count 3
              :density 0.5
              :propagation-cost 0.33
              :cycles []}
   :boundary {:incoming-count 1
              :outgoing-count 2
              :dependents ['other.ns]
              :external-deps ['app.main 'clojure.set]}
   :pairs {:conceptual {:internal [{:ns-a 'gordian.scan :ns-b 'gordian.close
                                    :score 0.40 :actionability-score 8.8}]}
           :change {:internal []}}
   :findings [{:severity :medium
               :category :hidden-conceptual
               :subject {:ns-a 'gordian.scan :ns-b 'gordian.close}
               :reason "hidden conceptual coupling"
               :actionability-score 8.8
               :evidence {:score 0.40 :same-family? false}}]
   :clusters []})

(def communities-data
  {:gordian/command :communities
   :lens :combined
   :threshold 0.75
   :communities [{:id 1
                  :members ['a 'b 'c]
                  :size 3
                  :density 0.67
                  :internal-weight 2.40
                  :boundary-weight 0.50
                  :dominant-terms ["state" "event"]
                  :bridge-namespaces ['b]}
                 {:id 2
                  :members ['x]
                  :size 1
                  :density 0.0
                  :internal-weight 0.0
                  :boundary-weight 0.0
                  :dominant-terms []
                  :bridge-namespaces []}]
   :summary {:community-count 2 :largest-size 3 :singleton-count 1}})

(def tests-data
  {:gordian/command :tests
   :summary {:src-count 3
             :test-count 3
             :test-role-counts {:executable 2 :support 1}
             :test-style-counts {:unit-ish 1 :integration-ish 1 :support 1}
             :pc-src 0.10
             :pc-with-tests 0.22
             :pc-delta 0.12
             :src->test-edge-count 1
             :test-support-leaked-to-src-count 1
             :executable-tests-with-incoming-deps-count 1}
   :invariants {:shared-test-support [{:ns 'app.test-support}]}
   :core-coverage {:tested-core [{:ns 'app.core}]
                   :untested-core [{:ns 'app.db}]}
   :pc-summary {:interpretation :over-coupled}
   :test-namespaces [{:ns 'app.core-test
                      :test-role :executable
                      :test-style :unit-ish
                      :reach 0.05
                      :ca 0
                      :ce 2
                      :role :isolated}
                     {:ns 'app.integration-test
                      :test-role :executable
                      :test-style :integration-ish
                      :reach 0.55
                      :ca 0
                      :ce 1
                      :role :peripheral}
                     {:ns 'app.test-support
                      :test-role :support
                      :test-style :support
                      :reach 0.0
                      :ca 1
                      :ce 0
                      :role :core}]
   :findings [{:severity :high
               :category :src-depends-on-test
               :subject {:ns 'app.core}
               :reason "production namespace depends on test namespace app.test-support"
               :evidence {:src 'app.core :test 'app.test-support :test-role :support}}
              {:severity :medium
               :category :untested-core
               :subject {:ns 'app.db}
               :reason "core namespace gains no direct test dependents"
               :evidence {:ns 'app.db :role :core :ca-src 1 :ca-with-tests 1 :ca-delta 0}}]})

(def cyclomatic-data
  {:gordian/command :complexity
   :metrics [:cyclomatic-complexity :lines-of-code]
   :src-dirs ["resources/fixture"]
   :options {:sort :cc :bar nil :mins nil}
   :bar-metric :cc
   :units [{:ns 'sample.core :var 'branchy :arity 1 :cc 3 :loc 8 :cc-decision-count 2 :cc-risk {:level :simple :label "Simple, low risk"}}
           {:ns 'sample.core :var 'simple :arity 1 :cc 1 :loc 3 :cc-decision-count 0 :cc-risk {:level :simple :label "Simple, low risk"}}
           {:ns 'sample.util :var 'helper :arity 1 :cc 3 :loc 5 :cc-decision-count 2 :cc-risk {:level :simple :label "Simple, low risk"}}]
   :namespace-rollups [{:ns 'sample.core
                        :unit-count 2
                        :total-cc 4
                        :avg-cc 2.0
                        :max-cc 3
                        :cc-risk-counts {:simple 2 :moderate 0 :high 0 :untestable 0}
                        :total-loc 11
                        :avg-loc 5.5
                        :max-loc 8}
                       {:ns 'sample.util
                        :unit-count 1
                        :total-cc 3
                        :avg-cc 3.0
                        :max-cc 3
                        :cc-risk-counts {:simple 1 :moderate 0 :high 0 :untestable 0}
                        :total-loc 5
                        :avg-loc 5.0
                        :max-loc 5}]
   :project-rollup {:unit-count 3
                    :namespace-count 2
                    :total-cc 7
                    :avg-cc (/ 7.0 3)
                    :max-cc 3
                    :cc-risk-counts {:simple 3 :moderate 0 :high 0 :untestable 0}
                    :total-loc 16
                    :avg-loc (/ 16.0 3)
                    :max-loc 8}
   :max-unit {:ns 'sample.core :var 'branchy :arity 1 :cc 3 :loc 8}})

(def local-data
  {:gordian/command :local
   :metric :local-comprehension-complexity
   :src-dirs ["resources/fixture"]
   :options {:sort :total :bar nil :mins nil}
   :bar-metric :total
   :calibration {:transform :log1p-over-scale
                 :scale-rule :p75-non-zero-with-sparse-median-fallback
                 :weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                 :families {:flow {:scale 2.0 :non-zero-count 3 :sample-count 3}
                            :state {:scale 1.0 :non-zero-count 1 :sample-count 3}
                            :shape {:scale 1.0 :non-zero-count 2 :sample-count 3}
                            :abstraction {:scale 2.0 :non-zero-count 3 :sample-count 3}
                            :dependency {:scale 2.0 :non-zero-count 2 :sample-count 3}
                            :working-set {:scale 1.25 :non-zero-count 3 :sample-count 3}}}
   :units [{:ns 'sample.core
            :var 'branchy
            :kind :defn-arity
            :arity 1
            :flow-burden 3.0
            :state-burden 1.0
            :shape-burden 2.0
            :abstraction-burden 4.0
            :dependency-burden 2.0
            :working-set {:peak 6 :avg 4.0 :burden 2.5}
            :normalized-burdens {:flow 0.9162907318741551
                                 :state 0.6931471805599453
                                 :shape 1.0986122886681096
                                 :abstraction 1.0986122886681096
                                 :dependency 0.6931471805599453
                                 :working-set 1.0986122886681096}
            :lcc-calibration {:weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                              :transform :log1p-over-scale}
            :lcc-total 5.6
            :findings [{:kind :abstraction-oscillation}
                       {:kind :working-set-overload}]}
           {:ns 'sample.core
            :var 'simple
            :kind :defn-arity
            :arity 1
            :flow-burden 1.0
            :state-burden 0.0
            :shape-burden 0.0
            :abstraction-burden 1.0
            :dependency-burden 0.0
            :working-set {:peak 3 :avg 2.0 :burden 1.0}
            :normalized-burdens {:flow 0.4054651081081644
                                 :state 0.0
                                 :shape 0.0
                                 :abstraction 0.4054651081081644
                                 :dependency 0.0
                                 :working-set 0.5877866649021191}
            :lcc-calibration {:weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                              :transform :log1p-over-scale}
            :lcc-total 1.4
            :findings []}
           {:ns 'sample.util
            :var 'helper
            :kind :defmethod
            :dispatch :html
            :flow-burden 2.0
            :state-burden 0.0
            :shape-burden 1.0
            :abstraction-burden 2.0
            :dependency-burden 3.0
            :working-set {:peak 5 :avg 3.5 :burden 1.25}
            :normalized-burdens {:flow 0.6931471805599453
                                 :state 0.0
                                 :shape 0.6931471805599453
                                 :abstraction 0.6931471805599453
                                 :dependency 0.9162907318741551
                                 :working-set 0.6931471805599453}
            :lcc-calibration {:weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                              :transform :log1p-over-scale}
            :lcc-total 3.7
            :findings [{:kind :helper-chasing}]}]
   :namespace-rollups [{:ns 'sample.core
                        :unit-count 2
                        :total-lcc 7.0
                        :avg-lcc 3.5
                        :max-lcc 5.6
                        :avg-flow 2.0
                        :avg-state 0.5
                        :avg-shape 1.0
                        :avg-abstraction 2.5
                        :avg-dependency 1.0
                        :avg-working-set 1.75}
                       {:ns 'sample.util
                        :unit-count 1
                        :total-lcc 3.7
                        :avg-lcc 3.7
                        :max-lcc 3.7
                        :avg-flow 2.0
                        :avg-state 0.0
                        :avg-shape 1.0
                        :avg-abstraction 2.0
                        :avg-dependency 3.0
                        :avg-working-set 1.25}]
   :display {:units [{:ns 'sample.core
                      :var 'branchy
                      :kind :defn-arity
                      :arity 1
                      :flow-burden 3.0
                      :state-burden 1.0
                      :shape-burden 2.0
                      :abstraction-burden 4.0
                      :dependency-burden 2.0
                      :working-set {:peak 6 :avg 4.0 :burden 2.5}
                      :normalized-burdens {:flow 0.9162907318741551
                                           :state 0.6931471805599453
                                           :shape 1.0986122886681096
                                           :abstraction 1.0986122886681096
                                           :dependency 0.6931471805599453
                                           :working-set 1.0986122886681096}
                      :lcc-calibration {:weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                                        :transform :log1p-over-scale}
                      :lcc-total 5.6
                      :findings [{:kind :abstraction-oscillation}
                                 {:kind :working-set-overload}]}
                     {:ns 'sample.core
                      :var 'simple
                      :kind :defn-arity
                      :arity 1
                      :flow-burden 1.0
                      :state-burden 0.0
                      :shape-burden 0.0
                      :abstraction-burden 1.0
                      :dependency-burden 0.0
                      :working-set {:peak 3 :avg 2.0 :burden 1.0}
                      :normalized-burdens {:flow 0.4054651081081644
                                           :state 0.0
                                           :shape 0.0
                                           :abstraction 0.4054651081081644
                                           :dependency 0.0
                                           :working-set 0.5877866649021191}
                      :lcc-calibration {:weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                                        :transform :log1p-over-scale}
                      :lcc-total 1.4
                      :findings []}
                     {:ns 'sample.util
                      :var 'helper
                      :kind :defmethod
                      :dispatch :html
                      :flow-burden 2.0
                      :state-burden 0.0
                      :shape-burden 1.0
                      :abstraction-burden 2.0
                      :dependency-burden 3.0
                      :working-set {:peak 5 :avg 3.5 :burden 1.25}
                      :normalized-burdens {:flow 0.6931471805599453
                                           :state 0.0
                                           :shape 0.6931471805599453
                                           :abstraction 0.6931471805599453
                                           :dependency 0.9162907318741551
                                           :working-set 0.6931471805599453}
                      :lcc-calibration {:weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0}
                                        :transform :log1p-over-scale}
                      :lcc-total 3.7
                      :findings [{:kind :helper-chasing}]}]
             :namespace-rollups [{:ns 'sample.core
                                  :unit-count 2
                                  :total-lcc 7.0
                                  :avg-lcc 3.5
                                  :max-lcc 5.6
                                  :avg-flow 2.0
                                  :avg-state 0.5
                                  :avg-shape 1.0
                                  :avg-abstraction 2.5
                                  :avg-dependency 1.0
                                  :avg-working-set 1.75}
                                 {:ns 'sample.util
                                  :unit-count 1
                                  :total-lcc 3.7
                                  :avg-lcc 3.7
                                  :max-lcc 3.7
                                  :avg-flow 2.0
                                  :avg-state 0.0
                                  :avg-shape 1.0
                                  :avg-abstraction 2.0
                                  :avg-dependency 3.0
                                  :avg-working-set 1.25}]}
   :project-rollup {:unit-count 3
                    :namespace-count 2
                    :total-lcc 10.7
                    :avg-lcc (/ 10.7 3)
                    :max-lcc 5.6
                    :avg-flow (/ 5.0 3)
                    :avg-state (/ 1.0 3)
                    :avg-shape 1.0
                    :avg-abstraction (/ 7.0 3)
                    :avg-dependency (/ 5.0 3)
                    :avg-working-set (/ 4.75 3)
                    :finding-counts {:abstraction-oscillation 1
                                     :working-set-overload 1
                                     :helper-chasing 1}}
   :max-unit {:ns 'sample.core :var 'branchy :kind :defn-arity :arity 1 :lcc-total 5.6}})
