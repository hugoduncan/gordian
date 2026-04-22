(ns gordian.cli.registry
  (:require [gordian.cyclomatic :as cyclomatic]
            [gordian.local.report :as local]))

(def ^:private output-spec
  {:json     {:desc "Output JSON to stdout (suppresses human-readable output)" :coerce :boolean}
   :edn      {:desc "Output EDN to stdout (suppresses human-readable output)" :coerce :boolean}
   :markdown {:desc "Output Markdown to stdout (suppresses human-readable output)" :coerce :boolean}
   :help     {:desc "Show this help message" :coerce :boolean}})

(def ^:private analyze-spec
  {:dot           {:desc "Write Graphviz DOT graph to <file>"}
   :conceptual    {:desc "Conceptual coupling analysis at the given similarity threshold" :coerce :double}
   :change        {:desc "Change coupling analysis; optional repo dir (default: .)"}
   :change-since  {:desc "Limit change coupling to commits after this date e.g. \"90 days ago\""}
   :include-tests {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude       {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}})

(def ^:private diagnose-spec
  {:conceptual    {:desc "Conceptual similarity threshold override (default 0.15)" :coerce :double}
   :change        {:desc "Change coupling analysis; optional repo dir (default: .)"}
   :change-since  {:desc "Limit change coupling to commits after this date e.g. \"90 days ago\""}
   :include-tests {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude       {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}
   :rank          {:desc "Diagnose ranking: actionability (default) or severity" :coerce :keyword}
   :top           {:desc "Show only the top N findings after ranking" :coerce :long}
   :show-noise    {:desc "Include family-naming-noise findings (suppressed by default)" :coerce :boolean}})

(def ^:private gate-spec
  {:conceptual              {:desc "Conceptual similarity threshold override (default 0.15)" :coerce :double}
   :change                  {:desc "Change coupling analysis; optional repo dir (default: .)"}
   :change-since            {:desc "Limit change coupling to commits after this date e.g. \"90 days ago\""}
   :include-tests           {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude                 {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}
   :baseline                {:desc "Saved EDN snapshot used as gate baseline"}
   :max-pc-delta            {:desc "Maximum allowed increase in propagation cost" :coerce :double}
   :max-new-high-findings   {:desc "Maximum newly introduced high-severity findings" :coerce :long}
   :max-new-medium-findings {:desc "Maximum newly introduced medium-severity findings" :coerce :long}
   :fail-on                 {:desc "Comma-separated strict gate checks e.g. new-cycles,new-high-findings"}})

(def ^:private subgraph-spec
  {:conceptual    {:desc "Conceptual similarity threshold override (default 0.15)" :coerce :double}
   :change        {:desc "Change coupling analysis; optional repo dir (default: .)"}
   :change-since  {:desc "Limit change coupling to commits after this date e.g. \"90 days ago\""}
   :include-tests {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude       {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}
   :rank          {:desc "Subgraph finding ranking: actionability (default) or severity" :coerce :keyword}})

(def ^:private communities-spec
  {:conceptual    {:desc "Conceptual similarity threshold override (default 0.15 unless lens=structural)" :coerce :double}
   :change        {:desc "Change coupling analysis; optional repo dir (default: .)"}
   :change-since  {:desc "Limit change coupling to commits after this date e.g. \"90 days ago\""}
   :include-tests {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude       {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}
   :lens          {:desc "Communities lens: structural | conceptual | change | combined" :coerce :keyword}
   :threshold     {:desc "Communities threshold" :coerce :double}})

(def ^:private dsm-spec
  {:html-file     {:desc "Write self-contained HTML report to <file>"}
   :include-tests {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude       {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}})

(def ^:private complexity-spec
  {:include-tests    {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :sort             {:desc "Sort by cc | loc | ns | var | cc-risk" :coerce :keyword}
   :bar              {:desc "Bar metric for human-readable histograms: cc | loc" :coerce :keyword}
   :min              {:desc "Display filter: repeatable metric=value, e.g. cc=10 or loc=20" :coerce [:string]}
   :min-cc           {:desc "Deprecated: use --min cc=<n>"}
   :namespace-rollup {:desc "Include namespace rollup section" :coerce :boolean}
   :project-rollup   {:desc "Include project rollup section" :coerce :boolean}
   :top              {:desc "Show only the top N units after sorting" :coerce :long}
   :source-only      {:desc "Discovered source paths only" :coerce :boolean}
   :tests-only       {:desc "Discovered test paths only" :coerce :boolean}})

(def ^:private local-spec
  {:include-tests    {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :sort             {:desc "Sort by total | flow | state | shape | abstraction | dependency | working-set | ns | var" :coerce :keyword}
   :bar              {:desc "Bar metric for human-readable histograms: total | flow | state | shape | abstraction | dependency | working-set" :coerce :keyword}
   :min              {:desc "Display filter: repeatable metric=value, e.g. total=12 or abstraction=4" :coerce [:string]}
   :namespace-rollup {:desc "Include namespace rollup section" :coerce :boolean}
   :project-rollup   {:desc "Include project rollup section" :coerce :boolean}
   :top              {:desc "Show only the top N units after sorting" :coerce :long}
   :source-only      {:desc "Discovered source paths only" :coerce :boolean}
   :tests-only       {:desc "Discovered test paths only" :coerce :boolean}})

(defn- merge-specs [& specs]
  (apply merge specs))

(def ^:private command-defs
  [{:canonical :analyze
    :names ["analyze"]
    :summary "Raw metrics table + optional coupling sections"
    :description ["Analyze namespace coupling and structural metrics."
                  ""
                  "With no explicit command, gordian defaults to `analyze`."
                  "When given a project root, source directories are auto-discovered."]
    :usage "gordian analyze [<dir-or-src>...] [options]"
    :positional ["<dir-or-src>...  Project root or explicit source directories (default: .)"]
    :examples ["gordian"
               "gordian . --include-tests"
               "gordian analyze src/ test/ --conceptual 0.30"
               "gordian src/ --change --change-since \"90 days ago\""]
    :spec (merge-specs output-spec analyze-spec)
    :parse (fn [{:keys [args opts]}]
             (assoc opts :src-dirs (if (seq args) (vec args) ["."]))) }
   {:canonical :diagnose
    :names ["diagnose"]
    :summary "Ranked findings with severity levels (auto-enables all lenses)"
    :description ["Diagnose architectural findings and rank them by severity or actionability."]
    :usage "gordian diagnose [<dir-or-src>...] [options]"
    :positional ["<dir-or-src>...  Project root or explicit source directories (default: .)"]
    :examples ["gordian diagnose"
               "gordian diagnose . --rank severity"
               "gordian diagnose . --top 10 --edn"]
    :spec (merge-specs output-spec diagnose-spec)
    :parse (fn [{:keys [args opts]}]
             (assoc opts :command :diagnose
                    :src-dirs (if (seq args) (vec args) ["."]))) }
   {:canonical :compare
    :names ["compare"]
    :summary "Compare two saved EDN snapshots (before.edn after.edn)"
    :description ["Compare two saved EDN snapshots and report structural deltas."]
    :usage "gordian compare <before.edn> <after.edn> [options]"
    :positional ["<before.edn>  Baseline snapshot"
                 "<after.edn>   Current snapshot"]
    :examples ["gordian compare before.edn after.edn"
               "gordian compare before.edn after.edn --markdown"]
    :spec output-spec
    :parse (fn [{:keys [args opts]}]
             (if (and (first args) (second args))
               (assoc opts :command :compare
                      :before-file (first args)
                      :after-file (second args))
               {:error "compare requires two EDN file arguments: <before.edn> <after.edn>"}))}
   {:canonical :gate
    :names ["gate"]
    :summary "Compare current codebase against a saved baseline and fail CI on regressions"
    :description ["Build a current diagnose-style snapshot, compare it against a baseline,"
                  "and evaluate CI gate checks."]
    :usage "gordian gate [<dir-or-src>...] --baseline <file> [options]"
    :positional ["<dir-or-src>...  Project root or explicit source directories (default: .)"]
    :examples ["gordian gate . --baseline baseline.edn"
               "gordian gate . --baseline baseline.edn --max-pc-delta 0.01"]
    :spec (merge-specs output-spec gate-spec)
    :parse (fn [{:keys [args opts]}]
             (if (:baseline opts)
               (assoc opts :command :gate
                      :src-dirs (if (seq args) (vec args) ["."]))
               {:error "gate requires --baseline <file>"}))}
   {:canonical :subgraph
    :names ["subgraph"]
    :summary "Family/subsystem view for a namespace prefix"
    :description ["Slice the current report to a namespace-prefix subsystem view."]
    :usage "gordian subgraph <prefix> [options]"
    :positional ["<prefix>  Namespace prefix to slice"]
    :examples ["gordian subgraph gordian"
               "gordian subgraph gordian --markdown"]
    :spec (merge-specs output-spec subgraph-spec)
    :parse (fn [{:keys [args opts]}]
             (if (first args)
               (assoc opts :command :subgraph
                      :prefix (first args)
                      :src-dirs ["."])
               {:error "subgraph requires a namespace prefix argument"}))}
   {:canonical :communities
    :names ["communities"]
    :summary "Discover latent architecture communities"
    :description ["Discover latent architecture communities across one or more lenses."]
    :usage "gordian communities [<dir-or-src>...] [options]"
    :positional ["<dir-or-src>...  Project root or explicit source directories (default: .)"]
    :examples ["gordian communities ."
               "gordian communities . --lens combined"
               "gordian communities . --lens structural --threshold 1.0"]
    :spec (merge-specs output-spec communities-spec)
    :parse (fn [{:keys [args opts]}]
             (assoc opts :command :communities
                    :src-dirs (if (seq args) (vec args) ["."]))) }
   {:canonical :dsm
    :names ["dsm"]
    :summary "Dependency Structure Matrix view with diagonal block partitions"
    :description ["Render a dependency structure matrix with diagonal block partitions."]
    :usage "gordian dsm [<dir-or-src>...] [options]"
    :positional ["<dir-or-src>...  Project root or explicit source directories (default: .)"]
    :examples ["gordian dsm ."
               "gordian dsm . --html-file dsm.html"]
    :spec (merge-specs output-spec dsm-spec)
    :parse (fn [{:keys [args opts]}]
             (assoc opts :command :dsm
                    :src-dirs (if (seq args) (vec args) ["."]))) }
   {:canonical :tests
    :names ["tests"]
    :summary "Analyze test architecture and test-vs-source coupling"
    :description ["Analyze the test layer and its relationship to production namespaces."]
    :usage "gordian tests [<dir-or-src>...] [options]"
    :positional ["<dir-or-src>...  Project root or explicit source/test directories (default: .)"]
    :examples ["gordian tests ."
               "gordian tests src/ test/ --markdown"]
    :spec output-spec
    :parse (fn [{:keys [args opts]}]
             (assoc opts :command :tests
                    :src-dirs (if (seq args) (vec args) ["."]))) }
   {:canonical :complexity
    :names ["complexity"]
    :summary "Analyze local code metrics (cyclomatic complexity + LOC)"
    :description ["Analyze local executable-unit complexity and lines of code."]
    :usage "gordian complexity [<dir-or-src>...] [options]"
    :positional ["<dir-or-src>...  Project root or explicit source directories (default: .)"]
    :examples ["gordian complexity ."
               "gordian complexity . --sort loc"
               "gordian complexity . --sort ns --bar loc"
               "gordian complexity . --namespace-rollup"
               "gordian complexity . --project-rollup"
               "gordian complexity . --min cc=10 --min loc=20"
               "gordian complexity . --json"]
    :spec (merge-specs output-spec complexity-spec)
    :parse (fn [{:keys [args opts]}]
             (assoc opts :command :complexity
                    :explicit-paths? (boolean (seq args))
                    :src-dirs (if (seq args) (vec args) ["."])))
    :validate (fn [{:keys [sort bar min top source-only tests-only min-cc explicit-paths?]}]
                (cond
                  (and source-only tests-only)
                  {:error "complexity rejects --source-only combined with --tests-only"}

                  (and explicit-paths?
                       (or source-only tests-only))
                  {:error "complexity rejects explicit paths combined with --source-only or --tests-only"}

                  (and sort
                       (not (contains? #{:cc :loc :ns :var :cc-risk} sort)))
                  {:error "complexity --sort must be one of: cc, loc, ns, var, cc-risk"}

                  (and bar
                       (not (contains? #{:cc :loc} bar)))
                  {:error "complexity --bar must be one of: cc, loc"}

                  (some? min-cc)
                  {:error "complexity no longer accepts --min-cc; use --min cc=<n>"}

                  (and (seq min)
                       (not-every? some? (map cyclomatic/parse-min-expression min)))
                  {:error "complexity --min values must be metric=value with metric in {cc,loc} and positive integer value"}

                  (and top (not (pos? top)))
                  {:error "complexity --top must be a positive integer"}))}
   {:canonical :local
    :names ["local"]
    :summary "Analyze local comprehension complexity burden vectors with findings"
    :description ["Analyze local executable units using Local Comprehension Complexity (LCC)."]
    :usage "gordian local [<dir-or-src>...] [options]"
    :positional ["<dir-or-src>...  Project root or explicit source directories (default: .)"]
    :examples ["gordian local ."
               "gordian local . --sort abstraction"
               "gordian local . --sort ns --bar working-set"
               "gordian local . --namespace-rollup"
               "gordian local . --project-rollup"
               "gordian local . --min total=12 --min abstraction=4"
               "gordian local . --json"]
    :spec (merge-specs output-spec local-spec)
    :parse (fn [{:keys [args opts]}]
             (assoc opts :command :local
                    :explicit-paths? (boolean (seq args))
                    :src-dirs (if (seq args) (vec args) ["."])))
    :validate (fn [{:keys [sort bar min top source-only tests-only explicit-paths?]}]
                (cond
                  (and source-only tests-only)
                  {:error "local rejects --source-only combined with --tests-only"}

                  (and explicit-paths?
                       (or source-only tests-only))
                  {:error "local rejects explicit paths combined with --source-only or --tests-only"}

                  (and sort
                       (not (local/valid-sort-key? sort)))
                  {:error "local --sort must be one of: total, flow, state, shape, abstraction, dependency, working-set, ns, var"}

                  (and bar
                       (not (local/valid-bar-metric? bar)))
                  {:error "local --bar must be one of: total, flow, state, shape, abstraction, dependency, working-set"}

                  (and (seq min)
                       (not-every? some? (map local/parse-min-expression min)))
                  {:error "local --min values must be metric=value with metric in {total,flow,state,shape,abstraction,dependency,working-set} and positive integer value"}

                  (and top (not (pos? top)))
                  {:error "local --top must be a positive integer"}))}
   {:canonical :explain
    :names ["explain"]
    :summary "Everything gordian knows about a namespace"
    :description ["Drill into a namespace, with subgraph fallback on prefix match."]
    :usage "gordian explain <namespace> [options]"
    :positional ["<namespace>  Namespace symbol to explain"]
    :examples ["gordian explain gordian.main"
               "gordian explain gordian.scan --edn"]
    :spec (merge-specs output-spec analyze-spec)
    :parse (fn [{:keys [args opts]}]
             (if (first args)
               (assoc opts :command :explain
                      :explain-ns (symbol (first args))
                      :src-dirs ["."])
               {:error "explain requires a namespace argument"}))}
   {:canonical :explain-pair
    :names ["explain-pair"]
    :summary "Everything gordian knows about a pair of namespaces"
    :description ["Drill into a pair of namespaces across structural, conceptual, and change lenses."]
    :usage "gordian explain-pair <ns-a> <ns-b> [options]"
    :positional ["<ns-a>  First namespace symbol"
                 "<ns-b>  Second namespace symbol"]
    :examples ["gordian explain-pair gordian.aggregate gordian.close"
               "gordian explain-pair a.core b.svc --markdown"]
    :spec (merge-specs output-spec analyze-spec)
    :parse (fn [{:keys [args opts]}]
             (if (and (first args) (second args))
               (assoc opts :command :explain-pair
                      :explain-ns-a (symbol (first args))
                      :explain-ns-b (symbol (second args))
                      :src-dirs ["."])
               {:error "explain-pair requires two namespace arguments"}))}])

(def ^:private command-index
  (into {}
        (mapcat (fn [{:keys [canonical names]}]
                  (map (fn [name] [name canonical]) names)))
        command-defs))

(def ^:private command-definitions-by-canonical
  (into {} (map (juxt :canonical identity)) command-defs))

(defn resolve-command [token]
  (get command-index token))

(defn command-summary-lines []
  (mapv (fn [{:keys [names canonical display-name summary]}]
          {:canonical canonical
           :name (or display-name (first names))
           :summary summary
           :aliases (vec (rest names))})
        command-defs))

(defn command-definition [command]
  (get command-definitions-by-canonical command))
