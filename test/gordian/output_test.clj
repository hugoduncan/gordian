(ns gordian.output-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.output :as sut]
            [gordian.main   :as main]))

(def fixture-report
  "Unified report matching the fixture graph (alpha←beta←gamma, no cycles)."
  {:src-dirs         ["resources/fixture"]
   :propagation-cost (/ 3.0 9.0)
   :cycles           []
   :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
            :ca 0 :ce 2 :instability 1.0  :role :peripheral}
           {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)
            :ca 1 :ce 1 :instability 0.5  :role :shared}
           {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)
            :ca 2 :ce 0 :instability 0.0  :role :core}]})

;;; ── column-widths ────────────────────────────────────────────────────────

(deftest column-widths-test
  (testing "minimum width is 20"
    (is (= 20 (:ns-col (sut/column-widths [{:ns 'a}])))))

  (testing "expands for long names"
    (let [long-ns 'very.long.namespace.name.here]
      (is (= (count (str long-ns))
             (:ns-col (sut/column-widths [{:ns long-ns}])))))))

;;; ── format-row ───────────────────────────────────────────────────────────

(deftest format-row-test
  (testing "contains ns name and key metrics"
    (let [row (sut/format-row 20 {:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
                                  :ca 0 :ce 2 :instability 1.0})]
      (is (str/includes? row "gamma"))
      (is (str/includes? row "66.7%"))
      (is (str/includes? row "0.0%"))
      (is (str/includes? row "1.00"))))

  (testing "missing metrics fall back to dash"
    (let [row (sut/format-row 20 {:ns 'x :reach 0.0 :fan-in 0.0})]
      (is (str/includes? row "-")))))

;;; ── format-report ────────────────────────────────────────────────────────

(deftest format-report-test
  (let [lines (sut/format-report fixture-report)]
    (testing "header contains 'gordian'"
      (is (str/includes? (first lines) "gordian")))

    (testing "src-dir appears"
      (is (str/includes? (second lines) "resources/fixture")))

    (testing "propagation cost value appears"
      (is (some #(str/includes? % "0.3333") lines)))

    (testing "all namespace names appear"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (some #(str/includes? % ns-name) lines))))

    (testing "column header includes Ca, Ce, I"
      (let [header (some #(str/includes? % "Ce") lines)]
        (is header)))

    (testing "instability values appear"
      (is (some #(str/includes? % "1.00") lines))
      (is (some #(str/includes? % "0.50") lines))
      (is (some #(str/includes? % "0.00") lines)))

    (testing "role column present in header"
      (is (some #(str/includes? % "role") lines)))

    (testing "role values appear"
      (is (some #(str/includes? % "core")       lines))
      (is (some #(str/includes? % "peripheral") lines))
      (is (some #(str/includes? % "shared")     lines)))))

;;; ── cycles section in format-report ─────────────────────────────────────

(deftest format-report-cycles-test
  (testing "no cycles → cycle section absent entirely"
    (let [lines (sut/format-report fixture-report)]
      (is (not (some #(clojure.string/includes? % "cycles") lines)))))

  (testing "with cycles → lists members"
    (let [report (assoc fixture-report :cycles [#{'a 'b}])
          lines  (sut/format-report report)]
      (is (some #(clojure.string/includes? % "cycles:") lines))
      (is (some #(and (clojure.string/includes? % "a")
                      (clojure.string/includes? % "b")) lines))
      (is (some #(clojure.string/includes? % "2 namespaces") lines)))))

;;; ── format-conceptual ────────────────────────────────────────────────────

(def ^:private sample-pairs
  [{:ns-a 'gordian.output  :ns-b 'gordian.json   :score 0.54 :kind :conceptual
    :structural-edge? false :shared-terms ["report" "format" "lines"]}
   {:ns-a 'gordian.classify :ns-b 'gordian.scc   :score 0.48 :kind :conceptual
    :structural-edge? true  :shared-terms ["cycle" "node"]}])

(deftest format-conceptual-test
  (testing "empty pairs → empty vector (section omitted)"
    (is (= [] (sut/format-conceptual [] 0.30))))

  (testing "returns a non-empty vector of strings"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (seq lines))
      (is (every? string? lines))))

  (testing "header includes threshold"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "0.30") lines))))

  (testing "column header present"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "namespace-a") lines))
      (is (some #(str/includes? % "structural") lines))
      (is (some #(str/includes? % "shared concepts") lines))))

  (testing "both namespace names appear"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "gordian.output") lines))
      (is (some #(str/includes? % "gordian.json") lines))))

  (testing "no-edge row shows ← and shared terms"
    (let [lines (sut/format-conceptual sample-pairs 0.30)
          no-row (first (filter #(str/includes? % "gordian.output") lines))]
      (is (str/includes? no-row "←"))
      (is (str/includes? no-row "report"))
      (is (str/includes? no-row "format"))))

  (testing "structural-edge row shows 'yes' and shared terms"
    (let [lines (sut/format-conceptual sample-pairs 0.30)
          yes-row (first (filter #(str/includes? % "gordian.classify") lines))]
      (is (str/includes? yes-row "yes"))
      (is (str/includes? yes-row "cycle"))))

  (testing "similarity score appears"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "0.54") lines))
      (is (some #(str/includes? % "0.48") lines)))))

;;; ── integration: full pipeline via build-report ──────────────────────────

(deftest full-pipeline-integration-test
  (let [output (with-out-str
                 (-> ["resources/fixture"]
                     main/build-report
                     sut/print-report))]
    (testing "output mentions propagation cost"
      (is (str/includes? output "propagation cost")))

    (testing "all fixture namespaces appear"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (str/includes? output ns-name))))

    (testing "PC ≈ 33.3%"
      (is (str/includes? output "33.3")))

    (testing "instability column present"
      (is (str/includes? output "1.00")))

    (testing "Ce and Ca columns present"
      (is (str/includes? output "Ce"))
      (is (str/includes? output "Ca")))

    (testing "role column present"
      (is (str/includes? output "role"))
      (is (str/includes? output "core"))
      (is (str/includes? output "peripheral")))))

;;; ── format-change-coupling ───────────────────────────────────────────────

(def ^:private change-pairs
  [{:ns-a 'gordian.output :ns-b 'gordian.main
    :score 0.6667 :kind :change :co-changes 2
    :confidence-a 0.6667 :confidence-b 1.0
    :structural-edge? true}
   {:ns-a 'gordian.scan :ns-b 'gordian.output
    :score 0.5 :kind :change :co-changes 2
    :confidence-a 0.6667 :confidence-b 0.6667
    :structural-edge? false}])

(deftest format-change-coupling-test
  (testing "empty pairs → empty vector (section omitted)"
    (is (= [] (sut/format-change-coupling [] 0.30))))

  (testing "returns a non-empty vector of strings"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (seq lines))
      (is (every? string? lines))))

  (testing "header includes threshold"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "0.30") lines))))

  (testing "column header present"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "namespace-a") lines))
      (is (some #(str/includes? % "Jaccard") lines))
      (is (some #(str/includes? % "conf-a") lines))
      (is (some #(str/includes? % "structural") lines))))

  (testing "both ns names appear in output"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "gordian.output") lines))
      (is (some #(str/includes? % "gordian.main") lines))
      (is (some #(str/includes? % "gordian.scan") lines))))

  (testing "coupling values appear"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "0.6667") lines))
      (is (some #(str/includes? % "0.5000") lines))))

  (testing "co-change count appears"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % " 2") lines))))

  (testing "structural-edge row shows 'yes' without ←"
    (let [lines  (sut/format-change-coupling change-pairs 0.30)
          yes-row (first (filter #(str/includes? % "gordian.main") lines))]
      (is (str/includes? yes-row "yes"))
      (is (not (str/includes? yes-row "←")))))

  (testing "non-structural row shows ← discovery marker"
    (let [lines   (sut/format-change-coupling change-pairs 0.30)
          no-row  (first (filter #(str/includes? % "gordian.scan") lines))]
      (is (str/includes? no-row "←"))
      (is (not (str/includes? no-row "yes")))))

  (testing "change section absent from report when no :change-pairs"
    (let [lines (sut/format-report fixture-report)]
      (is (not (some #(str/includes? % "change coupling") lines)))))

  (testing "change section present in report when :change-pairs provided"
    (let [report (assoc fixture-report
                        :change-pairs change-pairs
                        :change-threshold 0.30)
          lines  (sut/format-report report)]
      (is (some #(str/includes? % "change coupling") lines))
      (is (some #(str/includes? % "gordian.main") lines)))))

;;; ── dsm output ───────────────────────────────────────────────────────────

(def ^:private dsm-data
  {:src-dirs ["resources/fixture"]
   :ordering {:strategy :dfs-topo :refined? false :alpha 2.0 :beta 0.05 :nodes ['c 'a 'b]}
   :blocks [{:id 0 :members ['c] :size 1 :internal-edge-count 0 :density 0.0}
            {:id 1 :members ['a 'b] :size 2 :internal-edge-count 2 :density 1.0}]
   :edges [{:from 1 :to 0 :edge-count 1}]
   :summary {:block-count 2
             :singleton-block-count 1
             :largest-block-size 2
             :inter-block-edge-count 1
             :density 0.5}
   :details [{:id 1
              :members ['a 'b]
              :size 2
              :internal-edges [[0 1] [1 0]]
              :internal-edge-count 2
              :density 1.0}]})

(deftest format-dsm-test
  (let [lines (sut/format-dsm dsm-data)]
    (testing "shows summary counts"
      (is (some #(str/includes? % "blocks: 2") lines))
      (is (some #(str/includes? % "singleton blocks: 1") lines))
      (is (some #(str/includes? % "largest block: 2") lines))
      (is (some #(str/includes? % "ordering: dfs-topo") lines))
      (is (some #(str/includes? % "alpha: 2.0") lines)))

    (testing "block list includes ids and members"
      (is (some #(str/includes? % "B0") lines))
      (is (some #(str/includes? % "a, b") lines)))

    (testing "inter-block edge section shows counted relations"
      (is (some #(str/includes? % "B1 -> B0") lines)))

    (testing "block detail appears"
      (is (some #(str/includes? % "Block B1") lines))
      (is (some #(str/includes? % "mini-matrix edges") lines)))))

(deftest format-dsm-singleton-only-test
  (let [data {:src-dirs ["resources/fixture"]
              :ordering {:strategy :dfs-topo :refined? false :alpha 2.0 :beta 0.05 :nodes ['a]}
              :blocks [{:id 0 :members ['a] :size 1 :internal-edge-count 0 :density 0.0}]
              :edges []
              :summary {:block-count 1
                        :singleton-block-count 1
                        :largest-block-size 1
                        :inter-block-edge-count 0
                        :density 0.0}
              :details []}
        lines (sut/format-dsm data)]
    (is (some #(str/includes? % "Block details") lines))
    (is (some #(str/includes? % "No multi-namespace blocks.") lines))))

(deftest format-dsm-md-test
  (let [lines (sut/format-dsm-md dsm-data)]
    (testing "includes markdown summary table headers"
      (is (some #(str/includes? % "| Metric | Value |") lines)))

    (testing "includes blocks table columns"
      (is (some #(str/includes? % "| Block | Size | Density | Members |") lines)))

    (testing "includes inter-block table columns"
      (is (some #(str/includes? % "| From | To | Edge count |") lines)))

    (testing "renders block detail heading"
      (is (some #(str/includes? % "## Block B1") lines)))))

;;; ── format-diagnose ──────────────────────────────────────────────────────

(def ^:private diagnose-health
  {:propagation-cost 0.055 :health :healthy :cycle-count 0 :ns-count 14})

(def ^:private diagnose-findings
  [{:severity :high
    :category :cross-lens-hidden
    :subject {:ns-a 'a.core :ns-b 'b.util}
    :reason "hidden in 2 lenses — conceptual=0.35 change=0.40"
    :evidence {:conceptual-score 0.35 :shared-terms ["reach" "close"]
               :change-score 0.40 :co-changes 4
               :confidence-a 0.8 :confidence-b 0.6}}
   {:severity :medium
    :category :hidden-conceptual
    :subject {:ns-a 'c.scan :ns-b 'd.git}
    :reason "hidden conceptual coupling — score=0.25"
    :evidence {:score 0.25 :shared-terms ["file" "path"]}}
   {:severity :low
    :category :hub
    :subject {:ns 'e.main}
    :reason "high-reach hub — 92.9% of project reachable"
    :evidence {:reach 0.929 :ce 13 :instability 1.0 :role :peripheral}}])

(deftest format-diagnose-test
  (let [report {:src-dirs ["src/"]}
        lines  (sut/format-diagnose report diagnose-health diagnose-findings)]

    (testing "empty findings → health section + 0 findings"
      (let [lines (sut/format-diagnose report diagnose-health [])]
        (is (some #(str/includes? % "0 findings") lines))
        (is (some #(str/includes? % "HEALTH") lines))))

    (testing "health section shows PC"
      (is (some #(str/includes? % "5.5%") lines)))

    (testing "health section shows cycles"
      (is (some #(str/includes? % "cycles: none") lines)))

    (testing "health section shows ns count"
      (is (some #(str/includes? % "namespaces: 14") lines)))

    (testing "HIGH marker present"
      (is (some #(str/includes? % "● HIGH") lines)))

    (testing "cross-lens shows both scores"
      (is (some #(str/includes? % "conceptual score=0.35") lines))
      (is (some #(str/includes? % "change     score=0.40") lines)))

    (testing "hidden-conceptual shows shared terms"
      (is (some #(str/includes? % "file, path") lines)))

    (testing "hub shows reach"
      (is (some #(str/includes? % "e.main") lines)))

    (testing "summary line shows counts"
      (is (some #(str/includes? % "1 high, 1 medium, 1 low") lines)))))

(deftest format-diagnose-family-noise-test
  (let [report   {:src-dirs ["src/"]}
        findings [{:severity :low
                   :category :hidden-conceptual
                   :subject {:ns-a 'psi.session.core :ns-b 'psi.session.ui}
                   :reason "hidden conceptual coupling — score=0.30 — likely naming similarity"
                   :evidence {:score 0.30 :shared-terms ["session" "psi"]
                              :same-family? true
                              :family-terms ["session" "psi"]
                              :independent-terms []}}
                  {:severity :medium
                   :category :hidden-conceptual
                   :subject {:ns-a 'psi.session.core :ns-b 'psi.session.mutations}
                   :reason "hidden conceptual coupling — score=0.35"
                   :evidence {:score 0.35 :shared-terms ["session" "mutation"]
                              :same-family? true
                              :family-terms ["session"]
                              :independent-terms ["mutation"]}}]
        lines    (sut/format-diagnose report diagnose-health findings)]

    (testing "family-noise finding shows family terms line"
      (is (some #(str/includes? % "family terms: session, psi") lines)))

    (testing "mixed finding shows both family and independent terms"
      (is (some #(str/includes? % "family terms: session") lines))
      (is (some #(str/includes? % "independent: mutation") lines)))

    (testing "naming similarity appears in reason"
      (is (some #(str/includes? % "naming similarity") lines)))))

;;; ── format-finding-lines: action + next-step ────────────────────────────

(deftest format-finding-action-test
  (let [report {:src-dirs ["src/"]}]

    (testing "finding with known :action → output contains action line"
      (let [f     {:severity :high :category :cross-lens-hidden
                   :action :extract-abstraction :next-step nil
                   :subject {:ns-a 'a.core :ns-b 'b.util}
                   :reason "hidden in 2 lenses"
                   :evidence {:conceptual-score 0.35 :shared-terms []
                              :change-score 0.40 :co-changes 4
                              :confidence-a 0.8 :confidence-b 0.6}}
            lines (sut/format-diagnose report diagnose-health [f])]
        (is (some #(str/includes? % "→ extract abstraction") lines))))

    (testing "finding with :action :none → no action line"
      (let [f     {:severity :low :category :facade
                   :action :none :next-step nil
                   :subject {:ns 'foo.main}
                   :reason "likely façade"
                   :evidence {:reach 0.5 :fan-in 0.2 :ca 3 :ce 0 :role :shared
                              :ca-family 2 :ca-external 1 :ce-family 1 :ce-external 0
                              :family "foo"}}
            lines (sut/format-diagnose report diagnose-health [f])]
        (is (not (some #(str/includes? % "→") lines)))))

    (testing "finding with nil :action → no action line"
      (let [f     {:severity :low :category :hub
                   :action nil :next-step nil
                   :subject {:ns 'foo.main}
                   :reason "high-reach hub"
                   :evidence {:reach 0.9 :ce 5 :instability 1.0 :role :peripheral}}
            lines (sut/format-diagnose report diagnose-health [f])]
        (is (not (some #(str/includes? % "→") lines)))))

    (testing "finding with :next-step → output contains $ command line"
      (let [f     {:severity :high :category :cross-lens-hidden
                   :action :extract-abstraction
                   :next-step "gordian explain-pair a.core b.util"
                   :subject {:ns-a 'a.core :ns-b 'b.util}
                   :reason "hidden in 2 lenses"
                   :evidence {:conceptual-score 0.35 :shared-terms []
                              :change-score 0.40 :co-changes 4
                              :confidence-a 0.8 :confidence-b 0.6}}
            lines (sut/format-diagnose report diagnose-health [f])]
        (is (some #(str/includes? % "$ gordian explain-pair a.core b.util") lines))))

    (testing "finding with nil :next-step → no $ line"
      (let [f     {:severity :high :category :cross-lens-hidden
                   :action :extract-abstraction :next-step nil
                   :subject {:ns-a 'a.core :ns-b 'b.util}
                   :reason "hidden in 2 lenses"
                   :evidence {:conceptual-score 0.35 :shared-terms []
                              :change-score 0.40 :co-changes 4
                              :confidence-a 0.8 :confidence-b 0.6}}
            lines (sut/format-diagnose report diagnose-health [f])]
        (is (not (some #(str/includes? % "$ ") lines)))))))

(deftest format-diagnose-suppressed-count-test
  (let [report {:src-dirs ["src/"]}]

    (testing "suppressed-count > 0 → summary mentions noise suppressed"
      (let [lines (sut/format-diagnose report diagnose-health [] nil :severity 7)]
        (is (some #(str/includes? % "7 noise suppressed") lines))))

    (testing "suppressed-count 0 → no suppressed mention"
      (let [lines (sut/format-diagnose report diagnose-health [] nil :severity 0)]
        (is (not (some #(str/includes? % "noise suppressed") lines)))))

    (testing "suppressed-count nil → no suppressed mention"
      (let [lines (sut/format-diagnose report diagnose-health [] nil :severity nil)]
        (is (not (some #(str/includes? % "noise suppressed") lines)))))

    (testing "suppressed message includes --show-noise hint"
      (let [lines (sut/format-diagnose report diagnose-health [] nil :severity 3)]
        (is (some #(str/includes? % "--show-noise") lines))))))

(deftest format-diagnose-truncated-test
  (let [report {:src-dirs ["src/"]}]

    (testing "truncated-from → header shows top N of M"
      (let [lines (sut/format-diagnose report diagnose-health diagnose-findings nil :severity nil 10)]
        (is (some #(str/includes? % "top 3 of 10 findings") lines))))

    (testing "truncated-from → summary shows top N of M"
      (let [lines (sut/format-diagnose report diagnose-health diagnose-findings nil :severity nil 10)]
        (is (some #(and (str/includes? % "top 3 of 10") (str/includes? % "high")) lines))))

    (testing "no truncated-from → normal count shown"
      (let [lines (sut/format-diagnose report diagnose-health diagnose-findings nil :severity nil nil)]
        (is (some #(str/includes? % "3 findings") lines))
        (is (not (some #(str/includes? % "top") lines)))))))

;;; ── format-explain-ns ────────────────────────────────────────────────────

(def ^:private explain-ns-data
  {:ns 'gordian.scan
   :metrics {:reach 0.0 :fan-in 0.06 :ca 1 :ce 0 :instability 0.0 :role :core}
   :direct-deps {:project [] :external ['babashka.fs 'edamame.core]}
   :direct-dependents ['gordian.main]
   :conceptual-pairs
   [{:ns-a 'gordian.conceptual :ns-b 'gordian.scan :score 0.30
     :kind :conceptual :structural-edge? false :shared-terms ["term" "extract"]}]
   :change-pairs []
   :cycles []})

(deftest format-explain-ns-test
  (let [lines (sut/format-explain-ns explain-ns-data)]

    (testing "header shows ns name"
      (is (some #(str/includes? % "gordian.scan") lines)))

    (testing "shows role and metrics"
      (is (some #(str/includes? % "core") lines))
      (is (some #(str/includes? % "Ca=1") lines)))

    (testing "shows external deps"
      (is (some #(str/includes? % "babashka.fs") lines)))

    (testing "shows dependents"
      (is (some #(str/includes? % "gordian.main") lines)))

    (testing "shows conceptual pairs"
      (is (some #(str/includes? % "0.30") lines))
      (is (some #(str/includes? % "hidden") lines)))

    (testing "shows none for empty change pairs"
      (let [change-section (drop-while #(not (str/includes? % "CHANGE COUPLING")) lines)]
        (is (some #(str/includes? % "(none)") change-section))))

    (testing "shows cycles: none"
      (is (some #(str/includes? % "CYCLES: none") lines))))

  (testing "error data → error message"
    (let [lines (sut/format-explain-ns {:error "not found" :available ['a 'b]})]
      (is (some #(str/includes? % "Error:") lines)))))

;;; ── format-explain-pair ──────────────────────────────────────────────────

(def ^:private explain-pair-data
  {:ns-a 'gordian.aggregate :ns-b 'gordian.close
   :structural {:direct-edge? false :direction nil :shortest-path nil}
   :conceptual {:ns-a 'gordian.aggregate :ns-b 'gordian.close :score 0.37
                :kind :conceptual :structural-edge? false
                :shared-terms ["reach" "transitive" "node"]}
   :change nil
   :finding {:severity :medium :category :hidden-conceptual
             :reason "hidden conceptual coupling — score=0.37"}})

(deftest format-explain-pair-test
  (let [lines (sut/format-explain-pair explain-pair-data)]

    (testing "header shows both ns names"
      (is (some #(and (str/includes? % "gordian.aggregate")
                      (str/includes? % "gordian.close")) lines)))

    (testing "shows no direct edge"
      (is (some #(str/includes? % "direct edge: no") lines)))

    (testing "shows no shortest path"
      (is (some #(str/includes? % "(none)") lines)))

    (testing "shows conceptual score + terms"
      (is (some #(str/includes? % "0.37") lines))
      (is (some #(str/includes? % "reach, transitive, node") lines)))

    (testing "shows no change data"
      (let [change-section (drop-while #(not (str/includes? % "CHANGE COUPLING")) lines)]
        (is (some #(str/includes? % "(no data)") change-section))))

    (testing "shows diagnosis"
      (is (some #(str/includes? % "● MEDIUM") lines))))

  (testing "error data → error message"
    (let [lines (sut/format-explain-pair {:error "not found" :available ['a]})]
      (is (some #(str/includes? % "Error:") lines)))))

(deftest format-explain-pair-verdict-test
  (testing "verdict section appears in text output"
    (let [data  (assoc explain-pair-data
                       :verdict {:category :family-siblings
                                 :explanation "Family siblings with shared domain vocabulary."})
          lines (sut/format-explain-pair data)]
      (is (some #(str/includes? % "VERDICT") lines))
      (is (some #(str/includes? % "family-siblings") lines))
      (is (some #(str/includes? % "Family siblings") lines))))

  (testing "verdict section appears in markdown output"
    (let [data  (assoc explain-pair-data
                       :verdict {:category :likely-missing-abstraction
                                 :explanation "Likely missing abstraction."})
          lines (sut/format-explain-pair-md data)]
      (is (some #(str/includes? % "## Verdict") lines))
      (is (some #(str/includes? % "likely missing abstraction") lines))
      (is (some #(str/includes? % "🔴") lines))))

  (testing "no verdict → no section"
    (let [lines (sut/format-explain-pair explain-pair-data)]
      (is (not (some #(str/includes? % "VERDICT") lines))))))

;;; ── format-report-md ─────────────────────────────────────────────────────

(deftest format-report-md-test
  (let [lines (sut/format-report-md fixture-report)]

    (testing "header starts with # Gordian"
      (is (str/starts-with? (first lines) "# Gordian")))

    (testing "summary table contains propagation cost"
      (is (some #(str/includes? % "33.3%") lines)))

    (testing "namespace table has header"
      (is (some #(str/includes? % "| Namespace |") lines)))

    (testing "all fixture ns names appear"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (some #(str/includes? % ns-name) lines))))

    (testing "no cycles → no cycles section"
      (is (not (some #(str/includes? % "## Cycles") lines)))))

  (testing "conceptual section present when pairs provided"
    (let [report (assoc fixture-report
                        :conceptual-pairs sample-pairs
                        :conceptual-threshold 0.30)
          lines  (sut/format-report-md report)]
      (is (some #(str/includes? % "## Conceptual Coupling") lines))
      (is (some #(str/includes? % "gordian.output") lines))))

  (testing "conceptual section absent when no pairs"
    (let [lines (sut/format-report-md fixture-report)]
      (is (not (some #(str/includes? % "## Conceptual") lines)))))

  (testing "change section present when pairs provided"
    (let [report (assoc fixture-report
                        :change-pairs change-pairs
                        :change-threshold 0.30)
          lines  (sut/format-report-md report)]
      (is (some #(str/includes? % "## Change Coupling") lines))))

  (testing "cycles listed when present"
    (let [report (assoc fixture-report :cycles [#{'a 'b}])
          lines  (sut/format-report-md report)]
      (is (some #(str/includes? % "## Cycles") lines))
      (is (some #(and (str/includes? % "a") (str/includes? % "b")) lines)))))

;;; ── format-diagnose-md ───────────────────────────────────────────────────

(deftest format-diagnose-md-test
  (let [report {:src-dirs ["src/"]}
        lines  (sut/format-diagnose-md report diagnose-health diagnose-findings)]

    (testing "header contains finding count"
      (is (some #(str/includes? % "3 Finding") lines)))

    (testing "health table has propagation cost"
      (is (some #(str/includes? % "5.5%") lines)))

    (testing "🔴 marker for HIGH"
      (is (some #(str/includes? % "🔴") lines)))

    (testing "🟡 marker for MEDIUM"
      (is (some #(str/includes? % "🟡") lines)))

    (testing "🟢 marker for LOW"
      (is (some #(str/includes? % "🟢") lines)))

    (testing "cross-lens shows both scores"
      (is (some #(str/includes? % "Conceptual") lines))
      (is (some #(str/includes? % "Change") lines)))

    (testing "summary line at bottom"
      (is (some #(str/includes? % "1 high, 1 medium, 1 low") lines))))

  (testing "empty findings → 0 findings"
    (let [lines (sut/format-diagnose-md {:src-dirs ["src/"]} diagnose-health [])]
      (is (some #(str/includes? % "0 Finding") lines)))))

;;; ── format-explain-ns-md ─────────────────────────────────────────────────

(deftest format-explain-ns-md-test
  (let [lines (sut/format-explain-ns-md explain-ns-data)]

    (testing "header contains ns name"
      (is (some #(str/includes? % "gordian.scan") lines)))

    (testing "metrics table has Role, Ca, Ce"
      (is (some #(str/includes? % "| Role |") lines))
      (is (some #(str/includes? % "| Ca |") lines)))

    (testing "external deps with backticks"
      (is (some #(str/includes? % "`babashka.fs`") lines)))

    (testing "conceptual pairs as table"
      (is (some #(str/includes? % "| Namespace |") lines))
      (is (some #(str/includes? % "0.30") lines)))

    (testing "empty change pairs → (none)"
      (let [change-section (drop-while #(not (str/includes? % "## Change Coupling")) lines)]
        (is (some #(str/includes? % "(none)") change-section)))))

  (testing "error → error message"
    (let [lines (sut/format-explain-ns-md {:error "not found" :available ['a 'b]})]
      (is (some #(str/includes? % "Error") lines)))))

;;; ── format-explain-pair-md ───────────────────────────────────────────────

(deftest format-explain-pair-md-test
  (let [lines (sut/format-explain-pair-md explain-pair-data)]

    (testing "header contains both ns names"
      (is (some #(and (str/includes? % "gordian.aggregate")
                      (str/includes? % "gordian.close")) lines)))

    (testing "structural table shows edge status"
      (is (some #(str/includes? % "| Direct edge |") lines)))

    (testing "conceptual shows score"
      (is (some #(str/includes? % "0.37") lines)))

    (testing "diagnosis shows severity emoji"
      (is (some #(str/includes? % "🟡") lines))))

  (testing "no change data → (no data)"
    (let [lines (sut/format-explain-pair-md explain-pair-data)
          change-section (drop-while #(not (str/includes? % "## Change Coupling")) lines)]
      (is (some #(str/includes? % "(no data)") change-section)))))

;;; ── compare output ───────────────────────────────────────────────────────

(def compare-diff
  {:gordian/command  :compare
   :before           {:gordian/version "0.2.0" :src-dirs ["src/"]}
   :after            {:gordian/version "0.2.0" :src-dirs ["src/"]}
   :health           {:before {:propagation-cost 0.21 :cycle-count 2 :ns-count 10}
                      :after  {:propagation-cost 0.15 :cycle-count 0 :ns-count 11}
                      :delta  {:propagation-cost -0.06 :cycle-count -2 :ns-count 1}}
   :nodes            {:added   [{:ns 'd.new :metrics {:reach 0.05 :role :peripheral}}]
                      :removed [{:ns 'c.old :metrics {:reach 0.10 :role :core}}]
                      :changed [{:ns 'a.core
                                 :before {:reach 0.30 :instability 0.40 :role :shared}
                                 :after  {:reach 0.25 :instability 0.33 :role :core}
                                 :delta  {:reach -0.05 :instability -0.07
                                          :role {:before :shared :after :core}}}]}
   :cycles           {:added [] :removed [#{:x :y}]}
   :conceptual-pairs {:added   [{:ns-a 'a.core :ns-b 'd.new :score 0.28}]
                      :removed [{:ns-a 'a.core :ns-b 'c.old :score 0.35}]
                      :changed [{:ns-a 'a.core :ns-b 'b.core
                                 :before {:score 0.45} :after {:score 0.32}
                                 :delta {:score -0.13}}]}
   :change-pairs     {:added [] :removed [] :changed []}
   :findings         {:added   [{:severity :medium :category :hidden-conceptual
                                 :subject {:ns-a 'a.core :ns-b 'd.new}
                                 :reason "hidden conceptual coupling — score=0.28"}]
                      :removed [{:severity :high :category :cycle
                                 :subject {:members #{:x :y}}
                                 :reason "2-namespace cycle"}]}})

(deftest format-compare-test
  (let [lines (sut/format-compare compare-diff)
        text  (str/join "\n" lines)]

    (testing "header"
      (is (str/includes? text "gordian compare")))

    (testing "health section shows before → after"
      (is (str/includes? text "21.0%"))
      (is (str/includes? text "15.0%")))

    (testing "namespace changes"
      (is (str/includes? text "d.new"))
      (is (str/includes? text "c.old"))
      (is (str/includes? text "a.core")))

    (testing "cycles"
      (is (str/includes? text "✅ removed")))

    (testing "conceptual pairs"
      (is (str/includes? text "0.28"))
      (is (str/includes? text "0.35"))
      (is (str/includes? text "0.45"))
      (is (str/includes? text "0.32")))

    (testing "findings"
      (is (str/includes? text "hidden-conceptual"))
      (is (str/includes? text "cycle")))))

(deftest format-compare-md-test
  (let [lines (sut/format-compare-md compare-diff)
        text  (str/join "\n" lines)]

    (testing "markdown header"
      (is (str/includes? text "# gordian compare")))

    (testing "health table"
      (is (str/includes? text "| Propagation cost |"))
      (is (str/includes? text "21.0%"))
      (is (str/includes? text "15.0%")))

    (testing "namespace sections"
      (is (str/includes? text "`d.new`"))
      (is (str/includes? text "`c.old`")))

    (testing "cycles section"
      (is (str/includes? text "✅ Removed")))

    (testing "conceptual pairs section"
      (is (str/includes? text "## Conceptual Pairs")))

    (testing "findings section"
      (is (str/includes? text "## Findings")))))

(deftest format-compare-empty-diff-test
  (let [empty-diff {:gordian/command :compare
                    :before {:gordian/version "0.2.0" :src-dirs ["src/"]}
                    :after  {:gordian/version "0.2.0" :src-dirs ["src/"]}
                    :health {:before {:propagation-cost 0.10 :cycle-count 0 :ns-count 5}
                             :after  {:propagation-cost 0.10 :cycle-count 0 :ns-count 5}
                             :delta  {:propagation-cost 0.0 :cycle-count 0 :ns-count 0}}
                    :nodes  {:added [] :removed [] :changed []}
                    :cycles {:added [] :removed []}
                    :conceptual-pairs {:added [] :removed [] :changed []}
                    :change-pairs     {:added [] :removed [] :changed []}
                    :findings         {:added [] :removed []}}
        lines (sut/format-compare empty-diff)
        text  (str/join "\n" lines)]

    (testing "health is always shown"
      (is (str/includes? text "HEALTH")))

    (testing "empty sections are omitted"
      (is (not (str/includes? text "NAMESPACES")))
      (is (not (str/includes? text "CYCLES")))
      (is (not (str/includes? text "CONCEPTUAL PAIRS")))
      (is (not (str/includes? text "FINDINGS"))))))

;;; ── gate output ─────────────────────────────────────────────────────────

(def gate-result-data
  {:gordian/command :gate
   :baseline-file "baseline.edn"
   :result :fail
   :src-dirs ["src/"]
   :checks [{:name :pc-delta :status :pass :actual 0.004 :limit 0.01}
            {:name :new-cycles :status :fail :actual 1 :limit 0}
            {:name :new-high-findings :status :pass :actual 0 :limit 0}]
   :summary {:passed 2 :failed 1 :total 3}
   :warnings [{:kind :src-dirs-mismatch :baseline ["src/"] :current ["src/" "test/"]}]})

(deftest format-gate-test
  (let [text (str/join "\n" (sut/format-gate gate-result-data))]
    (is (str/includes? text "gordian gate — FAIL"))
    (is (str/includes? text "baseline.edn"))
    (is (str/includes? text "CHECKS"))
    (is (str/includes? text "pc-delta"))
    (is (str/includes? text "new-cycles"))
    (is (str/includes? text "WARNINGS"))
    (is (str/includes? text "SUMMARY"))))

(deftest format-gate-md-test
  (let [text (str/join "\n" (sut/format-gate-md gate-result-data))]
    (is (str/includes? text "# gordian gate — FAIL"))
    (is (str/includes? text "| Check | Status | Actual | Limit |"))
    (is (str/includes? text "baseline.edn"))
    (is (str/includes? text "## Warnings"))
    (is (str/includes? text "## Summary"))))

(deftest format-diagnose-actionability-test
  (let [fs [{:severity :medium
             :category :hidden-conceptual
             :subject {:ns-a 'a.core :ns-b 'b.svc}
             :reason "hidden conceptual coupling"
             :actionability-score 7.8
             :evidence {:score 0.35 :same-family? false}}]
        text (str/join "\n" (sut/format-diagnose {:src-dirs ["src/"]}
                                                 {:propagation-cost 0.1 :health :healthy
                                                  :cycle-count 0 :ns-count 2}
                                                 fs nil :actionability))
        md   (str/join "\n" (sut/format-diagnose-md {:src-dirs ["src/"]}
                                                    {:propagation-cost 0.1 :health :healthy
                                                     :cycle-count 0 :ns-count 2}
                                                    fs nil :actionability))]
    (is (str/includes? text "rank: actionability"))
    (is (str/includes? text "[act=7.8]"))
    (is (str/includes? md "**Rank:** `actionability`"))
    (is (str/includes? md "[act=7.8]"))))

;;; ── subgraph output ─────────────────────────────────────────────────────

(def subgraph-data
  {:gordian/command :subgraph
   :prefix "gordian"
   :rank-by :actionability
   :members ['gordian.close 'gordian.main 'gordian.scan]
   :internal {:node-count 3
              :edge-count 3
              :density 0.5
              :propagation-cost 0.33
              :cycles []}
   :boundary {:incoming-count 1
              :outgoing-count 2
              :dependents ['other.ns]
              :external-deps ['app.main 'clojure.set]}
   :pairs {:conceptual {:internal [{:ns-a 'gordian.scan :ns-b 'gordian.close
                                    :score 0.40 :actionability-score 8.8}]}
           :change {:internal []}}
   :findings [{:severity :medium
               :category :hidden-conceptual
               :subject {:ns-a 'gordian.scan :ns-b 'gordian.close}
               :reason "hidden conceptual coupling"
               :actionability-score 8.8
               :evidence {:score 0.40 :same-family? false}}]
   :clusters []})

(deftest format-subgraph-test
  (let [text (str/join "\n" (sut/format-subgraph subgraph-data))]
    (is (str/includes? text "gordian subgraph — gordian"))
    (is (str/includes? text "MEMBERS"))
    (is (str/includes? text "INTERNAL"))
    (is (str/includes? text "BOUNDARY"))
    (is (str/includes? text "INTERNAL CONCEPTUAL PAIRS"))
    (is (str/includes? text "[act=8.8]"))))

(deftest format-subgraph-md-test
  (let [text (str/join "\n" (sut/format-subgraph-md subgraph-data))]
    (is (str/includes? text "# gordian subgraph — gordian"))
    (is (str/includes? text "## Members"))
    (is (str/includes? text "## Internal"))
    (is (str/includes? text "## Boundary"))
    (is (str/includes? text "## Internal Conceptual Pairs"))
    (is (str/includes? text "[act=8.8]"))))

;;; ── communities output ──────────────────────────────────────────────────

(def communities-data
  {:gordian/command :communities
   :lens :combined
   :threshold 0.75
   :communities [{:id 1
                  :members ['a 'b 'c]
                  :size 3
                  :density 0.67
                  :internal-weight 2.40
                  :boundary-weight 0.50
                  :dominant-terms ["state" "event"]
                  :bridge-namespaces ['b]}
                 {:id 2
                  :members ['x]
                  :size 1
                  :density 0.0
                  :internal-weight 0.0
                  :boundary-weight 0.0
                  :dominant-terms []
                  :bridge-namespaces []}]
   :summary {:community-count 2 :largest-size 3 :singleton-count 1}})

(deftest format-communities-test
  (let [text (str/join "\n" (sut/format-communities communities-data))]
    (is (str/includes? text "gordian communities — combined"))
    (is (str/includes? text "SUMMARY"))
    (is (str/includes? text "communities: 2"))
    (is (str/includes? text "dominant terms: state, event"))
    (is (str/includes? text "bridges: b"))))

(deftest format-communities-md-test
  (let [text (str/join "\n" (sut/format-communities-md communities-data))]
    (is (str/includes? text "# gordian communities — combined"))
    (is (str/includes? text "## Summary"))
    (is (str/includes? text "## Community 1"))
    (is (str/includes? text "`state`"))
    (is (str/includes? text "### Members"))))

;;; ── tests output ─────────────────────────────────────────────────────────

(def tests-data
  {:gordian/command :tests
   :summary {:src-count 3
             :test-count 3
             :test-role-counts {:executable 2 :support 1}
             :test-style-counts {:unit-ish 1 :integration-ish 1 :support 1}
             :pc-src 0.10
             :pc-with-tests 0.22
             :pc-delta 0.12
             :src->test-edge-count 1
             :test-support-leaked-to-src-count 1
             :executable-tests-with-incoming-deps-count 1}
   :invariants {:shared-test-support [{:ns 'app.test-support}]}
   :core-coverage {:tested-core [{:ns 'app.core}]
                   :untested-core [{:ns 'app.db}]}
   :pc-summary {:interpretation :over-coupled}
   :test-namespaces [{:ns 'app.core-test
                      :test-role :executable
                      :test-style :unit-ish
                      :reach 0.05
                      :ca 0
                      :ce 2
                      :role :isolated}
                     {:ns 'app.integration-test
                      :test-role :executable
                      :test-style :integration-ish
                      :reach 0.55
                      :ca 0
                      :ce 1
                      :role :peripheral}
                     {:ns 'app.test-support
                      :test-role :support
                      :test-style :support
                      :reach 0.0
                      :ca 1
                      :ce 0
                      :role :core}]
   :findings [{:severity :high
               :category :src-depends-on-test
               :subject {:ns 'app.core}
               :reason "production namespace depends on test namespace app.test-support"
               :evidence {:src 'app.core :test 'app.test-support :test-role :support}}
              {:severity :medium
               :category :untested-core
               :subject {:ns 'app.db}
               :reason "core namespace gains no direct test dependents"
               :evidence {:ns 'app.db :role :core :ca-src 1 :ca-with-tests 1 :ca-delta 0}}]})

(deftest format-tests-test
  (let [text (str/join "\n" (sut/format-tests tests-data))]
    (is (str/includes? text "gordian tests"))
    (is (str/includes? text "SUMMARY"))
    (is (str/includes? text "INVARIANTS"))
    (is (str/includes? text "CORE COVERAGE"))
    (is (str/includes? text "TEST NAMESPACES"))
    (is (str/includes? text "role=executable"))
    (is (str/includes? text "style=integration-ish"))
    (is (str/includes? text "FINDINGS"))
    (is (str/includes? text "● HIGH"))))

(deftest format-tests-md-test
  (let [text (str/join "\n" (sut/format-tests-md tests-data))]
    (is (str/includes? text "# gordian tests"))
    (is (str/includes? text "## Summary"))
    (is (str/includes? text "## Invariants"))
    (is (str/includes? text "## Core Coverage"))
    (is (str/includes? text "## Test Namespaces"))
    (is (str/includes? text "`integration-ish`"))
    (is (str/includes? text "## Findings"))
    (is (str/includes? text "production namespace depends on test namespace"))))

;;; ── cyclomatic output ───────────────────────────────────────────────────

(def cyclomatic-data
  {:gordian/command :complexity
   :src-dirs ["resources/fixture"]
   :units [{:ns 'sample.core :var 'branchy :arity 1 :cc 3 :cc-decision-count 2 :cc-risk {:level :simple :label "Simple, low risk"}}
           {:ns 'sample.core :var 'simple :arity 1 :cc 1 :cc-decision-count 0 :cc-risk {:level :simple :label "Simple, low risk"}}
           {:ns 'sample.util :var 'helper :arity 1 :cc 3 :cc-decision-count 2 :cc-risk {:level :simple :label "Simple, low risk"}}]
   :namespace-rollups [{:ns 'sample.core
                        :unit-count 2
                        :total-cc 4
                        :avg-cc 2.0
                        :max-cc 3
                        :cc-risk-counts {:simple 2 :moderate 0 :high 0 :untestable 0}}
                       {:ns 'sample.util
                        :unit-count 1
                        :total-cc 3
                        :avg-cc 3.0
                        :max-cc 3
                        :cc-risk-counts {:simple 1 :moderate 0 :high 0 :untestable 0}}]
   :project-rollup {:unit-count 3
                    :namespace-count 2
                    :total-cc 7
                    :avg-cc (/ 7.0 3)
                    :max-cc 3
                    :cc-risk-counts {:simple 3 :moderate 0 :high 0 :untestable 0}}
   :max-unit {:ns 'sample.core :var 'branchy :arity 1 :cc 3}})

(deftest format-complexity-test
  (let [lines (sut/format-complexity cyclomatic-data)
        text  (str/join "\n" lines)
        unit-rows (filter #(and (str/includes? % "[arity") (str/includes? % "█")) lines)
        bar-cols  (map #(.indexOf % "█") unit-rows)]
    (is (str/includes? text "gordian complexity"))
    (is (str/includes? text "SUMMARY"))
    (is (str/includes? text "namespaces: 2"))
    (is (str/includes? text "units: 3"))
    (is (str/includes? text "sample.core/branchy [arity 1] (3)"))
    (is (str/includes? text "UNITS"))
    (is (str/includes? text "unit"))
    (is (str/includes? text "risk"))
    (is (str/includes? text "decisions"))
    (is (str/includes? text "NAMESPACE ROLLUP"))
    (is (str/includes? text "PROJECT ROLLUP"))
    (is (str/includes? text "branchy [arity 1]"))
    (is (str/includes? text "███"))
    (is (apply = bar-cols))))

(deftest format-complexity-md-test
  (let [text (str/join "\n" (sut/format-complexity-md cyclomatic-data))]
    (is (str/includes? text "# gordian complexity"))
    (is (str/includes? text "## Summary"))
    (is (str/includes? text "| Metric | Value |"))
    (is (str/includes? text "`sample.core/branchy [arity 1]` (3)"))
    (is (str/includes? text "## Units"))
    (is (str/includes? text "| Unit | CC | Risk | Decisions |"))
    (is (str/includes? text "## Namespace rollup"))
    (is (str/includes? text "## Project rollup"))))
