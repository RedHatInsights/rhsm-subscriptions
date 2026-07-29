# swatch-tally Component Test Plan

The **swatch-tally** module is a core service within the Subscription Watch platform that processes usage events and produces aggregated tally snapshots for reporting and capacity management. It transforms individual instance metrics into time-based snapshots at various granularities (hourly, daily, weekly, monthly, quarterly, yearly).

This document outlines the test plan for swatch-tally, including event processing, snapshot generation, and report retrieval.

**Purpose:** To ensure the swatch-tally service is functional, reliable, and meets all defined requirements for usage tracking and reporting.

**Scope:**
* Event ingestion and processing
* Hourly and nightly tally snapshot generation
* Tally report generation with various filters
* Instance report generation
* Conflict handling and data persistence
* Authorization and access control

**Assumptions:**
* The swatch-tally service is deployed in a stable and functional environment
* Events are provided via Kafka topics in the expected format
* Database (PostgreSQL) is available and properly configured

**Constraints:**
* Testing is limited to the functionality of swatch-tally at a component level
* End-to-end testing in ephemeral or stage environments is out of scope for this test plan

---

## Test Plans by Functional Area

Test cases are organized by functional area for easier navigation and maintenance. Each functional area covers a specific aspect of swatch-tally's behavior.

### 1. [Tally Processing Pipeline](test-plans/tally-processing.md)

**Functional Area:** Event ingestion, conflict resolution, data transformation, and summary message output

**Test Coverage (~730 lines):**
- Tally Conflict Handling (4 tests)
- Product Tag and Metric Filtering (8 tests)
- Hypervisor Handling (6 tests)
- Data Persistence (3 tests)
- Tally Summary Messages - Non-PAYG/Nightly (8 tests)
- Tally Summary Messages - PAYG/Hourly (5 tests)

The core tally processing pipeline: Events IN from Kafka → Process/Tally → Store Snapshots → Publish Summaries OUT to Kafka.

---

### 2. [Tally Reports API](test-plans/tally-reports-api.md)

**Functional Area:** REST API endpoints for querying tally/usage reports

**Test Coverage (~794 lines):**
- Report Granularity and Filtering - PAYG (25 tests)
- Report Has Data Based on Category - PAYG (3 tests)
- Report Granularity and Filtering - Non-PAYG (10 tests)
- Report Has Data Based on Category - Non-PAYG (4 tests)

REST endpoints that return aggregated usage data at various granularities with filtering by SLA, usage, billing provider, and billing account.

**Primary Endpoint:** `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}`

---

### 3. [Instance Reports API](test-plans/instance-reports-api.md)

**Functional Area:** REST API endpoints for querying instance/host data

**Test Coverage (~625 lines):**
- Instance Reporting for Billing Account IDs (5 tests)
- Instance Reporting with Parameters for PAYG Products (13 tests)
- Instance Reporting based on Pagination and Sorting (6 tests)
- Instance Reporting with Parameters for Non-PAYG Products (9 tests)

REST endpoints that return host-level data with filtering, pagination, and sorting capabilities.

**Primary Endpoints:**
- `GET /api/rhsm-subscriptions/v1/instances/products/{productId}`
- `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests`
- `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids`

---

### 4. [RBAC Authorization](test-plans/rbac-authorization.md)

**Functional Area:** Access control and authentication for all swatch-tally APIs

**Test Coverage (~550 lines):**
- RBACv1 (Kessel Flag OFF) - User, ServiceAccount, Associate, X509 identities
- RBACv2/Kessel (Flag ON) - User, ServiceAccount, Associate, X509 identities
- Opt-in endpoint authorization
- Export authorization via Kafka
- Resilience (fail-closed behavior)

Authorization parity testing between RBACv1 and RBACv2/Kessel across all identity types and permission levels.

**Related Jira:** [SWATCH-5267](https://redhat.atlassian.net/browse/SWATCH-5267), [SWATCH-5268](https://redhat.atlassian.net/browse/SWATCH-5268), [SWATCH-4153](https://redhat.atlassian.net/browse/SWATCH-4153), [SWATCH-5264](https://redhat.atlassian.net/browse/SWATCH-5264)

---

### 5. [System & Infrastructure](test-plans/system-infrastructure.md)

**Functional Area:** System-level features, integrations, and health checks

**Test Coverage (~44 lines):**
- Version API (1 test)
- Nightly Tally with HBI database (1 test)

System-level functionality including version information endpoints and database integrations.

---

## Test Strategy

This test plan focuses on covering test scenarios for component-level tests utilizing the Java component test framework.

**Testing Strategy:**

Test cases should be testable locally and in deployed environments.

- Kafka messages can be injected for event-driven testing
- Database state can be seeded directly for specific scenarios
- System state can be verified through internal and public API calls
- Tally summary messages can be verified on Kafka topics

**Test Organization:**

Each test file should correspond to a specific test case section in the functional area test plans. Tests are parameterized where appropriate to verify behavior under different configurations (e.g., feature flags, query paths).

---

## Quick Reference

**Total Test Coverage:** ~2,700 lines across 5 functional areas

**By Test Count:**
- Tally Processing Pipeline: ~30 tests
- Tally Reports API: ~40 tests
- Instance Reports API: ~30 tests
- RBAC Authorization: ~30 tests
- System & Infrastructure: ~2 tests

**Test File Naming Convention:**
- Test files follow pattern: `<FunctionalArea><Feature>Test.java`
- Example: `TallyReportFiltersPaygTest.java`, `TallyHandlingConflictsTest.java`
