(ns gordian.gate
  "Evaluate CI / ratchet checks over a compare diff.
  All functions are pure — they take data and return data."
  (:require [clojure.string :as str]))

(def ^:private default-checks
  [:pc-delta :new-cycles :new-high-findings])

(def ^:private known-checks
  #{:pc-delta :new-cycles :new-high-findings :new-medium-findings})

(defn check-pc-delta
  "Fail if propagation cost delta exceeds limit."
  [diff limit]
  (let [actual (double (or (get-in diff [:health :delta :propagation-cost]) 0.0))]
    {:name   :pc-delta
     :status (if (<= actual limit) :pass :fail)
     :limit  limit
     :actual actual
     :reason (str "propagation cost delta="
                  (format "%+.4f" actual)
                  " limit=" (format "%+.4f" limit))}))

(defn check-new-cycles
  "Fail if any new cycles were introduced."
  [diff]
  (let [actual (count (get-in diff [:cycles :added] []))]
    {:name   :new-cycles
     :status (if (zero? actual) :pass :fail)
     :limit  0
     :actual actual
     :reason (str actual " new cycle" (when (not= 1 actual) "s"))}))

(defn check-new-findings
  "Fail if newly-added findings of the given severity exceed limit."
  [diff severity limit]
  (let [actual (count (filter #(= severity (:severity %))
                              (get-in diff [:findings :added] [])))]
    {:name   (case severity
               :high   :new-high-findings
               :medium :new-medium-findings)
     :status (if (<= actual limit) :pass :fail)
     :limit  limit
     :actual actual
     :reason (str actual " new " (name severity)
                  " finding" (when (not= 1 actual) "s"))}))

(defn- parse-fail-on
  "Parse comma-separated check names from CLI.
  Returns a set of check keywords, or nil when not supplied."
  [s]
  (when (seq s)
    (let [checks (->> (str/split s #",")
                      (map str/trim)
                      (remove str/blank?)
                      (map keyword)
                      set)]
      (when-let [unknown (seq (remove known-checks checks))]
        (throw (ex-info (str "unknown gate checks: "
                             (str/join ", " (sort (map name unknown))))
                        {:unknown-checks unknown})))
      checks)))

(defn resolve-checks
  "Resolve and evaluate gate checks from CLI opts and a compare diff.
  Defaults: :pc-delta (0.01), :new-cycles, :new-high-findings.
  Explicit threshold opts add or override checks.
  --fail-on selects a strict named subset, with threshold opts also adding more."
  [opts diff]
  (let [selected (or (parse-fail-on (:fail-on opts)) (set default-checks))
        selected (cond-> selected
                   (contains? opts :max-pc-delta)            (conj :pc-delta)
                   (contains? opts :max-new-high-findings)   (conj :new-high-findings)
                   (contains? opts :max-new-medium-findings) (conj :new-medium-findings))]
    (vec
     (concat
      (when (contains? selected :pc-delta)
        [(check-pc-delta diff (double (or (:max-pc-delta opts) 0.01)))])
      (when (contains? selected :new-cycles)
        [(check-new-cycles diff)])
      (when (contains? selected :new-high-findings)
        [(check-new-findings diff :high (long (or (:max-new-high-findings opts) 0)))])
      (when (contains? selected :new-medium-findings)
        [(check-new-findings diff :medium (long (or (:max-new-medium-findings opts) 0)))])))))

(defn gate-result
  "Overall gate result from evaluated checks."
  [checks]
  (if (every? #(= :pass (:status %)) checks) :pass :fail))

(defn summarize
  "Summarize check statuses."
  [checks]
  (let [failed (count (filter #(= :fail (:status %)) checks))
        total  (count checks)]
    {:passed (- total failed)
     :failed failed
     :total  total}))

(defn gate-report
  "Assemble the gate result map from baseline/current comparison."
  [baseline-file baseline current diff checks]
  (let [warnings (cond-> []
                   (not= (:src-dirs baseline) (:src-dirs current))
                   (conj {:kind :src-dirs-mismatch
                          :baseline (:src-dirs baseline)
                          :current  (:src-dirs current)}))]
    {:gordian/command :gate
     :baseline-file   baseline-file
     :result          (gate-result checks)
     :checks          checks
     :summary         (summarize checks)
     :warnings        warnings
     :src-dirs        (:src-dirs current)
     :compare         diff}))
