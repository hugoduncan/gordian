(ns gordian.local.ops)

(def branch
  '#{if if-let if-some when when-not when-let when-some when-first cond condp case})

(def if-like
  '#{if if-let if-some when when-not when-let when-some when-first})

(def half-branch
  '#{when when-not when-let when-some when-first})

(def transparent
  '#{assoc dissoc update merge select-keys conj into vec set mapv
     map filter keep remove keys vals first rest next get contains?
     inc dec + - * / = < > <= >= not some? nil? empty?
     identity keyword symbol str hash-map array-map vector})

(def incidental
  '#{log debug info warn error trace println prn printf})

(def mutation
  '#{swap! reset! compare-and-set! vswap! vreset! set! assoc! conj! disj! pop!})

(def mutable-cell-constructors
  '#{atom volatile! ref agent})

(def effect-read
  '#{slurp read-string rand rand-int System/currentTimeMillis System/nanoTime})

(def effect-write
  '#{spit println prn printf})

(def inversion
  '#{reduce transduce doseq for future pmap mapcat keep})

(def shape
  '#{assoc dissoc merge update select-keys conj into vec set mapv keys vals
     hash-map array-map vector})

(def threading
  '#{-> ->> some-> some->> cond-> cond->>})

(def sentinel-literals
  #{nil false :ok :error :none :some :left :right :success :failure})

(def special-forms
  '#{let let* loop loop* do quote var fn fn* recur try catch throw def
     new . if if-let if-some when when-not when-let when-some when-first
     cond condp case letfn})
