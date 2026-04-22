(ns gordian.output.diagnose-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gordian.output :as sut]
            [gordian.output.fixtures :as fx]))

(deftest format-diagnose-test
  (let [report {:src-dirs ["src/"]}
        lines  (sut/format-diagnose report fx/diagnose-health fx/diagnose-findings)]
    (testing "empty findings → health section + 0 findings"
      (let [lines (sut/format-diagnose report fx/diagnose-health [])]
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
        lines    (sut/format-diagnose report fx/diagnose-health findings)]
    (testing "family-noise finding shows family terms line"
      (is (some #(str/includes? % "family terms: session, psi") lines)))
    (testing "mixed finding shows both family and independent terms"
      (is (some #(str/includes? % "family terms: session") lines))
      (is (some #(str/includes? % "independent: mutation") lines)))
    (testing "naming similarity appears in reason"
      (is (some #(str/includes? % "naming similarity") lines)))))

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
            lines (sut/format-diagnose report fx/diagnose-health [f])]
        (is (some #(str/includes? % "→ extract abstraction") lines))))
    (testing "finding with :action :none → no action line"
      (let [f     {:severity :low :category :facade
                   :action :none :next-step nil
                   :subject {:ns 'foo.main}
                   :reason "likely façade"
                   :evidence {:reach 0.5 :fan-in 0.2 :ca 3 :ce 0 :role :shared
                              :ca-family 2 :ca-external 1 :ce-family 1 :ce-external 0
                              :family "foo"}}
            lines (sut/format-diagnose report fx/diagnose-health [f])]
        (is (not (some #(str/includes? % "→") lines)))))
    (testing "finding with nil :action → no action line"
      (let [f     {:severity :low :category :hub
                   :action nil :next-step nil
                   :subject {:ns 'foo.main}
                   :reason "high-reach hub"
                   :evidence {:reach 0.9 :ce 5 :instability 1.0 :role :peripheral}}
            lines (sut/format-diagnose report fx/diagnose-health [f])]
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
            lines (sut/format-diagnose report fx/diagnose-health [f])]
        (is (some #(str/includes? % "$ gordian explain-pair a.core b.util") lines))))
    (testing "finding with nil :next-step → no $ line"
      (let [f     {:severity :high :category :cross-lens-hidden
                   :action :extract-abstraction :next-step nil
                   :subject {:ns-a 'a.core :ns-b 'b.util}
                   :reason "hidden in 2 lenses"
                   :evidence {:conceptual-score 0.35 :shared-terms []
                              :change-score 0.40 :co-changes 4
                              :confidence-a 0.8 :confidence-b 0.6}}
            lines (sut/format-diagnose report fx/diagnose-health [f])]
        (is (not (some #(str/includes? % "$ ") lines)))))))

(deftest format-diagnose-suppressed-count-test
  (let [report {:src-dirs ["src/"]}]
    (testing "suppressed-count > 0 → summary mentions noise suppressed"
      (let [lines (sut/format-diagnose report fx/diagnose-health [] nil :severity 7)]
        (is (some #(str/includes? % "7 noise suppressed") lines))))
    (testing "suppressed-count 0 → no suppressed mention"
      (let [lines (sut/format-diagnose report fx/diagnose-health [] nil :severity 0)]
        (is (not (some #(str/includes? % "noise suppressed") lines)))))
    (testing "suppressed-count nil → no suppressed mention"
      (let [lines (sut/format-diagnose report fx/diagnose-health [] nil :severity nil)]
        (is (not (some #(str/includes? % "noise suppressed") lines)))))
    (testing "suppressed message includes --show-noise hint"
      (let [lines (sut/format-diagnose report fx/diagnose-health [] nil :severity 3)]
        (is (some #(str/includes? % "--show-noise") lines))))))

(deftest format-diagnose-truncated-test
  (let [report {:src-dirs ["src/"]}]
    (testing "truncated-from → header shows top N of M"
      (let [lines (sut/format-diagnose report fx/diagnose-health fx/diagnose-findings nil :severity nil 10)]
        (is (some #(str/includes? % "top 3 of 10 findings") lines))))
    (testing "truncated-from → summary shows top N of M"
      (let [lines (sut/format-diagnose report fx/diagnose-health fx/diagnose-findings nil :severity nil 10)]
        (is (some #(and (str/includes? % "top 3 of 10") (str/includes? % "high")) lines))))
    (testing "no truncated-from → normal count shown"
      (let [lines (sut/format-diagnose report fx/diagnose-health fx/diagnose-findings nil :severity nil nil)]
        (is (some #(str/includes? % "3 findings") lines))
        (is (not (some #(str/includes? % "top") lines)))))))

(deftest format-diagnose-md-test
  (let [report {:src-dirs ["src/"]}
        lines  (sut/format-diagnose-md report fx/diagnose-health fx/diagnose-findings)]
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
    (let [lines (sut/format-diagnose-md {:src-dirs ["src/"]} fx/diagnose-health [])]
      (is (some #(str/includes? % "0 Finding") lines)))))

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
