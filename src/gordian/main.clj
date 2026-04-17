(ns gordian.main
  (:require [gordian.scan        :as scan]
            [gordian.close       :as close]
            [gordian.aggregate   :as aggregate]
            [gordian.metrics     :as metrics]
            [gordian.scc         :as scc]
            [gordian.classify    :as classify]
            [gordian.output      :as output]
            [gordian.conceptual  :as conceptual]
            [gordian.git         :as git]
            [gordian.cc-change   :as cc-change]
            [gordian.diagnose    :as diagnose]
            [gordian.finding     :as finding]
            [gordian.discover    :as discover]
            [gordian.explain     :as explain]
            [gordian.compare     :as compare]
            [gordian.cluster     :as cluster]
            [gordian.gate        :as gate]
            [gordian.html        :as html]
            [gordian.prioritize  :as prioritize]
            [gordian.subgraph    :as subgraph]
            [gordian.communities :as communities]
            [gordian.dsm         :as dsm]
            [gordian.tests       :as tests]
            [gordian.config      :as config]
            [gordian.filter      :as gfilter]
            [gordian.family      :as family]
            [gordian.envelope    :as envelope]
            [gordian.dot         :as dot]
            [gordian.json        :as report-json]
            [babashka.fs         :as fs]
            [gordian.edn         :as report-edn]
            [babashka.cli        :as cli]
            [clojure.edn         :as edn]))

;;; ── CLI spec ─────────────────────────────────────────────────────────────

(def ^:private cli-spec
  {:dot                   {:desc "Write Graphviz DOT graph to <file>"}
   :full                  {:desc "Reserved for future detailed/full output modes" :coerce :boolean}
   :json                  {:desc "Output JSON to stdout (suppresses table)" :coerce :boolean}
   :edn                   {:desc "Output EDN to stdout (suppresses table)"  :coerce :boolean}
   :markdown              {:desc "Output Markdown to stdout (suppresses table)" :coerce :boolean}
   :html-file             {:desc "Write self-contained HTML report to <file>"}
   :conceptual            {:desc "Conceptual coupling analysis; provide similarity threshold e.g. 0.30"
                           :coerce :double}
   :change                {:desc "Change coupling analysis; optional repo dir (default: .)"}
   :change-since          {:desc "Limit change coupling to commits after this date e.g. \"90 days ago\""}
   :baseline              {:desc "Saved EDN snapshot used as gate baseline"}
   :max-pc-delta          {:desc "Maximum allowed increase in propagation cost" :coerce :double}
   :max-new-high-findings {:desc "Maximum newly introduced high-severity findings" :coerce :long}
   :max-new-medium-findings {:desc "Maximum newly introduced medium-severity findings" :coerce :long}
   :fail-on               {:desc "Comma-separated strict gate checks e.g. new-cycles,new-high-findings"}
   :rank                  {:desc "Diagnose ranking: actionability (default) or severity" :coerce :keyword}
   :top                   {:desc "Show only the top N findings after ranking" :coerce :long}
   :lens                  {:desc "Communities lens: structural | conceptual | change | combined" :coerce :keyword}
   :threshold             {:desc "Communities threshold" :coerce :double}
   :include-tests         {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude               {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}
   :show-noise            {:desc "Include family-naming-noise findings (suppressed by default)" :coerce :boolean}
   :help                  {:desc "Show this help message" :coerce :boolean}})

(def ^:private usage-summary
  "Usage: gordian [analyze|diagnose|compare|gate|subgraph|communities|dsm|tests|explain|explain-pair] [<dir-or-src>...] [options]

When given a project root (dir with deps.edn, bb.edn, etc.), gordian
auto-discovers source directories. With no arguments, defaults to '.'.

Commands:
  analyze      (default) Raw metrics table + optional coupling sections
  diagnose     Ranked findings with severity levels (auto-enables all lenses)
  compare      Compare two saved EDN snapshots (before.edn after.edn)
  gate         Compare current codebase against a saved baseline and fail CI on regressions
  subgraph     Family/subsystem view for a namespace prefix
  communities  Discover latent architecture communities
  dsm          Dependency Structure Matrix view with diagonal block partitions
  tests        Analyze test architecture and test-vs-source coupling
  explain      Everything gordian knows about a namespace
  explain-pair Everything gordian knows about a pair of namespaces

Options:
  --dot  <file>                 Write Graphviz DOT graph to <file>
  --json                        Output JSON to stdout (suppresses human-readable table)
  --edn                         Output EDN to stdout (suppresses human-readable table)
  --markdown                    Output Markdown to stdout (suppresses human-readable table)
  --html-file <file>            Write self-contained HTML report to <file>
  --conceptual <float>          Conceptual coupling analysis at given similarity threshold
  --change [<repo-dir>]         Change coupling analysis; repo dir defaults to .
  --change-since <date>         Limit to commits after <date> e.g. \"90 days ago\"
  --baseline <file>             Saved EDN snapshot used as gate baseline
  --max-pc-delta <float>        Maximum allowed increase in propagation cost
  --max-new-high-findings <n>   Maximum newly introduced high-severity findings
  --max-new-medium-findings <n> Maximum newly introduced medium-severity findings
  --fail-on <csv>               Comma-separated strict gate checks
  --rank <mode>                 Diagnose ranking: actionability (default) or severity
  --top <n>                     Show only the top N findings after ranking
  --lens <mode>                 Communities lens: structural | conceptual | change | combined
  --threshold <float>           Communities threshold
  --include-tests               Include test directories in auto-discovery
  --exclude <regex>             Exclude namespaces matching regex (repeatable)
  --show-noise                  Include family-naming-noise findings (suppressed by default)
  --help                        Show this help message

Examples:
  gordian                             auto-discover from cwd
  gordian .                           auto-discover from cwd
  gordian /path/to/project            auto-discover from project root
  gordian . --include-tests           include test directories
  gordian . --exclude 'user|scratch'  exclude matching namespaces
  gordian src/                        explicit src dir (no discovery)
  gordian src/ test/                  explicit multiple dirs
  gordian src/ --conceptual 0.30
  gordian src/ --change
  gordian src/ --change --change-since \"90 days ago\"
  gordian diagnose                    ranked findings (auto-enables all lenses)
  gordian diagnose . --edn            machine-readable diagnose output
  gordian compare before.edn after.edn     compare two snapshots
  gordian compare before.edn after.edn --markdown
  gordian gate . --baseline baseline.edn
  gordian gate . --baseline baseline.edn --max-pc-delta 0.01
  gordian diagnose . --rank severity
  gordian subgraph gordian
  gordian communities . --lens combined
  gordian dsm .
  gordian dsm . --html-file dsm.html
  gordian tests .
  gordian explain gordian.scan        drill into a namespace
  gordian explain-pair a.core b.svc   drill into a pair")

(defn print-help []
  (println usage-summary))

;;; ── arg parsing ──────────────────────────────────────────────────────────

(defn parse-args
  "Parse command-line args. Recognises subcommands:
    analyze (default), diagnose, explain, explain-pair.
  For analyze/diagnose: positional args are dirs.
  For explain: first positional arg is a namespace symbol.
  For explain-pair: first two positional args are namespace symbols.
  Returns one of:
    {:help true}
    {:error <msg>}
    {:src-dirs [...] ...opts}
    {:command :diagnose|:explain|:explain-pair ...}"
  [raw-args]
  (let [command  ({"analyze" :analyze "diagnose" :diagnose
                   "explain" :explain "explain-pair" :explain-pair
                   "compare" :compare "gate" :gate "subgraph" :subgraph
                   "communities" :communities "dsm" :dsm "tests" :tests}
                  (first raw-args))
        raw-args (if command (rest raw-args) raw-args)
        {:keys [args opts]} (cli/parse-args raw-args {:spec cli-spec})]
    (cond
      (:help opts)
      {:help true}

      (< 1 (count (filter identity [(:json opts) (:edn opts) (:markdown opts)])))
      {:error "--json, --edn, and --markdown are mutually exclusive"}

      (= :compare command)
      (if (and (first args) (second args))
        (assoc opts :command :compare
               :before-file (first args)
               :after-file (second args))
        {:error "compare requires two EDN file arguments: <before.edn> <after.edn>"})

      (= :gate command)
      (if (:baseline opts)
        (assoc opts :command :gate
               :src-dirs (if (seq args) (vec args) ["."]))
        {:error "gate requires --baseline <file>"})

      (= :subgraph command)
      (if (first args)
        (assoc opts :command :subgraph
               :prefix (first args)
               :src-dirs ["."])
        {:error "subgraph requires a namespace prefix argument"})

      (= :communities command)
      (assoc opts :command :communities
             :src-dirs (if (seq args) (vec args) ["."]))

      (= :dsm command)
      (assoc opts :command :dsm
             :src-dirs (if (seq args) (vec args) ["."]))

      (= :tests command)
      (assoc opts :command :tests
             :src-dirs (if (seq args) (vec args) ["."]))

      (= :explain command)
      (if (first args)
        (assoc opts :command :explain
               :explain-ns (symbol (first args))
               :src-dirs ["."])
        {:error "explain requires a namespace argument"})

      (= :explain-pair command)
      (if (and (first args) (second args))
        (assoc opts :command :explain-pair
               :explain-ns-a (symbol (first args))
               :explain-ns-b (symbol (second args))
               :src-dirs ["."])
        {:error "explain-pair requires two namespace arguments"})

      :else
      (let [src-dirs (if (seq args) (vec args) ["."])
            opts     (if (= :diagnose command)
                       (assoc opts :command :diagnose)
                       opts)]
        (assoc opts :src-dirs src-dirs)))))

;;; ── discovery + config resolution ────────────────────────────────────────

(defn- normalize-src-dirs
  "Normalize src-dir strings using fs/normalize: strips leading ./ and
  trailing / so paths are consistent regardless of how they were supplied."
  [opts]
  (update opts :src-dirs #(mapv (fn [d] (str (fs/normalize d))) %)))

(defn resolve-opts
  "Resolve src-dirs from CLI args via auto-discovery or pass-through.
  When the sole positional arg is a project root (has deps.edn etc.),
  auto-discovers source directories and loads .gordian.edn config.
  Otherwise treats positional args as explicit src-dirs (backward compatible).
  Returns opts map with :src-dirs guaranteed to be a non-empty vector,
  or an {:error ...} map.
  All :src-dirs paths are normalized via fs/normalize (no leading ./, no trailing /)."
  [{:keys [src-dirs command] :as opts}]
  (let [;; single dir that is a project root → discover + config
        project-dir    (when (and (= 1 (count src-dirs))
                                  (discover/project-root? (first src-dirs)))
                         (first src-dirs))
        cfg            (when project-dir (config/load-config project-dir))
        merged0        (if cfg (config/merge-opts cfg opts) opts)
        merged         (if (= :tests command)
                         (assoc merged0 :include-tests true)
                         merged0)]
    (if project-dir
      ;; auto-discovery: config :src-dirs overrides discovery, else probe
      (if-let [cfg-dirs (seq (:src-dirs cfg))]
        (normalize-src-dirs (assoc merged :src-dirs (vec cfg-dirs)))
        (let [discovered (discover/discover-dirs project-dir)
              dirs       (discover/resolve-dirs discovered merged)]
          (if (seq dirs)
            (normalize-src-dirs (assoc merged :src-dirs dirs))
            {:error (str "no source directories found in " project-dir)})))
      ;; explicit dirs — normalize and use
      (normalize-src-dirs merged))))

;;; ── pipeline ─────────────────────────────────────────────────────────────

(defn- merge-node-metrics [report metrics-map]
  (update report :nodes
          (fn [nodes]
            (mapv #(merge % (get metrics-map (:ns %) {})) nodes))))

(defn structural-report-from-graph
  "Build the structural report from a direct dependency graph.
  Pure structural pipeline only: closure, aggregate metrics, direct metrics,
  family metrics, role classification, and SCC cycle detection."
  [direct]
  (let [closed (close/close direct)]
    (-> closed
        aggregate/aggregate
        (merge-node-metrics (metrics/compute direct))
        (merge-node-metrics (family/family-metrics direct))
        (update :nodes classify/classify)
        (assoc :graph  direct
               :cycles (scc/find-cycles direct)))))

(defn- explicit-test-path?
  "Heuristic for explicit dir args in `gordian tests`: test/support dirs are
  usually named .../test or contain /test/ in the path."
  [dir]
  (boolean (re-find #"(^|/)test(/|$)" (str dir))))

(defn resolve-test-paths
  "Resolve typed scan paths for the tests command.
  Project-root mode uses discovery/config; explicit-dir mode infers :test from
  path names containing /test/."
  [{:keys [src-dirs] :as opts}]
  (let [project-dir (when (and (= 1 (count src-dirs))
                               (discover/project-root? (first src-dirs)))
                      (first src-dirs))]
    (if project-dir
      (let [cfg        (config/load-config project-dir)
            discovered (discover/discover-dirs project-dir)
            src-dirs   (or (seq (:src-dirs cfg))
                           (:src-dirs discovered))
            typed      (discover/resolve-paths {:src-dirs src-dirs
                                                :test-dirs (:test-dirs discovered)}
                                               (assoc opts :include-tests true))]
        (if (seq typed)
          typed
          {:error (str "no source directories found in " project-dir)}))
      (mapv (fn [dir]
              {:dir dir :kind (if (explicit-test-path? dir) :test :src)})
            src-dirs))))

(defn build-report
  "Full pipeline: scan-dirs → close → aggregate + metrics + cycles.
  `src-dirs`            — vector of source directory paths.
  `conceptual-threshold` — when provided, runs conceptual coupling analysis
                           and attaches :conceptual-pairs + :conceptual-threshold.
  `change-opts`         — when provided, a map with:
                          :change  — repo dir for git log (string)
                          :min-co  — minimum co-change count (default 2)
                          Attaches :change-pairs + :change-threshold.
  `exclude`             — seq of regex strings. Matching namespaces are
                          removed from the graph after scanning.

  When conceptual-threshold is supplied, uses scan/scan-all-dirs so each file
  is read and parsed exactly once (rather than a structural scan + a separate
  full-file terms scan)."
  ([src-dirs] (build-report src-dirs nil nil nil))
  ([src-dirs conceptual-threshold] (build-report src-dirs conceptual-threshold nil nil))
  ([src-dirs conceptual-threshold change-opts] (build-report src-dirs conceptual-threshold change-opts nil))
  ([src-dirs conceptual-threshold change-opts exclude]
   (let [[raw-graph ns->terms]
         (if conceptual-threshold
           (let [{:keys [graph ns->terms]} (scan/scan-all-dirs conceptual/extract-terms src-dirs)]
             [graph ns->terms])
           [(scan/scan-dirs src-dirs) nil])
         direct   (gfilter/filter-graph raw-graph exclude)
         ns->terms (when ns->terms
                     (if (seq exclude)
                       (let [keep? (set (keys direct))]
                         (into {} (filter (fn [[k _]] (keep? k))) ns->terms))
                       ns->terms))
         report (assoc (structural-report-from-graph direct)
                       :src-dirs src-dirs)
         report (if conceptual-threshold
                  (let [tfidf  (conceptual/build-tfidf ns->terms)
                        result (conceptual/conceptual-pairs tfidf direct conceptual-threshold 3)
                        pairs  (family/annotate-conceptual-pairs (:pairs result))]
                    (assoc report
                           :conceptual-pairs           pairs
                           :conceptual-candidate-count (:candidate-count result)
                           :conceptual-threshold       conceptual-threshold))
                  report)]
     (if-let [change-dir (:change change-opts)]
       (let [min-co    (get change-opts :min-co 2)
             threshold (get change-opts :threshold 0.30)
             since     (get change-opts :since)
             commits   (-> (git/commits change-dir since)
                           (git/commits-as-ns src-dirs direct))
             result    (cc-change/change-coupling-pairs commits direct threshold min-co)]
         (assoc report
                :change-pairs           (:pairs result)
                :change-candidate-count (:candidate-count result)
                :change-threshold       threshold))
       report))))

(defn analyze
  "Run analysis with resolved opts map.
  :src-dirs      — required vector of source directories
  :dot <file>    — write Graphviz DOT to <file> (stderr status line)
  :json true     — print JSON to stdout, suppress human-readable table
  :edn  true     — print EDN to stdout, suppress human-readable table
  :conceptual    — similarity threshold for conceptual coupling section
  :change        — repo dir for change coupling analysis (git log)
  :change-since  — git date string limiting change coupling horizon
  :exclude       — vector of regex strings to exclude namespaces"
  [{:keys [src-dirs dot json edn markdown conceptual change change-since exclude]
    :as opts}]
  (let [change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)]
    (when dot
      (spit dot (dot/generate report))
      (binding [*out* *err*] (println (str "DOT written to " dot))))
    (cond
      json     (println (report-json/generate (envelope/wrap opts report :analyze)))
      edn      (print   (report-edn/generate  (envelope/wrap opts report :analyze)))
      markdown (run! println (output/format-report-md report))
      :else    (output/print-report report))))

(defn diagnose-cmd
  "Run diagnose with resolved opts map.
  Auto-enables conceptual (0.15) and change (.) when not explicitly set.
  Family-noise findings are suppressed by default; pass --show-noise to include."
  [{:keys [src-dirs json edn markdown conceptual change change-since exclude rank top show-noise]
    :as opts}]
  (let [conceptual   (or conceptual 0.15)
        change       (if (nil? change) true change)
        change-dir   (when change (if (string? change) change "."))
        change-opts  (when change-dir {:change change-dir :since change-since})
        rank         (or rank :actionability)
        report       (build-report src-dirs conceptual change-opts exclude)
        health       (diagnose/health report)
        all-findings (diagnose/diagnose report)
        ;; Noise suppression: filter before ranking/clustering for human output.
        ;; EDN/JSON consumers receive the full unfiltered set.
        display-findings (if show-noise
                           all-findings
                           (into [] (remove finding/family-noise?) all-findings))
        suppressed   (- (count all-findings) (count display-findings))
        clusters0    (cluster/cluster-findings display-findings)
        context      (prioritize/cluster-context (:clusters clusters0)
                                                 (:unclustered clusters0))
        ranked       (prioritize/rank-findings display-findings rank context)
        ;; Top-N truncation: applied after ranking, before final clustering.
        findings     (if top (vec (take top ranked)) ranked)
        truncated-from (when (and top (< (count findings) (count ranked)))
                         (count ranked))
        clusters     (cluster/cluster-findings findings)
        enriched     (assoc report :findings all-findings :health health
                            :clusters (:clusters clusters)
                            :unclustered (:unclustered clusters)
                            :rank-by rank)]
    (cond
      json     (println (report-json/generate (envelope/wrap opts enriched :diagnose)))
      edn      (print   (report-edn/generate  (envelope/wrap opts enriched :diagnose)))
      markdown (run! println (output/format-diagnose-md report health findings clusters rank suppressed truncated-from))
      :else    (output/print-diagnose report health findings clusters rank suppressed truncated-from))))

(defn subgraph-cmd
  "Run subgraph with resolved opts map.
  Builds a current diagnose-style report, then slices it to a family/prefix view."
  [{:keys [src-dirs json edn markdown conceptual change change-since exclude rank prefix]
    :as opts}]
  (let [conceptual  (or conceptual 0.15)
        change      (if (nil? change) true change)
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        rank        (or rank :actionability)
        report      (build-report src-dirs conceptual change-opts exclude)
        health      (diagnose/health report)
        findings0   (diagnose/diagnose report)
        clusters0   (cluster/cluster-findings findings0)
        context     (prioritize/cluster-context (:clusters clusters0)
                                                (:unclustered clusters0))
        findings    (prioritize/rank-findings findings0 rank context)
        enriched    (assoc report :findings findings :health health :rank-by rank)
        data        (subgraph/subgraph-summary enriched prefix)]
    (cond
      json     (println (report-json/generate (envelope/wrap opts data :subgraph)))
      edn      (print   (report-edn/generate  (envelope/wrap opts data :subgraph)))
      markdown (run! println (output/format-subgraph-md data))
      :else    (output/print-subgraph data))))

(defn explain-cmd
  "Run explain with resolved opts map.
  Auto-enables conceptual (0.15) and change (.) like diagnose.
  Falls back to subgraph view when no exact namespace exists but prefix matches members."
  [{:keys [src-dirs json edn markdown conceptual change change-since exclude explain-ns]
    :as opts}]
  (let [conceptual  (or conceptual 0.15)
        change      (if (nil? change) true change)
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)
        data        (explain/explain-ns report explain-ns)]
    (if-not (:error data)
      (cond
        json     (println (report-json/generate (envelope/wrap opts data :explain)))
        edn      (print   (report-edn/generate  (envelope/wrap opts data :explain)))
        markdown (run! println (output/format-explain-ns-md data))
        :else    (output/print-explain-ns data))
      ;; fallback to subgraph on prefix match
      (subgraph-cmd (assoc opts :command :subgraph :prefix (str explain-ns))))))

(defn compare-cmd
  "Run compare with parsed opts map.
  Reads two EDN files and produces a diff report."
  [{:keys [before-file after-file json edn markdown]}]
  (let [read-edn (fn [path]
                   (edn/read-string (slurp path)))
        before   (read-edn before-file)
        after    (read-edn after-file)
        diff     (compare/compare-reports before after)]
    (cond
      json     (println (report-json/generate diff))
      edn      (print   (report-edn/generate diff))
      markdown (run! println (output/format-compare-md diff))
      :else    (output/print-compare diff))))

(defn communities-cmd
  "Run communities with resolved opts map."
  [{:keys [src-dirs json edn markdown conceptual change change-since exclude lens threshold]
    :as opts}]
  (let [conceptual  (if (= lens :structural) nil (or conceptual 0.15))
        change      (if (= lens :change) true (if (nil? change) true change))
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)
        data        (communities/community-report report {:lens lens :threshold threshold})]
    (cond
      json     (println (report-json/generate (envelope/wrap opts data :communities)))
      edn      (print   (report-edn/generate  (envelope/wrap opts data :communities)))
      markdown (run! println (output/format-communities-md data))
      :else    (output/print-communities data))))

(defn dsm-cmd
  "Run DSM with resolved opts map."
  [{:keys [src-dirs json edn markdown exclude html-file]
    :as opts}]
  (let [direct (-> (scan/scan-dirs src-dirs)
                   (gfilter/filter-graph exclude))
        data   (assoc (dsm/dsm-report direct)
                      :src-dirs src-dirs)]
    (when html-file
      (spit html-file (html/dsm-html data)))
    (cond
      json     (println (report-json/generate (envelope/wrap opts data :dsm)))
      edn      (print   (report-edn/generate  (envelope/wrap opts data :dsm)))
      markdown (run! println (output/format-dsm-md data))
      :else    (output/print-dsm data))))

(defn tests-cmd
  "Run tests mode with resolved opts map."
  [{:keys [json edn markdown] :as opts}]
  (let [paths (resolve-test-paths opts)]
    (if (:error paths)
      (println (str "Error: " (:error paths)))
      (let [{:keys [graph origins]} (scan/scan-paths paths)
            data (-> (tests/tests-report graph origins structural-report-from-graph)
                     (assoc :src-dirs (mapv :dir paths)))]
        (cond
          json     (println (report-json/generate (envelope/wrap opts data :tests)))
          edn      (print   (report-edn/generate  (envelope/wrap opts data :tests)))
          markdown (run! println (output/format-tests-md data))
          :else    (output/print-tests data))))))

(defn gate-cmd
  "Run gate with resolved opts map.
  Builds a current diagnose-style report, compares against baseline,
  evaluates checks, prints output, and returns 0 (pass) or 1 (fail)."
  [{:keys [src-dirs baseline json edn markdown conceptual change change-since exclude]
    :as opts}]
  (let [conceptual  (or conceptual 0.15)
        change      (if (nil? change) true change)
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        baseline    (edn/read-string (slurp baseline))
        report      (build-report src-dirs conceptual change-opts exclude)
        health      (diagnose/health report)
        findings    (diagnose/diagnose report)
        clusters    (cluster/cluster-findings findings)
        current     (envelope/wrap opts
                                   (assoc report
                                          :findings findings
                                          :health health
                                          :clusters (:clusters clusters)
                                          :unclustered (:unclustered clusters))
                                   :diagnose)
        diff        (compare/compare-reports baseline current)
        checks      (gate/resolve-checks opts diff)
        result      (gate/gate-report (:baseline opts) baseline current diff checks)]
    (cond
      json     (println (report-json/generate result))
      edn      (print   (report-edn/generate result))
      markdown (run! println (output/format-gate-md result))
      :else    (output/print-gate result))
    (if (= :pass (:result result)) 0 1)))

(defn explain-pair-cmd
  "Run explain-pair with resolved opts map."
  [{:keys [src-dirs json edn markdown conceptual change change-since exclude
           explain-ns-a explain-ns-b]
    :as opts}]
  (let [conceptual  (or conceptual 0.15)
        change      (if (nil? change) true change)
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)
        data        (explain/explain-pair-data report explain-ns-a explain-ns-b)]
    (cond
      json     (println (report-json/generate (envelope/wrap opts data :explain-pair)))
      edn      (print   (report-edn/generate  (envelope/wrap opts data :explain-pair)))
      markdown (run! println (output/format-explain-pair-md data))
      :else    (output/print-explain-pair data))))

(defn run [args]
  (let [parsed (parse-args args)]
    (cond
      (:help parsed)  (do (print-help) (System/exit 0))
      (:error parsed) (do (println (str "Error: " (:error parsed)))
                          (println)
                          (print-help)
                          (System/exit 1))
      :else           (if (= :compare (:command parsed))
                        (compare-cmd parsed)
                        (let [opts (resolve-opts parsed)]
                          (if (:error opts)
                            (do (println (str "Error: " (:error opts)))
                                (println)
                                (print-help)
                                (System/exit 1))
                            (case (:command opts)
                              :diagnose     (diagnose-cmd opts)
                              :gate         (System/exit (gate-cmd opts))
                              :subgraph     (subgraph-cmd opts)
                              :communities  (communities-cmd opts)
                              :dsm          (dsm-cmd opts)
                              :tests        (tests-cmd opts)
                              :explain      (explain-cmd opts)
                              :explain-pair (explain-pair-cmd opts)
                              (analyze opts))))))))

(defn -main [& args]
  (run args))
