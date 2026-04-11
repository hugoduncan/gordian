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
