# Introduction

The **swatch-billable-usage** module is a service within the Subscription Watch platform that consumes tally summaries from Kafka, applies contract coverage from swatch-contracts, and produces billable usage remittance records and downstream Kafka aggregates for marketplace billing.

This document outlines the test plan for swatch-billable-usage component tests, including tally consumption, remittance calculation, contract coverage, status updates, and contract-adjustment behavior.

**Purpose:** To ensure the swatch-billable-usage service is functional, reliable, and meets all defined requirements for remittance processing and billable usage production.

**Scope:**

* Tally summary consumption from Kafka
* Contract coverage lookup (stubbed via Wiremock)
* Monthly remittance calculation and persistence
* Billable usage and hourly aggregate production to Kafka
* Remittance status updates from billable usage status events
* Mid-month contract adjustment remittance

**Assumptions:**

* The swatch-billable-usage service is deployed in a stable and functional environment
* Kafka is available for tally input and billable usage output topics
* PostgreSQL is available for remittance persistence
* Contract API responses are stubbed via Wiremock in component tests

**Constraints:**

* Testing is limited to the functionality of swatch-billable-usage at a component level
* End-to-end testing in ephemeral or stage environments is out of scope for this test plan

# Test Strategy

This test plan focuses on covering test scenarios for component-level tests utilizing the Java component test framework.

**Testing Strategy:**

Test cases should be testable locally and in deployed environments.

- Tally summaries can be published directly to the Kafka TALLY topic
- Contract coverage can be stubbed via Wiremock (`ContractsWiremockService`)
- Remittance state can be verified through the internal remittance API
- Billable usage and aggregate messages can be verified on Kafka output topics

# Test Cases

## Contract Adjustment Remittance

Component tests in `ContractAdjustmentComponentTest`.

**billable-usage-contract-adjustment-TC001 - Remove contract mid-month**

- **Description**: Verify removing a contract mid-month does not change remittance already recorded; additional usage after contract restore applies the adjustment formula.
- **Setup**:
  - Wiremock returns ROSA contract with equal Cores and Instance-hours coverage (6 billable units per metric)
  - Billing account ID generated for the test
- **Action**:
  - Phase 1: Publish tally increment 100 → verify initial remittance per metric
  - Phase 2: Stub no contract; re-tally with zero increment
  - Phase 3: Restore contract; publish second increment (month total 200)
- **Verification**:
  - Phase 2 remittance unchanged from phase 1
  - Phase 3 remittance matches `BillableUsageRemittanceExpectations.expectedRemittanceAfterUsageIncrease`
- **Expected Result**:
  - Cores: initial 76, final 176; Instance-hours: initial 94, final 194

**billable-usage-contract-adjustment-TC002 - Add contract mid-month**

- **Description**: Verify adding a second contract mid-month keeps remittance at the first contract value until usage exceeds combined coverage.
- **Setup**:
  - Wiremock returns ROSA contract with coverage 10 billable units per metric
  - Billing account ID generated for the test
- **Action**:
  - Phase 1: Publish tally increment 100 → verify initial remittance
  - Phase 2: Stub two contracts (10 + 100 coverage); re-tally with zero increment
  - Phase 3: Publish second increment (month total 200)
  - Phase 4: Publish large third increment (month total 501)
- **Verification**:
  - Phases 2 and 3 remittance unchanged from phase 1
  - Phase 4 remittance matches combined-contract adjustment formula
- **Expected Result**:
  - Cores: initial 60, final 64; Instance-hours: initial 90, final 391
