# Introduction

The **swatch-utilization** module is a service within the Subscription Watch platform that monitors resource usage against purchased capacity thresholds and triggers proactive notifications to users when their consumption exceeds contracted limits.

The service processes pre-enriched utilization data from Kafka topics (containing usage from swatch-tally already combined with contract capacity by swatch-contracts), detects threshold crossing, and sends notification events via Kafka to the notifications-backend service for email delivery. There are two independent notification types: over-usage (usage exceeds contracted capacity) and custom threshold (usage reaches an org-configured percentage). Both can fire for the same utilization event.

This document outlines the test plan for swatch-utilization, which involves threshold crossing detection and notification triggering.

**Purpose:** To ensure the swatch-utilization service is functional, reliable, and meets all defined requirements for capacity monitoring and notification delivery.

**Scope:**

* overusage Detection Logic
* Notification Triggering Rules
* Testing is limited to the functionality of the swatch-utilization service at a component level.

**Assumptions:**

* The swatch-utilization service is a stable and functional platform.
* The swatch-tally service provides accurate usage data.
* The swatch-contracts service provides accurate capacity data.
* The notifications-backend service is available for email delivery.

# Test Strategy

This test plan focuses on covering test scenarios across three levels: component-level tests for core business logic, integration tests for service coordination, and external dependencies tests for end-to-end validation.

**Testing Strategy:**

Test cases should be testable locally and in deployed environments.

Kafka messages can be injected for event-driven testing.
  - This implies that there is no need to create an actual organization and contract.
  - The `Setup` section is only about configuring the utilization summary message.

# Test Cases

## overusage Detection Logic

**utilization-overusage-TC001 - Process overusage**

- **Description**: Verify that overusage detection works.
- **Setup**:
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event contains correct information (org_id, product_id, metric_id and utilization_percentage)
    - Notification action `severity` is `IMPORTANT`
    - Record timestamp reflects current calculation time

**utilization-overusage-TC002 - No overusage when usage is below capacity**

- **Description**: Verify that no overusage notification message when usage is within purchased limits.
- **Setup**:
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate usage data less than the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-overusage-TC003 - No overusage when usage is above capacity but below threshold**

- **Description**: Verify that no overusage notification message when usage is above purchased limits but does not cross threshold.
- **Setup**:
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate usage data less than the threshold but more than capacity for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-overusage-TC004 - overusage persists after insufficient capacity increase**

- **Description**: Verify continued overusage detection after insufficient capacity purchase.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Current usage exceeds threshold
- **Action**:
    - Increase capacity by insufficient amount
    - Maintain usage at the same level
    - Trigger utilization calculation
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event still created (overusage persists)
    - Notification event contains correct information (org_id, product_id, metric_id and utilization_percentage)
    - Record timestamp reflects current calculation time

**utilization-overusage-TC005 - Usage above capacity but below threshold after capacity increase**

- **Description**: Verify overusage resolution when usage stays above capacity but capacity increase brings usage below threshold.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Current usage is above threshold
- **Action**:
    - Increase capacity enough to bring overusage below threshold (but usage still above 100% capacity)
    - Maintain usage at the same level
    - Trigger utilization calculation
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created (overusage below threshold despite usage > 100% capacity)

**utilization-overusage-TC006 - overusage resolved after sufficient capacity increase**

- **Description**: Verify overusage resolution when sufficient capacity is purchased to it above utilization.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Current usage is above threshold
- **Action**:
    - Increase capacity enough to bring overusage below 100% capacity
    - Trigger utilization calculation
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-overusage-TC007 - Usage exactly at threshold boundary**

- **Description**: Verify behavior when usage is exactly at the threshold.
- **Setup**:
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate usage data exactly at threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-overusage-TC008 - Capacity reduction below current usage**

- **Description**: Verify overusage detection when customer downgrades capacity while usage remains high.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Current usage is within capacity limits
- **Action**:
    - Reduce capacity to below current usage level
    - Maintain usage at the same level
    - Trigger utilization calculation process
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event created (usage now exceeds reduced capacity + threshold)
    - Notification event contains correct information (org_id, product_id, metric_id and utilization_percentage)
    - Record timestamp reflects current calculation time

**utilization-overusage-TC009 - Overusage notification severity**

- **Description**: Verify that overusage notifications declare severity `IMPORTANT`.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Conditions allow sending notifications (global flag or org allowlist as applicable)
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify `severity` on the notification action
- **Expected Result**:
    - Notification action `severity` is `IMPORTANT` (string name of the utilization severity enum)

## Multi-Resource Processing

**utilization-multi-TC001 - Multiple resources with mixed overusage states**

- **Description**: Verify overusage detection handles multiple resources correctly.
- **Setup**:
    - An organization has the following:
    - Product A has capacity for metric B
    - Product X has capacity for metric Y
- **Action**:
    - Generate usage data for both resources, making exceeding threshold for metric Y
    - Trigger utilization calculation
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - overusage record created only for product X
    - Notification event contains correct information (org_id, product_id, metric_id and utilization_percentage)
    - Record timestamp reflects current calculation time
    - No overusage record for product A

**utilization-multi-TC002 - Multiple metrics with mixed overusage states within same product**

- **Description**: Verify overusage detection handles multiple metrics correctly within the same product.
- **Setup**:
    - An organization has the following:
    - Product A has capacity for metric B
    - Product A has capacity for metric C
- **Action**:
    - Generate usage data for both metrics, making exceeding threshold for metric B
    - Trigger utilization calculation
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event created only for metric B of product A
    - Notification event contains correct information (org_id, product_id, metric_id and utilization_percentage)
    - Record timestamp reflects current calculation time
    - No notification event for metric C of product A

**utilization-multi-TC003 - Same product and metric with different service level and usage**

- **Description**: Verify that over-usage monitoring records separate counter increments per service level and usage when the same product and metric exceed the threshold under different SLA/usage combinations.
- **Setup**:
    - An organization has capacity for metric B of product A
    - Two utilization summaries for product A and metric B: one with Premium service level and Production usage, one with Standard service level and Development/Test usage
- **Action**:
    - Generate usage exceeding the threshold for metric B on both summaries
    - Trigger utilization calculation once per summary (separate utilization events)
- **Verification**:
    - Inspect the over-usage counter for metric B with SLA and usage labels
- **Expected Result**:
    - Counter increments by one for the Premium/Production label combination
    - Counter increments by one for the Standard/Development/Test label combination

## Capacity Handling

**utilization-capacity-TC001 - Zero capacity with positive usage**

- **Description**: Verify behavior when capacity is zero but usage is positive.
- **Setup**:
    - An organization has no capacity for the metric A of the product B
- **Action**:
    - Generate usage data for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
     - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-capacity-TC002 - Unlimited capacity metric**

- **Description**: Verify no notifications for unlimited capacity metrics.
- **Setup**:
    - An organization has unlimited capacity for the metric A of the product B
- **Action**:
    - Generate usage data for the metric A of the product B that would bring it in overusage
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created for unlimited capacity metrics

**utilization-capacity-TC003 - Contract without dimensions does not emit notification**

- **Description**: Verify no notification is emitted for contract without dimensions.  
- **Setup**:  
  - An organization has a contract without any dimensions (null capacity) for the metric A of the product B  
- **Action**:  
  - Generate usage data for the metric A of the product B  
  - Trigger utilization calculation process  
- **Verification**:  
  - Check absence of notification message on notifications topic  
- **Expected Result**:
  - No notification event created (contracts without dimensions have null capacity and over-usage check is skipped)  
  - Service handles null capacity data gracefully

**utilization-capacity-TC004 - Zero capacity with positive usage when service level and usage are set**

- **Description**: Verify that zero capacity still skips over-usage detection when the utilization summary includes specific service level and usage values (not the case where both dimensions are unset or `ANY` on the payload).
- **Setup**:
    - An organization has zero capacity for the metric A of the product B
    - The utilization summary sets specific service level and usage (so metric labels are not both `_ANY`)
- **Action**:
    - Generate positive usage data for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
    - Verify over-usage counter is unchanged for the series matching the summary’s service level and usage labels, and for the series where both `sla` and `usage` are `_ANY`
- **Expected Result**:
    - No notification event created
    - No increment on either counter series

## Notification feature flags (Unleash)

Additional cases can be added under the same `utilization-notifications-featureflags-TC0XX` prefix.

**utilization-notifications-featureflags-TC001 - Allowlisted org receives notification even when the global flag is disabled**

- **Description**: Verify that when the global send-notifications flag is disabled but the org is in the allowlist, notifications are still sent.
- **Feature flags covered**: `swatch.swatch-notifications.send-notifications` (disabled); `swatch.swatch-notifications.send-notifications-orgs-allowlist` (enabled; Unleash variant name `orgs`).
- **Setup**:
    - The global `swatch.swatch-notifications.send-notifications` flag is disabled
    - The `swatch.swatch-notifications.send-notifications-orgs-allowlist` flag is enabled with the org ID in its variant payload
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event contains correct information (org_id, product_id, metric_id and utilization_percentage)

**utilization-notifications-featureflags-TC002 - Non-allowlisted org does not receive notification when the global flag is disabled**

- **Description**: Verify that when the global send-notifications flag is disabled and the org is NOT in the allowlist, no notification is sent.
- **Feature flags covered**: `swatch.swatch-notifications.send-notifications` (disabled); `swatch.swatch-notifications.send-notifications-orgs-allowlist` (enabled; Unleash variant name `orgs`).
- **Setup**:
    - The global `swatch.swatch-notifications.send-notifications` flag is disabled
    - The `swatch.swatch-notifications.send-notifications-orgs-allowlist` flag is enabled with different org IDs in its variant payload
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check the absence of the notification message on the notifications topic
- **Expected Result**:
    - No notification event created

**utilization-notifications-featureflags-TC003 - Empty allowlist does not affect behavior**

- **Description**: Verify that when the allowlist flag is enabled but the variant payload is empty, behavior falls back to the global flag.
- **Feature flags covered**: `swatch.swatch-notifications.send-notifications` (disabled); `swatch.swatch-notifications.send-notifications-orgs-allowlist` (enabled; Unleash variant name `orgs`).
- **Setup**:
    - The global `swatch.swatch-notifications.send-notifications` flag is disabled
    - The `swatch.swatch-notifications.send-notifications-orgs-allowlist` flag is enabled with an empty variant payload
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-notifications-featureflags-TC004 - Denylisted event type suppressed when the global flag is enabled**

- **Description**: Verify that when `swatch.swatch-notifications.send-notifications` is enabled with the `send-notifications-config` variant and a JSON payload listing the over-usage notification event type in `event_types_denylist`, no notification is emitted for that event type (and the org is not on the org allowlist).
- **Feature flags covered**: `swatch.swatch-notifications.send-notifications` (enabled; Unleash variant name `send-notifications-config` with payload property `event_types_denylist`). `swatch.swatch-notifications.send-notifications-orgs-allowlist` is not used to bypass for the test org.
- **Setup**:
    - The global `swatch.swatch-notifications.send-notifications` flag is enabled
    - The toggle exposes variant `send-notifications-config` with payload `{"event_types_denylist":["exceeded-utilization-threshold"]}`
    - The org allowlist flag is not used to bypass suppression for the test org
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on the notifications topic
- **Expected Result**:
    - No notification event created

**utilization-notifications-featureflags-TC005 - Non-denylisted event types are not suppressed**

- **Description**: Verify that when the denylist contains other event types only, over-usage notifications are still sent.
- **Feature flags covered**: `swatch.swatch-notifications.send-notifications` (enabled; Unleash variant name `send-notifications-config` with payload property `event_types_denylist`).
- **Setup**:
    - The global `swatch.swatch-notifications.send-notifications` flag is enabled
    - Variant `send-notifications-config` with payload `{"event_types_denylist":["some-other-event-type"]}`
    - An organization has capacity for the metric A of the product B
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event contains correct information (org_id, product_id, metric_id and utilization_percentage)

**utilization-notifications-featureflags-TC006 - Allowlisted vs non-allowlisted orgs under the same denylist**

- **Description**: With the over-usage event type on the denylist and the org allowlist containing a **single** org ID, verify the contrast: that allowlisted org still receives a notification, and a **second** org (not in the allowlist) does not, under the same denylist configuration.
- **Feature flags covered**: `swatch.swatch-notifications.send-notifications` (enabled; Unleash variant name `send-notifications-config` with payload property `event_types_denylist`); `swatch.swatch-notifications.send-notifications-orgs-allowlist` (enabled; Unleash variant name `orgs`).
- **Setup**:
    - The global `swatch.swatch-notifications.send-notifications` flag is enabled
    - Variant `send-notifications-config` denies `exceeded-utilization-threshold`
    - The org allowlist has exactly one org ID in the `orgs` variant (the org for the “in allowlist” path)
    - A different org ID is not on the allowlist
- **Action**:
    - For the allowlisted org: exceed threshold and trigger utilization
    - For the non-allowlisted org: exceed threshold and trigger utilization (separate utilization event)
- **Verification**:
    - Allowlisted org: wait for a notification and verify the payload
    - Non-allowlisted org: confirm the notification is not sent (e.g. service log) with the same denylist active
- **Expected Result**:
    - Allowlisted org: notification event with correct (org_id, product_id, metric_id, utilization_percentage)
    - Non-allowlisted org: no notification event

## Organization Preferences API

**org-preferences-TC001 - Retrieve default threshold for organization without custom preferences**

- **Description**: Verify that GET request returns system default threshold when org has not configured custom preferences.
- **Setup**:
    - An organization has not configured any custom preferences
- **Action**:
    - Call GET /api/rhsm-subscriptions/v1/utilization/org-preferences with valid x-rh-identity header
- **Verification**:
    - Verify response status code is 200
    - Verify response contains custom_threshold field
    - Verify response last_modified is absent (field omitted; org has no persisted preferences)
- **Expected Result**:
    - HTTP 200 response
    - Response contains custom_threshold matching system default (ORG_PREFERENCE_DEFAULT_THRESHOLD config property)
    - last_modified is absent (omitted from response body)

**org-preferences-TC002 - Update organization threshold preferences**

- **Description**: Verify that POST request successfully persists custom threshold for organization.
- **Setup**:
    - An organization has not configured any custom preferences
- **Action**:
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences with valid request body containing custom_threshold value
- **Verification**:
    - Verify response status code is 200
    - Verify response contains the persisted custom_threshold value
    - Verify response contains last_modified (UTC timestamp of persistence)
- **Expected Result**:
    - HTTP 200 response
    - Response custom_threshold matches the value from request body
    - Response last_modified is present and matches the value returned by subsequent GET

**org-preferences-TC003 - Retrieve custom threshold after update**

- **Description**: Verify that GET request returns custom threshold after organization has configured preferences.
- **Setup**:
    - An organization has configured custom threshold via POST request
- **Action**:
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences to set custom threshold
    - Call GET /api/rhsm-subscriptions/v1/utilization/org-preferences to retrieve preferences
- **Verification**:
    - Verify GET response status code is 200
    - Verify GET response contains the previously configured custom_threshold
    - Verify GET response contains last_modified
- **Expected Result**:
    - HTTP 200 response
    - Response custom_threshold matches the value set in previous POST request (not the system default)
    - Response last_modified is present

**org-preferences-TC004 - Update existing custom threshold**

- **Description**: Verify that POST request can update previously configured threshold.
- **Setup**:
    - An organization has already configured custom threshold
- **Action**:
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences with initial custom_threshold value
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences again with different custom_threshold value
    - Call GET /api/rhsm-subscriptions/v1/utilization/org-preferences to verify
- **Verification**:
    - Verify final GET response returns the updated threshold value
    - Verify final GET response contains last_modified reflecting the latest update
- **Expected Result**:
    - HTTP 200 response
    - Response custom_threshold matches the second POST request value (not the first)
    - Response last_modified is present

**org-preferences-TC005 - Reject invalid threshold values**

- **Description**: Verify that POST request rejects threshold values outside valid range (0-100).
- **Setup**:
    - An organization attempts to configure invalid threshold
- **Action**:
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences with custom_threshold value outside 0-100 range (e.g., -5 or 150)
- **Verification**:
    - Verify response status code is 400 Bad Request
- **Expected Result**:
    - HTTP 400 Bad Request response for threshold values < 0 or > 100

**org-preferences-TC006 - Threshold value boundary validation**

- **Description**: Verify that POST request accepts threshold values at boundaries (0 and 100).
- **Setup**:
    - An organization configures threshold at boundary values
- **Action**:
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences with custom_threshold = 0
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences with custom_threshold = 100
- **Verification**:
    - Verify both requests return 200
    - Verify responses contain the boundary values
- **Expected Result**:
    - HTTP 200 response for both 0 and 100 threshold values
    - Response custom_threshold matches request value
    - Response last_modified is present for each successful POST

**org-preferences-TC007 - Org admin can update custom threshold**

- **Description**: Verify that an org admin (is_org_admin=true in x-rh-identity) can successfully update the custom threshold.
- **Setup**:
    - An organization has not configured any custom preferences
- **Action**:
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences with User-type identity where is_org_admin=true
- **Verification**:
    - Verify response status code is 200
    - Verify response contains the persisted custom_threshold value
- **Expected Result**:
    - HTTP 200 response
    - Custom threshold is updated successfully

**org-preferences-TC008 - Non-admin user cannot update custom threshold**

- **Description**: Verify that a regular user (is_org_admin=false in x-rh-identity) is rejected when attempting to update the custom threshold.
- **Setup**:
    - An organization has not configured any custom preferences
- **Action**:
    - Call POST /api/rhsm-subscriptions/v1/utilization/org-preferences with User-type identity where is_org_admin=false
- **Verification**:
    - Verify response status code is 403 Forbidden
- **Expected Result**:
    - HTTP 403 Forbidden
    - Threshold is not updated

**org-preferences-TC009 - Org admin from another organization cannot tamper with a different org's preferences**

- **Description**: Verify that an admin from org A, when using their own x-rh-identity (org_id=A), can only update org A's preferences, not org B's. The endpoint is inherently safe from cross-org tampering because org_id is always extracted from the authenticated identity.
- **Setup**:
    - Org B has a custom threshold configured
- **Action**:
    - Org A admin calls POST /api/rhsm-subscriptions/v1/utilization/org-preferences using their own identity (org_id=A) with a different threshold value
- **Verification**:
    - Verify that org B's threshold is unchanged
    - Verify that org A's threshold is updated
- **Expected Result**:
    - Org B's preferences remain unchanged (org A's identity cannot target org B)
    - Org A's preferences are updated

## Custom Usage Threshold Notification

**custom-threshold-TC001 - Detect custom threshold exceeded and send notification**

- **Description**: Verify that a notification is sent when utilization exceeds the org custom threshold.
- **Setup**:
    - The organization has a custom threshold configured via the org preferences API
    - A utilization summary is produced with usage above the custom threshold
- **Action**:
    - Send the utilization summary to the utilization topic
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event contains correct information (org_id, product_id, metric_id, utilization_percentage and preferences_hash)
    - Notification action `severity` is `MODERATE`

**custom-threshold-TC002 - Notification sent when utilization is exactly at custom threshold**

- **Description**: Verify that a notification is sent when utilization is exactly at the org custom threshold boundary.
- **Setup**:
    - The organization has a custom threshold configured via the org preferences API
    - A utilization summary is produced with usage exactly at the custom threshold
- **Action**:
    - Send the utilization summary to the utilization topic
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event contains correct information (org_id, product_id, metric_id, utilization_percentage and preferences_hash)
    - Notification action `severity` is `MODERATE`
    - Notification is sent (threshold comparison uses greater-than-or-equal)

**custom-threshold-TC003 - Notification sent using default 80% threshold when no org preference exists**

- **Description**: Verify that a custom threshold notification is sent using the default 80% threshold when the organization has not configured a custom threshold.
- **Setup**:
    - The organization has not configured a custom threshold
    - A utilization summary is produced with usage above 80%
- **Action**:
    - Send the utilization summary to the utilization topic
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event is created using the default 80% threshold
    - Notification event contains correct information (org_id, product_id, metric_id, utilization_percentage)
    - Notification action `severity` is `MODERATE`
    - `preferences_hash` is absent from notification context (no persisted preference)

**custom-threshold-TC004 - No notification when utilization is below custom threshold**

- **Description**: Verify no notification is sent when utilization is below the configured threshold.
- **Setup**:
    - The organization has a custom threshold configured via the org preferences API
    - A utilization summary is produced with usage below the custom threshold
- **Action**:
    - Send the utilization summary to the utilization topic
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**custom-threshold-TC005 - Both notifications sent independently when usage exceeds both thresholds**

- **Description**: Verify that when usage exceeds both the custom threshold and the over-usage threshold, both notifications are sent independently.
- **Setup**:
    - The organization has a custom threshold configured via the org preferences API
    - A utilization summary is produced with usage exceeding both the custom threshold and the over-usage threshold
- **Action**:
    - Send the utilization summary to the utilization topic
- **Verification**:
    - Wait for notification messages on notifications topic
    - Verify notification payloads
- **Expected Result**:
    - Both notifications are sent. The two notification types are independent (neither suppresses the other)
    - One custom threshold notification with `severity` `MODERATE`
    - One over-usage notification with `severity` `IMPORTANT`

**custom-threshold-TC006 - No notification when utilization is below default threshold and no org preference exists**

- **Description**: Verify that no custom threshold notification is sent when the organization has not configured a custom threshold and usage is below the default 80% threshold.
- **Setup**:
    - The organization has not configured a custom threshold
    - A utilization summary is produced with usage below 80%
- **Action**:
    - Send the utilization summary to the utilization topic
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

## Customer API access control

**org-preferences-auth-TC001 - User with wildcard subscriptions permission can read org preferences**
- **Description**: Verify that a User with `subscriptions:*:*` receives HTTP 200 from GET org-preferences under both authorization models.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - **RBAC**: stub RBAC to grant `subscriptions:*:*`
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test user_id / `subscriptions_report_view` relation; stub workspace resolution
- **Action**:
  - GET E1 with the identity header
- **Verification**:
  - HTTP 200 under both models
- **Expected Result**:
  - Request authorized; identical 200 under RBACv1 and RBACv2

**org-preferences-auth-TC002 - User with reader permission can read org preferences**
- **Description**: Verify that a User with `subscriptions:reports:read` receives HTTP 200 from GET org-preferences under both authorization models. Both RBAC permission strings (`subscriptions:*:*` and `subscriptions:reports:read`) map to the same Kessel relation `subscriptions_report_view`; this case confirms the reader path specifically. The Kessel stub is intentionally identical to TC001 — the differentiation is exclusively in the RBAC permission string.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - **RBAC**: stub RBAC to grant `subscriptions:reports:read`
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the test user_id / `subscriptions_report_view` relation; stub workspace resolution
- **Action**:
  - GET E1 with the identity header
- **Verification**:
  - HTTP 200 under both models
- **Expected Result**:
  - Reader-level permission sufficient for GET; identical 200 under RBACv1 and RBACv2

**org-preferences-auth-TC003 - User without subscriptions permission is denied GET**
- **Description**: Verify that a User with no subscriptions permissions receives HTTP 403 from GET org-preferences under both authorization models.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - **RBAC**: stub RBAC to return no subscriptions permissions
  - **KESSEL**: stub Kessel Check to return `ALLOWED_FALSE`; stub workspace resolution
- **Action**:
  - GET E1 with the identity header
- **Verification**:
  - HTTP 403 under both models
- **Expected Result**:
  - Request denied; identical 403 under RBACv1 and RBACv2

**org-preferences-auth-TC004 - User with subscriptions permission but not org_admin is denied POST**
- **Description**: Verify that a User granted subscriptions access but with `is_org_admin=false` is denied POST org-preferences under both authorization models. POST requires `org_admin`; `customer` is not sufficient.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - **RBAC**: stub RBAC to grant `subscriptions:*:*`
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE`; stub workspace resolution
- **Action**:
  - POST E2 with the identity header
- **Verification**:
  - HTTP 403 under both models
- **Expected Result**:
  - `customer` role not sufficient for POST; identical 403 under RBACv1 and RBACv2

**org-preferences-auth-TC005 - org_admin without subscriptions permission: POST allowed, GET denied**
- **Description**: Verify the `org_admin`/`customer` role boundary in one scenario. A User with `is_org_admin=true` but no subscriptions permissions can POST (`org_admin` is identity-derived) but cannot GET (`org_admin` does not grant `customer`). RBAC and Kessel return no permissions; the access difference comes from the identity header alone.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=true
  - **RBAC**: stub RBAC to return no subscriptions permissions
  - **KESSEL**: stub Kessel Check to return `ALLOWED_FALSE`; stub workspace resolution
- **Action**:
  - POST E2 with the identity header
  - GET E1 with the identity header
- **Verification**:
  - HTTP 200 for POST (org_admin grants write)
  - HTTP 403 for GET (org_admin does not grant read)
- **Expected Result**:
  - Role boundaries correctly enforced; identical behavior under RBACv1 and RBACv2

**org-preferences-auth-TC006 - Service role can access both endpoints**
- **Description**: Verify that PSK-authenticated service calls receive HTTP 200 from both endpoints. The `service` role is granted by the PSK mechanism and bypasses RBAC and Kessel entirely.
- **Setup**:
  - Authenticate via `x-rh-swatch-psk` with valid PSK; supply x-rh-identity for org_id resolution
  - No RBAC or Kessel stub required
- **Action**:
  - GET E1 with PSK auth
  - POST E2 with PSK auth
- **Verification**:
  - HTTP 200 for both
- **Expected Result**:
  - `service` role bypasses both authorization backends; both endpoints accessible

**org-preferences-auth-TC007 - ServiceAccount with subscriptions permission can read org preferences**
- **Description**: Verify that a ServiceAccount identity granted subscriptions access receives HTTP 200 from GET org-preferences under both authorization models. Kessel resolves the ServiceAccount principal ID from `client_id`, not `user_id` — this tests that code path specifically.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, org_id, client_id
  - **RBAC**: stub RBAC to grant `subscriptions:*:*` for the ServiceAccount
  - **KESSEL**: stub Kessel Check to return `ALLOWED_TRUE` for the client_id principal / `subscriptions_report_view` relation; stub workspace resolution
- **Action**:
  - GET E1 with the identity header
- **Verification**:
  - HTTP 200 under both models
- **Expected Result**:
  - ServiceAccount principal ID resolved correctly under RBACv1 and RBACv2; identical 200

**org-preferences-auth-TC008 - ServiceAccount without subscriptions permission is denied**
- **Description**: Verify that a ServiceAccount with no subscriptions permissions is denied access to both endpoints under both authorization models. ServiceAccount identity cannot carry the `org_admin` role (it is only derivable from `is_org_admin=true` in a User identity header); POST denial is structural, not permission-based.
- **Setup**:
  - Prepare x-rh-identity with type=ServiceAccount, org_id, client_id
  - **RBAC**: stub RBAC to return no subscriptions permissions
  - **KESSEL**: stub Kessel Check to return `ALLOWED_FALSE` for the client_id principal; stub workspace resolution
- **Action**:
  - GET E1 and POST E2 with the identity header
- **Verification**:
  - HTTP 403 for both endpoints under both models
- **Expected Result**:
  - ServiceAccount denial identical under RBACv1 and RBACv2

**org-preferences-auth-TC009 - Kessel unavailable falls back to denial**
- **Description**: Verify fail-closed behavior: when the Kessel service is unreachable and the Unleash flag is ON, access is denied rather than granted. KESSEL model only.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - Unleash flag ON
  - Stub workspace resolution to succeed (`GET /api/rbac/v2/workspaces/?type=default` returns valid workspace)
  - Do not stub (or stub to return gRPC error for) the Kessel Check endpoint
- **Action**:
  - GET E1 with the identity header
- **Verification**:
  - HTTP 403
- **Expected Result**:
  - Access denied when Kessel is unavailable (fail-closed)

**org-preferences-auth-TC010 - Associate identity is denied both endpoints**
- **Description**: Verify that a Red Hat Associate identity is denied access to both endpoints. Associates receive only the `support` role via `RolesAugmentor`; neither RBAC nor Kessel is invoked for them. `support` is not in the allowed roles for either endpoint.
- **Setup**:
  - Not parameterized — run once; Unleash flag state does not affect the result
  - Prepare x-rh-identity with type=Associate, associate.email
  - No RBAC or Kessel stub required (augmentors are not invoked for Associate identities)
- **Action**:
  - GET E1 with the identity header
  - POST E2 with the identity header
- **Verification**:
  - HTTP 403 for both endpoints
- **Expected Result**:
  - Associate identity denied regardless of Unleash flag state; RBAC and Kessel are not called

**org-preferences-auth-TC011 - RBAC unavailable falls back to denial**
- **Description**: Verify fail-closed behavior for the RBAC code path: when the RBAC service is unreachable and the Unleash flag is OFF, `RbacRolesAugmentor` catches the exception, returns the identity without the `customer` role, and access is denied. RBAC model only.
- **Setup**:
  - Prepare x-rh-identity with type=User, org_id, user_id, is_org_admin=false
  - Unleash flag OFF
  - Do not stub (or stub to return an error for) the RBAC access endpoint
- **Action**:
  - GET E1 with the identity header
- **Verification**:
  - HTTP 403
- **Expected Result**:
  - Access denied when RBAC is unavailable (fail-closed); symmetric behavior to TC009
