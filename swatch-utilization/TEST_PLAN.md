# Introduction

The **swatch-utilization** module is a service within the Subscription Watch platform that monitors resource usage against purchased capacity thresholds and triggers proactive notifications to users when their consumption exceeds contracted limits.

The service processes pre-enriched utilization data from Kafka topics (containing usage from swatch-tally already combined with contract capacity by swatch-contracts), detects over-usage thresholds, and sends notification events via Kafka to the notifications-backend service for email delivery.

This document outlines the test plan for swatch-utilization, which involves overage detection and notification triggering.

**Purpose:** To ensure the swatch-utilization service is functional, reliable, and meets all defined requirements for capacity monitoring and notification delivery.

**Scope:**

* Overage Detection Logic
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

- Kafka messages can be injected for event-driven testing.
- External services' APIs can be mocked.
- System state can be verified through internal API calls.

# Test Cases

## Overage Detection Logic

**utilization-overage-TC001 \- Process overage**

- **Description**: Verify that overage detection works.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - The utilization threshold of product is set to C for that product
- **Action**:
    - Generate enough usage to exceed the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event contains correct information (org\_id, product\_id, metric\_id and utilization percentage)
    - Record timestamp reflects current calculation time

**utilization-overage-TC002 \- No overage when usage is below capacity**

- **Description**: Verify that no overage notification message when usage is within purchased limits.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - The utilization threshold of product is set to C for that product
- **Action**:
    - Generate usage data less than the threshold for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-overage-TC003 \- No overage when usage is above capacity but below threshold**

- **Description**: Verify that no overage notification message when usage is above purchased limits but does not cross threshold.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - The utilization threshold of product is set to C for that product
- **Action**:
    - Generate usage data less than the threshold but more than capacity for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-overage-TC004 \- Overage persists after insufficient capacity increase**

- **Description**: Verify continued overage detection after insufficient capacity purchase.
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
    - Notification event still created (overage persists)
    - Notification event contains correct information (org\_id, product\_id, metric\_id and utilization percentage)
    - Record timestamp reflects current calculation time

**utilization-overage-TC005 \- Usage above capacity but below threshold after capacity increase**

- **Description**: Verify overage resolution when usage stays above capacity but capacity increase brings usage below threshold.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Current usage is above threshold
- **Action**:
    - Increase capacity enough to bring overage below threshold (but usage still above 100% capacity)
    - Maintain usage at the same level
    - Trigger utilization calculation
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created (overage below threshold despite usage \> 100% capacity)

**utilization-overage-TC006 \- Overage resolved after sufficient capacity increase**

- **Description**: Verify overage resolution when sufficient capacity is purchased to it above utilization.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Current usage is above threshold
- **Action**:
    - Increase capacity enough to bring overage below 100% capacity
    - Trigger utilization calculation
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created

**utilization-overage-TC007 \- Usage exactly at threshold boundary**

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

**utilization-overage-TC008 \- Capacity reduction below current usage**

- **Description**: Verify overage detection when customer downgrades capacity while usage remains high.
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
    - Notification event created (usage now exceeds reduced capacity \+ threshold)
    - Notification event contains correct information (org\_id, product\_id, metric\_id and utilization percentage)
    - Record timestamp reflects current calculation time

## Multi-Resource Processing

**utilization-multi-TC001 \- Multiple resources with mixed overage states**

- **Description**: Verify overage detection handles multiple resources correctly.
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
    - Overage record created only for product X
    - Notification event contains correct information (org\_id, product\_id, metric\_id and utilization percentage)
    - Record timestamp reflects current calculation time
    - No overage record for product A

**utilization-multi-TC002 \- Multiple metrics with mixed overage states within same product**

- **Description**: Verify overage detection handles multiple metrics correctly within the same product.
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
    - Notification event contains correct information (org\_id, product\_id, metric\_id and utilization percentage)
    - Record timestamp reflects current calculation time
    - No notification event for metric C of product A

## Capacity Handling

**utilization-capacity-TC001 \- Zero capacity with positive usage**

- **Description**: Verify behavior when capacity is zero but usage is positive.
- **Setup**:
    - An organization has no capacity for the metric A of the product B
- **Action**:
    - Generate usage data for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Wait for notification message on notifications topic
    - Verify notification payload
- **Expected Result**:
    - Notification event created (any usage above zero capacity triggers notification)
    - Notification event contains correct information (org\_id, product\_id, metric\_id and utilization percentage)
    - Record timestamp reflects current calculation time

**utilization-capacity-TC002 \- Unlimited capacity metric**

- **Description**: Verify no notifications for unlimited capacity metrics.
- **Setup**:
    - An organization has unlimited capacity for the metric A of the product B
- **Action**:
    - Generate usage data for the metric A of the product B that would bring it in overage
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

## Configuration

**utilization-config-TC001 \- Negative threshold disables detection**

- **Description**: Verify products with negative thresholds never trigger notifications regardless of usage.
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - The utilization threshold is set to a negative value for that metric
- **Action**:
    - Generate enough usage for the metric A of the product B to trigger an overage with default threshold
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created for negative threshold products

**utilization-config-TC002 \- Custom product thresholds**

- **Description**: Verify products with custom threshold values work correctly (different from 5% default).
- **Setup**:
    - An organization has capacity for the metric A of the product B
    - Product B has a custom threshold configured (e.g., 10% instead of default 5%)
- **Action**:
    - Generate usage data greater than 105% but less than (100+custom threshold)% capacity for the metric A of the product B
    - Trigger utilization calculation process
- **Verification**:
    - Check absence of notification message on notifications topic
- **Expected Result**:
    - No notification event created (less than 100 \+ custom threshold %)
    - Custom threshold takes precedence over default 5%
