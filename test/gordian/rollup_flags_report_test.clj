(ns gordian.rollup-flags-report-test
  (:require [clojure.test :refer [deftest is]]
            [gordian.cyclomatic :as cyclomatic]
            [gordian.local.report :as local-report]))

(deftest complexity-finalize-report-omits-unrequested-rollups
  (let [report {:gordian/command :complexity
                :metrics [:cyclomatic-complexity :lines-of-code]
                :units [{:ns 'a.core :var 'f :kind :defn-arity :arity 1 :dispatch nil :cc 3 :loc 7 :cc-risk {:level :simple}}]
                :namespace-rollups [{:ns 'a.core :unit-count 1 :total-cc 3 :avg-cc 3.0 :max-cc 3 :cc-risk-counts {:simple 1 :moderate 0 :high 0 :untestable 0} :total-loc 7 :avg-loc 7.0 :max-loc 7}]
                :project-rollup {:unit-count 1 :namespace-count 1 :total-cc 3 :avg-cc 3.0 :max-cc 3 :cc-risk-counts {:simple 1 :moderate 0 :high 0 :untestable 0} :total-loc 7 :avg-loc 7.0 :max-loc 7}
                :max-unit {:ns 'a.core :var 'f :arity 1 :cc 3 :loc 7}}
        finalized (cyclomatic/finalize-report report :explicit [{:dir "src" :kind :src}] {})]
    (is (not (contains? finalized :namespace-rollups)))
    (is (not (contains? finalized :project-rollup)))
    (is (= false (get-in finalized [:options :namespace-rollup])))
    (is (= false (get-in finalized [:options :project-rollup])))))

(deftest local-finalize-report-omits-unrequested-rollups
  (let [units [{:ns 'a.core :var 'f :kind :defn-arity :arity 1
                :flow-burden 1.0 :state-burden 0.0 :shape-burden 0.0 :abstraction-burden 1.0 :dependency-burden 0.0
                :working-set {:peak 2 :avg 1.0 :burden 0.5}
                :normalized-burdens {:flow 0.5 :state 0.0 :shape 0.0 :abstraction 0.5 :dependency 0.0 :working-set 0.4}
                :lcc-total 1.4 :findings []}]
        report {:gordian/command :local
                :metric :local-comprehension-complexity
                :calibration {:transform :log1p-over-scale :weights {:flow 1.0 :state 1.0 :shape 1.0 :abstraction 1.0 :dependency 1.0 :working-set 1.0} :families {}}
                :units units
                :namespace-rollups (local-report/namespace-rollups units)
                :project-rollup (local-report/project-rollup units (local-report/namespace-rollups units))
                :max-unit (local-report/max-unit units)}
        finalized (local-report/finalize-report report :explicit [{:dir "src" :kind :src}] {})]
    (is (not (contains? finalized :namespace-rollups)))
    (is (not (contains? finalized :project-rollup)))
    (is (not (contains? (get finalized :display) :namespace-rollups)))
    (is (= false (get-in finalized [:options :namespace-rollup])))
    (is (= false (get-in finalized [:options :project-rollup])))))
