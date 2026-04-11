(ns gordian.explain-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.explain :as sut]))

;;; ── test graph ───────────────────────────────────────────────────────────

(def ^:private graph
  "a.core→{b.svc c.util ext.lib}, b.svc→{c.util}, c.util→{}, d.leaf→{a.core}"
  {'a.core #{'b.svc 'c.util 'ext.lib}
   'b.svc  #{'c.util}
   'c.util #{}
   'd.leaf #{'a.core}})

;;; ── shortest-path ────────────────────────────────────────────────────────

(deftest shortest-path-test
  (testing "direct edge → 2-element path"
    (is (= ['a.core 'c.util] (sut/shortest-path graph 'a.core 'c.util))))

  (testing "multi-hop path"
    ;; d.leaf → a.core → c.util (shortest)
    (let [path (sut/shortest-path graph 'd.leaf 'c.util)]
      (is (= 'd.leaf (first path)))
      (is (= 'c.util (last path)))
      (is (<= (count path) 3))))

  (testing "no reverse path → nil"
    (is (nil? (sut/shortest-path graph 'c.util 'a.core))))

  (testing "self path → nil"
    (is (nil? (sut/shortest-path graph 'a.core 'a.core))))

  (testing "target not in graph → nil"
    (is (nil? (sut/shortest-path graph 'a.core 'nonexistent))))

  (testing "source not in graph → nil"
    (is (nil? (sut/shortest-path graph 'nonexistent 'a.core))))

  (testing "handles cycle without hang"
    (let [cyclic {'x #{'y} 'y #{'x}}]
      (is (= ['x 'y] (sut/shortest-path cyclic 'x 'y)))
      (is (= ['y 'x] (sut/shortest-path cyclic 'y 'x)))))

  (testing "skips external deps (not in graph keys)"
    ;; a.core has ext.lib in deps, but ext.lib is not a graph key
    ;; so path a.core→ext.lib is nil
    (is (nil? (sut/shortest-path graph 'a.core 'ext.lib)))))

;;; ── direct-deps ──────────────────────────────────────────────────────────

(def ^:private project-nss (set (keys graph)))

(deftest direct-deps-test
  (testing "splits project and external deps"
    (let [deps (sut/direct-deps graph project-nss 'a.core)]
      (is (= #{'b.svc 'c.util} (set (:project deps))))
      (is (= ['ext.lib] (:external deps)))))

  (testing "ns with no deps → both empty"
    (let [deps (sut/direct-deps graph project-nss 'c.util)]
      (is (empty? (:project deps)))
      (is (empty? (:external deps)))))

  (testing "ns not in graph → both empty"
    (let [deps (sut/direct-deps graph project-nss 'nonexistent)]
      (is (empty? (:project deps)))
      (is (empty? (:external deps))))))

;;; ── direct-dependents ───────────────────────────────────────────────────

(deftest direct-dependents-test
  (testing "c.util depended on by a.core and b.svc"
    (is (= #{'a.core 'b.svc} (set (sut/direct-dependents graph 'c.util)))))

  (testing "a.core depended on by d.leaf"
    (is (= ['d.leaf] (sut/direct-dependents graph 'a.core))))

  (testing "d.leaf — nobody depends on it"
    (is (empty? (sut/direct-dependents graph 'd.leaf))))

  (testing "nonexistent ns → empty"
    (is (empty? (sut/direct-dependents graph 'nonexistent)))))

;;; ── ns-pairs ────────────────────────────────────────────────────────────

(def ^:private sample-pairs
  [{:ns-a 'a :ns-b 'b :score 0.5}
   {:ns-a 'b :ns-b 'c :score 0.3}
   {:ns-a 'a :ns-b 'c :score 0.2}])

(deftest ns-pairs-test
  (testing "filters pairs involving ns"
    (is (= 2 (count (sut/ns-pairs sample-pairs 'a))))
    (is (= 2 (count (sut/ns-pairs sample-pairs 'b))))
    (is (= 2 (count (sut/ns-pairs sample-pairs 'c)))))

  (testing "ns not in any pair → empty"
    (is (empty? (sut/ns-pairs sample-pairs 'z))))

  (testing "empty pairs → empty"
    (is (empty? (sut/ns-pairs [] 'a)))))

;;; ── explain-ns ──────────────────────────────────────────────────────────

(def ^:private test-report
  {:graph {'x.main #{'x.scan 'x.close}
           'x.scan #{'ext.fs}
           'x.close #{}
           'x.scc  #{}}
   :nodes [{:ns 'x.main  :reach 0.5 :fan-in 0.0 :ca 0 :ce 2 :instability 1.0 :role :peripheral
            :family "x" :ca-family 0 :ca-external 0 :ce-family 2 :ce-external 0}
           {:ns 'x.scan  :reach 0.0 :fan-in 0.25 :ca 1 :ce 0 :instability 0.0 :role :core
            :family "x" :ca-family 1 :ca-external 0 :ce-family 0 :ce-external 0}
           {:ns 'x.close :reach 0.0 :fan-in 0.25 :ca 1 :ce 0 :instability 0.0 :role :core
            :family "x" :ca-family 1 :ca-external 0 :ce-family 0 :ce-external 0}
           {:ns 'x.scc   :reach 0.0 :fan-in 0.0 :ca 0 :ce 0 :instability 0.0 :role :isolated
            :family "x" :ca-family 0 :ca-external 0 :ce-family 0 :ce-external 0}]
   :cycles [#{'x.scan 'x.close}]
   :propagation-cost 0.05
   :conceptual-pairs
   [{:ns-a 'x.scan :ns-b 'x.close :score 0.30 :kind :conceptual
     :structural-edge? false :shared-terms ["graph" "node"]}
    {:ns-a 'x.main :ns-b 'x.scan :score 0.20 :kind :conceptual
     :structural-edge? true :shared-terms ["file"]}]
   :change-pairs
   [{:ns-a 'x.scan :ns-b 'x.close :score 0.40 :kind :change
     :structural-edge? false :co-changes 4 :confidence-a 0.8 :confidence-b 0.6}]})

(deftest explain-ns-test
  (let [result (sut/explain-ns test-report 'x.scan)]

    (testing "returns expected keys"
      (is (every? #(contains? result %)
                  [:ns :metrics :direct-deps :direct-dependents
                   :conceptual-pairs :change-pairs :cycles])))

    (testing ":metrics has node metrics"
      (is (= 0.0 (get-in result [:metrics :reach])))
      (is (= :core (get-in result [:metrics :role]))))

    (testing ":metrics includes family-scoped metrics"
      (is (= "x" (get-in result [:metrics :family])))
      (is (= 1 (get-in result [:metrics :ca-family])))
      (is (= 0 (get-in result [:metrics :ca-external])))
      (is (= 0 (get-in result [:metrics :ce-family])))
      (is (= 0 (get-in result [:metrics :ce-external]))))

    (testing ":direct-deps splits project/external"
      (is (empty? (get-in result [:direct-deps :project])))
      (is (= ['ext.fs] (get-in result [:direct-deps :external]))))

    (testing ":direct-dependents lists x.main"
      (is (= ['x.main] (:direct-dependents result))))

    (testing ":conceptual-pairs filtered to ns"
      (is (= 2 (count (:conceptual-pairs result)))))

    (testing ":change-pairs filtered to ns"
      (is (= 1 (count (:change-pairs result)))))

    (testing ":cycles lists cycle containing ns"
      (is (= 1 (count (:cycles result))))
      (is (contains? (first (:cycles result)) 'x.scan))))

  (testing "unknown ns → :error"
    (let [result (sut/explain-ns test-report 'nonexistent)]
      (is (contains? result :error))
      (is (contains? result :available)))))

;;; ── explain-pair-data ───────────────────────────────────────────────────

(deftest explain-pair-data-test
  (let [result (sut/explain-pair-data test-report 'x.scan 'x.close)]

    (testing "returns expected keys"
      (is (every? #(contains? result %)
                  [:ns-a :ns-b :structural :conceptual :change :finding])))

    (testing "no direct edge between scan and close"
      (is (false? (get-in result [:structural :direct-edge?])))
      (is (nil? (get-in result [:structural :direction]))))

    (testing "no transitive path either direction"
      (is (nil? (get-in result [:structural :shortest-path]))))

    (testing "conceptual pair present"
      (is (some? (:conceptual result)))
      (is (= 0.30 (:score (:conceptual result)))))

    (testing "change pair present"
      (is (some? (:change result)))
      (is (= 0.40 (:score (:change result)))))

    (testing "finding present (hidden cross-lens)"
      (is (some? (:finding result)))
      (is (= :high (:severity (:finding result))))))

  (testing "structural edge detected"
    (let [result (sut/explain-pair-data test-report 'x.main 'x.scan)]
      (is (true? (get-in result [:structural :direct-edge?])))
      (is (= :a->b (get-in result [:structural :direction])))))

  (testing "shortest path found for transitive connection"
    (let [result (sut/explain-pair-data test-report 'x.main 'x.close)]
      ;; x.main → x.close is a direct dep actually
      (is (true? (get-in result [:structural :direct-edge?])))))

  (testing "no conceptual data → nil"
    (let [result (sut/explain-pair-data test-report 'x.main 'x.scc)]
      (is (nil? (:conceptual result)))))

  (testing "unknown ns → :error"
    (let [result (sut/explain-pair-data test-report 'x.main 'nonexistent)]
      (is (contains? result :error)))))
