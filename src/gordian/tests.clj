(ns gordian.tests
  "Pure helpers for test-architecture analysis."
  (:require [clojure.string :as str]
            [gordian.finding :as finding]))

(def ^:private support-fragments
  ["support" "helper" "helpers" "fixture" "fixtures"
   "util" "utils" "assert" "assertions"
   "builder" "builders" "generator" "generators"
   "stub" "stubs" "mock" "mocks"])

(def ^:private integration-fragments
  ["integration" "system" "api" "e2e" "acceptance" "smoke"])

(defn support-test-ns?
  "True when the namespace name suggests shared test support.
  Heuristic only: based on common support/helper fragments in the ns name."
  [ns-sym]
  (let [s (str ns-sym)]
    (boolean (some #(str/includes? s %) support-fragments))))

(defn- integration-cue?
  "True when the namespace name suggests an integration/system style test."
  [ns-sym]
  (let [s (str ns-sym)]
    (boolean (some #(str/includes? s %) integration-fragments))))

(defn test-role
  "Classify a test-tree namespace as :support or :executable."
  [ns-sym]
  (if (support-test-ns? ns-sym) :support :executable))

(defn- src-only-graph
  "Restrict graph to source namespaces only, preserving only src→src edges."
  [graph origins]
  (let [src-ns (set (keep (fn [[ns-sym kind]] (when (= :src kind) ns-sym)) origins))]
    (into {}
          (map (fn [[ns-sym deps]]
                 [ns-sym (set (filter src-ns deps))]))
          (filter (fn [[ns-sym _]] (contains? src-ns ns-sym)) graph))))

(defn- incoming-index
  "Build {ns -> #{incoming-dependents}} from a direct dependency graph."
  [graph]
  (reduce-kv (fn [acc ns-sym deps]
               (reduce (fn [m dep]
                         (update m dep (fnil conj #{}) ns-sym))
                       acc
                       deps))
             {}
             graph))

(defn- mean
  [xs]
  (if (seq xs)
    (/ (reduce + 0.0 xs) (count xs))
    0.0))

(defn- test-style
  "Classify one test profile as :support, :unit-ish, :integration-ish, or :mixed.
  Thresholds map accepts :high-reach, :high-ce, :low-reach, :low-ce."
  [{:keys [test-role reach ce integration-cue?]} {:keys [high-reach high-ce low-reach low-ce]}]
  (cond
    (= test-role :support) :support
    (or integration-cue?
        (>= (or reach 0.0) high-reach)
        (>= (or ce 0) high-ce)) :integration-ish
    (and (<= (or reach 0.0) low-reach)
         (<= (or ce 0) low-ce)) :unit-ish
    :else :mixed))

(defn- classify-test-styles
  "Attach :test-style to test profiles.
  Thresholds are derived from executable-test distributions with conservative
  floors/caps so small fixtures remain deterministic.

  The integration-ish threshold is intentionally permissive: obviously broad
  tests should classify as integration-ish even in tiny projects where the
  mean itself is inflated by a small sample."
  [profiles]
  (let [execs      (filter #(= :executable (:test-role %)) profiles)
        mean-reach (mean (map :reach execs))
        mean-ce    (mean (map :ce execs))
        thresholds {:high-reach (max 0.20 (* 1.5 mean-reach))
                    :high-ce    (long (Math/ceil (max 4.0 (* 1.5 mean-ce))))
                    :low-reach  (min 0.08 (* 1.25 mean-reach))
                    :low-ce     2}]
    (mapv #(assoc % :test-style (test-style % thresholds)) profiles)))

(defn test-profiles
  "Return per-test namespace profiles from a full structural report + origins.
  Each profile includes structural metrics plus derived :test-role and :test-style."
  [full-report origins]
  (let [test-ns   (set (keep (fn [[ns-sym kind]] (when (= :test kind) ns-sym)) origins))
        nodes     (filter #(contains? test-ns (:ns %)) (:nodes full-report))
        profiles  (mapv (fn [{:keys [ns] :as node}]
                          (assoc (select-keys node [:ns :reach :fan-in :ca :ce :instability :role])
                                 :origin :test
                                 :test-role (test-role ns)
                                 :integration-cue? (integration-cue? ns)))
                        nodes)]
    (classify-test-styles profiles)))

(defn- src->test-edges
  "Return direct src→test dependency edges."
  [graph origins profiles]
  (let [role-by-ns (into {} (map (juxt :ns :test-role)) profiles)]
    (vec
     (for [[src deps] graph
           :when (= :src (get origins src))
           dep deps
           :when (= :test (get origins dep))]
       {:src src :test dep :test-role (get role-by-ns dep)}))))

(defn- test->test-edges
  "Return direct test→test dependency edges annotated with target test role."
  [graph origins profiles]
  (let [role-by-ns (into {} (map (juxt :ns :test-role)) profiles)]
    (vec
     (for [[from deps] graph
           :when (= :test (get origins from))
           dep deps
           :when (= :test (get origins dep))]
       {:from from :to dep :to-role (get role-by-ns dep)}))))

(defn- executable-tests-with-incoming-deps
  "Executable test namespaces with any incoming project deps. Incoming source
  deps are more severe, but plain test reuse is still surfaced here."
  [graph origins profiles]
  (let [incoming   (incoming-index graph)
        profile-by (into {} (map (juxt :ns identity)) profiles)]
    (->> profiles
         (filter #(= :executable (:test-role %)))
         (keep (fn [{:keys [ns ca] :as _profile}]
                 (let [incoming-ns   (sort (get incoming ns #{}))
                       incoming-src  (vec (filter #(= :src (get origins %)) incoming-ns))
                       incoming-test (vec (filter #(= :test (get origins %)) incoming-ns))]
                   (when (or (pos? (or ca 0)) (seq incoming-ns))
                     {:ns ns
                      :ca (or ca 0)
                      :incoming-src incoming-src
                      :incoming-test incoming-test
                      :from-support (vec (filter #(= :support (:test-role (get profile-by %))) incoming-test))
                      :from-executable (vec (filter #(= :executable (:test-role (get profile-by %))) incoming-test))}))))
         vec)))

(defn- support-leaked-to-src
  "Support test namespaces that have incoming source deps."
  [graph origins profiles]
  (let [incoming (incoming-index graph)]
    (->> profiles
         (filter #(= :support (:test-role %)))
         (keep (fn [{:keys [ns ca]}]
                 (let [incoming-ns  (sort (get incoming ns #{}))
                       incoming-src (vec (filter #(= :src (get origins %)) incoming-ns))]
                   (when (seq incoming-src)
                     {:ns ns
                      :ca (or ca 0)
                      :incoming-src incoming-src}))))
         vec)))

(defn- shared-test-support
  "Support test namespaces with incoming deps only from tests. Usually healthy,
  but surfaced for visibility."
  [graph origins profiles]
  (let [incoming (incoming-index graph)]
    (->> profiles
         (filter #(= :support (:test-role %)))
         (keep (fn [{:keys [ns ca]}]
                 (let [incoming-ns   (sort (get incoming ns #{}))
                       incoming-src  (vec (filter #(= :src (get origins %)) incoming-ns))
                       incoming-test (vec (filter #(= :test (get origins %)) incoming-ns))]
                   (when (and (empty? incoming-src) (seq incoming-test))
                     {:ns ns
                      :ca (or ca 0)
                      :incoming-test incoming-test}))))
         vec)))

(defn- mixed-cycles
  "Return SCCs that span both source and test namespaces."
  [cycles origins]
  (->> cycles
       (keep (fn [members]
               (let [src-members  (vec (sort (filter #(= :src (get origins %)) members)))
                     test-members (vec (sort (filter #(= :test (get origins %)) members)))]
                 (when (and (seq src-members) (seq test-members))
                   {:members (vec (sort members))
                    :src-members src-members
                    :test-members test-members}))))
       vec))

(defn invariants
  "Assemble test-architecture invariants and edge categories from a full report."
  [full-report origins profiles]
  {:src->test-edges                     (src->test-edges (:graph full-report) origins profiles)
   :test->test-edges                    (test->test-edges (:graph full-report) origins profiles)
   :executable-tests-with-incoming-deps (executable-tests-with-incoming-deps (:graph full-report) origins profiles)
   :support-leaked-to-src               (support-leaked-to-src (:graph full-report) origins profiles)
   :shared-test-support                 (shared-test-support (:graph full-report) origins profiles)
   :mixed-cycles                        (mixed-cycles (:cycles full-report) origins)})

(defn core-coverage
  "Compare source-only vs src+test Ca for core source namespaces.
  Returns {:tested-core [...] :untested-core [...]}, where tested means
  the namespace gained incoming direct dependents when tests were included."
  [src-report full-report origins]
  (let [src-origins (set (keep (fn [[ns-sym kind]] (when (= :src kind) ns-sym)) origins))
        src-by-ns   (into {} (map (juxt :ns identity)) (:nodes src-report))
        full-by-ns  (into {} (map (juxt :ns identity)) (:nodes full-report))
        rows        (->> src-by-ns
                         (keep (fn [[ns-sym src-node]]
                                 (let [full-node (get full-by-ns ns-sym)]
                                   (when (and (contains? src-origins ns-sym)
                                              (= :core (:role src-node))
                                              full-node)
                                     {:ns ns-sym
                                      :role :core
                                      :ca-src (or (:ca src-node) 0)
                                      :ca-with-tests (or (:ca full-node) 0)
                                      :ca-delta (- (or (:ca full-node) 0)
                                                   (or (:ca src-node) 0))}))))
                         (sort-by (juxt (comp - :ca-delta) :ns))
                         vec)]
    {:tested-core   (vec (filter #(pos? (:ca-delta %)) rows))
     :untested-core (vec (filter #(zero? (:ca-delta %)) rows))}))

(defn pc-summary
  "Compare propagation cost of src-only vs src+test views.
  Interpretation follows the practical-guide / skill guidance:
    small delta   -> targeted tests
    large delta   -> tests over-coupled
    near-zero     -> tests may miss integration pressure"
  [src-report full-report]
  (let [pc-src         (double (or (:propagation-cost src-report) 0.0))
        pc-with-tests  (double (or (:propagation-cost full-report) 0.0))
        pc-delta       (- pc-with-tests pc-src)
        interpretation (cond
                         (>= pc-delta 0.10) :over-coupled
                         (<= pc-delta 0.01) :no-integration-pressure
                         :else              :targeted)]
    {:pc-src pc-src
     :pc-with-tests pc-with-tests
     :pc-delta pc-delta
     :interpretation interpretation}))

(defn- severity-rank [s]
  (case s :high 0 :medium 1 :low 2 3))

(defn- finding-sort-key [f]
  [(severity-rank (:severity f))
   (- (finding/finding-magnitude f))])

(defn- find-src-depends-on-test
  [edges]
  (mapv (fn [{:keys [src test test-role]}]
          {:severity :high
           :category :src-depends-on-test
           :subject  {:ns src}
           :reason   (str "production namespace depends on test namespace " test)
           :evidence {:src src :test test :test-role test-role}})
        edges))

(defn- find-test-support-leaked-to-src
  [rows]
  (mapv (fn [{:keys [ns incoming-src ca]}]
          {:severity :high
           :category :test-support-leaked-to-src
           :subject  {:ns ns}
           :reason   "test support namespace is depended on by source namespaces"
           :evidence {:ns ns :ca ca :incoming-src incoming-src}})
        rows))

(defn- find-test-executable-has-incoming-deps
  [rows]
  (mapv (fn [{:keys [ns ca incoming-src incoming-test from-executable from-support]}]
          {:severity (if (seq incoming-src) :high :medium)
           :category :test-executable-has-incoming-deps
           :subject  {:ns ns}
           :reason   (if (seq incoming-src)
                       "executable test has incoming source deps"
                       "executable test is reused by other tests")
           :evidence {:ns ns
                      :ca ca
                      :incoming-src incoming-src
                      :incoming-test incoming-test
                      :from-executable from-executable
                      :from-support from-support}})
        rows))

(defn- find-mixed-cycles
  [rows]
  (mapv (fn [{:keys [members src-members test-members]}]
          {:severity :high
           :category :mixed-cycle
           :subject  {:members members}
           :reason   "cycle spans production and test namespaces"
           :evidence {:members members
                      :src-members src-members
                      :test-members test-members
                      :size (count members)}})
        rows))

(defn- find-unit-test-too-broad
  [profiles]
  (->> profiles
       (filter #(and (= :executable (:test-role %))
                     (= :integration-ish (:test-style %))
                     (not (:integration-cue? %))
                     (or (>= (or (:reach %) 0.0) 0.30)
                         (>= (or (:ce %) 0) 3))))
       (mapv (fn [{:keys [ns reach ce role]}]
               {:severity :medium
                :category :unit-test-too-broad
                :subject  {:ns ns}
                :reason   "test may be broader than a focused unit test"
                :evidence {:ns ns :reach reach :ce ce :role role}}))))

(defn- find-integration-test-very-broad
  [profiles]
  (->> profiles
       (filter #(and (= :executable (:test-role %))
                     (= :integration-ish (:test-style %))
                     (:integration-cue? %)
                     (or (>= (or (:reach %) 0.0) 0.50)
                         (>= (or (:ce %) 0) 8))))
       (mapv (fn [{:keys [ns reach ce role]}]
               {:severity :low
                :category :integration-test-very-broad
                :subject  {:ns ns}
                :reason   "integration-style test pulls in a very broad slice of the system"
                :evidence {:ns ns :reach reach :ce ce :role role}}))))

(defn- find-untested-core
  [coverage]
  (mapv (fn [{:keys [ns ca-src ca-with-tests ca-delta role]}]
          {:severity :medium
           :category :untested-core
           :subject  {:ns ns}
           :reason   "core namespace gains no direct test dependents"
           :evidence {:ns ns
                      :role role
                      :ca-src ca-src
                      :ca-with-tests ca-with-tests
                      :ca-delta ca-delta}})
        (:untested-core coverage)))

(defn- find-suite-coupling-findings
  [pc]
  (case (:interpretation pc)
    :over-coupled
    [{:severity :medium
      :category :over-coupled-tests
      :subject  {:suite :tests}
      :reason   "adding tests substantially increases propagation cost"
      :evidence pc}]

    :no-integration-pressure
    [{:severity :low
      :category :tests-miss-coupling-core
      :subject  {:suite :tests}
      :reason   "adding tests barely changes propagation cost; suite may miss broader integration pressure"
      :evidence pc}]

    []))

(defn tests-findings
  "Generate ranked findings for test mode."
  [_graph _origins _src-report _full-report profiles invariants coverage pc]
  (->> (concat
        (find-src-depends-on-test (:src->test-edges invariants))
        (find-test-support-leaked-to-src (:support-leaked-to-src invariants))
        (find-test-executable-has-incoming-deps (:executable-tests-with-incoming-deps invariants))
        (find-mixed-cycles (:mixed-cycles invariants))
        (find-unit-test-too-broad profiles)
        (find-integration-test-very-broad profiles)
        (find-untested-core coverage)
        (find-suite-coupling-findings pc))
       (sort-by finding-sort-key)
       vec))

(defn- count-by-key
  [k xs]
  (reduce (fn [acc x] (update acc (k x) (fnil inc 0))) {} xs))

(defn tests-report
  "Assemble the full pure report for the `gordian tests` command.
  `structural-report-from-graph` is injected to keep this namespace pure and
  easy to test."
  [graph origins structural-report-from-graph]
  (let [full-report  (structural-report-from-graph graph)
        src-report   (structural-report-from-graph (src-only-graph graph origins))
        profiles     (test-profiles full-report origins)
        invariants   (invariants full-report origins profiles)
        coverage     (core-coverage src-report full-report origins)
        pc           (pc-summary src-report full-report)
        findings     (tests-findings graph origins src-report full-report profiles invariants coverage pc)
        src-count    (count (filter #(= :src (val %)) origins))
        test-count   (count (filter #(= :test (val %)) origins))]
    {:gordian/command :tests
     :summary         {:src-count src-count
                       :test-count test-count
                       :test-role-counts  (count-by-key :test-role profiles)
                       :test-style-counts (count-by-key :test-style profiles)
                       :pc-src (:pc-src pc)
                       :pc-with-tests (:pc-with-tests pc)
                       :pc-delta (:pc-delta pc)
                       :src->test-edge-count (count (:src->test-edges invariants))
                       :test-support-leaked-to-src-count (count (:support-leaked-to-src invariants))
                       :executable-tests-with-incoming-deps-count (count (:executable-tests-with-incoming-deps invariants))
                       :unit-test-too-broad-count (count (filter #(= :unit-test-too-broad (:category %)) findings))
                       :untested-core-count (count (:untested-core coverage))}
     :src-report       src-report
     :full-report      full-report
     :test-namespaces  profiles
     :invariants       invariants
     :core-coverage    coverage
     :pc-summary       pc
     :findings         findings}))
