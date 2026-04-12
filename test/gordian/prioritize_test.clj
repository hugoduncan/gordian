(ns gordian.prioritize-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.prioritize :as sut]))

(def high-cross
  {:severity :high
   :category :cross-lens-hidden
   :subject {:ns-a 'a.core :ns-b 'b.svc}
   :evidence {:conceptual-score 0.4 :same-family? false}})

(def low-noise
  {:severity :low
   :category :hidden-conceptual
   :subject {:ns-a 'x.a :ns-b 'x.b}
   :evidence {:score 0.3 :same-family? true :independent-terms []}})

(def medium-same-family
  {:severity :medium
   :category :hidden-conceptual
   :subject {:ns-a 'f.a :ns-b 'f.b}
   :evidence {:score 0.25 :same-family? true :independent-terms ["real"]}})

(def hub
  {:severity :low
   :category :hub
   :subject {:ns 'main}
   :evidence {:reach 0.9}})

(deftest cluster-context-test
  (let [clusters [{:findings [high-cross low-noise]}]
        ctx      (sut/cluster-context clusters [])]
    (is (= 2 (get-in ctx [:cluster-size-by-finding (sut/finding-key high-cross)])))
    (is (= 2 (get-in ctx [:cluster-size-by-finding (sut/finding-key low-noise)])))))

(deftest actionability-score-test
  (let [ctx (sut/cluster-context [{:findings [high-cross medium-same-family]}] [])]
    (testing "cross-lens cross-family scores high"
      (is (> (sut/actionability-score high-cross ctx) 10.0)))
    (testing "same-family naming noise is penalized"
      (is (< (sut/actionability-score low-noise ctx) 2.0)))
    (testing "hub is low actionability"
      (is (< (sut/actionability-score hub ctx) 2.0)))))

(deftest annotate-actionability-test
  (let [ctx (sut/cluster-context [] [])
        xs  (sut/annotate-actionability [hub] ctx)]
    (is (number? (:actionability-score (first xs))))))

(deftest rank-findings-severity-test
  (let [ctx (sut/cluster-context [] [])
        ranked (sut/rank-findings [low-noise high-cross hub] :severity ctx)]
    (is (= high-cross (dissoc (first ranked) :actionability-score)))))

(deftest rank-findings-actionability-test
  (let [ctx (sut/cluster-context [{:findings [high-cross medium-same-family]}] [])
        ranked (sut/rank-findings [low-noise high-cross medium-same-family hub]
                                  :actionability ctx)
        top (first ranked)
        by-subject (into {} (map (juxt :subject identity)) ranked)]
    (is (= (:subject high-cross) (:subject top)))
    (is (> (:actionability-score (get by-subject (:subject medium-same-family)))
           (:actionability-score (get by-subject (:subject low-noise)))))))
