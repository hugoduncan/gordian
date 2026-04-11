💡 edamame: use parse-next not parse-string-all for source files

parse-string-all fails on '#{...} (quoted sets) — edamame splits '#'
from '{}', causing map-arity errors.  This breaks many test files.

Fix: use incremental parse-next, stopping at the first (ns ...) form.
Never parse the file body — only the ns form matters for dep analysis.

  (let [rdr (e/reader content)]
    (loop []
      (let [form (try (e/parse-next rdr opts) (catch Exception _ ::skip))]
        (cond
          (= ::e/eof form) nil
          (= ::skip  form) (recur)
          (and (seq? form) (= 'ns (first form))) form
          :else (recur)))))

Required opts for parse-next (same as parse-string-all):
  {:read-cond :allow :features #{:clj} :fn true :deref true :regex true}

Symptom: parse-file returns nil; self-analysis PC dramatically wrong.
