(ns gordian.local.report
  (:require [gordian.local.units :as units]
            [gordian.local.evidence :as evidence]
            [gordian.local.burden :as burden]
            [gordian.local.findings :as findings]))

(def ^:private min-metrics
  #{:total :flow :state :shape :abstraction :dependency :working-set})

(def ^:private bar-metrics
  min-metrics)

(def ^:private sort-keys
  (conj min-metrics :ns :var))

(def ^:private metric->field
  {:total :lcc-total
   :flow :flow-burden
   :state :state-burden
   :shape :shape-burden
   :abstraction :abstraction-burden
   :dependency :dependency-burden
   :working-set [:working-set :burden]})

(def ^:private metric-name-order
  [:flow-burden :state-burden :shape-burden :abstraction-burden :dependency-burden])

(defn parse-min-expression
  [expr]
  (when-let [[_ metric n] (re-matches #"([a-z-]+)=(\d+)" (str expr))]
    (let [metric (keyword metric)
          n      (parse-long n)]
      (when (and (min-metrics metric) (pos? n))
        [metric n]))))

(defn mins-map [{:keys [min]}]
  (when (seq min)
    (into {} (keep parse-min-expression) min)))

(defn local-options
  [{:keys [sort top bar] :as opts}]
  {:sort sort
   :top top
   :bar bar
   :mins (mins-map opts)})

(defn local-scope [mode paths]
  {:mode mode
   :source? (boolean (some #(= :src (:kind %)) paths))
   :tests? (boolean (some #(= :test (:kind %)) paths))
   :paths (mapv :dir paths)})

(defn effective-bar-metric
  [{:keys [sort bar]}]
  (or bar
      (when (min-metrics sort) sort)
      :total))

(defn metric-value
  [unit metric]
  (let [field (get metric->field metric)]
    (if (vector? field)
      (get-in unit field 0)
      (get unit field 0))))

(defn unit-satisfies-mins?
  [mins unit]
  (every? (fn [[metric threshold]]
            (<= threshold (metric-value unit metric)))
          mins))

(defn filter-units-by-mins
  [units mins]
  (if (seq mins)
    (into [] (filter #(unit-satisfies-mins? mins %)) units)
    (vec units)))

(defn truncate-section
  [xs top]
  (if (and top (pos? top))
    (vec (take top xs))
    (vec xs)))

(defn sort-units
  [units sort-key]
  (vec
   (case (or sort-key :total)
     :ns (sort-by (juxt :ns (comp - double :lcc-total) :var :kind :arity :dispatch) units)
     :var (sort-by (juxt :ns :var (comp - double :lcc-total) :kind :arity :dispatch) units)
     (sort-by (juxt (comp - double #(metric-value % (or sort-key :total)))
                    (comp - double :lcc-total)
                    :ns :var :kind :arity :dispatch)
              units))))

(defn- avg [xs]
  (if (seq xs)
    (/ (double (reduce + 0 xs)) (count xs))
    0.0))

(defn namespace-rollups
  [units]
  (->> units
       (group-by :ns)
       (map (fn [[ns ns-units]]
              (let [totals (map :lcc-total ns-units)]
                {:ns ns
                 :unit-count (count ns-units)
                 :total-lcc (reduce + 0 totals)
                 :avg-lcc (avg totals)
                 :max-lcc (apply max 0 totals)
                 :avg-flow (avg (map :flow-burden ns-units))
                 :avg-state (avg (map :state-burden ns-units))
                 :avg-shape (avg (map :shape-burden ns-units))
                 :avg-abstraction (avg (map :abstraction-burden ns-units))
                 :avg-dependency (avg (map :dependency-burden ns-units))
                 :avg-working-set (avg (map #(get-in % [:working-set :burden]) ns-units))})))
       (sort-by :ns)
       vec))

(defn project-rollup
  [units namespace-rollups]
  (let [totals (map :lcc-total units)]
    {:unit-count (count units)
     :namespace-count (count namespace-rollups)
     :total-lcc (reduce + 0 totals)
     :avg-lcc (avg totals)
     :max-lcc (apply max 0 totals)
     :avg-flow (avg (map :flow-burden units))
     :avg-state (avg (map :state-burden units))
     :avg-shape (avg (map :shape-burden units))
     :avg-abstraction (avg (map :abstraction-burden units))
     :avg-dependency (avg (map :dependency-burden units))
     :avg-working-set (avg (map #(get-in % [:working-set :burden]) units))
     :finding-counts (reduce (fn [acc {:keys [kind]}]
                               (update acc kind (fnil inc 0)))
                             {}
                             (mapcat :findings units))}))

(defn max-unit
  [units]
  (first (sort-by (juxt (comp - double :lcc-total) :ns :var :kind :arity :dispatch) units)))

(defn- rollup-sort-value [sort-key rollup]
  (case sort-key
    :flow (:avg-flow rollup)
    :state (:avg-state rollup)
    :shape (:avg-shape rollup)
    :abstraction (:avg-abstraction rollup)
    :dependency (:avg-dependency rollup)
    :working-set (:avg-working-set rollup)
    :ns nil
    :var nil
    (:max-lcc rollup)))

(defn sort-rollups
  [rollups sort-key]
  (vec
   (case (or sort-key :total)
     :ns (sort-by (juxt :ns (comp - double :max-lcc)) rollups)
     :var (sort-by (juxt :ns (comp - double :max-lcc)) rollups)
     (sort-by (juxt (comp - double #(rollup-sort-value sort-key %))
                    (comp - double :max-lcc)
                    :ns)
              rollups))))

(defn analyze-units
  [files]
  (->> files
       (mapcat #(units/analyzable-units (assoc % :origin (or (:origin %) :src))))
       (map evidence/extract-evidence)
       (map burden/score-unit)
       (map #(assoc % :findings (findings/findings-for-unit %)))
       (map #(dissoc % :body :args :unit-id :evidence))
       vec))

(defn rollup
  [files]
  (let [units             (analyze-units files)
        namespace-rollups (namespace-rollups units)
        project           (project-rollup units namespace-rollups)]
    {:gordian/command :local
     :metric :local-comprehension-complexity
     :units units
     :namespace-rollups namespace-rollups
     :project-rollup project
     :max-unit (max-unit units)}))

(defn finalize-report
  [report mode paths opts]
  (let [mins (mins-map opts)]
    (-> report
        (assoc :src-dirs (mapv :dir paths)
               :scope (local-scope mode paths)
               :options (local-options opts)
               :bar-metric (effective-bar-metric opts))
        (update :units filter-units-by-mins mins)
        (update :units sort-units (:sort opts))
        (update :units truncate-section (:top opts))
        (update :namespace-rollups sort-rollups (:sort opts))
        (update :namespace-rollups truncate-section (:top opts)))))

(defn valid-sort-key? [k]
  (contains? sort-keys k))

(defn valid-bar-metric? [k]
  (contains? bar-metrics k))
