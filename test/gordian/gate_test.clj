(ns gordian.gate-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.gate :as sut]))

(def check-pc-delta #'gordian.gate/check-pc-delta)
(def check-new-cycles #'gordian.gate/check-new-cycles)
(def check-new-findings #'gordian.gate/check-new-findings)
(def gate-result #'gordian.gate/gate-result)
(def summarize #'gordian.gate/summarize)

(def diff
  {:health   {:delta {:propagation-cost 0.015}}
   :cycles   {:added [#{'a 'b}]}
   :findings {:added [{:severity :high   :category :cycle}
                      {:severity :medium :category :hidden-conceptual}
                      {:severity :medium :category :hidden-change}]}})

(deftest check-pc-delta-test
  (testing "pass when delta <= limit"
    (is (= :pass (:status (check-pc-delta {:health {:delta {:propagation-cost 0.005}}} 0.01)))))
  (testing "fail when delta > limit"
    (let [c (check-pc-delta diff 0.01)]
      (is (= :fail (:status c)))
      (is (= :pc-delta (:name c)))
      (is (= 0.01 (:limit c))))))

(deftest check-new-cycles-test
  (testing "pass when no cycles added"
    (is (= :pass (:status (check-new-cycles {:cycles {:added []}})))))
  (testing "fail when cycles added"
    (let [c (check-new-cycles diff)]
      (is (= :fail (:status c)))
      (is (= 1 (:actual c))))))

(deftest check-new-findings-test
  (testing "high severity"
    (let [c (check-new-findings diff :high 0)]
      (is (= :new-high-findings (:name c)))
      (is (= :fail (:status c)))
      (is (= 1 (:actual c)))))
  (testing "medium severity"
    (let [c (check-new-findings diff :medium 2)]
      (is (= :new-medium-findings (:name c)))
      (is (= :pass (:status c)))
      (is (= 2 (:actual c))))))

(deftest resolve-checks-defaults-test
  (let [checks (sut/resolve-checks {} diff)
        names  (mapv :name checks)]
    (is (= [:pc-delta :new-cycles :new-high-findings] names))
    (is (= [:fail :fail :fail] (mapv :status checks)))))

(deftest resolve-checks-threshold-overrides-test
  (let [checks (sut/resolve-checks {:max-pc-delta 0.02
                                    :max-new-high-findings 1
                                    :max-new-medium-findings 2}
                                   diff)
        by-name (into {} (map (juxt :name identity)) checks)]
    (is (= :pass (:status (get by-name :pc-delta))))
    (is (= :pass (:status (get by-name :new-high-findings))))
    (is (= :pass (:status (get by-name :new-medium-findings))))))

(deftest resolve-checks-fail-on-test
  (testing "select explicit checks"
    (let [checks (sut/resolve-checks {:fail-on "new-cycles,new-high-findings"} diff)]
      (is (= [:new-cycles :new-high-findings] (mapv :name checks)))))
  (testing "unknown check throws"
    (is (thrown? Exception (sut/resolve-checks {:fail-on "wat"} diff)))))

(deftest gate-result-test
  (is (= :pass (gate-result [{:status :pass} {:status :pass}])))
  (is (= :fail (gate-result [{:status :pass} {:status :fail}]))))

(deftest summarize-test
  (is (= {:passed 2 :failed 1 :total 3}
         (summarize [{:status :pass} {:status :fail} {:status :pass}]))))

(deftest gate-report-test
  (let [checks [{:name :pc-delta :status :pass}
                {:name :new-cycles :status :fail}]
        report (sut/gate-report "base.edn"
                                {:src-dirs ["src/"]}
                                {:src-dirs ["src/"]}
                                {:health {:delta {:propagation-cost 0.0}}}
                                checks)]
    (is (= :gate (:gordian/command report)))
    (is (= "base.edn" (:baseline-file report)))
    (is (= :fail (:result report)))
    (is (= {:passed 1 :failed 1 :total 2} (:summary report)))
    (is (= [] (:warnings report)))))

(deftest gate-report-warning-test
  (let [report (sut/gate-report "base.edn"
                                {:src-dirs ["src/"]}
                                {:src-dirs ["src/" "test/"]}
                                {}
                                [{:status :pass}])]
    (is (= 1 (count (:warnings report))))
    (is (= :src-dirs-mismatch (get-in report [:warnings 0 :kind])))))
