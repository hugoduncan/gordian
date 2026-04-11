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
            [gordian.discover    :as discover]
            [gordian.explain     :as explain]
            [gordian.config      :as config]
            [gordian.filter      :as gfilter]
            [gordian.dot         :as dot]
            [gordian.json        :as report-json]
            [gordian.edn         :as report-edn]
            [babashka.cli        :as cli]))

;;; ── CLI spec ─────────────────────────────────────────────────────────────

(def ^:private cli-spec
  {:dot           {:desc "Write Graphviz DOT graph to <file>"}
   :json          {:desc "Output JSON to stdout (suppresses table)" :coerce :boolean}
   :edn           {:desc "Output EDN to stdout (suppresses table)"  :coerce :boolean}
   :conceptual    {:desc "Conceptual coupling analysis; provide similarity threshold e.g. 0.30"
                   :coerce :double}
   :change        {:desc "Change coupling analysis; optional repo dir (default: .)"}
   :change-since  {:desc "Limit change coupling to commits after this date e.g. \"90 days ago\""}
   :include-tests {:desc "Include test directories in auto-discovery" :coerce :boolean}
   :exclude       {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}
   :help          {:desc "Show this help message"                   :coerce :boolean}})

(def ^:private usage-summary
  "Usage: gordian [analyze|diagnose] [<dir-or-src>...] [options]

When given a project root (dir with deps.edn, bb.edn, etc.), gordian
auto-discovers source directories. With no arguments, defaults to '.'.

Commands:
  analyze      (default) Raw metrics table + optional coupling sections
  diagnose     Ranked findings with severity levels (auto-enables all lenses)
  explain      Everything gordian knows about a namespace
  explain-pair Everything gordian knows about a pair of namespaces

Options:
  --dot  <file>         Write Graphviz DOT graph to <file>
  --json                Output JSON to stdout (suppresses human-readable table)
  --edn                 Output EDN to stdout (suppresses human-readable table)
  --conceptual <float>  Conceptual coupling analysis at given similarity threshold
  --change [<repo-dir>] Change coupling analysis; repo dir defaults to .
  --change-since <date> Limit to commits after <date> e.g. \"90 days ago\"
  --include-tests       Include test directories in auto-discovery
  --exclude <regex>     Exclude namespaces matching regex (repeatable)
  --help                Show this help message

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
                   "explain" :explain "explain-pair" :explain-pair}
                  (first raw-args))
        raw-args (if command (rest raw-args) raw-args)
        {:keys [args opts]} (cli/parse-args raw-args {:spec cli-spec})]
    (cond
      (:help opts)
      {:help true}

      (and (:json opts) (:edn opts))
      {:error "--json and --edn are mutually exclusive"}

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

(defn resolve-opts
  "Resolve src-dirs from CLI args via auto-discovery or pass-through.
  When the sole positional arg is a project root (has deps.edn etc.),
  auto-discovers source directories and loads .gordian.edn config.
  Otherwise treats positional args as explicit src-dirs (backward compatible).
  Returns opts map with :src-dirs guaranteed to be a non-empty vector,
  or an {:error ...} map."
  [{:keys [src-dirs] :as opts}]
  (let [;; single dir that is a project root → discover + config
        project-dir (when (and (= 1 (count src-dirs))
                               (discover/project-root? (first src-dirs)))
                      (first src-dirs))
        cfg         (when project-dir (config/load-config project-dir))
        merged      (if cfg (config/merge-opts cfg opts) opts)]
    (if project-dir
      ;; auto-discovery: config :src-dirs overrides discovery, else probe
      (if-let [cfg-dirs (seq (:src-dirs cfg))]
        (assoc merged :src-dirs (vec cfg-dirs))
        (let [discovered (discover/discover-dirs project-dir)
              dirs       (discover/resolve-dirs discovered merged)]
          (if (seq dirs)
            (assoc merged :src-dirs dirs)
            {:error (str "no source directories found in " project-dir)})))
      ;; explicit dirs — use as-is
      merged)))

;;; ── pipeline ─────────────────────────────────────────────────────────────

(defn- merge-node-metrics [report metrics-map]
  (update report :nodes
          (fn [nodes]
            (mapv #(merge % (get metrics-map (:ns %) {})) nodes))))

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
         closed (close/close direct)
         report (-> closed
                    aggregate/aggregate
                    (merge-node-metrics (metrics/compute direct))
                    (update :nodes classify/classify)
                    (assoc :src-dirs src-dirs
                           :graph    direct
                           :cycles   (scc/find-cycles direct)))
         report (if conceptual-threshold
                  (let [tfidf (conceptual/build-tfidf ns->terms)
                        pairs (conceptual/conceptual-pairs tfidf direct conceptual-threshold 3)]
                    (assoc report
                           :conceptual-pairs     pairs
                           :conceptual-threshold conceptual-threshold))
                  report)]
     (if-let [change-dir (:change change-opts)]
       (let [min-co    (get change-opts :min-co 2)
             threshold (get change-opts :threshold 0.30)
             since     (get change-opts :since)
             commits   (-> (git/commits change-dir since)
                           (git/commits-as-ns src-dirs direct))
             pairs     (cc-change/change-coupling-pairs commits direct threshold min-co)]
         (assoc report
                :change-pairs     pairs
                :change-threshold threshold))
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
  [{:keys [src-dirs dot json edn conceptual change change-since exclude]}]
  (let [change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)]
    (when dot
      (spit dot (dot/generate report))
      (binding [*out* *err*] (println (str "DOT written to " dot))))
    (cond
      json (println (report-json/generate report))
      edn  (print   (report-edn/generate  report))
      :else (output/print-report report))))

(defn diagnose-cmd
  "Run diagnose with resolved opts map.
  Auto-enables conceptual (0.15) and change (.) when not explicitly set."
  [{:keys [src-dirs json edn conceptual change change-since exclude]}]
  (let [conceptual  (or conceptual 0.15)
        change      (if (nil? change) true change)
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)
        health      (diagnose/health report)
        findings    (diagnose/diagnose report)]
    (cond
      json (println (report-json/generate
                     (assoc report :findings findings :health health)))
      edn  (print   (report-edn/generate
                     (assoc report :findings findings :health health)))
      :else (output/print-diagnose report health findings))))

(defn explain-cmd
  "Run explain with resolved opts map.
  Auto-enables conceptual (0.15) and change (.) like diagnose."
  [{:keys [src-dirs json edn conceptual change change-since exclude explain-ns]}]
  (let [conceptual  (or conceptual 0.15)
        change      (if (nil? change) true change)
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)
        data        (explain/explain-ns report explain-ns)]
    (cond
      json (println (report-json/generate data))
      edn  (print   (report-edn/generate data))
      :else (output/print-explain-ns data))))

(defn explain-pair-cmd
  "Run explain-pair with resolved opts map."
  [{:keys [src-dirs json edn conceptual change change-since exclude
           explain-ns-a explain-ns-b]}]
  (let [conceptual  (or conceptual 0.15)
        change      (if (nil? change) true change)
        change-dir  (when change (if (string? change) change "."))
        change-opts (when change-dir {:change change-dir :since change-since})
        report      (build-report src-dirs conceptual change-opts exclude)
        data        (explain/explain-pair-data report explain-ns-a explain-ns-b)]
    (cond
      json (println (report-json/generate data))
      edn  (print   (report-edn/generate data))
      :else (output/print-explain-pair data))))

(defn run [args]
  (let [parsed (parse-args args)]
    (cond
      (:help parsed)  (do (print-help) (System/exit 0))
      (:error parsed) (do (println (str "Error: " (:error parsed)))
                          (println)
                          (print-help)
                          (System/exit 1))
      :else           (let [opts (resolve-opts parsed)]
                        (if (:error opts)
                          (do (println (str "Error: " (:error opts)))
                              (println)
                              (print-help)
                              (System/exit 1))
                          (case (:command opts)
                            :diagnose     (diagnose-cmd opts)
                            :explain      (explain-cmd opts)
                            :explain-pair (explain-pair-cmd opts)
                            (analyze opts)))))))

(defn -main [& args]
  (run args))
