✅ unit vs integration tests: reach metric reveals the split

reach 4%  = unit test (requires only its own module, which has no project deps)
reach 50% = integration test (transitively pulls in gordian.main → everything)

Root cause of integration coupling: using (main/build-report [...]) as
a test fixture. Convenient but makes the whole test file integration-scoped.

Fix pattern:
1. Hand-craft the fixture map in the test file (same data, no pipeline call)
2. Extract integration tests to a dedicated gordian.integration-test namespace
3. Unit test files require only their subject namespace

Result for gordian:
- dot/json/edn-test: 50% → 4.3% reach (unit)
- integration-test: 47.8% reach (explicit, named)
- PC src/+test/: 14.7% → 9.8%

The metric can only see namespace-level coupling. Even one integration test
in a file makes the whole namespace high-reach. Separation requires its own file.
