# Gordian Output Schema

Version: **0.2.0** · Schema: **1**

All machine-readable output (EDN via `--edn`, JSON via `--json`) is wrapped
in a standard envelope. Human-readable and markdown output are not affected.

---

## Envelope

Every command's output includes these top-level keys:

| Key | Type | Description |
|-----|------|-------------|
| `gordian/version` | string | Gordian version (e.g. `"0.2.0"`) |
| `gordian/schema` | integer | Schema version. Bumped on breaking changes. |
| `gordian/command` | keyword | `analyze`, `diagnose`, `explain`, or `explain-pair` |
| `lenses` | map | Which analysis lenses ran and their parameters |
| `src-dirs` | vector of strings | Source directories analysed |
| `excludes` | vector of strings | Namespace exclusion patterns applied |
| `include-tests` | boolean | Whether test directories were included |

### Lenses

```edn
{:structural true
 :conceptual {:enabled true
              :threshold 0.20
              :candidate-pairs 170    ; pairs sharing ≥1 term (evaluated)
              :reported-pairs 8}      ; pairs meeting threshold
 :change     {:enabled true
              :threshold 0.30
              :candidate-pairs 42     ; project-internal pairs with ≥1 co-change
              :reported-pairs 3       ; pairs meeting threshold + min-co
              :repo-dir "."
              :since "90 days ago"}}  ; nil if not specified
```

When a lens is disabled: `{:enabled false}`.

**Interpreting zero reported-pairs:** If `candidate-pairs` is positive but
`reported-pairs` is zero, the lens ran successfully but no pairs met the
threshold. If `candidate-pairs` is also zero, no qualifying input existed
(e.g. no co-changing namespace pairs in git history).

---

## analyze

Command: `gordian [analyze] [dirs...] --edn`

Payload keys (alongside envelope):

| Key | Type | Description |
|-----|------|-------------|
| `propagation-cost` | double | Fraction of project reachable per random change |
| `cycles` | vector of sets | Strongly connected components (Tarjan) |
| `nodes` | vector of maps | Per-namespace metrics |
| `conceptual-pairs` | vector of maps | Conceptual coupling pairs (when lens enabled) |
| `change-pairs` | vector of maps | Change coupling pairs (when lens enabled) |

### Node shape

```edn
{:ns          symbol      ; namespace name
 :reach       double      ; transitive reach as fraction of project
 :fan-in      double      ; transitive fan-in as fraction of project
 :ca          integer     ; afferent coupling (direct dependents)
 :ce          integer     ; efferent coupling (direct dependencies)
 :instability double      ; Ce / (Ca + Ce), ∈ [0, 1]
 :role        keyword}    ; :core, :peripheral, :shared, or :isolated
```

### Conceptual pair shape

```edn
{:ns-a             symbol
 :ns-b             symbol
 :score            double      ; cosine similarity
 :kind             :conceptual
 :structural-edge? boolean     ; true if require edge exists
 :shared-terms     [string]}   ; top terms driving similarity
```

### Change pair shape

```edn
{:ns-a             symbol
 :ns-b             symbol
 :score            double      ; Jaccard coupling
 :kind             :change
 :co-changes       integer     ; commits containing both
 :confidence-a     double      ; P(b changed | a changed)
 :confidence-b     double      ; P(a changed | b changed)
 :structural-edge? boolean}
```

---

## diagnose

Command: `gordian diagnose [dirs...] --edn`

Same payload as analyze, plus:

| Key | Type | Description |
|-----|------|-------------|
| `health` | map | Overall project health assessment |
| `findings` | vector of maps | Ranked findings sorted by severity |

### Health shape

```edn
{:propagation-cost double
 :health           keyword    ; :healthy, :moderate, or :concerning
 :cycle-count      integer
 :ns-count         integer}
```

### Finding shape

```edn
{:severity keyword             ; :high, :medium, or :low
 :category keyword             ; see categories below
 :subject  map                 ; {:ns sym} or {:ns-a sym :ns-b sym} or {:members set}
 :reason   string              ; human-readable explanation
 :evidence map}                ; category-specific evidence
```

Finding categories:
- `cycle` — namespace cycle (high)
- `cross-lens-hidden` — hidden in both conceptual + change lenses (high)
- `hidden-conceptual` — shared vocabulary, no structural edge (medium/low)
- `hidden-change` — co-changing in git, no structural edge (medium)
- `sdp-violation` — high Ca but high instability (medium)
- `god-module` — shared role with extreme reach and fan-in (medium)
- `hub` — very high reach (low)

---

## explain

Command: `gordian explain <ns> --edn`

Payload keys (alongside envelope):

| Key | Type | Description |
|-----|------|-------------|
| `ns` | symbol | The namespace being explained |
| `metrics` | map | `{:reach :fan-in :ca :ce :instability :role}` |
| `direct-deps` | map | `{:project [sym] :external [sym]}` |
| `direct-dependents` | vector of symbols | Namespaces that require this one |
| `conceptual-pairs` | vector of maps | Conceptual pairs involving this ns |
| `change-pairs` | vector of maps | Change pairs involving this ns |
| `cycles` | vector of sets | Cycles this namespace participates in |

Error case (namespace not found):
```edn
{:error     string
 :available [symbol]}
```

---

## explain-pair

Command: `gordian explain-pair <ns-a> <ns-b> --edn`

Payload keys (alongside envelope):

| Key | Type | Description |
|-----|------|-------------|
| `ns-a` | symbol | First namespace |
| `ns-b` | symbol | Second namespace |
| `structural` | map | `{:direct-edge? bool :direction kw :shortest-path [sym]}` |
| `conceptual` | map or nil | Conceptual pair data (if any) |
| `change` | map or nil | Change pair data (if any) |
| `finding` | map or nil | Diagnosis finding for this pair (if hidden) |

---

## JSON notes

In JSON output:
- Symbols become strings (`gordian.scan` → `"gordian.scan"`)
- Keywords become strings (`:core` → `"core"`)
- Sets become sorted arrays
- Namespaced keywords preserve namespace (`gordian/version` → `"gordian/version"`)

---

## Versioning policy

- `gordian/schema` is an integer, incremented on breaking output changes
- Additive changes (new keys) do not bump the schema version
- Consumers should tolerate unknown keys
