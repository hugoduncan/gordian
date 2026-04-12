(ns gordian.tests-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.tests :as sut]))

(deftest support-test-ns?-test
  (testing "support-like namespace names are detected"
    (is (true? (sut/support-test-ns? 'app.test-support)))
    (is (true? (sut/support-test-ns? 'app.fixtures)))
    (is (true? (sut/support-test-ns? 'app.assertions))))

  (testing "ordinary test namespaces are not support"
    (is (false? (sut/support-test-ns? 'app.core-test)))
    (is (false? (sut/support-test-ns? 'app.integration-test)))))

(deftest executable-test-ns?-test
  (testing "non-support test namespaces are executable"
    (is (true? (sut/executable-test-ns? 'app.core-test))))

  (testing "support namespaces are not executable"
    (is (false? (sut/executable-test-ns? 'app.test-support)))))

(deftest integration-cue?-test
  (testing "common integration cues are detected"
    (is (true? (sut/integration-cue? 'app.integration-test)))
    (is (true? (sut/integration-cue? 'app.api-test)))
    (is (true? (sut/integration-cue? 'app.system-smoke-test))))

  (testing "ordinary unit-ish names have no integration cue"
    (is (false? (sut/integration-cue? 'app.core-test)))))

(deftest test-role-test
  (testing "support-like names become :support"
    (is (= :support (sut/test-role 'app.test-support))))

  (testing "other test names become :executable"
    (is (= :executable (sut/test-role 'app.core-test)))))

(deftest src-only-graph-test
  (let [graph   {'app.core #{'app.db 'app.test-support}
                 'app.db   #{}}
        origins {'app.core :src
                 'app.db :src
                 'app.test-support :test}]
    (is (= {'app.core #{'app.db}
            'app.db   #{}}
           (sut/src-only-graph graph origins)))))

(deftest incoming-index-test
  (let [graph {'app.core #{'app.db}
               'app.api  #{'app.db 'app.core}
               'app.test #{'app.core}}]
    (is (= {'app.db   #{'app.core 'app.api}
            'app.core #{'app.api 'app.test}}
           (sut/incoming-index graph)))))

(deftest test-style-test
  (testing "support profiles stay :support"
    (is (= :support
           (sut/test-style {:test-role :support :reach 0.5 :ce 9}
                           {:high-reach 0.25 :high-ce 4 :low-reach 0.08 :low-ce 2}))))

  (testing "low reach + low ce => :unit-ish"
    (is (= :unit-ish
           (sut/test-style {:test-role :executable :reach 0.03 :ce 1}
                           {:high-reach 0.25 :high-ce 4 :low-reach 0.08 :low-ce 2}))))

  (testing "high reach => :integration-ish"
    (is (= :integration-ish
           (sut/test-style {:test-role :executable :reach 0.40 :ce 2}
                           {:high-reach 0.25 :high-ce 4 :low-reach 0.08 :low-ce 2}))))

  (testing "middle case => :mixed"
    (is (= :mixed
           (sut/test-style {:test-role :executable :reach 0.12 :ce 3}
                           {:high-reach 0.25 :high-ce 4 :low-reach 0.08 :low-ce 2})))))

(deftest classify-test-styles-test
  (let [profiles [{:ns 'app.core-test :test-role :executable :reach 0.02 :ce 1}
                  {:ns 'app.api-test :test-role :executable :reach 0.40 :ce 5}
                  {:ns 'app.test-support :test-role :support :reach 0.01 :ce 0}]
        by-ns    (into {} (map (juxt :ns identity)) (sut/classify-test-styles profiles))]
    (is (= :unit-ish (get-in by-ns ['app.core-test :test-style])))
    (is (= :integration-ish (get-in by-ns ['app.api-test :test-style])))
    (is (= :support (get-in by-ns ['app.test-support :test-style])))))

(deftest test-profiles-test
  (let [full-report {:nodes [{:ns 'app.core-test
                              :reach 0.03 :fan-in 0.0 :ca 0 :ce 1 :instability 1.0 :role :isolated}
                             {:ns 'app.api-test
                              :reach 0.45 :fan-in 0.0 :ca 0 :ce 6 :instability 1.0 :role :peripheral}
                             {:ns 'app.test-support
                              :reach 0.01 :fan-in 0.2 :ca 2 :ce 0 :instability 0.0 :role :core}
                             {:ns 'app.core
                              :reach 0.0 :fan-in 0.3 :ca 2 :ce 0 :instability 0.0 :role :core}]}
        origins     {'app.core-test :test
                     'app.api-test :test
                     'app.test-support :test
                     'app.core :src}
        by-ns       (into {} (map (juxt :ns identity)) (sut/test-profiles full-report origins))]
    (testing "only test-origin namespaces are profiled"
      (is (= #{'app.core-test 'app.api-test 'app.test-support}
             (set (keys by-ns)))))

    (testing "test role is attached"
      (is (= :executable (get-in by-ns ['app.core-test :test-role])))
      (is (= :support (get-in by-ns ['app.test-support :test-role]))))

    (testing "integration cue is surfaced"
      (is (true? (get-in by-ns ['app.api-test :integration-cue?])))
      (is (false? (get-in by-ns ['app.core-test :integration-cue?]))))

    (testing "test style is attached"
      (is (= :unit-ish (get-in by-ns ['app.core-test :test-style])))
      (is (= :integration-ish (get-in by-ns ['app.api-test :test-style])))
      (is (= :support (get-in by-ns ['app.test-support :test-style]))))))

(deftest invariant-detection-test
  (let [graph {'app.core-test #{'app.core 'app.test-support}
               'app.other-test #{'app.core-test 'app.test-support}
               'app.api-test #{'app.core}
               'app.core #{'app.db 'app.test-support}
               'app.db #{}}
        origins {'app.core-test :test
                 'app.other-test :test
                 'app.api-test :test
                 'app.test-support :test
                 'app.core :src
                 'app.db :src}
        profiles [{:ns 'app.core-test :test-role :executable :ca 1}
                  {:ns 'app.other-test :test-role :executable :ca 0}
                  {:ns 'app.api-test :test-role :executable :ca 0}
                  {:ns 'app.test-support :test-role :support :ca 2}]
        report {:graph graph
                :cycles [#{'app.core 'app.test-support} #{'app.core-test 'app.other-test}]}
        inv (sut/invariants report origins profiles)]
    (testing "src->test edges are detected"
      (is (= [{:src 'app.core :test 'app.test-support :test-role :support}]
             (:src->test-edges inv))))

    (testing "test->test edges are detected and annotated"
      (is (= #{{:from 'app.core-test :to 'app.test-support :to-role :support}
               {:from 'app.other-test :to 'app.core-test :to-role :executable}
               {:from 'app.other-test :to 'app.test-support :to-role :support}}
             (set (:test->test-edges inv)))))

    (testing "executable tests with incoming deps are surfaced"
      (is (= [{:ns 'app.core-test
               :ca 1
               :incoming-src []
               :incoming-test ['app.other-test]
               :from-support []
               :from-executable ['app.other-test]}]
             (:executable-tests-with-incoming-deps inv))))

    (testing "support leaked to src is detected"
      (is (= [{:ns 'app.test-support
               :ca 2
               :incoming-src ['app.core]}]
             (:support-leaked-to-src inv))))

    (testing "shared test support excludes support with src leakage"
      (is (= [] (:shared-test-support inv))))

    (testing "mixed cycles include only src+test SCCs"
      (is (= [{:members ['app.core 'app.test-support]
               :src-members ['app.core]
               :test-members ['app.test-support]}]
             (:mixed-cycles inv))))))

(deftest shared-test-support-test
  (let [graph {'app.core-test #{'app.test-support}
               'app.api-test #{'app.test-support}}
        origins {'app.core-test :test
                 'app.api-test :test
                 'app.test-support :test}
        profiles [{:ns 'app.core-test :test-role :executable :ca 0}
                  {:ns 'app.api-test :test-role :executable :ca 0}
                  {:ns 'app.test-support :test-role :support :ca 2}]]
    (is (= [{:ns 'app.test-support
             :ca 2
             :incoming-test ['app.api-test 'app.core-test]}]
           (sut/shared-test-support graph origins profiles)))))

(deftest core-coverage-test
  (let [src-report {:nodes [{:ns 'app.core :role :core :ca 2}
                            {:ns 'app.db :role :core :ca 1}
                            {:ns 'app.main :role :peripheral :ca 0}]}
        full-report {:nodes [{:ns 'app.core :role :core :ca 4}
                             {:ns 'app.db :role :core :ca 1}
                             {:ns 'app.main :role :peripheral :ca 0}
                             {:ns 'app.core-test :role :isolated :ca 0}]}
        origins {'app.core :src
                 'app.db :src
                 'app.main :src
                 'app.core-test :test}
        coverage (sut/core-coverage src-report full-report origins)]
    (is (= [{:ns 'app.core :role :core :ca-src 2 :ca-with-tests 4 :ca-delta 2}]
           (:tested-core coverage)))
    (is (= [{:ns 'app.db :role :core :ca-src 1 :ca-with-tests 1 :ca-delta 0}]
           (:untested-core coverage)))))

(deftest pc-summary-test
  (testing "large delta => :over-coupled"
    (is (= {:pc-src 0.10 :pc-with-tests 0.25 :pc-delta 0.15 :interpretation :over-coupled}
           (sut/pc-summary {:propagation-cost 0.10}
                           {:propagation-cost 0.25}))))

  (testing "near-zero delta => :no-integration-pressure"
    (is (= {:pc-src 0.10 :pc-with-tests 0.11 :pc-delta 0.009999999999999995 :interpretation :no-integration-pressure}
           (sut/pc-summary {:propagation-cost 0.10}
                           {:propagation-cost 0.11}))))

  (testing "middle delta => :targeted"
    (is (= {:pc-src 0.10 :pc-with-tests 0.14 :pc-delta 0.04000000000000001 :interpretation :targeted}
           (sut/pc-summary {:propagation-cost 0.10}
                           {:propagation-cost 0.14})))))

(deftest tests-findings-test
  (let [profiles [{:ns 'app.core-test
                   :test-role :executable
                   :test-style :integration-ish
                   :integration-cue? false
                   :reach 0.35 :ce 5 :role :peripheral}
                  {:ns 'app.integration-test
                   :test-role :executable
                   :test-style :integration-ish
                   :integration-cue? true
                   :reach 0.60 :ce 9 :role :peripheral}]
        invariants {:src->test-edges [{:src 'app.core :test 'app.test-support :test-role :support}]
                    :support-leaked-to-src [{:ns 'app.test-support :ca 2 :incoming-src ['app.core]}]
                    :executable-tests-with-incoming-deps [{:ns 'app.core-test
                                                           :ca 1
                                                           :incoming-src []
                                                           :incoming-test ['app.other-test]
                                                           :from-support []
                                                           :from-executable ['app.other-test]}]
                    :mixed-cycles [{:members ['app.core 'app.test-support]
                                    :src-members ['app.core]
                                    :test-members ['app.test-support]}]}
        coverage {:untested-core [{:ns 'app.db :role :core :ca-src 1 :ca-with-tests 1 :ca-delta 0}]}
        pc {:pc-src 0.10 :pc-with-tests 0.25 :pc-delta 0.15 :interpretation :over-coupled}
        findings (sut/tests-findings {} {} {} {} profiles invariants coverage pc)
        categories (set (map :category findings))
        severities (map :severity findings)]
    (is (= #{:src-depends-on-test
             :test-support-leaked-to-src
             :mixed-cycle
             :test-executable-has-incoming-deps
             :unit-test-too-broad
             :untested-core
             :over-coupled-tests
             :integration-test-very-broad}
           categories))
    (is (= [:high :high :high :medium :medium :medium :medium :low]
           severities))))
