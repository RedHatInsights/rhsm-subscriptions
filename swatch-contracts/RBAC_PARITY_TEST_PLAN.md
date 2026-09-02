# Introduction

This document defines the test plan for achieving RBACv1/RBACv2 authorization parity in the **swatch-contracts** service. The goal is to verify that every public-facing endpoint produces identical authorization outcomes under both RBACv1 and Kessel (RBACv2), controlled by the Unleash flag `swatch.common-security.use-kessel-rbac`.

**Purpose:** Ensure that toggling from RBACv1 to RBACv2 produces zero behavioral change for end users across all identity types and permission levels.

**Scope:**

* Customer-facing endpoints protected by `@RolesAllowed`
* All identity types: User, ServiceAccount, Associate, X509
* All RBAC permission levels: admin (`subscriptions:*:*`), reader (`subscriptions:reports:read`), none
* Subscription data export pipeline (explicit `RbacDelegate` RBAC check)

**Assumptions:**

* The Kessel inventory API is reachable and has the swatch relations schema loaded
* The Unleash flag `swatch.common-security.use-kessel-rbac` controls which authorization path is used
* Wiremock stubs or MockKesselServer can simulate both RBAC v1 and Kessel responses

**Constraints:**

* Testing is limited to component-level authorization behavior
* Functional correctness of endpoint responses (beyond auth) is covered by existing test plans

# Test Strategy

Each test case exercises the same endpoint under both RBAC modes (flag OFF = v1, flag ON = v2) and asserts identical HTTP status codes. The test matrix covers the cross product of identity types, permission levels, and endpoint categories.

**Endpoints under test:**

| ID | Method | Path | Roles |
|----|--------|------|-------|
| E1 | GET | /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id} | customer, service |
| E2 | GET | /api/rhsm-subscriptions/v1/subscriptions/products/{product_id} | customer, service |
| E3 | GET | /api/rhsm-subscriptions/v2/subscriptions/products/{product_id} | customer, service |
| E4 | GET | /api/swatch-contracts/v1/subscriptions/billing_account_ids | customer, support, test |

# Test Cases

## User with admin permissions (subscriptions:*:*)

**rbac-parity-TC001 - User with admin permission is granted access to reporting endpoints**
- **Description**: Verify that a User identity with `subscriptions:*:*` RBAC permission can access capacity and subscription reporting endpoints under both RBACv1 and RBACv2.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:*:*` for this user
- **Action**:
  - GET E1, E2, E3 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC002 - User with admin permission is granted access to billing account IDs**
- **Description**: Verify that a User identity with `subscriptions:*:*` can access the billing account IDs endpoint under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - GET E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

## User with reader permissions (subscriptions:reports:read)

**rbac-parity-TC003 - User with reader permission is granted access to reporting endpoints**
- **Description**: Verify that a User identity with `subscriptions:reports:read` can access capacity and subscription reporting endpoints under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:reports:read`
- **Action**:
  - GET E1, E2, E3 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC004 - User with reader permission is granted access to billing account IDs**
- **Description**: Verify that a User identity with `subscriptions:reports:read` can access the billing account IDs endpoint under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:reports:read`
- **Action**:
  - GET E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

## User with no subscriptions permissions

**rbac-parity-TC005 - User with no permissions is denied access to reporting endpoints**
- **Description**: Verify that a User identity with no subscriptions permissions is denied access to capacity and subscription reporting endpoints under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E1, E2, E3 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 under both RBAC modes
- **Expected Result**:
  - Identical 403 response under RBACv1 and RBACv2

**rbac-parity-TC006 - User with no permissions is denied access to billing account IDs**
- **Description**: Verify that a User identity with no subscriptions permissions is denied access to the billing account IDs endpoint under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 under both RBAC modes
- **Expected Result**:
  - Identical 403 response under RBACv1 and RBACv2

## ServiceAccount identity

**rbac-parity-TC007 - ServiceAccount with admin permission is granted access**
- **Description**: Verify that a ServiceAccount identity with `subscriptions:*:*` can access all customer-facing endpoints under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, client_id
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - GET E1, E2, E3, E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC008 - ServiceAccount with no permissions is denied access**
- **Description**: Verify that a ServiceAccount identity with no subscriptions permissions is denied access under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, client_id
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E1, E2, E3, E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 under both RBAC modes
- **Expected Result**:
  - Identical 403 response under RBACv1 and RBACv2

## Associate identity (bypasses RBAC)

**rbac-parity-TC009 - Associate identity is granted access to billing account IDs**
- **Description**: Verify that an Associate identity (Red Hat employee) can access the billing account IDs endpoint, which allows the `support` role. Associate identities bypass RBAC entirely and receive the `support` role directly.
- **Setup**:
  - Prepare x-rh-identity with type=Associate, associate.email
- **Action**:
  - GET E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response (RBAC/Kessel is never called for Associates)

**rbac-parity-TC010 - Associate identity is denied access to reporting endpoints**
- **Description**: Verify that an Associate identity is denied access to reporting endpoints, which require the `customer` role that Associates do not receive.
- **Setup**:
  - Prepare x-rh-identity with type=Associate, associate.email
- **Action**:
  - GET E1, E2, E3 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 under both RBAC modes
- **Expected Result**:
  - Identical 403 response (RBAC/Kessel is never called for Associates)

## Resilience

**rbac-parity-TC011 - Kessel unavailable falls back to denial**
- **Description**: Verify that when the Kessel service is unreachable and the flag is ON, the user is denied access (fail-closed behavior).
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Enable Unleash flag
  - Kessel endpoint is unreachable or returns error
- **Action**:
  - GET E1 with the identity header
- **Verification**:
  - HTTP 403
- **Expected Result**:
  - Access denied when Kessel is unavailable (fail-closed)

## Export pipeline

**rbac-parity-TC012 - Export request with admin permission succeeds under both modes**
- **Description**: Verify that the subscription data export pipeline grants access when the user has `subscriptions:*:*` under both RBACv1 and RBACv2.
- **Setup**:
  - Prepare export request event with x-rh-identity containing type=User with admin permissions
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - Publish export request to Kafka topic (flag OFF, then flag ON)
- **Verification**:
  - Export completes successfully under both RBAC modes
- **Expected Result**:
  - Identical export behavior under RBACv1 and RBACv2

**rbac-parity-TC013 - Export request with no permissions is denied under both modes**
- **Description**: Verify that the subscription data export pipeline denies access when the user has no subscriptions permissions under both modes.
- **Setup**:
  - Prepare export request event with x-rh-identity containing type=User with no permissions
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - Publish export request to Kafka topic (flag OFF, then flag ON)
- **Verification**:
  - Export is denied under both RBAC modes
- **Expected Result**:
  - Identical denial under RBACv1 and RBACv2
