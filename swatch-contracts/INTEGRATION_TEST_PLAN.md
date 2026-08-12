# Integration Test Plan for swatch-contracts

Component-level testing is covered in [COMPONENT_TEST_PLAN.md](COMPONENT_TEST_PLAN.md). This document covers integration testing that exercises swatch-contracts against real downstream services in a deployed environment (stage or ephemeral).

Integration tests live in the IQE plugin: [iqe-rhsm-subscriptions-plugin](https://gitlab.cee.redhat.com/insights-qe/iqe-rhsm-subscriptions-plugin/-/tree/master/iqe_rhsm_subscriptions/tests/integration/swatch_contracts?ref_type=heads)

All tests are marked `@pytest.mark.post_stage_deploy`.

## Endpoints Under Test

| IQE Helper | Endpoint | Operation |
|---|---|---|
| `get_sku_capacity_report` | `/v1/subscriptions/products/{product_id}` | `getSkuCapacityReport` |
| `get_capacity_report_by_metric_id` | `/v1/capacity/products/{product_id}/{metric_id}` | `getCapacityReportByMetricId` |
| `get_today_capacity_report` | `/v1/capacity/products/{product_id}/{metric_id}` | `getCapacityReportByMetricId` |

## RBAC Authorization (RBACv1)

Source: `tests/integration/rbac/test_rbac.py`

### Admin Role Authorization

**Service Flow:** User or service account authenticates → console.redhat.com RBAC verifies the Subscription Watch Administrator role → swatch-contracts API grants access to capacity and subscription endpoints

**Services Tested:** console.redhat.com RBAC, swatch-contracts

**Failure Indicates:** The Subscription Watch Administrator role is not being recognized by swatch-contracts, or the RBAC integration with console.redhat.com is broken. Users and service accounts with admin privileges would be unable to access capacity and subscription endpoints.

#### contracts-rbac-admin-access-TC001 - Swatch Administrator role grants access

- **Given** a group with the Subscription Watch Administrator role and a non-admin user added to it
- **When** the user opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_swatch_group_permission_for_swatch_contracts`

#### contracts-rbac-admin-access-TC002 - Service account with admin roles grants access

- **Given** a service account with admin roles
- **When** the service account opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_service_account_admin_for_swatch_contracts`

### Read Role Authorization

**Service Flow:** User authenticates → console.redhat.com RBAC verifies the Subscriptions user (read) role → swatch-contracts API grants read access to capacity and subscription endpoints

**Services Tested:** console.redhat.com RBAC, swatch-contracts

**Failure Indicates:** The Subscriptions user (read) role is not being recognized by swatch-contracts, or role evaluation logic is incorrectly requiring admin-level permissions for read-only operations. Users with read-only access would be blocked from viewing capacity and subscription data.

#### contracts-rbac-read-access-TC001 - Subscriptions user (read) role grants access

- **Given** a group with the Subscriptions user role and a non-admin user added to it
- **When** the user opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_swatch_read_permission_for_swatch_contracts`

#### contracts-rbac-read-access-TC002 - Both read and administrator roles grant access

- **Given** a group with both Subscriptions user and Subscription Watch Administrator roles and a non-admin user added to it
- **When** the user opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_swatch_read_and_administrator_permission_for_swatch_contracts`

### Unauthorized Access Denial

**Service Flow:** User or service account authenticates → console.redhat.com RBAC finds no matching swatch role → swatch-contracts API returns "Access Denied" for all endpoints

**Services Tested:** console.redhat.com RBAC, swatch-contracts

**Failure Indicates:** swatch-contracts is not properly enforcing RBAC — unauthorized users or service accounts without swatch roles are being granted access to protected endpoints. This is a security issue where capacity and subscription data could be exposed to unprivileged users.

#### contracts-rbac-access-denied-TC001 - Service account without roles denied access

- **Given** a service account with no roles
- **When** the service account attempts opt-in, `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** all calls raise exception with "Access Denied"
- **IQE Function:** `test_verify_rbac_with_service_account_non_admin_for_swatch_contracts`

#### contracts-rbac-access-denied-TC002 - User without roles denied access

- **Given** a group with no roles and a non-admin user added to it
- **When** the user attempts opt-in, `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** all calls raise exception with "Access Denied"
- **IQE Function:** `test_verify_rbac_without_swatch_group_permission_for_swatch_contracts`

### Opt-in Enforcement

**Service Flow:** User authenticates → RBAC check (may pass or fail) → opt-in check finds organization not opted in → swatch-contracts API returns "Opt-in required" for capacity endpoints, "Access Denied" for subscription table

**Services Tested:** console.redhat.com RBAC, swatch opt-in service, swatch-contracts

**Failure Indicates:** The opt-in gate is not being enforced — organizations that have not opted in to Subscription Watch are able to access capacity and subscription data, or the opt-in service is not correctly communicating opt-in status to swatch-contracts.

#### contracts-rbac-optin-required-TC001 - User without roles and without opt-in denied access

- **Given** a group with no roles, a non-admin user added to it, and opt-in deleted by admin
- **When** the user attempts opt-in, `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** capacity call returns "Opt-in required.", sku capacity returns "Access Denied"
- **IQE Function:** `test_verify_rbac_not_opt_in_and_without_swatch_group_permission_for_swatch_contracts`

#### contracts-rbac-optin-required-TC002 - Read role without opt-in denied access

- **Given** a group with the Subscriptions user role, a non-admin user added to it, and opt-in deleted
- **When** the user attempts `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** capacity call returns "Opt-in required.", sku capacity returns "Access Denied"
- **IQE Function:** `test_verify_rbac_not_opt_in_and_with_subscriptions_user_permission_for_swatch_contracts`

## Kessel RBAC (v2) Authorization

Source: `tests/integration/rbac/test_rbac.py` (Kessel variants)

These tests validate that the Kessel authorization backend produces the same outcomes as RBACv1 for all contracts endpoints. Each test mirrors an existing RBAC test case above but runs with Kessel enabled.

### Admin Role Authorization (Kessel)

**Service Flow:** User or service account authenticates → Kessel RBAC verifies the Subscription Watch Administrator role → swatch-contracts API grants access to capacity and subscription endpoints

**Services Tested:** Kessel RBAC, swatch-contracts

**Failure Indicates:** The Kessel authorization backend is not correctly evaluating the Subscription Watch Administrator role, or the migration from RBACv1 to Kessel has introduced a regression in admin access. Admin users would be blocked from swatch-contracts when Kessel is enabled.

#### contracts-kessel-admin-access-TC001 - Swatch Administrator role grants access via Kessel

- **Given** Kessel RBAC is enabled, and a group with the Subscription Watch Administrator role and a non-admin user added to it
- **When** the user opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed, matching RBACv1 behavior

#### contracts-kessel-admin-access-TC002 - Service account with admin roles grants access via Kessel

- **Given** Kessel RBAC is enabled, and a service account with admin roles
- **When** the service account opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed, matching RBACv1 behavior

### Read Role Authorization (Kessel)

**Service Flow:** User authenticates → Kessel RBAC verifies the Subscriptions user (read) role → swatch-contracts API grants read access

**Services Tested:** Kessel RBAC, swatch-contracts

**Failure Indicates:** Kessel is not correctly recognizing the Subscriptions user (read) role, or the Kessel migration has introduced a parity gap where read-only access works under RBACv1 but not under Kessel.

#### contracts-kessel-read-access-TC001 - Subscriptions user (read) role grants access via Kessel

- **Given** Kessel RBAC is enabled, and a group with the Subscriptions user role and a non-admin user added to it
- **When** the user opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed, matching RBACv1 behavior

#### contracts-kessel-read-access-TC002 - Both read and administrator roles grant access via Kessel

- **Given** Kessel RBAC is enabled, and a group with both Subscriptions user and Subscription Watch Administrator roles and a non-admin user added to it
- **When** the user opts in and calls `get_today_capacity_report(Sockets or Cores)`, `get_sku_capacity_report(RHEL for x86)`
- **Then** all API calls succeed, matching RBACv1 behavior

### Unauthorized Access Denial (Kessel)

**Service Flow:** User or service account authenticates → Kessel RBAC finds no matching swatch role → swatch-contracts API returns "Access Denied"

**Services Tested:** Kessel RBAC, swatch-contracts

**Failure Indicates:** Kessel is not properly denying access to unauthorized users — the migration from RBACv1 to Kessel has introduced a security regression where unprivileged users or service accounts can access protected swatch-contracts endpoints.

#### contracts-kessel-access-denied-TC001 - Service account without roles denied access via Kessel

- **Given** Kessel RBAC is enabled, and a service account with no roles
- **When** the service account attempts opt-in, `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** all calls raise exception with "Access Denied", matching RBACv1 behavior

#### contracts-kessel-access-denied-TC002 - User without roles denied access via Kessel

- **Given** Kessel RBAC is enabled, and a group with no roles and a non-admin user added to it
- **When** the user attempts opt-in, `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** all calls raise exception with "Access Denied", matching RBACv1 behavior

### Opt-in Enforcement (Kessel)

**Service Flow:** User authenticates → Kessel RBAC check (may pass or fail) → opt-in check finds organization not opted in → swatch-contracts API returns "Opt-in required" for capacity endpoints, "Access Denied" for subscription table

**Services Tested:** Kessel RBAC, swatch opt-in service, swatch-contracts

**Failure Indicates:** The opt-in enforcement is not functioning correctly under Kessel — organizations that have not opted in are able to access data when Kessel is the authorization backend, or the interaction between Kessel and the opt-in service has a parity gap with RBACv1.

#### contracts-kessel-optin-required-TC001 - User without roles and without opt-in denied access via Kessel

- **Given** Kessel RBAC is enabled, a group with no roles, a non-admin user added to it, and opt-in deleted by admin
- **When** the user attempts opt-in, `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** capacity call returns "Opt-in required.", sku capacity returns "Access Denied", matching RBACv1 behavior

#### contracts-kessel-optin-required-TC002 - Read role without opt-in denied access via Kessel

- **Given** Kessel RBAC is enabled, a group with the Subscriptions user role, a non-admin user added to it, and opt-in deleted
- **When** the user attempts `get_capacity_report_by_metric_id`, `get_sku_capacity_report`
- **Then** capacity call returns "Opt-in required.", sku capacity returns "Access Denied", matching RBACv1 behavior
