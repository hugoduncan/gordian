(ns gordian.cli)

(def ^:private command-definitions
  [{:canonical :analyze
    :names ["analyze"]
    :summary "Raw metrics table + optional coupling sections"}
   {:canonical :diagnose
    :names ["diagnose"]
    :summary "Ranked findings with severity levels (auto-enables all lenses)"}
   {:canonical :compare
    :names ["compare"]
    :summary "Compare two saved EDN snapshots (before.edn after.edn)"}
   {:canonical :gate
    :names ["gate"]
    :summary "Compare current codebase against a saved baseline and fail CI on regressions"}
   {:canonical :subgraph
    :names ["subgraph"]
    :summary "Family/subsystem view for a namespace prefix"}
   {:canonical :communities
    :names ["communities"]
    :summary "Discover latent architecture communities"}
   {:canonical :dsm
    :names ["dsm"]
    :summary "Dependency Structure Matrix view with diagonal block partitions"}
   {:canonical :tests
    :names ["tests"]
    :summary "Analyze test architecture and test-vs-source coupling"}
   {:canonical :cyclomatic
    :names ["complexity" "cyclomatic"]
    :display-name "complexity"
    :summary "Analyze local code metrics (cyclomatic complexity + LOC) with namespace rollups"}
   {:canonical :explain
    :names ["explain"]
    :summary "Everything gordian knows about a namespace"}
   {:canonical :explain-pair
    :names ["explain-pair"]
    :summary "Everything gordian knows about a pair of namespaces"}])

(def ^:private command-index
  (into {}
        (mapcat (fn [{:keys [canonical names]}]
                  (map (fn [name] [name canonical]) names)))
        command-definitions))

(defn resolve-command [token]
  (get command-index token))

(defn command-definitions []
  command-definitions)

(defn command-summary-lines []
  (mapv (fn [{:keys [names canonical display-name summary]}]
          {:canonical canonical
           :name (or display-name (first names))
           :summary summary
           :aliases (vec (rest names))})
        command-definitions))
