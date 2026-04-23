(ns gordian.main
  (:require [gordian.scan        :as scan]
            [gordian.close       :as close]
            [gordian.aggregate   :as aggregate]
            [gordian.metrics     :as metrics]
            [gordian.scc         :as scc]
            [gordian.classify    :as classify]
            [gordian.output      :as output]
            [gordian.cli         :as gcli]
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
            [gordian.cyclomatic  :as cyclomatic]
            [gordian.local.report :as local]
            [gordian.config      :as config]
            [gordian.filter      :as gfilter]
            [gordian.family      :as family]
            [gordian.enforcement :as enforcement]
            [gordian.envelope    :as envelope]
            [gordian.dot         :as dot]
            [gordian.json        :as report-json]
            [babashka.fs         :as fs]
            [gordian.edn         :as report-edn]
            [clojure.edn         :as edn]))

;;; ── help + arg parsing ──────────────────────────────────────────────────

(defn print-help
  ([] (println (gcli/help-text)))
  ([command] (println (gcli/help-text command))))

(defn parse-args [raw-args]
  (gcli/parse-args raw-args))

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
        merged         (cond
                         (= :tests command)
                         (assoc merged0 :include-tests true)

                         (and (contains? #{:complexity :local} command) (:tests-only merged0))
                         (assoc merged0 :include-tests true)

                         (and (contains? #{:complexity :local} command) (:source-only merged0))
                         (assoc merged0 :include-tests false)

                         :else
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

(defn- resolve-local-analysis-paths
  "Resolve typed scan paths for local analysis modes.
  Default project-root behavior is discovered source paths only.
  `--tests-only` switches to discovered test paths only.
  Explicit paths override discovery and are typed heuristically."
  [{:keys [src-dirs tests-only] :as opts}]
  (let [project-dir (when (and (= 1 (count src-dirs))
                               (discover/project-root? (first src-dirs)))
                      (first src-dirs))]
    (if project-dir
      (let [cfg         (config/load-config project-dir)
            discovered  (discover/discover-dirs project-dir)
            chosen-src  (if tests-only [] (or (seq (:src-dirs cfg)) (:src-dirs discovered)))
            chosen-test (if tests-only (:test-dirs discovered) [])
            typed       (discover/resolve-paths {:src-dirs chosen-src
                                                 :test-dirs chosen-test}
                                                (assoc opts :include-tests (boolean tests-only)))]
        (if (seq typed)
          typed
          {:error (str "no source directories found in " project-dir)}))
      (mapv (fn [dir]
              {:dir dir :kind (if (explicit-test-path? dir) :test :src)})
            src-dirs))))

(defn complexity-cmd
  "Run complexity mode with resolved opts map. Returns 0 on success, 1 on threshold failure."
  [{:keys [json edn markdown] :as opts}]
  (let [mode  (if (and (= 1 (count (:src-dirs opts)))
                       (discover/project-root? (first (:src-dirs opts))))
                :discovered
                :explicit)
        paths (resolve-local-analysis-paths opts)]
    (if (:error paths)
      (do
        (println (str "Error: " (:error paths)))
        1)
      (let [files         (->> paths
                               (mapcat (fn [{:keys [dir kind]}]
                                         (->> (fs/glob dir "**.clj")
                                              (map str)
                                              clojure.core/sort
                                              (keep #(some-> (scan/parse-file-all-forms %)
                                                             (assoc :origin kind))))))
                               vec)
            canonical    (cyclomatic/rollup files)
            base-data    (cyclomatic/finalize-report canonical
                                                     mode
                                                     paths
                                                     opts)
            enforcement  (when-let [checks (seq (cyclomatic/fail-above-checks opts))]
                           (enforcement/evaluate {:units (:units canonical)
                                                  :checks checks
                                                  :metric-value cyclomatic/metric-value
                                                  :unit->violation cyclomatic/unit->enforcement-violation}))
            data         (cond-> base-data enforcement (assoc :enforcement enforcement))]
        (cond
          json     (println (report-json/generate (envelope/wrap opts data :complexity)))
          edn      (print   (report-edn/generate  (envelope/wrap opts data :complexity)))
          markdown (run! println (output/format-complexity-md data))
          :else    (output/print-complexity data))
        (if (and enforcement (not (:passed? enforcement))) 1 0)))))

(defn local-cmd
  "Run local mode with resolved opts map. Returns 0 on success, 1 on threshold failure."
  [{:keys [json edn markdown] :as opts}]
  (let [mode  (if (and (= 1 (count (:src-dirs opts)))
                       (discover/project-root? (first (:src-dirs opts))))
                :discovered
                :explicit)
        paths (resolve-local-analysis-paths opts)]
    (if (:error paths)
      (do
        (println (str "Error: " (:error paths)))
        1)
      (let [files        (->> paths
                              (mapcat (fn [{:keys [dir kind]}]
                                        (->> (fs/glob dir "**.clj")
                                             (map str)
                                             clojure.core/sort
                                             (keep #(some-> (scan/parse-file-all-forms %)
                                                            (assoc :origin kind))))))
                              vec)
            base-data    (local/finalize-report (local/rollup files)
                                                mode
                                                paths
                                                opts)
            enforcement  (when-let [checks (seq (local/fail-above-checks opts))]
                           (enforcement/evaluate {:units (:units base-data)
                                                  :checks checks
                                                  :metric-value local/metric-value
                                                  :unit->violation local/unit->enforcement-violation}))
            data         (cond-> base-data enforcement (assoc :enforcement enforcement))]
        (cond
          json     (println (report-json/generate (envelope/wrap opts data :local)))
          edn      (print   (report-edn/generate  (envelope/wrap opts data :local)))
          markdown (run! println (output/format-local-md data))
          :else    (output/print-local data))
        (if (and enforcement (not (:passed? enforcement))) 1 0)))))

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

(def ^:private command-handlers
  {:diagnose     diagnose-cmd
   :subgraph     subgraph-cmd
   :communities  communities-cmd
   :dsm          dsm-cmd
   :tests        tests-cmd
   :complexity   complexity-cmd
   :local        local-cmd
   :explain      explain-cmd
   :explain-pair explain-pair-cmd})

(defn run [args]
  (let [parsed (parse-args args)]
    (cond
      (:help parsed)  (do (if-let [command (gcli/parse-result-help-command parsed)]
                            (print-help command)
                            (print-help))
                          (System/exit 0))
      (:error parsed) (do (println (str "Error: " (:error parsed)))
                          (println)
                          (if-let [command (:command parsed)]
                            (print-help command)
                            (print-help))
                          (System/exit 1))
      :else           (if (= :compare (:command parsed))
                        (compare-cmd parsed)
                        (let [opts (resolve-opts parsed)]
                          (if (:error opts)
                            (do (println (str "Error: " (:error opts)))
                                (println)
                                (if-let [command (:command parsed)]
                                  (print-help command)
                                  (print-help))
                                (System/exit 1))
                            (if-let [handler (get command-handlers (:command opts))]
                              (let [result (handler opts)]
                                (when (contains? #{:complexity :local} (:command opts))
                                  (System/exit result)))
                              (if (= :gate (:command opts))
                                (System/exit (gate-cmd opts))
                                (analyze opts)))))))))

(defn -main [& args]
  (run args))
