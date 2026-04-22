(ns gordian.local.common
  (:require [clojure.set :as set]))

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
