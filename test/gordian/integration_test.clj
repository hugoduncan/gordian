(ns gordian.integration-test
  "End-to-end pipeline tests.  This namespace requires gordian.main and
  therefore exercises the full scan → close → aggregate → metrics → scc
  → classify → output chain.  Kept separate so the individual serialisation
  tests (dot, json, edn) can remain unit-scoped."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [gordian.dot  :as dot]
            [gordian.json :as report-json]
            [gordian.edn  :as report-edn]
            [gordian.main :as main]))

(def ^:private fixture-dirs ["resources/fixture"])

(deftest dot-pipeline-test
  (testing "DOT from real scan is well-formed and contains fixture namespaces"
    (let [dot (dot/generate (main/build-report fixture-dirs))]
      (is (str/includes? dot "digraph gordian {"))
      (is (str/includes? dot "\"alpha\""))
      (is (str/ends-with? (str/trim dot) "}")))))

(deftest json-pipeline-test
  (testing "JSON from real scan is parseable with correct shape"
    (let [parsed (json/parse-string
                  (report-json/generate (main/build-report fixture-dirs))
                  true)]
      (is (= fixture-dirs (:src-dirs parsed)))
      (is (= 3 (count (:nodes parsed))))
      (is (vector? (:cycles parsed))))))

(deftest edn-pipeline-test
  (testing "EDN from real scan is parseable with correct shape"
    (let [parsed (read-string
                  (report-edn/generate (main/build-report fixture-dirs)))]
      (is (= fixture-dirs (:src-dirs parsed)))
      (is (every? symbol? (map :ns (:nodes parsed))))
      (is (every? keyword? (map :role (:nodes parsed)))))))

(deftest multi-dir-pipeline-test
  (testing "two dirs merged — all namespaces present"
    (let [report (main/build-report ["resources/fixture"
                                     "resources/fixture-cljc"])]
      (is (= 4 (count (:nodes report))))
      (is (every? #(contains? #{'alpha 'beta 'gamma 'portable}
                              (:ns %))
                  (:nodes report))))))
