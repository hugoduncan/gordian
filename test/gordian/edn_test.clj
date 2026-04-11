(ns gordian.edn-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.edn :as sut]))

;;; Hand-crafted fixture — tests edn/generate in isolation.
(def fixture-report
  {:src-dirs         ["resources/fixture"]
   :propagation-cost (/ 3.0 9.0)
   :cycles           []
   :graph            {'alpha #{}
                      'beta  '#{alpha}
                      'gamma '#{alpha beta}}
   :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
            :ca 0 :ce 2 :instability 1.0  :role :peripheral}
           {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)
            :ca 1 :ce 1 :instability 0.5  :role :shared}
           {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)
            :ca 2 :ce 0 :instability 0.0  :role :core}]})

;;; ── generate ─────────────────────────────────────────────────────────────

(deftest generate-valid-edn-test
  (testing "output is parseable EDN"
    (is (map? (read-string (sut/generate fixture-report)))))

  (testing "empty report produces parseable EDN"
    (is (map? (read-string (sut/generate {:src-dirs ["x"] :propagation-cost 0.0
                                          :cycles [] :nodes []}))))))

(deftest generate-content-test
  (let [parsed (read-string (sut/generate fixture-report))]
    (testing "src-dirs present as vector"
      (is (= ["resources/fixture"] (:src-dirs parsed))))

    (testing "propagation-cost present"
      (is (number? (:propagation-cost parsed))))

    (testing "cycles is a vector"
      (is (vector? (:cycles parsed))))

    (testing "nodes is a vector"
      (is (= 3 (count (:nodes parsed)))))

    (testing "ns values are symbols not strings"
      (is (every? symbol? (map :ns (:nodes parsed)))))

    (testing "role values are keywords not strings"
      (is (every? keyword? (map :role (:nodes parsed)))))

    (testing "graph key excluded from output"
      (is (not (contains? parsed :graph))))))

(deftest generate-node-values-test
  (let [by-ns (into {} (map (juxt :ns identity)
                            (:nodes (read-string (sut/generate fixture-report)))))]
    (testing "alpha is :core, Ce=0 Ca=2"
      (is (= :core (:role (get by-ns 'alpha))))
      (is (= 0 (:ce (get by-ns 'alpha))))
      (is (= 2 (:ca (get by-ns 'alpha)))))

    (testing "gamma is :peripheral, I=1.0"
      (is (= :peripheral (:role (get by-ns 'gamma))))
      (is (= 1.0 (:instability (get by-ns 'gamma)))))))

(deftest generate-cycle-edn-test
  (testing "cycles preserved as sets of symbols"
    (let [parsed (read-string (sut/generate (assoc fixture-report :cycles [#{'beta 'alpha}])))]
      (is (= [#{'alpha 'beta}] (:cycles parsed))))))


