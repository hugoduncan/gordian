(ns gordian.local.common
  (:require [clojure.set :as set]))

(def branch-ops
  '#{if if-let if-some when when-not when-let when-some when-first cond condp case})

(def if-like-ops
  '#{if if-let if-some when when-not when-let when-some when-first})

(def half-branch-ops
  '#{when when-not when-let when-some when-first})

(def transparent-ops
  '#{assoc dissoc update merge select-keys conj into vec set mapv
     map filter keep remove keys vals first rest next get contains?
     inc dec + - * / = < > <= >= not some? nil? empty?
     identity keyword symbol str hash-map array-map vector})

(def incidental-ops
  '#{log debug info warn error trace println prn printf})

(def mutation-ops
  '#{swap! reset! compare-and-set! vswap! vreset! set! assoc! conj! disj! pop!})

(def mutable-cell-constructors
  '#{atom volatile! ref agent})

(def effect-read-ops
  '#{slurp read-string rand rand-int System/currentTimeMillis System/nanoTime})

(def effect-write-ops
  '#{spit println prn printf})

(def inversion-ops
  '#{reduce transduce doseq for future pmap mapcat keep})

(def shape-ops
  '#{assoc dissoc merge update select-keys conj into vec set mapv keys vals
     hash-map array-map vector})

(def threading-ops
  '#{-> ->> some-> some->> cond-> cond->>})

(def sentinel-literals
  #{nil false :ok :error :none :some :left :right :success :failure})

(def special-forms
  '#{let let* loop loop* do quote var fn fn* recur try catch throw def
     new . if if-let if-some when when-not when-let when-some when-first
     cond condp case letfn})

(defn seq-form? [x]
  (seq? x))

(defn op-of [form]
  (when (seq-form? form)
    (first form)))

(defn tree-forms [body]
  (tree-seq coll? seq body))

(defn scalar-literal? [x]
  (or (nil? x)
      (true? x)
      (false? x)
      (string? x)
      (number? x)
      (char? x)
      (keyword? x)))

(defn symbol-binding? [sym]
  (and (symbol? sym)
       (not= '_ sym)
       (not= '& sym)))

(defn literal-keyword-set [x]
  (when (and (coll? x) (every? keyword? x))
    (set x)))

(defn binding-symbols [binding]
  (letfn [(collect [x]
            (cond
              (symbol-binding? x) #{x}

              (vector? x)
              (reduce set/union #{} (map collect (remove #{'&} x)))

              (map? x)
              (reduce
               set/union
               #{}
               (concat
                (when-let [as-sym (:as x)] [(collect as-sym)])
                (when-let [ks (:keys x)] [(set (filter symbol-binding? ks))])
                (when-let [ss (:syms x)] [(set (filter symbol-binding? ss))])
                (when-let [ss (:strs x)] [(set (map symbol ss))])
                (for [[k v] x
                      :when (not (contains? #{:as :keys :syms :strs :or} k))]
                  (collect v))))

              :else
              #{}))]
    (collect binding)))

(defn destructure-symbol-count [binding]
  (count (binding-symbols binding)))
