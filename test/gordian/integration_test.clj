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

;;; ── envelope integration ─────────────────────────────────────────────────

(deftest envelope-analyze-edn-test
  (let [report (main/build-report fixture-dirs 0.01)
        opts   {:src-dirs fixture-dirs :conceptual 0.01}
        env    ((requiring-resolve 'gordian.envelope/wrap) opts report :analyze)
        parsed (read-string (report-edn/generate env))]
    (testing "envelope metadata in EDN output"
      (is (= "0.2.0" (:gordian/version parsed)))
      (is (= 1 (:gordian/schema parsed)))
      (is (= :analyze (:gordian/command parsed))))

    (testing "lenses describe what ran"
      (is (true? (get-in parsed [:lenses :structural])))
      (is (true? (get-in parsed [:lenses :conceptual :enabled])))
      (is (= 0.01 (get-in parsed [:lenses :conceptual :threshold])))
      (is (pos-int? (get-in parsed [:lenses :conceptual :candidate-pairs])))
      (is (false? (get-in parsed [:lenses :change :enabled]))))

    (testing "payload preserved through envelope"
      (is (number? (:propagation-cost parsed)))
      (is (vector? (:nodes parsed)))
      (is (vector? (:conceptual-pairs parsed))))

    (testing "internal keys stripped"
      (is (not (contains? parsed :graph)))
      (is (not (contains? parsed :conceptual-threshold))))))

(deftest envelope-diagnose-json-test
  (let [report   (main/build-report fixture-dirs 0.01)
        health   ((requiring-resolve 'gordian.diagnose/health) report)
        findings ((requiring-resolve 'gordian.diagnose/diagnose) report)
        enriched (assoc report :findings findings :health health)
        opts     {:src-dirs fixture-dirs :conceptual 0.01}
        env      ((requiring-resolve 'gordian.envelope/wrap) opts enriched :diagnose)
        parsed   (json/parse-string (report-json/generate env) true)]
    (testing "diagnose JSON has envelope + findings"
      (is (= "0.2.0" (get parsed (keyword "gordian/version"))))
      (is (= "diagnose" (get parsed (keyword "gordian/command"))))
      (is (vector? (:findings parsed)))
      (is (map? (:health parsed))))))

(deftest multi-dir-pipeline-test
  (testing "two dirs merged — all namespaces present"
    (let [report (main/build-report ["resources/fixture"
                                     "resources/fixture-cljc"])]
      (is (= 4 (count (:nodes report))))
      (is (every? #(contains? #{'alpha 'beta 'gamma 'portable}
                              (:ns %))
                  (:nodes report))))))

(deftest change-coupling-pipeline-test
  (testing "change coupling runs against real gordian repo"
    (let [report (main/build-report ["src/"] nil {:change "." :min-co 1})]
      (is (contains? report :change-pairs))
      (is (vector? (:change-pairs report)))
      ;; gordian has real commit history — at least some pairs expected
      (is (seq (:change-pairs report)))))

  (testing "change pairs have required shape"
    (let [pairs (:change-pairs (main/build-report ["src/"] nil {:change "." :min-co 1}))]
      (doseq [p pairs]
        (is (symbol? (:ns-a p)))
        (is (symbol? (:ns-b p)))
        (is (number? (:score p)))
        (is (= :change (:kind p)))
        (is (number? (:co-changes p)))
        (is (boolean? (:structural-edge? p))))))

  (testing "change pairs contain only project namespaces"
    (let [report (main/build-report ["src/"] nil {:change "." :min-co 1})
          graph  (:graph report)
          pairs  (:change-pairs report)]
      (doseq [{:keys [ns-a ns-b]} pairs]
        (is (contains? graph ns-a))
        (is (contains? graph ns-b))))))
