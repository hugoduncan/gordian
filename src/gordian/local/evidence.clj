(ns gordian.local.evidence
  (:require [gordian.local.common :as common]
            [gordian.local.dependency :as dependency]
            [gordian.local.flow :as flow]
            [gordian.local.shape :as shape]
            [gordian.local.state :as state]
            [gordian.local.steps :as steps]
            [gordian.local.working-set :as working-set]))

(defn extract-evidence
  "Attach the canonical local-analysis evidence payload to one analyzable unit.

   Evidence schema:
   {:main-steps     [step ...]          ; ordered main-path/binding/pipeline steps
    :branch-regions [region ...]        ; branch forms with classified outcomes
    :step-levels    [level ...]         ; abstraction level per main step
    :program-points [point ...]         ; working-set sampling points
    :flow           {...}               ; control-flow burden inputs
    :state          {...}               ; mutation/effect burden inputs
    :shape          {...}               ; shape/variant burden inputs
    :abstraction    {...}               ; abstraction-mix burden inputs
    :dependency     {...}}              ; helper/opacity burden inputs"
  [{:keys [body args var] :as unit}]
  (let [forms               (filter common/seq-form? (common/tree-forms body))
        main-steps          (steps/main-path-steps body)
        {:keys [steps*]}    (shape/step-shapes main-steps args)
        flow-data           (flow/gather-flow `(do ~@body) 0 var)
        branch-regions      (shape/branch-regions body {})
        mutable-syms        (state/mutable-binding-symbols steps*)
        mutation-sites      (count (for [form forms
                                         :when (and (common/mutation-ops (common/op-of form))
                                                    (symbol? (second form))
                                                    (mutable-syms (second form)))]
                                     form))
        external-writes     (+ (count (filter #(common/effect-write-ops (common/op-of %)) forms))
                               (count (for [form forms
                                            :when (and (common/mutation-ops (common/op-of form))
                                                       (symbol? (second form))
                                                       (not (mutable-syms (second form))))]
                                        form)))
        helper-sites        (dependency/non-transparent-call-sites forms)
        {:keys [opaque-stages stage-ops]} (dependency/opaque-chain-data steps*)
        helper-count        (count (remove #(stage-ops (common/op-of %)) helper-sites))
        levels              (mapv steps/step-abstraction-level steps*)
        program-points'     (working-set/program-points steps* args)]
    (assoc unit
           :evidence
           {:main-steps steps*
            :branch-regions branch-regions
            :step-levels levels
            :program-points program-points'
            :flow {:branch (:branch flow-data)
                   :nest (:nest flow-data)
                   :interrupt (:interrupt flow-data)
                   :recursion (min 1 (:recursion flow-data))
                   :logic (:logic flow-data)
                   :max-depth (:max-depth flow-data)}
            :state {:mutation-sites mutation-sites
                    :rebindings (state/meaningful-rebindings steps*)
                    :temporal-dependencies (state/temporal-dependencies (common/tree-forms body) mutable-syms)
                    :effect-reads (count (filter #(common/effect-read-ops (common/op-of %)) forms))
                    :effect-writes external-writes
                    :mutable-entities (count mutable-syms)}
            :shape {:transitions (shape/main-path-transitions steps*)
                    :destructure (->> steps*
                                      (filter #(= :binding (:kind %)))
                                      (map :destructure-count)
                                      (map #(max 0 (* 0.5 (- % 3))))
                                      (reduce + 0))
                    :variant (reduce + 0 (map #(or (:variant-count %) 0) branch-regions))
                    :nesting (count (filter #(> (shape/data-nesting-depth %) 2) body))
                    :sentinel (shape/sentinel-count forms)}
            :abstraction {:levels levels
                          :distinct-levels (set levels)
                          :incidental (count (filter #(common/incidental-ops (common/op-of %)) forms))}
            :dependency {:helpers helper-count
                         :opaque-stages opaque-stages
                         :inversion (count (filter #(common/inversion-ops (common/op-of %)) forms))
                         :semantic-jumps (dependency/distinct-semantic-jumps steps*)}})))
