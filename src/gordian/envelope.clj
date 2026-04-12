(ns gordian.envelope
  "Assemble the standard metadata envelope for machine-readable output.
  Pure — takes data, returns data.")

(def schema-version
  "Integer schema version. Bumped on breaking changes to output shape."
  1)

(def gordian-version
  "Human-readable version string."
  "0.2.0")

(defn- lens-conceptual
  "Build the :conceptual lens descriptor from a report map."
  [report]
  (if-let [threshold (:conceptual-threshold report)]
    {:enabled         true
     :threshold       threshold
     :candidate-pairs (or (:conceptual-candidate-count report) 0)
     :reported-pairs  (count (or (:conceptual-pairs report) []))}
    {:enabled false}))

(defn- lens-change
  "Build the :change lens descriptor from a report map and opts."
  [report opts]
  (if-let [threshold (:change-threshold report)]
    (cond-> {:enabled         true
             :threshold       threshold
             :candidate-pairs (or (:change-candidate-count report) 0)
             :reported-pairs  (count (or (:change-pairs report) []))}
      (:change-since opts) (assoc :since (:change-since opts))
      (:change opts)       (assoc :repo-dir (if (string? (:change opts))
                                              (:change opts) ".")))
    {:enabled false}))

(def ^:private internal-keys
  "Keys stripped from the report before embedding in the envelope.
  These are either internal implementation details or migrated to the
  envelope's :lenses section."
  [:graph :ns->terms :conceptual-threshold :change-threshold
   :conceptual-candidate-count :change-candidate-count])

(defn wrap
  "Wrap a report or result map in the standard gordian envelope.
  opts    — resolved CLI options (for lens config metadata)
  data    — output of build-report, or explain/explain-pair data
  command — :analyze | :diagnose | :explain | :explain-pair"
  [opts data command]
  (merge
   {:gordian/version gordian-version
    :gordian/schema  schema-version
    :gordian/command command
    :lenses          {:structural true
                      :conceptual (lens-conceptual data)
                      :change     (lens-change data opts)}
    :src-dirs        (or (:src-dirs opts) (:src-dirs data) [])
    :excludes        (or (:exclude opts) [])
    :include-tests   (boolean (:include-tests opts))}
   (apply dissoc data internal-keys)))
