# Introduction

This document defines the test plan for achieving RBACv1/RBACv2 authorization parity in the **swatch-utilization** service. The goal is to verify that every public-facing endpoint produces identical authorization outcomes under both RBACv1 and Kessel (RBACv2), controlled by the Unleash flag `swatch.common-security.use-kessel-rbac`.

**Purpose:** Ensure that toggling from RBACv1 to RBACv2 produces zero behavioral change for end users across all identity types and permission levels.

**Scope:**

* Org preferences GET endpoint (requires `customer` role from RBAC)
* Org preferences POST endpoint (requires `org_admin` role from identity header, not RBAC)
* All identity types: User, ServiceAccount, Associate
* All RBAC permission levels: admin (`subscriptions:*:*`), reader (`subscriptions:reports:read`), none
* org_admin flag interaction with RBAC permissions

**Assumptions:**

* The Kessel inventory API is reachable and has the swatch relations schema loaded
* The Unleash flag `swatch.common-security.use-kessel-rbac` controls which authorization path is used

**Constraints:**

* Testing is limited to component-level authorization behavior
* The `org_admin` role is derived from `is_org_admin` in the x-rh-identity header, not from RBAC — but the RBAC augmentor is still invoked for the request

# Test Strategy

Each test case exercises the same endpoint under both RBAC modes and asserts identical HTTP status codes.

**Endpoints under test:**

| ID | Method | Path | Roles |
|----|--------|------|-------|
| E1 | GET | /api/rhsm-subscriptions/v1/utilization/org-preferences | customer, service |
| E2 | POST | /api/rhsm-subscriptions/v1/utilization/org-preferences | org_admin, service |

# Test Cases

## User with admin permissions

**rbac-parity-TC001 - User with admin permission accesses org preferences GET**
- **Description**: Verify that a User with `subscriptions:*:*` can read org preferences under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - GET E1 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC002 - User with admin permission but not org_admin is denied org preferences POST**
- **Description**: Verify that a User with `subscriptions:*:*` but `is_org_admin=false` cannot update org preferences. The POST endpoint requires the `org_admin` role, which comes from the identity header, not RBAC.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - POST E2 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 under both RBAC modes
- **Expected Result**:
  - Identical 403 response under RBACv1 and RBACv2

## User with admin permissions and org_admin

**rbac-parity-TC003 - User with admin permission and org_admin accesses org preferences POST**
- **Description**: Verify that a User with `subscriptions:*:*` and `is_org_admin=true` can update org preferences under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=true
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - POST E2 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

## User with no permissions but is org_admin

**rbac-parity-TC004 - User with no RBAC permissions but org_admin accesses org preferences POST**
- **Description**: Verify that a User with no subscriptions RBAC permissions but `is_org_admin=true` can update org preferences. The `org_admin` role is independent of RBAC.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=true
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - POST E2 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC005 - User with no RBAC permissions but org_admin is denied org preferences GET**
- **Description**: Verify that a User with no subscriptions permissions but `is_org_admin=true` is denied access to GET org preferences (which requires `customer` role from RBAC, not `org_admin`).
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=true
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E1 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 under both RBAC modes
- **Expected Result**:
  - Identical 403 response under RBACv1 and RBACv2

## User with no permissions

**rbac-parity-TC006 - User with no permissions is denied access to all endpoints**
- **Description**: Verify that a User with no subscriptions permissions and `is_org_admin=false` is denied access to both endpoints.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E1 and POST E2 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 for both endpoints under both RBAC modes
- **Expected Result**:
  - Identical 403 responses under RBACv1 and RBACv2

## ServiceAccount identity

**rbac-parity-TC007 - ServiceAccount with admin permission accesses org preferences GET**
- **Description**: Verify that a ServiceAccount with `subscriptions:*:*` can read org preferences under both modes.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, client_id
  - Configure RBAC/Kessel to grant `subscriptions:*:*`
- **Action**:
  - GET E1 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 200 under both RBAC modes
- **Expected Result**:
  - Identical 200 response under RBACv1 and RBACv2

**rbac-parity-TC008 - ServiceAccount with no permissions is denied access**
- **Description**: Verify that a ServiceAccount with no subscriptions permissions is denied access to both endpoints.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, client_id
  - Configure RBAC/Kessel to return no subscriptions permissions
- **Action**:
  - GET E1 and POST E2 with the identity header (flag OFF, then flag ON)
- **Verification**:
  - HTTP 403 for both endpoints under both RBAC modes
- **Expected Result**:
  - Identical 403 responses under RBACv1 and RBACv2

## Resilience

**rbac-parity-TC009 - Kessel unavailable falls back to denial**
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
