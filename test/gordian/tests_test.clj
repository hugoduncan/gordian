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
