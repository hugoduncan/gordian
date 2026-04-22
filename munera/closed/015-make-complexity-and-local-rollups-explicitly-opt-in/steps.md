- [x] Phase 1: lock explicit option names as `--namespace-rollup` and `--project-rollup`
- [x] Phase 1: document default omission semantics for both commands
- [x] Phase 1: identify the report/finalizer seams for optional section inclusion in `complexity` and `local`

- [x] Phase 2: add namespace-rollup include flag to `gordian complexity`
- [x] Phase 2: add project-rollup include flag to `gordian complexity`
- [x] Phase 2: record rollup inclusion choices in `complexity` option/report metadata
- [x] Phase 2: omit `:namespace-rollups` from canonical `complexity` output unless requested
- [x] Phase 2: omit `:project-rollup` from canonical `complexity` output unless requested
- [x] Phase 2: preserve existing unit sorting/filtering/bar behavior in `complexity`

- [x] Phase 3: add namespace-rollup include flag to `gordian local`
- [x] Phase 3: add project-rollup include flag to `gordian local`
- [x] Phase 3: record rollup inclusion choices in `local` option/report metadata
- [x] Phase 3: omit `:namespace-rollups` from canonical `local` output unless requested
- [x] Phase 3: omit `:project-rollup` from canonical `local` output unless requested
- [x] Phase 3: preserve the existing canonical/display distinction in `local`

- [x] Phase 4: update `complexity` text output to render namespace rollups only when requested
- [x] Phase 4: update `complexity` text output to render project rollup only when requested
- [x] Phase 4: update `complexity` markdown output to render namespace rollups only when requested
- [x] Phase 4: update `complexity` markdown output to render project rollup only when requested
- [x] Phase 4: update `local` text output to render namespace rollups only when requested
- [x] Phase 4: update `local` text output to render project rollup only when requested
- [x] Phase 4: update `local` markdown output to render namespace rollups only when requested
- [x] Phase 4: update `local` markdown output to render project rollup only when requested
- [x] Phase 4: update scoped help and examples for both commands
- [x] Phase 4: update README / practical guide / related docs for the explicit rollup model

- [x] Phase 5: add tests for `complexity` default unit-only behavior
- [ ] Phase 5: add tests for `complexity` namespace-only opt-in
- [ ] Phase 5: add tests for `complexity` project-only opt-in
- [ ] Phase 5: add tests for `complexity` both-rollups opt-in
- [x] Phase 5: add tests for `local` default unit-only behavior
- [ ] Phase 5: add tests for `local` namespace-only opt-in
- [ ] Phase 5: add tests for `local` project-only opt-in
- [ ] Phase 5: add tests for `local` both-rollups opt-in
- [x] Phase 5: verify EDN/JSON omission semantics for unrequested rollups
- [x] Phase 5: verify interaction with `--sort`, `--top`, `--min`, and `--bar`
- [x] Phase 5: run full suite
