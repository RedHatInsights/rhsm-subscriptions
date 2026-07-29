# Integration Test Plan for swatch-tally

Component-level testing is covered in [SWATCH-TALLY-COMPONENT-TEST-PLAN.md](SWATCH-TALLY-COMPONENT-TEST-PLAN.md). This document covers integration testing that exercises swatch-tally against real downstream services in a deployed environment (stage or ephemeral).

Integration tests live in the IQE plugin: iqe-rhsm-subscriptions-plugin

All tests are marked `@pytest.mark.post_stage_deploy`.

## Endpoints Under Test

| IQE Helper | Endpoint | Operation |
|---|---|---|
| `get_instances_by_product` | `/v1/instances/products/{product_id}` | `getInstancesByProduct` |
| `get_instance_guests` | `/v1/instances/{id}/guests` | `getInstanceGuests` |
| `get_tally_report` | `/v1/tally/products/{product_id}/{metric_id}` | `getTallyReportData` |
| `get_today_tally_report` | `/v1/tally/products/{product_id}/{metric_id}` | `getTallyReportData` (today) |
| `delete_opt_in_config` | opt-in API | `deleteOptInConfig` |
| `get_opt_in_config` | opt-in API | `getOptInConfig` |
| `put_opt_in_config` | opt-in API | `putOptInConfig` |

## RBAC Authorization (RBACv1)

Source: `tests/integration/rbac/test_rbac.py`

### Admin Role Authorization

**Service Flow:** User or service account authenticates → console.redhat.com RBAC verifies the Subscription Watch Administrator role → swatch-tally API grants access to tally, instances, and opt-in endpoints

**Services Tested:** console.redhat.com RBAC, swatch-tally

**Failure Indicates:** The Subscription Watch Administrator role is not being recognized by swatch-tally, or the RBAC integration with console.redhat.com is broken. Users and service accounts with admin privileges would be unable to access tally, instances, or opt-in endpoints.

#### tally-rbac-admin-access-TC001 - Swatch Administrator role grants access

- **Given** a group with the Subscription Watch Administrator role and a non-admin user added to it
- **When** the user opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_swatch_group_permission_for_swatch_tally`

#### tally-rbac-admin-access-TC002 - Service account with admin roles grants access

- **Given** a service account with admin roles
- **When** the service account opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_service_account_admin_for_swatch_tally`

### Read Role Authorization

**Service Flow:** User authenticates → console.redhat.com RBAC verifies the Subscriptions user (read) role → swatch-tally API grants read access to tally, instances, and opt-in endpoints

**Services Tested:** console.redhat.com RBAC, swatch-tally

**Failure Indicates:** The Subscriptions user (read) role is not being recognized by swatch-tally, or role evaluation logic is incorrectly requiring admin-level permissions for read-only operations. Users with read-only access would be blocked from viewing tally and instance data.

#### tally-rbac-read-access-TC001 - Subscriptions user (read) role grants access

- **Given** a group with the Subscriptions user role and a non-admin user added to it
- **When** the user opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_swatch_read_permission_for_swatch_tally`

#### tally-rbac-read-access-TC002 - Both read and administrator roles grant access

- **Given** a group with both Subscriptions user and Subscription Watch Administrator roles and a non-admin user added to it
- **When** the user opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed
- **IQE Function:** `test_verify_rbac_with_swatch_read_and_administrator_permission_for_swatch_tally`

### Unauthorized Access Denial

**Service Flow:** User or service account authenticates → console.redhat.com RBAC finds no matching swatch role → swatch-tally API returns "Access Denied" for all endpoints

**Services Tested:** console.redhat.com RBAC, swatch-tally

**Failure Indicates:** swatch-tally is not properly enforcing RBAC — unauthorized users or service accounts without swatch roles are being granted access to protected endpoints. This is a security issue where data could be exposed to unprivileged users.

#### tally-rbac-access-denied-TC001 - Service account without roles denied access

- **Given** a service account with no roles
- **When** the service account attempts opt-in, `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls raise exception with "Access Denied"
- **IQE Function:** `test_verify_rbac_with_service_account_non_admin_for_swatch_tally`

#### tally-rbac-access-denied-TC002 - User without roles denied access

- **Given** a group with no roles and a non-admin user added to it
- **When** the user attempts opt-in, `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls raise exception with "Access Denied"
- **IQE Function:** `test_verify_rbac_without_swatch_group_permission_for_swatch_tally`

### Opt-in Enforcement

**Service Flow:** User authenticates → RBAC check (may pass or fail) → opt-in check finds organization not opted in → swatch-tally API returns "Opt-in required" for data endpoints, "Access Denied" for subscription table

**Services Tested:** console.redhat.com RBAC, swatch opt-in service, swatch-tally

**Failure Indicates:** The opt-in gate is not being enforced — organizations that have not opted in to Subscription Watch are able to access tally and instance data, or the opt-in service is not correctly communicating opt-in status to swatch-tally.

#### tally-rbac-optin-required-TC001 - User without roles and without opt-in denied access

- **Given** a group with no roles, a non-admin user added to it, and opt-in deleted by admin
- **When** the user attempts opt-in, `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls raise exception with "Opt-in required."
- **IQE Function:** `test_verify_rbac_not_opt_in_and_without_swatch_group_permission_for_swatch_tally`

#### tally-rbac-optin-required-TC002 - Read role without opt-in denied access

- **Given** a group with the Subscriptions user role, a non-admin user added to it, and opt-in deleted
- **When** the user attempts `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls return "Opt-in required."
- **IQE Function:** `test_verify_rbac_not_opt_in_and_with_subscriptions_user_permission_for_swatch_tally`

## Kessel RBAC (v2) Authorization

Source: `tests/integration/rbac/test_rbac.py` (Kessel variants)

These tests validate that the Kessel authorization backend produces the same outcomes as RBACv1 for all tally endpoints. Each test mirrors an existing RBAC test case above but runs with Kessel enabled.

### Admin Role Authorization (Kessel)

**Service Flow:** User or service account authenticates → Kessel RBAC verifies the Subscription Watch Administrator role → swatch-tally API grants access to tally, instances, and opt-in endpoints

**Services Tested:** Kessel RBAC, swatch-tally

**Failure Indicates:** The Kessel authorization backend is not correctly evaluating the Subscription Watch Administrator role, or the migration from RBACv1 to Kessel has introduced a regression in admin access. Admin users would be blocked from swatch-tally when Kessel is enabled.

#### tally-kessel-admin-access-TC001 - Swatch Administrator role grants access via Kessel

- **Given** Kessel RBAC is enabled, and a group with the Subscription Watch Administrator role and a non-admin user added to it
- **When** the user opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed, matching RBACv1 behavior

#### tally-kessel-admin-access-TC002 - Service account with admin roles grants access via Kessel

- **Given** Kessel RBAC is enabled, and a service account with admin roles
- **When** the service account opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed, matching RBACv1 behavior

### Read Role Authorization (Kessel)

**Service Flow:** User authenticates → Kessel RBAC verifies the Subscriptions user (read) role → swatch-tally API grants read access

**Services Tested:** Kessel RBAC, swatch-tally

**Failure Indicates:** Kessel is not correctly recognizing the Subscriptions user (read) role, or the Kessel migration has introduced a parity gap where read-only access works under RBACv1 but not under Kessel.

#### tally-kessel-read-access-TC001 - Subscriptions user (read) role grants access via Kessel

- **Given** Kessel RBAC is enabled, and a group with the Subscriptions user role and a non-admin user added to it
- **When** the user opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed, matching RBACv1 behavior

#### tally-kessel-read-access-TC002 - Both read and administrator roles grant access via Kessel

- **Given** Kessel RBAC is enabled, and a group with both Subscriptions user and Subscription Watch Administrator roles and a non-admin user added to it
- **When** the user opts in and calls `get_today_tally_report(Sockets)`, `get_instances_by_product(RHEL for x86)`, `get_instance_guests(uuid)`
- **Then** all API calls succeed, matching RBACv1 behavior

### Unauthorized Access Denial (Kessel)

**Service Flow:** User or service account authenticates → Kessel RBAC finds no matching swatch role → swatch-tally API returns "Access Denied"

**Services Tested:** Kessel RBAC, swatch-tally

**Failure Indicates:** Kessel is not properly denying access to unauthorized users — the migration from RBACv1 to Kessel has introduced a security regression where unprivileged users or service accounts can access protected swatch-tally endpoints.

#### tally-kessel-access-denied-TC001 - Service account without roles denied access via Kessel

- **Given** Kessel RBAC is enabled, and a service account with no roles
- **When** the service account attempts opt-in, `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls raise exception with "Access Denied", matching RBACv1 behavior

#### tally-kessel-access-denied-TC002 - User without roles denied access via Kessel

- **Given** Kessel RBAC is enabled, and a group with no roles and a non-admin user added to it
- **When** the user attempts opt-in, `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls raise exception with "Access Denied", matching RBACv1 behavior

### Opt-in Enforcement (Kessel)

**Service Flow:** User authenticates → Kessel RBAC check (may pass or fail) → opt-in check finds organization not opted in → swatch-tally API returns "Opt-in required" for data endpoints

**Services Tested:** Kessel RBAC, swatch opt-in service, swatch-tally

**Failure Indicates:** The opt-in enforcement is not functioning correctly under Kessel — organizations that have not opted in are able to access data when Kessel is the authorization backend, or the interaction between Kessel and the opt-in service has a parity gap with RBACv1.

#### tally-kessel-optin-required-TC001 - User without roles and without opt-in denied access via Kessel

- **Given** Kessel RBAC is enabled, a group with no roles, a non-admin user added to it, and opt-in deleted by admin
- **When** the user attempts opt-in, `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls raise exception with "Opt-in required.", matching RBACv1 behavior

#### tally-kessel-optin-required-TC002 - Read role without opt-in denied access via Kessel

- **Given** Kessel RBAC is enabled, a group with the Subscriptions user role, a non-admin user added to it, and opt-in deleted
- **When** the user attempts `get_tally_report_data`, `get_instances_by_product`, `get_instance_guests`
- **Then** all calls return "Opt-in required.", matching RBACv1 behavior
