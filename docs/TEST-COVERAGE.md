# Test Coverage Index

Central index of all test plans across SWATCH services. Each service has component-level tests (run locally and in ephemeral) and integration-level tests (run in stage/ephemeral against real dependencies).

## Test Plans by Service

| Service | Component Test Plan | Integration Test Plan |
|---|---|---|
| swatch-tally | [SWATCH-TALLY-COMPONENT-TEST-PLAN.md](../swatch-tally/SWATCH-TALLY-COMPONENT-TEST-PLAN.md) | [SWATCH-TALLY-INTEGRATION-TEST-PLAN.md](../swatch-tally/SWATCH-TALLY-INTEGRATION-TEST-PLAN.md) |
| swatch-contracts | [SWATCH-CONTRACTS-COMPONENT-TEST-PLAN.md](../swatch-contracts/SWATCH-CONTRACTS-COMPONENT-TEST-PLAN.md) | [SWATCH-CONTRACTS-INTEGRATION-TEST-PLAN.md](../swatch-contracts/SWATCH-CONTRACTS-INTEGRATION-TEST-PLAN.md) |
| swatch-billable-usage | [TEST_PLAN.md](../swatch-billable-usage/TEST_PLAN.md) | TBD |
| swatch-metrics-hbi | [TEST_PLAN.md](../swatch-metrics-hbi/TEST_PLAN.md) | TBD |
| swatch-metrics | [TEST_PLAN.md](../swatch-metrics/ct/TEST_PLAN.md) | TBD |
| swatch-producer-aws | [TEST_PLAN.md](../swatch-producer-aws/TEST_PLAN.md) | TBD |
| swatch-utilization | [TEST_PLAN.md](../swatch-utilization/TEST_PLAN.md) | TBD |

## Cross-Service Integration Plans

| Area | Test Plan |
|---|---|
| Notification Service | [Notification Service Integration Test Plan](integration-test-plans/Notification%20Service%20Integration%20Test%20Plan.md) |

## Test ID Convention

Test IDs follow the pattern `{service}-{area}-{functionality}-TC00X`:

- **service** — the swatch service under test (e.g. `tally`, `contracts`)
- **area** — the integration area being tested (e.g. `rbac`, `kessel`, `exports`)
- **functionality** — the specific functionality and expected outcome (e.g. `admin-access`, `read-access`, `access-denied`, `optin-required`)
- **TC00X** — sequential test case number within that functionality group

Examples: `tally-rbac-admin-access-TC001`, `contracts-kessel-optin-required-TC002`

Component tests use the `@TestPlanName` Java annotation. Integration tests use the `@pytest.mark.test_plan_name` pytest marker. Both reference the same ID that appears in the corresponding test plan document.

## References

- [Component Testing Framework](component-tests.md) — framework overview and getting started guide
- IQE Plugin: [iqe-rhsm-subscriptions-plugin](https://gitlab.cee.redhat.com/insights-qe/iqe-rhsm-subscriptions-plugin) — integration test source (external)
