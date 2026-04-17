(ns gordian.finding-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.finding :as sut]))

(deftest action-for-category-test
  (testing "all defined categories return a keyword"
    (is (keyword? (sut/action-for-category :cycle)))
    (is (keyword? (sut/action-for-category :cross-lens-hidden)))
    (is (keyword? (sut/action-for-category :sdp-violation)))
    (is (keyword? (sut/action-for-category :god-module)))
    (is (keyword? (sut/action-for-category :vestigial-edge)))
    (is (keyword? (sut/action-for-category :hidden-conceptual)))
    (is (keyword? (sut/action-for-category :hidden-change)))
    (is (keyword? (sut/action-for-category :hub)))
    (is (keyword? (sut/action-for-category :facade))))
  (testing "unknown category returns nil"
    (is (nil? (sut/action-for-category :unknown)))
    (is (nil? (sut/action-for-category nil)))))

(deftest action-display-test
  (testing "every action keyword from action-for-category has an entry"
    (let [all-actions (map sut/action-for-category
                          [:cycle :cross-lens-hidden :sdp-violation :god-module
                           :vestigial-edge :hidden-conceptual :hidden-change
                           :hub :facade])]
      (is (every? #(contains? sut/action-display %) all-actions))))
  (testing ":none maps to nil (no line emitted)"
    (is (nil? (get sut/action-display :none))))
  (testing "all other known actions have non-nil display strings"
    (let [non-none (dissoc sut/action-display :none)]
      (is (every? string? (vals non-none))))))

(deftest next-step-for-test
  (let [nodes [{:ns 'foo.a :ca 2} {:ns 'foo.b :ca 5} {:ns 'bar.c :ca 1}]]

    (testing "pair categories → explain-pair command"
      (doseq [cat [:cross-lens-hidden :hidden-conceptual :hidden-change :vestigial-edge]]
        (is (= "gordian explain-pair foo.scan foo.main"
               (sut/next-step-for cat {:ns-a 'foo.scan :ns-b 'foo.main} nil)))))

    (testing "ns categories → explain command"
      (doseq [cat [:sdp-violation :god-module :hub :facade]]
        (is (= "gordian explain foo.core"
               (sut/next-step-for cat {:ns 'foo.core} nil)))))

    (testing "cycle with common prefix → subgraph command"
      (is (= "gordian subgraph foo"
             (sut/next-step-for :cycle {:members #{'foo.a 'foo.b 'foo.c}} nodes))))

    (testing "cycle with no common prefix → explain highest-Ca member"
      ;; foo.b has Ca=5, bar.c has Ca=1
      (is (= "gordian explain foo.b"
             (sut/next-step-for :cycle {:members #{'foo.b 'bar.c}} nodes))))

    (testing "cycle with common prefix uses prefix, not Ca fallback"
      (is (= "gordian subgraph foo"
             (sut/next-step-for :cycle {:members #{'foo.x 'foo.y}} nodes))))

    (testing "unknown category returns nil"
      (is (nil? (sut/next-step-for :unknown {} nil)))
      (is (nil? (sut/next-step-for nil {} nil))))))
