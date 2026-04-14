(ns gordian.conceptual-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.conceptual :as sut]))

(def normalize-tfidf #'gordian.conceptual/normalize-tfidf)

;;; ── tokenize ──────────────────────────────────────────────────────────────

(deftest tokenize-test
  (testing "kebab-case symbol splits on hyphens"
    (is (= ["propagation" "cost"] (sut/tokenize 'propagation-cost))))

  (testing "dot-separated namespace splits on dots"
    (is (= ["gordian" "aggregate"] (sut/tokenize 'gordian.aggregate))))

  (testing "slash-separated (qualified symbol) splits on slash"
    (is (= ["some" "fn"] (sut/tokenize 'some/fn))))

  (testing "lowercases all tokens"
    (is (= ["build" "report"] (sut/tokenize 'Build-Report))))

  (testing "drops single-character tokens"
    (is (= ["ca" "ce"] (sut/tokenize 'ca-ce)))
    (is (= ["format"] (sut/tokenize "format-a"))))

  (testing "plain string tokenizes same as symbol"
    (is (= ["propagation" "cost"] (sut/tokenize "propagation-cost"))))

  (testing "whitespace-separated prose — stop words removed"
    (is (= ["return" "cost"] (sut/tokenize "return the cost"))))

  (testing "underscores treated as separators; 'in' is a stop word"
    (is (= ["fan"] (sut/tokenize 'fan_in))))

  (testing "nil returns nil"
    (is (nil? (sut/tokenize nil))))

  (testing "english function words removed"
    (is (= [] (sut/tokenize "the of to and or but")))
    (is (= [] (sut/tokenize "is are was were be been")))
    (is (= [] (sut/tokenize "it its this that which"))))

  (testing "domain words are not stop-worded"
    (is (= ["propagation" "cost"] (sut/tokenize "propagation-cost")))
    (is (= ["report"] (sut/tokenize "report")))
    (is (= ["scan"] (sut/tokenize "scan"))))

  (testing "backtick-wrapped token — markdown code style"
    (is (= ["graph"] (sut/tokenize "`graph`"))))

  (testing "brace-contaminated token from docstring"
    (is (= ["node"] (sut/tokenize "{node}"))))

  (testing "mixed clojure syntax noise stripped"
    (is (= ["return" "dep"] (sut/tokenize "return: #{dep}"))))

  (testing "ellipsis and punctuation stripped; terms stemmed"
    (is (= ["ns" "dep"] (sut/tokenize "{ns→#{deps}}")))))

(testing "stemming applied in tokenize pipeline"
  (is (= ["return"] (sut/tokenize "returns")))
  (is (= ["scan"]   (sut/tokenize "scanning")))
  (is (= ["scan"]   (sut/tokenize "scanned")))
  (is (= ["reach"]  (sut/tokenize "reachable")))
  (is (= ["coupl"]  (sut/tokenize "coupling"))))

;;; ── stem ──────────────────────────────────────────────────────────────────

(deftest stem-test
  (testing "words with no applicable rule pass through unchanged"
    (is (= "scan"     (sut/stem "scan")))
    (is (= "graph"    (sut/stem "graph")))
    (is (= "report"   (sut/stem "report"))))

  (testing "trailing doubled consonant in ss/us/is words is protected"
    (is (= "class"    (sut/stem "class")))     ; ends in ss
    (is (= "status"   (sut/stem "status")))    ; ends in us
    (is (= "analysis" (sut/stem "analysis")))) ; ends in is

  (testing "-ing + dedup-final"
    (is (= "scan"   (sut/stem "scanning")))
    (is (= "coupl"  (sut/stem "coupling")))
    (is (= "comput" (sut/stem "computing")))
    (is (= "run"    (sut/stem "running"))))

  (testing "-ed + dedup-final"
    (is (= "scan"   (sut/stem "scanned")))
    (is (= "comput" (sut/stem "computed"))))

  (testing "-ing and -ed produce the same stem (enables matching)"
    (is (= (sut/stem "scanning") (sut/stem "scanned"))))

  (testing "-er + dedup-final"
    (is (= "scan"   (sut/stem "scanner"))))

  (testing "-ers + dedup-final"
    (is (= "scan"   (sut/stem "scanners"))))

  (testing "-er and -ers produce the same stem"
    (is (= (sut/stem "scanner") (sut/stem "scanners"))))

  (testing "-ies → -y"
    (is (= "dependency" (sut/stem "dependencies")))
    (is (= "query"      (sut/stem "queries"))))

  (testing "-ness → stem"
    (is (= "empti"  (sut/stem "emptiness")))
    (is (= "random" (sut/stem "randomness"))))

  (testing "-able → stem"
    (is (= "reach"  (sut/stem "reachable"))))

  (testing "-ible → stem"
    (is (= "compat" (sut/stem "compatible"))))

  (testing "-ment → stem (root is 'measure', not 'measur')"
    (is (= "measure" (sut/stem "measurement"))))

  (testing "-ly → stem"
    (is (= "direct"     (sut/stem "directly")))
    (is (= "transitive" (sut/stem "transitively"))))

  (testing "-es for sibilant stems only; others fall through to -s"
    (is (= "process"   (sut/stem "processes")))    ; ss-stem → -es fires
    (is (= "index"     (sut/stem "indexes")))      ; x-stem  → -es fires
    (is (= "namespace" (sut/stem "namespaces")))   ; c-stem  → -es skipped → -s fires
    (is (= "tree"      (sut/stem "trees"))))       ; short   → -es skipped → -s fires

  (testing "-s → stem (guards protect ss/us/is words)"
    (is (= "return" (sut/stem "returns")))
    (is (= "node"   (sut/stem "nodes")))
    (is (= "dep"    (sut/stem "deps"))))

  (testing "-ing and -s variants of same root produce matching stems"
    (is (= (sut/stem "scanning") (sut/stem "scans")))
    (is (= (sut/stem "returns")  (sut/stem "return")))))

;;; ── term-freqs ────────────────────────────────────────────────────────────

(deftest term-freqs-test
  (let [term-freqs #'gordian.conceptual/term-freqs]
    (testing "counts occurrences correctly"
      (is (= {"scan" 3 "file" 1 "dir" 2}
             (term-freqs ["scan" "file" "scan" "dir" "scan" "dir"]))))

    (testing "empty sequence → empty map"
      (is (= {} (term-freqs []))))

    (testing "single term"
      (is (= {"cost" 1} (term-freqs ["cost"]))))))

;;; ── build-tfidf ───────────────────────────────────────────────────────────

(def ^:private small-corpus
  "Three namespaces with controlled term overlap for TF-IDF verification.
  'cost' appears in ns-a and ns-b (common).
  'propagation' and 'scan' appear only in ns-a (specific).
  'report' appears only in ns-b (specific).
  'format' appears in ns-b and ns-c (common)."
  {'ns-a ["cost" "cost" "propagation" "scan"]
   'ns-b ["cost" "format" "report" "report"]
   'ns-c ["format" "format" "output" "output"]})

(deftest build-tfidf-test
  (let [tfidf (sut/build-tfidf small-corpus)]

    (testing "returns a map keyed by ns syms"
      (is (= '#{ns-a ns-b ns-c} (set (keys tfidf)))))

    (testing "each namespace value is a map of term → weight"
      (doseq [[_ tv] tfidf]
        (is (map? tv))
        (is (every? string? (keys tv)))
        (is (every? number? (vals tv)))))

    (testing "all weights are positive"
      (doseq [[_ tv] tfidf
              [_ w]  tv]
        (is (pos? w))))

    (testing "term absent from a namespace is absent from its map"
      (is (nil? (get-in tfidf ['ns-a "report"])))
      (is (nil? (get-in tfidf ['ns-c "cost"]))))

    (testing "unique terms weight more than shared terms within same namespace"
      ;; In ns-a: 'propagation' (df=1) should outweigh 'cost' (df=2) despite same TF
      (let [tv (get tfidf 'ns-a)]
        (is (> (get tv "propagation") (get tv "cost")))))

    (testing "higher frequency term weighs more than lower frequency within same namespace"
      ;; In ns-b: 'report' appears twice, 'cost' once — but 'report' is also unique (df=1)
      ;; vs 'cost' df=2, so 'report' wins on both TF and IDF
      (let [tv (get tfidf 'ns-b)]
        (is (> (get tv "report") (get tv "cost")))))

    (testing "single-namespace corpus — IDF is log(1/1)=0, all weights zero → empty maps"
      (let [solo (sut/build-tfidf {'only-ns ["alpha" "beta"]})]
        (is (= {} (get solo 'only-ns)))))))

;;; ── normalize-tfidf ───────────────────────────────────────────────────────

(deftest normalize-tfidf-test
  (let [tfidf (sut/build-tfidf small-corpus)
        unit  (normalize-tfidf tfidf)]

    (testing "same keyset as input"
      (is (= (set (keys tfidf)) (set (keys unit)))))

    (testing "each vector has unit magnitude (within floating-point tolerance)"
      (doseq [[_ v] unit
              :when (seq v)]
        (let [mag (Math/sqrt (reduce-kv (fn [s _ w] (+ s (* w w))) 0.0 v))]
          (is (< (Math/abs (- 1.0 mag)) 1e-9)
              (str "magnitude should be 1.0, got " mag)))))

    (testing "normalization preserves cosine-as-dot-product behavior"
      (let [[a b] (vec (keys tfidf))
            dot-unit (reduce-kv (fn [s t wa]
                                  (+ s (* wa (get (get unit b) t 0.0))))
                                0.0 (get unit a))]
        (is (<= 0.0 dot-unit 1.0))))

    (testing "empty vector passes through unchanged"
      (let [result (normalize-tfidf {'empty-ns {}})]
        (is (= {} (get result 'empty-ns)))))))

;;; ── conceptual-pairs ──────────────────────────────────────────────────────

(def ^:private small-tfidf
  (sut/build-tfidf small-corpus))

(def ^:private small-graph
  "ns-a → ns-b edge only; ns-b ↔ ns-c has no structural edge."
  {'ns-a #{'ns-b}
   'ns-b #{}
   'ns-c #{}})

(deftest conceptual-pairs-test
  (let [result (sut/conceptual-pairs small-tfidf small-graph 0.01 3)
        pairs  (:pairs result)]

    (testing "returns a map with :pairs and :candidate-count"
      (is (map? result))
      (is (contains? result :pairs))
      (is (contains? result :candidate-count))
      (is (vector? pairs))
      (is (pos-int? (:candidate-count result))))

    (testing "candidate-count >= reported pairs"
      (is (>= (:candidate-count result) (count pairs))))

    (testing "each entry has required keys"
      (doseq [p pairs]
        (is (contains? p :ns-a))
        (is (contains? p :ns-b))
        (is (contains? p :score))
        (is (contains? p :kind))
        (is (= :conceptual (:kind p)))
        (is (contains? p :structural-edge?))
        (is (contains? p :shared-terms))))

    (testing "sorted by score descending"
      (let [scores (map :score pairs)]
        (is (= scores (sort > scores)))))

    (testing "all returned scores are >= threshold"
      (doseq [{:keys [score]} pairs]
        (is (>= score 0.01))))

    (testing "ns-a ↔ ns-b has structural edge"
      (let [p (first (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-a 'ns-b}) pairs))]
        (is (true? (:structural-edge? p)))))

    (testing "ns-b ↔ ns-c has no structural edge"
      (let [p (first (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-b 'ns-c}) pairs))]
        (when p   ; may be above threshold
          (is (false? (:structural-edge? p))))))

    (testing "ns-a ↔ ns-c (zero overlap) excluded even at low threshold"
      (let [ac (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-a 'ns-c}) pairs)]
        (is (empty? ac))))

    (testing "shared-terms vector present for each pair"
      (doseq [{:keys [shared-terms]} pairs]
        (is (vector? shared-terms))))

    (testing "ns-a ↔ ns-b shared term is 'cost'"
      (let [p (first (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-a 'ns-b}) pairs))]
        (is (= ["cost"] (:shared-terms p))))))

  (testing "threshold filters out all pairs"
    (is (empty? (:pairs (sut/conceptual-pairs small-tfidf small-graph 1.0 3)))))

  (testing "empty tfidf → empty result"
    (let [result (sut/conceptual-pairs {} {} 0.01 3)]
      (is (empty? (:pairs result)))
      (is (zero? (:candidate-count result))))))

;;; ── extract-terms ─────────────────────────────────────────────────────────

(deftest extract-terms-test
  (testing "namespace name tokens always present"
    (let [terms (sut/extract-terms 'gordian.scan [])]
      (is (some #{"gordian"} terms))
      (is (some #{"scan"} terms))))

  (testing "defn symbol tokens extracted"
    (let [forms '[(defn propagation-cost [g] 0)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"propagation"} terms))
      (is (some #{"cost"} terms))))

  (testing "def symbol tokens extracted"
    (let [forms '[(def parse-opts {})]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"parse"} terms))
      (is (some #{"opt"} terms))))

  (testing "defmulti, defrecord, defprotocol tokens extracted"
    (doseq [head '[defmulti defrecord defprotocol defmacro]]
      (let [forms [(list head 'my-concept [])]
            terms (sut/extract-terms 'my.ns forms)]
        (is (some #{"my"} terms)   (str head " should yield 'my'"))
        (is (some #{"concept"} terms) (str head " should yield 'concept'")))))

  (testing "defn- (private) symbol extracted"
    (let [forms '[(defn- build-tfidf [x] x)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"build"} terms))
      (is (some #{"tfidf"} terms))))

  (testing "docstring tokens extracted and stemmed"
    (let [forms '[(defn format-report "Return lines for the coupling report." [r] [])]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"return"} terms))
      (is (some #{"line"} terms))       ; "lines" → stem → "line"
      (is (some #{"coupl"} terms))      ; "coupling" → stem → "coupl"
      (is (some #{"report"} terms))))

  (testing "non-def top-level forms are ignored"
    (let [forms '[(comment anything here) (let [x 1] x) [1 2 3]]
          terms (sut/extract-terms 'my.ns forms)]
      ;; only ns tokens present
      (is (= ["my" "ns"] terms))))

  (testing "multiple defs accumulate terms"
    (let [forms '[(defn scan-file [p] nil)
                  (defn scan-dir  [d] nil)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"scan"} terms))
      (is (some #{"file"} terms))
      (is (some #{"dir"} terms))))

  (testing "repetition preserved — frequency drives TF"
    (let [forms '[(defn scan-one [x] nil)
                  (defn scan-two [x] nil)
                  (defn scan-three [x] nil)]
          terms (sut/extract-terms 'my.ns forms)
          freq  (frequencies terms)]
      (is (= 3 (get freq "scan"))))))
