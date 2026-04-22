Approach:
- treat the existing `gordian complexity` command as the anchor, not a throwaway prototype
- add LOC as the next concrete metric while using that work to lock a durable two-metric command model
- prefer a narrow explicit design that supports exactly two metrics well in v1 (`cyclomatic-complexity` and `lines-of-code`) over a prematurely abstract metric framework
- compute both metrics implicitly on every run; do not add metric-selection flags in v1
- replace cyclomatic-shaped threshold syntax with canonical generic metric-qualified syntax
- remove `--min-cc` cleanly rather than preserving it as a compatibility alias

Implementation shape:
- first lock the command contract in an implementation companion doc
- then move option parsing/finalization for sort/top/min into a clearly pure layer
- then add LOC extraction and two-metric rollups
- then converge text output, machine-readable output, docs, and tests around the new canonical shape

Recommended implementation sequence:
1. write the implementation companion doc that locks CLI examples, schema examples, LOC rules, sort keys, threshold semantics, display-only filter semantics, and explicit `--min-cc` removal
2. refactor pure complexity option parsing/finalization so generic `--min` handling and two-metric sort/truncation semantics live in pure code
3. remove `--min-cc` from CLI parsing/help/tests and make misuse fail clearly with a migration hint
4. add LOC extraction from unit source spans and roll it into canonical unit records and rollups
5. implement display-only `--min` filtering for unit rows while keeping namespace and project rollups computed from the full analyzed unit set
6. update text output to a stable two-metric table shape and bar-selection rule
7. update EDN/JSON output metadata and schema docs
8. expand tests for LOC counting, generic threshold parsing, combined thresholds, new sort semantics, bar selection, display-only filter behavior, output shape, and explicit rejection of `--min-cc`

Design basis to preserve:
- LOC is measured per extracted analyzable unit using source-span-based non-blank, non-comment lines over the whole reported unit form span
- canonical payload exposes both metric identities explicitly and carries compact metric-qualified fields such as `:cc` and `:loc`
- `--sort` chooses the primary ranking key and `--top` truncates relative to that order
- canonical `--sort` keys in v1 are `cc`, `loc`, `cc-risk`, `ns`, and `var`
- thresholds/filters use generic syntax such as repeated `--min cc=10` and `--min loc=20`
- multiple `--min` constraints combine conjunctively for displayed unit rows
- namespace and project rollups remain computed from the full analyzed unit set rather than the thresholded display subset
- there is no user-facing `--metric` or `--metrics` selection in v1 because both metrics are always active
- human-readable output always shows both metrics together in the same tables
- `--min-cc` is removed, not deprecated-in-place

Compatibility stance:
- canonical threshold syntax is generic (`--min metric=value`)
- cyclomatic-specific threshold flags are removed from accepted CLI
- error messaging should guide users from `--min-cc` to `--min cc=...`
- existing sort behavior should be preserved where compatible, with `loc` added as a first-class sort key and tie-break rules documented

Risks:
- over-generalising too early and making the complexity command harder to use
- under-generalising and baking cyclomatic-specific assumptions into future incompatible flags
- ambiguous LOC semantics causing noisy or disputed results
- output clutter when both metrics are shown together on every run
- user confusion if `--min` affects unit rows but not rollups unless this is made explicit in help/output/docs

Mitigations:
- decide a simple v1 LOC semantics and document it precisely
- design around exactly two concrete metrics, then stop
- keep the canonical report schema explicit and metric-qualified
- keep multi-metric text output tabular and compact
- prefer direct convergence to the generic threshold form so the command does not accumulate more transitional surfaces than necessary
- document display-only filter semantics explicitly in help, docs, and tests
