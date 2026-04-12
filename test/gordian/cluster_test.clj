(ns gordian.cluster-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.cluster :as cluster]))

;;; ── finding-namespaces ───────────────────────────────────────────────────

(deftest finding-namespaces-test
  (testing "pair finding → two ns"
    (is (= ['a.core 'b.svc]
           (cluster/finding-namespaces
            {:subject {:ns-a 'a.core :ns-b 'b.svc}}))))

  (testing "single ns finding"
    (is (= ['a.core]
           (cluster/finding-namespaces
            {:subject {:ns 'a.core}}))))

  (testing "cycle finding with :members"
    (let [nss (cluster/finding-namespaces
               {:subject {:members #{'x 'y 'z}}})]
      (is (= 3 (count nss)))
      (is (= #{'x 'y 'z} (set nss)))))

  (testing "empty subject"
    (is (= [] (cluster/finding-namespaces {:subject {}})))))

;;; ── cluster-findings ─────────────────────────────────────────────────────

(deftest cluster-findings-empty-test
  (testing "no findings → empty clusters"
    (let [result (cluster/cluster-findings [])]
      (is (= [] (:clusters result)))
      (is (= [] (:unclustered result))))))

(deftest cluster-findings-single-test
  (testing "one finding → unclustered"
    (let [f {:severity :medium :category :hidden-conceptual
             :subject {:ns-a 'a :ns-b 'b}}
          result (cluster/cluster-findings [f])]
      (is (= [] (:clusters result)))
      (is (= 1 (count (:unclustered result)))))))

(deftest cluster-findings-disjoint-test
  (testing "two unrelated findings → both unclustered"
    (let [f1 {:severity :medium :category :hidden-conceptual
              :subject {:ns-a 'a :ns-b 'b}}
          f2 {:severity :low :category :hub
              :subject {:ns 'z}}
          result (cluster/cluster-findings [f1 f2])]
      (is (= [] (:clusters result)))
      (is (= 2 (count (:unclustered result)))))))

(deftest cluster-findings-connected-pair-test
  (testing "two pair findings sharing a namespace → clustered"
    (let [f1 {:severity :medium :category :hidden-conceptual
              :subject {:ns-a 'a.core :ns-b 'b.svc}}
          f2 {:severity :medium :category :hidden-change
              :subject {:ns-a 'a.core :ns-b 'c.util}}
          result (cluster/cluster-findings [f1 f2])
          cluster (first (:clusters result))]
      (is (= 1 (count (:clusters result))))
      (is (= #{'a.core 'b.svc 'c.util} (:namespaces cluster)))
      (is (= 2 (count (:findings cluster))))
      (is (= :medium (:max-severity cluster)))
      (is (= [] (:unclustered result))))))

(deftest cluster-findings-transitive-test
  (testing "transitively connected findings form one cluster"
    ;; a↔b, b↔c, c↔d → all in one cluster
    (let [f1 {:severity :high :category :cross-lens-hidden
              :subject {:ns-a 'a :ns-b 'b}}
          f2 {:severity :medium :category :hidden-conceptual
              :subject {:ns-a 'b :ns-b 'c}}
          f3 {:severity :low :category :hidden-change
              :subject {:ns-a 'c :ns-b 'd}}
          result (cluster/cluster-findings [f1 f2 f3])
          cluster (first (:clusters result))]
      (is (= 1 (count (:clusters result))))
      (is (= #{'a 'b 'c 'd} (:namespaces cluster)))
      (is (= 3 (count (:findings cluster))))
      (is (= :high (:max-severity cluster)))
      (is (= [] (:unclustered result))))))

(deftest cluster-findings-mixed-test
  (testing "some findings cluster, others don't"
    (let [f1 {:severity :high :category :cycle
              :subject {:members #{'a 'b}}}
          f2 {:severity :medium :category :hidden-conceptual
              :subject {:ns-a 'a :ns-b 'c}}
          f3 {:severity :low :category :hub
              :subject {:ns 'z}}
          result (cluster/cluster-findings [f1 f2 f3])]
      ;; f1 and f2 share 'a → cluster
      (is (= 1 (count (:clusters result))))
      (is (= #{'a 'b 'c} (:namespaces (first (:clusters result)))))
      ;; f3 is unclustered
      (is (= 1 (count (:unclustered result))))
      (is (= :hub (:category (first (:unclustered result))))))))

(deftest cluster-findings-severity-sorting-test
  (testing "clusters sorted by severity then count"
    (let [;; cluster 1: low severity, 2 findings
          f1a {:severity :low :category :hidden-conceptual
               :subject {:ns-a 'x :ns-b 'y}}
          f1b {:severity :low :category :hidden-change
               :subject {:ns-a 'x :ns-b 'z}}
          ;; cluster 2: high severity, 2 findings
          f2a {:severity :high :category :cycle
               :subject {:members #{'a 'b}}}
          f2b {:severity :medium :category :hidden-conceptual
               :subject {:ns-a 'a :ns-b 'c}}
          result (cluster/cluster-findings [f1a f1b f2a f2b])]
      (is (= 2 (count (:clusters result))))
      ;; high-severity cluster first
      (is (= :high (:max-severity (first (:clusters result)))))
      (is (= :low (:max-severity (second (:clusters result))))))))

(deftest cluster-findings-node-finding-connects-test
  (testing "node finding (single ns) connects to pair finding sharing that ns"
    (let [f1 {:severity :medium :category :god-module
              :subject {:ns 'a.core}}
          f2 {:severity :medium :category :hidden-conceptual
              :subject {:ns-a 'a.core :ns-b 'b.svc}}
          result (cluster/cluster-findings [f1 f2])
          cluster (first (:clusters result))]
      (is (= 1 (count (:clusters result))))
      (is (= #{'a.core 'b.svc} (:namespaces cluster)))
      (is (= 2 (count (:findings cluster)))))))
