(ns gordian.classify)

;;; Core/periphery classification adapted from MacCormack, Baldwin & Rusnak (2012).
;;;
;;; Each namespace is placed in a 2×2 grid using mean reach and mean fan-in as
;;; thresholds.  Both dimensions are already normalised to [0,1] by aggregate.
;;;
;;;             fan-in < mean   fan-in ≥ mean
;;;  reach < mean   isolated        core
;;;  reach ≥ mean   peripheral      shared
;;;
;;; :core       — stable foundation: low reach, high fan-in
;;;               "many things depend on this; it depends on little"
;;; :peripheral — leaf / entry point: high reach, low fan-in
;;;               "depends on much; nothing depends on it"
;;; :shared     — highly coupled in both directions
;;; :isolated   — standalone; loosely connected to the rest

(defn- mean [xs]
  (if (empty? xs)
    0.0
    (/ (double (apply + xs)) (count xs))))

(defn classify-node
  "Return role keyword for a single node given threshold values."
  [{:keys [reach fan-in]} reach-threshold fan-in-threshold]
  (cond
    (and (< reach reach-threshold)
         (>= fan-in fan-in-threshold)) :core
    (and (>= reach reach-threshold)
         (< fan-in fan-in-threshold))  :peripheral
    (and (>= reach reach-threshold)
         (>= fan-in fan-in-threshold)) :shared
    :else                              :isolated))

(defn classify
  "Annotate each node map in `nodes` with a :role keyword.
  Thresholds are the mean reach and mean fan-in across all nodes."
  [nodes]
  (if (empty? nodes)
    nodes
    (let [reach-mean  (mean (map :reach  nodes))
          fan-in-mean (mean (map :fan-in nodes))]
      (mapv #(assoc % :role (classify-node % reach-mean fan-in-mean))
            nodes))))
