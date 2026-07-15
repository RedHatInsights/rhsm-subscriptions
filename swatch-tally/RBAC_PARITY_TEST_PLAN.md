# Introduction

This document defines the test plan for achieving RBACv1/RBACv2 authorization parity in the **swatch-tally** service (Spring Boot, hosting the swatch-api endpoints). The goal is to verify that every public-facing endpoint produces identical authorization outcomes under both RBACv1 and Kessel (RBACv2), controlled by the Unleash flag `swatch.common-security.use-kessel-rbac`.

**Purpose:** Ensure that toggling from RBACv1 to RBACv2 produces zero behavioral change for end users across all identity types and permission levels.

**Scope:**

* Tally reporting endpoints (`@ReportingAccessRequired`)
* Instances endpoints (`@ReportingAccessRequired`, `@ReportingAccessOrInternalRequired`)
* Opt-in endpoints (`@SubscriptionWatchAdminOnly`)
* Instances data export pipeline (explicit `RbacDelegate` RBAC check)
* All identity types: User, ServiceAccount, Associate, X509
* All RBAC permission levels: admin (`subscriptions:*:*`), reader (`subscriptions:reports:read`), none

**Assumptions:**

* The Kessel inventory API is reachable and has the swatch relations schema loaded
* The Unleash flag `swatch.common-security.use-kessel-rbac` controls which authorization path is used
* `@SubscriptionWatchAdminOnly` accepts both SUBSCRIPTION_WATCH_ADMIN and SUBSCRIPTION_WATCH_REPORT_READER (despite the name)

**Constraints:**

* Testing is limited to component-level authorization behavior
* The `reportAccessService.providesAccessTo()` allowlist check in `@ReportingAccessRequired` is orthogonal to RBAC and out of scope for parity testing

# Test Strategy

Each test case exercises the same endpoint under both RBAC modes and asserts identical HTTP status codes. The Spring security stack uses `IdentityHeaderAuthenticationDetailsService` which calls either `RbacService` (v1) or `KesselService` (v2) based on the Unleash flag.

**Endpoints under test:**

| ID | Method | Path | Security Annotation |
|----|--------|------|---------------------|
| E1 | GET | /api/rhsm-subscriptions/v1/tally/products/{product_id}/{metric_id} | @ReportingAccessRequired |
| E2 | GET | /api/rhsm-subscriptions/v1/instances/products/{product_id} | @ReportingAccessRequired |
| E3 | GET | /api/rhsm-subscriptions/v1/instances/{id}/guests | @ReportingAccessRequired |
| E4 | GET | /api/rhsm-subscriptions/v1/instances/billing_account_ids | @ReportingAccessOrInternalRequired |
| E5 | GET | /api/rhsm-subscriptions/v1/opt-in | @SubscriptionWatchAdminOnly |
| E6 | PUT | /api/rhsm-subscriptions/v1/opt-in | @SubscriptionWatchAdminOnly |
| E7 | DELETE | /api/rhsm-subscriptions/v1/opt-in | @SubscriptionWatchAdminOnly |

# Test Cases

## User with admin permissions (subscriptions:*:*)

**rbac-parity-TC001 - User with admin permission accesses reporting endpoints**
- **Description**: Verify that a User with `subscriptions:*:*` can access tally and instances reporting endpoints under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - GET E1, E2, E3, E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC002 - User with admin permission accesses opt-in endpoints**
- **Description**: Verify that a User with `subscriptions:*:*` can access opt-in GET/PUT/DELETE under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - GET E5, PUT E6, DELETE E7 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

## User with reader permissions (subscriptions:reports:read)

**rbac-parity-TC003 - User with reader permission accesses reporting endpoints**
- **Description**: Verify that a User with `subscriptions:reports:read` can access tally and instances reporting endpoints under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:reports:read`
- **Action**:
  - GET E1, E2, E3, E4 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC004 - User with reader permission accesses opt-in endpoints**
- **Description**: Verify that a User with `subscriptions:reports:read` can access opt-in endpoints. Despite the annotation name `@SubscriptionWatchAdminOnly`, it accepts both admin and reader roles.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to grant `subscriptions:reports:read`
- **Action**:
  - GET E5, PUT E6, DELETE E7 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

## User with no permissions

**rbac-parity-TC005 - User with no permissions is denied all endpoints**
- **Description**: Verify that a User with no subscriptions permissions is denied access to all customer-facing endpoints under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E1, E2, E3, E4, E5 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 for all endpoints under both RBAC modes
- **Expected Result**:
  - Identical 403 responses under RBACv1 and RBACv2

## ServiceAccount identity

**rbac-parity-TC006 - ServiceAccount with admin permission is granted access**
- **Description**: Verify that a ServiceAccount with `subscriptions:*:*` can access all customer-facing endpoints under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, client_id
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - GET E1, E2, E3, E4, E5 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC007 - ServiceAccount with no permissions is denied access**
- **Description**: Verify that a ServiceAccount with no subscriptions permissions is denied access under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, client_id
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E1, E2, E3, E4, E5 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 for all endpoints under both RBAC modes
- **Expected Result**:
  - Identical 403 responses under RBACv1 and RBACv2

## Associate identity (bypasses RBAC)

**rbac-parity-TC008 - Associate gets INTERNAL role, denied customer endpoints**
- **Description**: Verify that an Associate identity receives `ROLE_INTERNAL` (not SUBSCRIPTION_WATCH_ADMIN/READER) and is denied access to customer-facing endpoints. RBAC is never called for Associates on the Spring path.
- **Setup**:
  - Prepare x-rh-identity with type=Associate, associate.email
- **Action**:
  - GET E1, E2, E5 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 for all endpoints under both RBAC modes
- **Expected Result**:
  - Identical 403 responses (RBAC/Kessel is never called)

## X509/Turnpike identity (bypasses RBAC)

**rbac-parity-TC009 - X509 identity gets INTERNAL role, denied customer endpoints**
- **Description**: Verify that an X509 identity receives `ROLE_INTERNAL` and is denied access to customer-facing endpoints. RBAC is never called for X509 identities.
- **Setup**:
  - Prepare x-rh-identity with type=X509
- **Action**:
  - GET E1, E2, E5 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 for all endpoints under both RBAC modes
- **Expected Result**:
  - Identical 403 responses (RBAC/Kessel is never called)

## Resilience

**rbac-parity-TC010 - Kessel unavailable falls back to denial**
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

**rbac-parity-TC011 - Export with admin permission succeeds under both modes**
- **Description**: Verify that the instances data export pipeline grants access when the user has `subscriptions:*:*` under both modes.
- **Setup**:
  - Prepare export request event with x-rh-identity containing type=User with admin permissions
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - Publish export request to Kafka topic (flag OFF, then flag ON)
- **Verification**:
  - Export completes successfully under both RBAC modes
- **Expected Result**:
  - Identical export behavior under RBACv1 and RBACv2

**rbac-parity-TC012 - Export with no permissions is denied under both modes**
- **Description**: Verify that the instances data export pipeline denies access when the user has no subscriptions permissions under both modes.
- **Setup**:
  - Prepare export request event with x-rh-identity containing type=User with no permissions
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - Publish export request to Kafka topic (flag OFF, then flag ON)
- **Verification**:
  - Export is denied under both RBAC modes
- **Expected Result**:
  - Identical denial under RBACv1 and RBACv2
