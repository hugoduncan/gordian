(ns gordian.local.report
  (:require [clojure.string :as str]
            [gordian.local.units :as units]
            [gordian.local.evidence :as evidence]
            [gordian.local.burden :as burden]
            [gordian.local.findings :as findings]))

(def ^:private metric-aliases
  {:total [:lcc-total]
   :flow [:flow-burden]
   :state [:state-burden]
   :shape [:shape-burden]
   :abstraction [:abstraction-burden]
   :dependency [:dependency-burden]
   :working-set [:working-set :burden]})

(def ^:private special-sort-keys
  #{:ns :var})

(def ^:private supported-local-numeric-schema
  {:line 0
   :arity 0
   :flow-burden 0.0
   :state-burden 0.0
   :shape-burden 0.0
   :abstraction-burden 0.0
   :dependency-burden 0.0
   :working-set {:peak 0
                 :avg 0.0
                 :burden 0.0}
   :normalized-burdens {:flow 0.0
                        :state 0.0
                        :shape 0.0
                        :abstraction 0.0
                        :dependency 0.0
                        :working-set 0.0}
   :lcc-total 0.0})

(defn- numeric-leaf-paths
  ([x] (numeric-leaf-paths [] x))
  ([prefix x]
   (cond
     (number? x) #{prefix}
     (map? x) (into #{}
                    (mapcat (fn [[k v]]
                              (numeric-leaf-paths (conj prefix k) v)))
                    x)
     :else #{})))

(def ^:private supported-local-numeric-paths
  (numeric-leaf-paths supported-local-numeric-schema))

(defn metric-token->path
  [metric]
  (when metric
    (or (get metric-aliases metric)
        (let [path (mapv keyword (str/split (name metric) #"\."))]
          (when (contains? supported-local-numeric-paths path)
            path)))))

(defn numeric-metric-token?
  [metric]
  (some? (metric-token->path metric)))

(defn parse-min-expression
  [expr]
  (when-let [[_ metric n] (re-matches #"([a-zA-Z0-9.-]+)=(\d+)" (str expr))]
    (let [metric (keyword metric)
          n      (parse-long n)]
      (when (and (numeric-metric-token? metric) (pos? n))
        [metric n]))))

(defn parse-fail-above-expression
  [expr]
  (when-let [[_ metric n] (re-matches #"([a-zA-Z0-9.-]+)=([0-9]+(?:\.[0-9]+)?)" (str expr))]
    (let [metric (keyword metric)
          n      (Double/parseDouble n)]
      (when (and (numeric-metric-token? metric) (pos? n))
        [metric n]))))

(defn unit->enforcement-violation
  [{:keys [ns var kind arity dispatch]}]
  {:ns ns
   :var var
   :kind kind
   :arity arity
   :dispatch dispatch})

(defn fail-above-checks
  [{:keys [fail-above]}]
  (when (seq fail-above)
    (mapv (fn [[metric threshold]]
            {:metric (metric-token->path metric)
             :metric-token metric
             :threshold threshold})
          (keep parse-fail-above-expression fail-above))))

(defn mins-map [{:keys [min]}]
  (when (seq min)
    (into {} (keep parse-min-expression) min)))

(defn local-options
  [{:keys [sort top bar namespace-rollup project-rollup] :as opts}]
  {:sort sort
   :top top
   :bar bar
   :namespace-rollup (boolean namespace-rollup)
   :project-rollup (boolean project-rollup)
   :mins (mins-map opts)})

(defn local-scope [mode paths]
  {:mode mode
   :source? (boolean (some #(= :src (:kind %)) paths))
   :tests? (boolean (some #(= :test (:kind %)) paths))
   :paths (mapv :dir paths)})

(defn effective-bar-metric
  [{:keys [sort bar]}]
  (or bar
      (when (numeric-metric-token? sort) sort)
      :total))

(defn metric-value
  [unit metric]
  (double (or (some->> (metric-token->path metric)
                       (get-in unit))
              0)))

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
     (sort-by (juxt (comp - #(metric-value % (or sort-key :total)))
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

(defn- rollup-sort-value
  [sort-key ns-units rollup]
  (case sort-key
    :flow (:avg-flow rollup)
    :state (:avg-state rollup)
    :shape (:avg-shape rollup)
    :abstraction (:avg-abstraction rollup)
    :dependency (:avg-dependency rollup)
    :working-set (:avg-working-set rollup)
    :ns nil
    :var nil
    (if (numeric-metric-token? sort-key)
      (avg (map #(metric-value % sort-key) ns-units))
      (:max-lcc rollup))))

(defn sort-rollups
  [units rollups sort-key]
  (let [units-by-ns (group-by :ns units)]
    (vec
     (case (or sort-key :total)
       :ns (sort-by (juxt :ns (comp - double :max-lcc)) rollups)
       :var (sort-by (juxt :ns (comp - double :max-lcc)) rollups)
       (sort-by (juxt (fn [rollup]
                        (- (double (rollup-sort-value sort-key (get units-by-ns (:ns rollup) []) rollup))))
                      (comp - double :max-lcc)
                      :ns)
                rollups)))))

(defn analyze-units
  [files]
  (let [units (->> files
                   (mapcat #(units/analyzable-units (assoc % :origin (or (:origin %) :src))))
                   (map evidence/extract-evidence)
                   (map burden/score-unit)
                   vec)
        calibration (burden/calibrate units)]
    {:calibration calibration
     :units (->> units
                 (map #(assoc % :findings (findings/findings-for-unit %)))
                 (map #(burden/apply-calibration calibration %))
                 (map #(dissoc % :body :args :unit-id :evidence))
                 vec)}))

(defn rollup
  [files]
  (let [{:keys [units calibration]} (analyze-units files)
        namespace-rollups (namespace-rollups units)
        project           (project-rollup units namespace-rollups)]
    {:gordian/command :local
     :metric :local-comprehension-complexity
     :calibration calibration
     :units units
     :namespace-rollups namespace-rollups
     :project-rollup project
     :max-unit (max-unit units)}))

(defn- canonical-summary
  [units]
  {:unit-count (count units)
   :namespaces (->> units (map :ns) distinct sort vec)})

(defn finalize-report
  [report mode paths opts]
  (let [options         (local-options opts)
        canonical-units (:units report)
        shaped-units    (-> canonical-units
                            (filter-units-by-mins (:mins options))
                            (sort-units (:sort options))
                            (truncate-section (:top options)))
        shaped-rollups  (when (:namespace-rollup options)
                          (-> (sort-rollups canonical-units (:namespace-rollups report) (:sort options))
                              (truncate-section (:top options))))]
    (cond-> (-> report
                (assoc :src-dirs (mapv :dir paths)
                       :scope (local-scope mode paths)
                       :options options
                       :bar-metric (effective-bar-metric opts)
                       :canonical-summary (canonical-summary canonical-units)
                       :units shaped-units)
                (assoc :namespace-rollups shaped-rollups))
      (not (:namespace-rollup options)) (dissoc :namespace-rollups)
      (not (:project-rollup options)) (dissoc :project-rollup))))

(defn valid-sort-key? [k]
  (or (contains? special-sort-keys k)
      (numeric-metric-token? k)))

(defn valid-bar-metric? [k]
  (numeric-metric-token? k))
