(ns gordian.close-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.close :as sut]))

(deftest close-test
  (testing "empty graph"
    (is (= {} (sut/close {}))))

  (testing "single node with no deps"
    (is (= {'A #{}} (sut/close {'A #{}}))))

  (testing "linear chain A→B→C"
    ;; A transitively reaches B and C; B reaches C; C reaches nothing
    (is (= {'A '#{B C}
            'B '#{C}
            'C #{}}
           (sut/close {'A '#{B}
                       'B '#{C}
                       'C #{}}))))

  (testing "diamond  A→B,A→C, B→D, C→D"
    ;; A reaches all; B and C each reach only D; D reaches nothing
    (is (= {'A '#{B C D}
            'B '#{D}
            'C '#{D}
            'D #{}}
           (sut/close {'A '#{B C}
                       'B '#{D}
                       'C '#{D}
                       'D #{}}))))

  (testing "self-loop  A→A"
    ;; A's dep set includes itself via the cycle
    (is (= {'A '#{A}} (sut/close {'A '#{A}}))))

  (testing "mutual cycle  A→B→A"
    ;; Both nodes reach each other and themselves through the cycle
    (is (= {'A '#{A B}
            'B '#{A B}}
           (sut/close {'A '#{B}
                       'B '#{A}}))))

  (testing "external dep not a graph key"
    ;; ext is a dep of A but has no entry in the graph
    (is (= {'A '#{ext}} (sut/close {'A '#{ext}}))))

  (testing "disconnected subgraphs  A→B, C→D"
    (is (= {'A '#{B}
            'B #{}
            'C '#{D}
            'D #{}}
           (sut/close {'A '#{B}
                       'B #{}
                       'C '#{D}
                       'D #{}}))))

  (testing "fixture graph  alpha←beta←gamma, gamma←alpha"
    (let [g {'alpha #{}
             'beta  '#{alpha}
             'gamma '#{alpha beta}}]
      (is (= {'alpha #{}
              'beta  '#{alpha}
              'gamma '#{alpha beta}}
             (sut/close g))))))
