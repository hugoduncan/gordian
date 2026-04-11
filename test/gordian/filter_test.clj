(ns gordian.filter-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.filter :as sut]))

(def ^:private graph
  {'a.core #{'b.core 'c.util}
   'b.core #{'c.util}
   'c.util #{}
   'user   #{'a.core}})

(deftest filter-graph-test
  (testing "exclude removes ns from keys"
    (let [g (sut/filter-graph graph ["user"])]
      (is (not (contains? g 'user)))))

  (testing "exclude removes ns from dep sets"
    (let [g (sut/filter-graph graph ["c\\.util"])]
      (is (not (contains? g 'c.util)))
      (is (= #{'b.core} (get g 'a.core)))
      (is (= #{} (get g 'b.core)))))

  (testing "multiple patterns — all matched ns removed"
    (let [g (sut/filter-graph graph ["user" "util"])]
      (is (not (contains? g 'user)))
      (is (not (contains? g 'c.util)))
      (is (= #{'b.core} (get g 'a.core)))))

  (testing "empty patterns → graph unchanged"
    (is (= graph (sut/filter-graph graph []))))

  (testing "nil patterns → graph unchanged"
    (is (= graph (sut/filter-graph graph nil))))

  (testing "pattern matching nothing → graph unchanged"
    (is (= graph (sut/filter-graph graph ["zzz"]))))

  (testing "pattern matching everything → empty graph"
    (is (= {} (sut/filter-graph graph [".*"]))))

  (testing "regex dot matches literal dot"
    (let [g (sut/filter-graph graph ["a\\.core"])]
      (is (not (contains? g 'a.core)))
      ;; a.core removed from user's dep set
      (is (= #{} (get g 'user)))))

  (testing "ns only in dep sets (not as key) is removed from dep sets"
    (let [graph2 {'x.main #{'y.lib}
                  'x.test #{'y.lib}}
          g (sut/filter-graph graph2 ["y\\.lib"])]
      (is (= #{} (get g 'x.main)))
      (is (= #{} (get g 'x.test))))))
