(ns gordian.family-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.family :as sut]))

;;; ── family-prefix ────────────────────────────────────────────────────────

(deftest family-prefix-test
  (testing "two-segment namespace → first segment"
    (is (= "gordian" (sut/family-prefix 'gordian.scan)))
    (is (= "gordian" (sut/family-prefix 'gordian.main))))

  (testing "three-segment namespace → first two segments"
    (is (= "psi.agent-session" (sut/family-prefix 'psi.agent-session.core)))
    (is (= "psi.agent-session" (sut/family-prefix 'psi.agent-session.mutations))))

  (testing "four-segment namespace → first three segments"
    (is (= "psi.agent-session.mutations"
           (sut/family-prefix 'psi.agent-session.mutations.extensions))))

  (testing "single-segment namespace → empty string (root family)"
    (is (= "" (sut/family-prefix 'alpha)))
    (is (= "" (sut/family-prefix 'beta)))))

;;; ── same-family? ─────────────────────────────────────────────────────────

(deftest same-family?-test
  (testing "siblings in same family"
    (is (true? (sut/same-family? 'gordian.scan 'gordian.main)))
    (is (true? (sut/same-family? 'psi.agent-session.core
                                 'psi.agent-session.mutations))))

  (testing "different families"
    (is (false? (sut/same-family? 'gordian.scan 'psi.agent-session.core)))
    (is (false? (sut/same-family? 'psi.agent-session.core
                                  'psi.background.runner))))

  (testing "single-segment namespaces are all in root family"
    (is (true? (sut/same-family? 'alpha 'beta))))

  (testing "same namespace is same family"
    (is (true? (sut/same-family? 'gordian.scan 'gordian.scan))))

  (testing "different nesting depths with same parent are same family"
    ;; psi.agent-session.core and psi.agent-session.ui share psi.agent-session
    (is (true? (sut/same-family? 'psi.agent-session.core
                                 'psi.agent-session.ui))))

  (testing "child sub-family is NOT same family as parent family"
    ;; psi.agent-session.mutations.extensions → family psi.agent-session.mutations
    ;; psi.agent-session.core → family psi.agent-session
    (is (false? (sut/same-family? 'psi.agent-session.mutations.extensions
                                  'psi.agent-session.core)))))

;;; ── family-metrics ───────────────────────────────────────────────────────

;; Fixture: a small project with two families.
;;
;;   Family "app":
;;     app.core    → app.util, app.db      (Ce-family=2, Ce-external=0)
;;     app.util    → (nothing)
;;     app.db      → (nothing)
;;
;;   Family "lib":
;;     lib.api     → app.core              (Ce-family=0, Ce-external=1)
;;     lib.impl    → lib.api               (Ce-family=1, Ce-external=0)
;;
;;   Cross-family edges: lib.api → app.core
;;
;;   Dependents of app.core:  lib.api (external)
;;   Dependents of app.util:  app.core (family)
;;   Dependents of app.db:    app.core (family)
;;   Dependents of lib.api:   lib.impl (family)
;;   Dependents of lib.impl:  (none)

(def ^:private graph
  {'app.core #{'app.util 'app.db}
   'app.util #{}
   'app.db   #{}
   'lib.api  #{'app.core}
   'lib.impl #{'lib.api}})

(deftest family-metrics-test
  (let [fm (sut/family-metrics graph)]

    (testing "returns metrics for all project namespaces"
      (is (= (set (keys graph)) (set (keys fm)))))

    (testing "family prefix assigned correctly"
      (is (= "app" (:family (fm 'app.core))))
      (is (= "app" (:family (fm 'app.util))))
      (is (= "lib" (:family (fm 'lib.api)))))

    (testing "app.core: Ce-family=2, Ce-external=0"
      (let [m (fm 'app.core)]
        (is (= 2 (:ce-family m)))
        (is (= 0 (:ce-external m)))))

    (testing "app.core: Ca-family=0, Ca-external=1 (lib.api depends on it)"
      (let [m (fm 'app.core)]
        (is (= 0 (:ca-family m)))
        (is (= 1 (:ca-external m)))))

    (testing "app.util: Ca-family=1 (app.core), Ca-external=0"
      (let [m (fm 'app.util)]
        (is (= 1 (:ca-family m)))
        (is (= 0 (:ca-external m)))))

    (testing "lib.api: Ce-family=0, Ce-external=1 (depends on app.core)"
      (let [m (fm 'lib.api)]
        (is (= 0 (:ce-family m)))
        (is (= 1 (:ce-external m)))))

    (testing "lib.api: Ca-family=1 (lib.impl), Ca-external=0"
      (let [m (fm 'lib.api)]
        (is (= 1 (:ca-family m)))
        (is (= 0 (:ca-external m)))))

    (testing "lib.impl: Ce-family=1, Ce-external=0"
      (let [m (fm 'lib.impl)]
        (is (= 1 (:ce-family m)))
        (is (= 0 (:ce-external m)))))

    (testing "lib.impl: Ca-family=0, Ca-external=0 (no dependents)"
      (let [m (fm 'lib.impl)]
        (is (= 0 (:ca-family m)))
        (is (= 0 (:ca-external m)))))

    (testing "Ca-family + Ca-external = total Ca for all nodes"
      (doseq [[ns m] fm]
        (let [total-ca (count (filter (fn [[_ deps]] (contains? deps ns)) graph))]
          (is (= total-ca (+ (:ca-family m) (:ca-external m)))
              (str ns " Ca decomposition")))))

    (testing "Ce-family + Ce-external = total project Ce for all nodes"
      (let [project (set (keys graph))]
        (doseq [[ns m] fm]
          (let [total-ce (count (filter project (get graph ns #{})))]
            (is (= total-ce (+ (:ce-family m) (:ce-external m)))
                (str ns " Ce decomposition"))))))))

(deftest family-metrics-empty-graph-test
  (testing "empty graph → empty metrics"
    (is (= {} (sut/family-metrics {})))))

(deftest family-metrics-single-family-test
  (testing "all namespaces in one family — all coupling is family-scoped"
    (let [g  {'gordian.main #{'gordian.scan 'gordian.close}
              'gordian.scan #{}
              'gordian.close #{}}
          fm (sut/family-metrics g)]
      (doseq [[_ m] fm]
        (is (= 0 (:ca-external m)))
        (is (= 0 (:ce-external m)))))))

;;; ── annotate-conceptual-pair ─────────────────────────────────────────────

(deftest annotate-conceptual-pair-test
  (testing "same-family pair: prefix tokens classified as family-terms"
    (let [pair {:ns-a 'psi.agent-session.core
                :ns-b 'psi.agent-session.mutations
                :score 0.35
                :shared-terms ["agent" "session" "mutation"]}
          result (sut/annotate-conceptual-pair pair)]
      (is (true? (:same-family? result)))
      ;; "agent" and "session" come from prefix "psi.agent-session"
      ;; "mutation" comes from the mutations ns, not the prefix
      (is (= ["agent" "session"] (:family-terms result)))
      (is (= ["mutation"] (:independent-terms result)))))

  (testing "same-family pair: all terms from prefix → empty independent"
    (let [pair {:ns-a 'psi.agent-session.core
                :ns-b 'psi.agent-session.ui
                :score 0.25
                :shared-terms ["agent" "session"]}
          result (sut/annotate-conceptual-pair pair)]
      (is (true? (:same-family? result)))
      (is (= ["agent" "session"] (:family-terms result)))
      (is (= [] (:independent-terms result)))))

  (testing "same-family pair: all terms independent (none from prefix)"
    (let [pair {:ns-a 'gordian.scan
                :ns-b 'gordian.close
                :score 0.30
                :shared-terms ["reach" "transitive" "node"]}
          result (sut/annotate-conceptual-pair pair)]
      (is (true? (:same-family? result)))
      ;; prefix is "gordian" → tokenizes to ["gordian"]
      ;; none of the shared terms are "gordian"
      (is (= [] (:family-terms result)))
      (is (= ["reach" "transitive" "node"] (:independent-terms result)))))

  (testing "different-family pair: all terms are independent"
    (let [pair {:ns-a 'gordian.scan
                :ns-b 'psi.agent-session.core
                :score 0.20
                :shared-terms ["file" "parse"]}
          result (sut/annotate-conceptual-pair pair)]
      (is (false? (:same-family? result)))
      (is (= [] (:family-terms result)))
      (is (= ["file" "parse"] (:independent-terms result)))))

  (testing "empty shared-terms handled"
    (let [pair {:ns-a 'a.x :ns-b 'a.y :score 0.15 :shared-terms []}
          result (sut/annotate-conceptual-pair pair)]
      (is (true? (:same-family? result)))
      (is (= [] (:family-terms result)))
      (is (= [] (:independent-terms result)))))

  (testing "nil shared-terms handled"
    (let [pair {:ns-a 'a.x :ns-b 'a.y :score 0.15}
          result (sut/annotate-conceptual-pair pair)]
      (is (= [] (:family-terms result)))
      (is (= [] (:independent-terms result)))))

  (testing "single-segment namespaces: root family, prefix tokens empty"
    (let [pair {:ns-a 'alpha :ns-b 'beta :score 0.20
                :shared-terms ["data" "transform"]}
          result (sut/annotate-conceptual-pair pair)]
      (is (true? (:same-family? result)))
      ;; prefix "" tokenizes to nil/empty
      (is (= [] (:family-terms result)))
      (is (= ["data" "transform"] (:independent-terms result))))))

(deftest annotate-conceptual-pairs-test
  (testing "annotates all pairs in a vector"
    (let [pairs [{:ns-a 'a.x :ns-b 'a.y :score 0.3 :shared-terms ["foo"]}
                 {:ns-a 'a.x :ns-b 'b.y :score 0.2 :shared-terms ["bar"]}]
          result (sut/annotate-conceptual-pairs pairs)]
      (is (= 2 (count result)))
      (is (true? (:same-family? (first result))))
      (is (false? (:same-family? (second result))))))

  (testing "empty pairs → empty result"
    (is (= [] (sut/annotate-conceptual-pairs [])))))
