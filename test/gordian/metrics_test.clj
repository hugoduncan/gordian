(ns gordian.metrics-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.metrics :as sut]))

(def fixture
  "alpha←beta←gamma, gamma←alpha (no cycles)"
  {'alpha #{}
   'beta  '#{alpha}
   'gamma '#{alpha beta}})

;;; ── ce ───────────────────────────────────────────────────────────────────

(deftest ce-test
  (testing "leaf has Ce=0"
    (is (= 0 (sut/ce fixture 'alpha))))

  (testing "middle node has Ce=1"
    (is (= 1 (sut/ce fixture 'beta))))

  (testing "most-dependent node has Ce=2"
    (is (= 2 (sut/ce fixture 'gamma))))

  (testing "external dep does not count towards Ce"
    ;; ext is required but is not a project key
    (is (= 0 (sut/ce {'A '#{ext}} 'A)))))

;;; ── ca ───────────────────────────────────────────────────────────────────

(deftest ca-test
  (testing "root foundation has Ca=2"
    (is (= 2 (sut/ca fixture 'alpha))))

  (testing "middle layer has Ca=1"
    (is (= 1 (sut/ca fixture 'beta))))

  (testing "leaf has Ca=0"
    (is (= 0 (sut/ca fixture 'gamma)))))

;;; ── instability ──────────────────────────────────────────────────────────

(deftest instability-test
  (testing "both zero → 0.0 (isolated node is treated as stable)"
    (is (= 0.0 (sut/instability 0 0))))

  (testing "Ce=0, Ca>0 → 0.0 (maximally stable)"
    (is (= 0.0 (sut/instability 2 0))))

  (testing "Ce>0, Ca=0 → 1.0 (maximally unstable)"
    (is (= 1.0 (sut/instability 0 3))))

  (testing "balanced → 0.5"
    (is (= 0.5 (sut/instability 1 1))))

  (testing "weighted towards efferent"
    (let [i (sut/instability 1 3)]
      (is (< 0.5 i 1.0)))))

;;; ── compute ──────────────────────────────────────────────────────────────

(deftest compute-test
  (testing "returns an entry for every project node"
    (is (= (set (keys fixture))
           (set (keys (sut/compute fixture))))))

  (testing "alpha — stable foundation"
    (let [{:keys [ca ce instability]} (get (sut/compute fixture) 'alpha)]
      (is (= 2 ca))
      (is (= 0 ce))
      (is (= 0.0 instability))))

  (testing "beta — balanced middle layer"
    (let [{:keys [ca ce instability]} (get (sut/compute fixture) 'beta)]
      (is (= 1 ca))
      (is (= 1 ce))
      (is (= 0.5 instability))))

  (testing "gamma — unstable leaf"
    (let [{:keys [ca ce instability]} (get (sut/compute fixture) 'gamma)]
      (is (= 0 ca))
      (is (= 2 ce))
      (is (= 1.0 instability))))

  (testing "empty graph"
    (is (= {} (sut/compute {})))))
