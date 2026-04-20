# Introduction

The **swatch-utilization** module is a service within the Subscription Watch platform that monitors resource usage against purchased capacity thresholds and triggers proactive notifications to users when their consumption exceeds contracted limits.

The service processes pre-enriched utilization data from Kafka topics (containing usage from swatch-tally already combined with contract capacity by swatch-contracts), detects over-usage thresholds, and sends notification events via Kafka to the notifications-backend service for email delivery.

This document outlines the test plan for swatch-utilization, which involves overusage detection and notification triggering.

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

## Org Allowlist for Notifications

**utilization-allowlist-TC001 - Allowlisted org receives notification even when the global flag is disabled**

- **Description**: Verify that when the global send-notifications flag is disabled but the org is in the allowlist, notifications are still sent.
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

**utilization-allowlist-TC002 - Non-allowlisted org does not receive notification when the global flag is disabled**

- **Description**: Verify that when the global send-notifications flag is disabled and the org is NOT in the allowlist, no notification is sent.
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

**utilization-allowlist-TC003 - Empty allowlist does not affect behavior**

- **Description**: Verify that when the allowlist flag is enabled but the variant payload is empty, behavior falls back to the global flag.
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

