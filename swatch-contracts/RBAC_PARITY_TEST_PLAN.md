# Introduction

This document defines the test plan for RBACv1 / RBACv2 (Kessel) authorization parity in **swatch-contracts**. The goal is to verify that every public-facing authorization check produces the same outcomes under both paths, controlled by Unleash flag `swatch.common-security.use-kessel-rbac`.

**Purpose:** Ensure toggling from RBACv1 to RBACv2 produces zero behavioral change for end users across identity types and permission levels.

**Scope:**

* Customer-facing endpoints protected by `@RolesAllowed`
* Identity types: User, ServiceAccount, Associate
* Permission levels: admin (`subscriptions:*:*`), reader (`subscriptions:reports:read`), none
* Subscription data export pipeline (explicit `RbacDelegate` check)

**Assumptions:**

* The Kessel inventory API is reachable and has the swatch relations schema loaded (or is stubbed in CT)
* Unleash flag `swatch.common-security.use-kessel-rbac` selects the authorization path
* Wiremock (or equivalent) can stub RBAC v1 REST and Kessel gRPC Check responses

**Constraints:**

* Coverage is authorization behavior only
* Functional correctness of endpoint payloads is covered by `TEST_PLAN.md`

# Test Strategy

Each case runs under both authorization models and asserts the same HTTP status (or export outcome).

**Authorization models (parameterized):** each case below runs for `RBAC` and `KESSEL`, except resilience cases that apply only when Kessel is enabled.

* **RBAC**: Unleash `swatch.common-security.use-kessel-rbac` off. Stub `GET /api/rbac/v1/access/`
* **KESSEL**: Unleash flag on. Stub gRPC
  `POST /kessel.inventory.v1beta2.KesselInventoryService/Check` for relation
  `subscriptions_report_view` (Wiremock gRPC + Kessel descriptor)

**Endpoints under test:**

| ID | Method | Path | Roles |
|----|--------|------|-------|
| E1 | GET | `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` | customer, service |
| E2 | GET | `/api/rhsm-subscriptions/v1/subscriptions/products/{product_id}` | customer, service |
| E3 | GET | `/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}` | customer, service |
| E4 | GET | `/api/swatch-contracts/v1/subscriptions/billing_account_ids` | customer, support, test |

**Reporting endpoints (E1-E3)** accept only `customer` or `service`. The `test` role must not be accepted so `SWATCH_TEST_APIS_ENABLED` cannot bypass RBAC on those paths.

**Billing account IDs (E4)** accepts `customer`, `support`, and `test` (`ContractsV1Resource` / OpenAPI). When `SWATCH_TEST_APIS_ENABLED` is true, `RolesAugmentor` grants `test` to authenticated principals, which is enough to authorize E4 without RBAC or Kessel subscriptions permissions.

# Test Cases

## User with admin permissions (`subscriptions:*:*`)

**contracts-rbac-parity-TC001 - User with admin permission is granted access to reporting endpoints**
- **Description**: Verify that a User identity with `subscriptions:*:*` receives HTTP 200 from capacity and subscription reporting endpoints under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a User `x-rh-identity` header for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to grant `subscriptions:*:*` for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test principal / relation
- **Action**:
  - GET `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v1/subscriptions/products/{product_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}` with the identity header
- **Verification**:
  - HTTP 200 for each request under both RBAC modes
- **Expected Result**:
  - Requests are authorized. Outcomes match under RBACv1 and Kessel.

**contracts-rbac-parity-TC002 - User with admin permission is granted access to billing account IDs**
- **Description**: Verify that a User identity with `subscriptions:*:*` receives HTTP 200 from the billing account IDs endpoint under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a User `x-rh-identity` header for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to grant `subscriptions:*:*` for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test principal / relation
- **Action**:
  - GET `/api/swatch-contracts/v1/subscriptions/billing_account_ids` with the identity header
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Request is authorized. Outcomes match under RBACv1 and Kessel.

## User with reader permissions (`subscriptions:reports:read`)

**contracts-rbac-parity-TC003 - User with reader permission is granted access to reporting endpoints**
- **Description**: Verify that a User identity with only `subscriptions:reports:read` receives HTTP 200 from capacity and subscription reporting endpoints under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a User `x-rh-identity` header for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to grant `subscriptions:reports:read` for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test principal / relation
- **Action**:
  - GET `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v1/subscriptions/products/{product_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}` with the identity header
- **Verification**:
  - HTTP 200 for each request under both RBAC modes
- **Expected Result**:
  - Requests are authorized (reader permission is sufficient). Outcomes match under RBACv1 and Kessel.

**contracts-rbac-parity-TC004 - User with reader permission is granted access to billing account IDs**
- **Description**: Verify that a User identity with only `subscriptions:reports:read` receives HTTP 200 from the billing account IDs endpoint under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a User `x-rh-identity` header for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to grant `subscriptions:reports:read` for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test principal / relation
- **Action**:
  - GET `/api/swatch-contracts/v1/subscriptions/billing_account_ids` with the identity header
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Request is authorized (reader permission is sufficient). Outcomes match under RBACv1 and Kessel.

## User with no subscriptions permissions

**contracts-rbac-parity-TC005 - User with no permissions is denied access to reporting endpoints**
- **Description**: Verify that a User identity with no subscriptions permissions receives HTTP 403 from capacity and subscription reporting endpoints under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a User `x-rh-identity` header for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to return no subscriptions permissions for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_FALSE` for the test principal / relation
- **Action**:
  - GET `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v1/subscriptions/products/{product_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}` with the identity header
- **Verification**:
  - HTTP 403 for each request under both RBAC modes
- **Expected Result**:
  - Requests are denied (Quarkus returns 403 with an empty body on `@RolesAllowed` failure).
    Outcomes match under RBACv1 and Kessel.

**contracts-rbac-parity-TC006 - User with no permissions is denied access to billing account IDs**
- **Description**: Verify that a User identity with no subscriptions permissions receives HTTP 403 from the billing account IDs endpoint under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a User `x-rh-identity` header for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to return no subscriptions permissions for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_FALSE` for the test principal / relation
- **Action**:
  - GET `/api/swatch-contracts/v1/subscriptions/billing_account_ids` with the identity header
- **Verification**:
  - HTTP 403 under both RBAC modes
- **Expected Result**:
  - Request is denied. Outcomes match under RBACv1 and Kessel.

## ServiceAccount identity

**contracts-rbac-parity-TC007 - ServiceAccount with admin permission is granted access**
- **Description**: Verify that a ServiceAccount identity with `subscriptions:*:*` receives HTTP 200 from all customer-facing endpoints under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a ServiceAccount `x-rh-identity` header with `client_id`
  - **RBAC**: stub RBAC to grant `subscriptions:*:*` for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test principal / relation
- **Action**:
  - GET `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v1/subscriptions/products/{product_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}` with the identity header
  - GET `/api/swatch-contracts/v1/subscriptions/billing_account_ids` with the identity header
- **Verification**:
  - HTTP 200 for each request under both RBAC modes
- **Expected Result**:
  - Requests are authorized. Outcomes match under RBACv1 and Kessel.

**contracts-rbac-parity-TC008 - ServiceAccount with no permissions is denied access**
- **Description**: Verify that a ServiceAccount identity with no subscriptions permissions receives HTTP 403 from all customer-facing endpoints under both RBACv1 and Kessel.
- **Setup**:
  - Prepare a ServiceAccount `x-rh-identity` header with `client_id`
  - **RBAC**: stub RBAC to return no subscriptions permissions for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_FALSE` for the test principal / relation
- **Action**:
  - GET `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v1/subscriptions/products/{product_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}` with the identity header
  - GET `/api/swatch-contracts/v1/subscriptions/billing_account_ids` with the identity header
- **Verification**:
  - HTTP 403 for each request under both RBAC modes
- **Expected Result**:
  - Requests are denied. Outcomes match under RBACv1 and Kessel.

## Associate identity (bypasses RBAC)

**contracts-rbac-parity-TC009 - Associate identity is granted access to billing account IDs**
- **Description**: Verify that an Associate identity (Red Hat employee) receives HTTP 200 from the billing account IDs endpoint under both modes. Associates bypass RBAC and receive the `support` role directly. That endpoint allows `support`.
- **Setup**:
  - Prepare an Associate `x-rh-identity` header with `associate.email`
- **Action**:
  - GET `/api/swatch-contracts/v1/subscriptions/billing_account_ids` with the identity header
- **Verification**:
  - HTTP 200 under both RBAC modes
  - Zero invocations of the RBAC stub (`GET /api/rbac/v1/access/`) for the request in RBAC mode
  - Zero invocations of the Kessel Check stub for the request in Kessel mode
- **Expected Result**:
  - Request is authorized. Associate requests bypass both authorization backends. Outcomes match under both flag settings.

**contracts-rbac-parity-TC010 - Associate identity is denied access to reporting endpoints**
- **Description**: Verify that an Associate identity receives HTTP 403 from capacity and subscription reporting endpoints under both modes. Those endpoints require the `customer` role. Associates do not receive it.
- **Setup**:
  - Prepare an Associate `x-rh-identity` header with `associate.email`
- **Action**:
  - GET `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v1/subscriptions/products/{product_id}` with the identity header
  - GET `/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}` with the identity header
- **Verification**:
  - HTTP 403 for each request under both RBAC modes
  - Zero invocations of the RBAC stub (`GET /api/rbac/v1/access/`) for each of the three requests in RBAC mode
  - Zero invocations of the Kessel Check stub for each of the three requests in Kessel mode
- **Expected Result**:
  - Requests are denied. Associate requests bypass both authorization backends. Outcomes match under both flag settings.

## Resilience

**contracts-rbac-parity-TC011 - Kessel unavailable falls back to denial**
- **Description**: Verify that when Kessel is unreachable and the Unleash flag is on, the user is denied access (fail-closed).
- **Setup**:
  - Prepare a User `x-rh-identity` header for the test org (with resolvable `user_id`)
  - Enable Unleash `swatch.common-security.use-kessel-rbac`
  - Kessel endpoint is unreachable or returns an error
- **Action**:
  - GET `/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}` with the identity header
- **Verification**:
  - HTTP 403
- **Expected Result**:
  - Access is denied when Kessel is unavailable (fail-closed)

## Export pipeline

**contracts-rbac-parity-TC012 - Export request with admin permission succeeds under both modes**
- **Description**: Verify that the subscription data export pipeline grants access when the user has `subscriptions:*:*` under both RBACv1 and Kessel.
- **Setup**:
  - Prepare an export request event with a User `x-rh-identity` for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to grant `subscriptions:*:*` for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test principal / relation
- **Action**:
  - Publish the export request to the export Kafka topic under both RBAC modes
- **Verification**:
  - Export completes successfully under both RBAC modes
- **Expected Result**:
  - Export behavior matches under RBACv1 and Kessel

**contracts-rbac-parity-TC013 - Export request with no permissions is denied under both modes**
- **Description**: Verify that the subscription data export pipeline denies access when the user has no subscriptions permissions under both RBACv1 and Kessel.
- **Setup**:
  - Prepare an export request event with a User `x-rh-identity` for the test org (with resolvable `user_id`)
  - **RBAC**: stub RBAC to return no subscriptions permissions for the test identity
  - **KESSEL**: stub Kessel Check to return `ALLOWED_FALSE` for the test principal / relation
- **Action**:
  - Publish the export request to the export Kafka topic under both RBAC modes
- **Verification**:
  - Export is denied under both RBAC modes
- **Expected Result**:
  - Denial behavior matches under RBACv1 and Kessel
