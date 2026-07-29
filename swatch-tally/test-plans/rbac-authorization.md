# RBAC Authorization

**Functional Area:** Access control and authentication for all swatch-tally APIs

This test plan validates authorization parity between RBACv1 and RBACv2/Kessel for swatch-tally REST APIs and Kafka export processing.

**Test Coverage:**
- RBACv1 and RBACv2/Kessel authorization backends
- Identity types:
  - REST endpoints: User, ServiceAccount, Associate, and X509
  - Kafka exports: User only
- Admin and reader permission levels
- Reporting endpoints (tally reports, instances, billing accounts)
- Opt-in configuration endpoints
- Export authorization via Kafka
- Fail-closed resilience when Kessel is unavailable

**Endpoints Under Test:**
- E1: `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}`
- E2: `GET /api/rhsm-subscriptions/v1/instances/products/{productId}`
- E3: `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests`
- E4: `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids`
- E5: `GET /api/rhsm-subscriptions/v1/opt-in`
- E6: `PUT /api/rhsm-subscriptions/v1/opt-in`
- E7: `DELETE /api/rhsm-subscriptions/v1/opt-in`

**Related Jira:**
- [SWATCH-5267](https://redhat.atlassian.net/browse/SWATCH-5267)
- [SWATCH-5268](https://redhat.atlassian.net/browse/SWATCH-5268)
- [SWATCH-4153](https://redhat.atlassian.net/browse/SWATCH-4153)
- [SWATCH-5264](https://redhat.atlassian.net/browse/SWATCH-5264)

---

## RBAC Authorization (RBACv1 and RBACv2 Parity)

### RBACv1 (Kessel Flag OFF)

**rbac-v1-TC001 - User with admin permission accesses all reporting endpoints**

- **Description**: Verify that a User granted `subscriptions:*:*` permission can access all reporting endpoints (E1, E2, E3, E4) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return `subscriptions:*:*` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200 for all four endpoints
- **Expected Result**: Admin permission grants access to all reporting endpoints

**rbac-v1-TC002 - User with reader permission accesses reporting endpoints**

- **Description**: Verify that a User granted `subscriptions:reports:read` permission can access all reporting endpoints (E1, E2, E3, E4) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return `subscriptions:reports:read` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200 for all four endpoints
- **Expected Result**: Reader permission grants access to all reporting endpoints

**rbac-v1-TC003 - User with no permissions denied all reporting endpoints**

- **Description**: Verify that a User with no subscriptions permissions is denied access to all reporting endpoints (E1, E2, E3, E4) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return no subscriptions permissions (`{"data": [], "meta": {"count": 0}}`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 403 for all four endpoints
- **Expected Result**: No permissions results in denial for all reporting endpoints

**rbac-v1-TC004 - ServiceAccount with admin permission accesses all reporting endpoints**

- **Description**: Verify that a ServiceAccount granted `subscriptions:*:*` permission can access all reporting endpoints (E1, E2, E3, E4) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub RBACv1 endpoint to return `subscriptions:*:*` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200 for all four endpoints
- **Expected Result**: ServiceAccount with admin permission accesses all reporting endpoints

**rbac-v1-TC005 - ServiceAccount with reader permission accesses reporting endpoints**

- **Description**: Verify that a ServiceAccount granted `subscriptions:reports:read` permission can access reporting endpoints (E1, E2, E3, E4) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub RBACv1 endpoint to return `subscriptions:reports:read` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200 for E1, E2, E3, E4
- **Expected Result**: ServiceAccount with reader permission accesses reporting endpoints

**rbac-v1-TC006 - ServiceAccount with no permissions denied all reporting endpoints**

- **Description**: Verify that a ServiceAccount with no subscriptions permissions is denied access to all reporting endpoints (E1, E2, E3, E4) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub RBACv1 endpoint to return no subscriptions permissions (`{"data": [], "meta": {"count": 0}}`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 403 for all four endpoints
- **Expected Result**: ServiceAccount with no permissions is denied all reporting endpoints

**rbac-v1-TC007a - Associate identity denied reporting endpoints**

- **Description**: Verify that an Associate identity is denied access to reporting endpoints (E1, E2, E3). Associate bypasses RBAC and receives `ROLE_INTERNAL`, which is not accepted by @ReportingAccessRequired.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=Associate`, `associate.email`
  - No RBAC stub needed (Associate bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: Associate identity denied reporting endpoints (INTERNAL role not accepted by @ReportingAccessRequired)

**rbac-v1-TC007b - Associate identity accesses billing account IDs**

- **Description**: Verify that an Associate identity can access the billing account IDs endpoint (E4). @ReportingAccessOrInternalRequired accepts `ROLE_INTERNAL`.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=Associate`, `associate.email`
  - No RBAC stub needed (Associate bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**: `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200
- **Expected Result**: Associate identity accesses billing account IDs (INTERNAL role accepted)

**rbac-v1-TC008a - X509 identity denied reporting endpoints**

- **Description**: Verify that an X509/Turnpike identity is denied access to reporting endpoints (E1, E2, E3). X509 bypasses RBAC and receives `ROLE_INTERNAL`.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=X509`
  - No RBAC stub needed (X509 bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: X509 identity denied reporting endpoints (INTERNAL role not accepted)

**rbac-v1-TC008b - X509 identity accesses billing account IDs**

- **Description**: Verify that an X509/Turnpike identity can access the billing account IDs endpoint (E4). @ReportingAccessOrInternalRequired accepts `ROLE_INTERNAL`.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=X509`
  - No RBAC stub needed (X509 bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**: `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200
- **Expected Result**: X509 identity accesses billing account IDs (INTERNAL role accepted)

**rbac-v1-TC009 - Export with admin permission succeeds**

- **Description**: Verify that an export request with a User identity granted `subscriptions:*:*` permission completes successfully under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare export request event with `x-rh-identity` containing `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return `subscriptions:*:*` for this identity
- **Action**: Publish export request to Kafka export topic
- **Verification**:
  - Export completes successfully
  - Export data is produced on the export response topic
- **Expected Result**: Admin permission grants export access

**rbac-v1-TC010 - Export with reader permission succeeds**

- **Description**: Verify that an export request with a User identity granted `subscriptions:reports:read` permission completes successfully under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare export request event with `x-rh-identity` containing `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return `subscriptions:reports:read` for this identity
- **Action**: Publish export request to Kafka export topic
- **Verification**:
  - Export completes successfully
  - Export data is produced on the export response topic
- **Expected Result**: Reader permission grants export access

**rbac-v1-TC011 - Export with no permissions denied**

- **Description**: Verify that an export request with a User identity with no subscriptions permissions is denied under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare export request event with `x-rh-identity` containing `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return no subscriptions permissions (`{"data": [], "meta": {"count": 0}}`)
- **Action**: Publish export request to Kafka export topic
- **Verification**:
  - Export is denied
  - No export data is produced
- **Expected Result**: No permissions results in export denial

#### Opt-in

**rbac-v1-optin-TC001 - User with admin permission accesses all opt-in endpoints**

- **Description**: Verify that a User granted `subscriptions:*:*` permission can access all opt-in endpoints (E5, E6, E7) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return `subscriptions:*:*` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 200 for all three endpoints
- **Expected Result**: Admin permission grants access to all opt-in endpoints

**rbac-v1-optin-TC002 - User with reader permission accesses opt-in endpoints**

- **Description**: Verify that a User granted `subscriptions:reports:read` permission can access opt-in endpoints (E5, E6, E7) under RBACv1. Despite the annotation name `@SubscriptionWatchAdminOnly`, both admin and reader roles are accepted.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return `subscriptions:reports:read` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 200 for all three endpoints
- **Expected Result**: Reader permission grants access to all opt-in endpoints

**rbac-v1-optin-TC003 - User with no permissions denied all opt-in endpoints**

- **Description**: Verify that a User with no subscriptions permissions is denied access to all opt-in endpoints (E5, E6, E7) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub RBACv1 endpoint to return no subscriptions permissions (`{"data": [], "meta": {"count": 0}}`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: No permissions results in denial for all opt-in endpoints

**rbac-v1-optin-TC004 - ServiceAccount with admin permission accesses all opt-in endpoints**

- **Description**: Verify that a ServiceAccount granted `subscriptions:*:*` permission can access all opt-in endpoints (E5, E6, E7) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub RBACv1 endpoint to return `subscriptions:*:*` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 200 for all three endpoints
- **Expected Result**: ServiceAccount with admin permission accesses all opt-in endpoints

**rbac-v1-optin-TC005 - ServiceAccount with reader permission accesses all opt-in endpoints**

- **Description**: Verify that a ServiceAccount granted `subscriptions:reports:read` permission can access all opt-in endpoints (E5, E6, E7) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub RBACv1 endpoint to return `subscriptions:reports:read` for this identity
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 200 for all three endpoints
- **Expected Result**: ServiceAccount with reader permission accesses all opt-in endpoints

**rbac-v1-optin-TC006 - ServiceAccount with no permissions denied all opt-in endpoints**

- **Description**: Verify that a ServiceAccount with no subscriptions permissions is denied access to all opt-in endpoints (E5, E6, E7) under RBACv1.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub RBACv1 endpoint to return no subscriptions permissions (`{"data": [], "meta": {"count": 0}}`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: ServiceAccount with no permissions is denied from all opt-in endpoints

**rbac-v1-optin-TC007 - Associate identity denied opt-in endpoint**

- **Description**: Verify that an Associate identity is denied access to opt-in endpoint (E5). `ROLE_INTERNAL` is not accepted by @SubscriptionWatchAdminOnly.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=Associate`, `associate.email`
  - No RBAC stub needed (Associate bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for opt-in endpoint
- **Expected Result**: Associate identity denied opt-in endpoint (INTERNAL role not accepted)

**rbac-v1-optin-TC008 - X509 identity denied opt-in endpoint**

- **Description**: Verify that an X509/Turnpike identity is denied access to opt-in endpoint (E5). `ROLE_INTERNAL` is not accepted by @SubscriptionWatchAdminOnly.
- **Setup**:
  - Kessel Unleash flag is OFF
  - Prepare `x-rh-identity` header with `type=X509`
  - No RBAC stub needed (X509 bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for opt-in endpoint
- **Expected Result**: X509 identity denied opt-in endpoint (INTERNAL role not accepted)

### RBACv2 / Kessel (Flag ON)

**rbac-v2-TC001 - User with Kessel permission accesses all reporting endpoints**

- **Description**: Verify that a User granted Kessel `subscriptions_report_view` relation can access all reporting endpoints (E1, E2, E3, E4) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub Kessel workspace endpoint to return default workspace (`{"data": [{"id": "<workspace-id>", "name": "Default", "type": "default"}]}`)
  - Stub Kessel check endpoint to return `ALLOWED_TRUE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=user_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200 for all four endpoints
- **Expected Result**: Kessel permission grants access to all reporting endpoints

**rbac-v2-TC002 - User with no permissions denied all reporting endpoints**

- **Description**: Verify that a User with no Kessel permissions is denied access to all reporting endpoints (E1, E2, E3, E4) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_FALSE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=user_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 403 for all four endpoints
- **Expected Result**: No Kessel permissions results in denial for all reporting endpoints

**rbac-v2-TC003 - ServiceAccount with admin permission accesses all reporting endpoints**

- **Description**: Verify that a ServiceAccount granted Kessel `subscriptions_report_view` relation can access all reporting endpoints (E1, E2, E3, E4) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_TRUE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=client_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200 for all four endpoints
- **Expected Result**: ServiceAccount with Kessel permission accesses all reporting endpoints

**rbac-v2-TC004 - ServiceAccount with no permissions denied all reporting endpoints**

- **Description**: Verify that a ServiceAccount with no Kessel permissions is denied access to all reporting endpoints (E1, E2, E3, E4) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_FALSE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=client_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 403 for all four endpoints
- **Expected Result**: ServiceAccount with no Kessel permissions is denied all reporting endpoints

**rbac-v2-TC005a - Associate identity denied reporting endpoints**

- **Description**: Verify that an Associate identity is denied access to reporting endpoints (E1, E2, E3) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=Associate`, `associate.email`
  - No Kessel stub needed (Associate bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: Associate identity denied reporting endpoints

**rbac-v2-TC005b - Associate identity accesses billing account IDs**

- **Description**: Verify that an Associate identity can access the billing account IDs endpoint (E4) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=Associate`, `associate.email`
  - No Kessel stub needed (Associate bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**: `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200
- **Expected Result**: Associate identity accesses billing account IDs

**rbac-v2-TC006a - X509 identity denied reporting endpoints**

- **Description**: Verify that an X509/Turnpike identity is denied access to reporting endpoints (E1, E2, E3) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=X509`
  - No Kessel stub needed (X509 bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/products/{productId}` with the identity header
  - `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: X509 identity denied reporting endpoints

**rbac-v2-TC006b - X509 identity accesses billing account IDs**

- **Description**: Verify that an X509/Turnpike identity can access the billing account IDs endpoint (E4) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=X509`
  - No Kessel stub needed (X509 bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**: `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids` with the identity header
- **Verification**: HTTP 200
- **Expected Result**: X509 identity accesses billing account IDs

**rbac-v2-TC007 - Export with admin permission succeeds**

- **Description**: Verify that an export request with a User identity granted Kessel `subscriptions_report_view` relation completes successfully under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare export request event with `x-rh-identity` containing `type=User`, `org_id`, `user_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_TRUE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=user_id`
- **Action**: Publish export request to Kafka export topic
- **Verification**:
  - Export completes successfully
  - Export data is produced on the export response topic
- **Expected Result**: Kessel permission grants export access

**rbac-v2-TC008 - Export with no permissions denied**

- **Description**: Verify that an export request with a User identity with no Kessel permissions is denied under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare export request event with `x-rh-identity` containing `type=User`, `org_id`, `user_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_FALSE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=user_id`
- **Action**: Publish export request to Kafka export topic
- **Verification**:
  - Export is denied
  - No export data is produced
- **Expected Result**: No Kessel permissions results in export denial

### Opt-in

**rbac-v2-optin-TC001 - User with Kessel permission accesses all opt-in endpoints**

- **Description**: Verify that a User granted Kessel `subscriptions_report_view` relation can access all opt-in endpoints (E5, E6, E7) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_TRUE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=user_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 200 for all three endpoints
- **Expected Result**: Kessel permission grants access to all opt-in endpoints

**rbac-v2-optin-TC002 - User with no permissions denied all opt-in endpoints**

- **Description**: Verify that a User with no Kessel permissions is denied access to all opt-in endpoints (E5, E6, E7) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_FALSE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=user_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: No Kessel permissions results in denial for all opt-in endpoints

**rbac-v2-optin-TC003 - ServiceAccount with admin permission accesses all opt-in endpoints**

- **Description**: Verify that a ServiceAccount granted Kessel `subscriptions_report_view` relation can access all opt-in endpoints (E5, E6, E7) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_TRUE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=client_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 200 for all three endpoints
- **Expected Result**: ServiceAccount with Kessel permission accesses all opt-in endpoints

**rbac-v2-optin-TC004 - ServiceAccount with no permissions denied all opt-in endpoints**

- **Description**: Verify that a ServiceAccount with no Kessel permissions is denied access to all opt-in endpoints (E5, E6, E7) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=ServiceAccount`, `org_id`, `client_id`
  - Stub Kessel workspace endpoint to return default workspace
  - Stub Kessel check endpoint to return `ALLOWED_FALSE` for `relation=subscriptions_report_view`, `subject.resource.resourceId=client_id`
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `PUT /api/rhsm-subscriptions/v1/opt-in` with the identity header
  - `DELETE /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for all three endpoints
- **Expected Result**: ServiceAccount with no Kessel permissions is denied access to all opt-in endpoints

**rbac-v2-optin-TC005 - Associate identity denied opt-in endpoint**

- **Description**: Verify that an Associate identity is denied access to opt-in endpoint (E5) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=Associate`, `associate.email`
  - No Kessel stub needed (Associate bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for opt-in endpoint
- **Expected Result**: Associate identity denied opt-in endpoint

**rbac-v2-optin-TC006 - X509 identity denied opt-in endpoint**

- **Description**: Verify that an X509/Turnpike identity is denied access to opt-in endpoint (E5) under RBACv2.
- **Setup**:
  - Kessel Unleash flag is ON
  - Prepare `x-rh-identity` header with `type=X509`
  - No Kessel stub needed (X509 bypasses RBAC, gets `ROLE_INTERNAL`)
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/opt-in` with the identity header
- **Verification**: HTTP 403 for opt-in endpoint
- **Expected Result**: X509 identity denied opt-in endpoint


### Resilience

**rbac-v2-resilience-TC001 - Kessel unavailable falls back to denial**

- **Description**: Verify that when the Kessel service is unreachable and the flag is ON, the user is denied access (fail-closed behavior).
- **Setup**:
  - Prepare `x-rh-identity` header with `type=User`, `org_id`, `user_id`
  - Kessel Unleash flag is ON
  - Kessel endpoint is unreachable or returns error
- **Action**:
  - `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}` with the identity header
- **Verification**: HTTP 403
- **Expected Result**: Access denied when Kessel is unavailable (fail-closed)
