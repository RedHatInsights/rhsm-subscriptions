# Performance Testing

This document explains how to request a performance test for SWATCH services.

## How to Request a Performance Test

To request a performance test, you need to create a JIRA ticket under the **HCEPERF** project:

[https://issues.redhat.com/browse/HCEPERF](https://issues.redhat.com/browse/HCEPERF)

## What to Include in the Ticket

When creating the ticket, make sure to provide the following information:

### 1. Test Objective
Clearly describe what you want to test and why. For example:
- Which services or integrations are involved
- What specific scenarios need to be validated
- What metrics you want to measure (latency, throughput, error rates, etc.)

### 2. Expected Volume
Provide the minimum expected volume that the system should support. Be specific about:
- Number of messages/events per time unit (per second, per minute, per hour, per day)
- Peak vs sustained load expectations
- Any relevant growth projections

**Example:**
> - Daily Snapshots: ~75,000 messages per day
> - Hourly Snapshots: ~18,000 messages per hour

### 3. Success Criteria
Define what constitutes a successful test:
- Maximum acceptable latency
- Minimum throughput requirements
- Error rate thresholds

## Example Ticket

**Title:** Performance testing for SWATCH tally-contracts-utilization integration

**Description:**
We need to validate that the integration between SWATCH tally, SWATCH contracts, and SWATCH utilization can handle production-scale message volumes.

**Objective:** Measure end-to-end latency from event generation to notification delivery under load.

**Expected Volume:**
- Daily Snapshots: ~75,000 messages per day
- Hourly Snapshots: ~18,000 messages per hour

**Test Approach:**
1. Generate N events that trigger customer over-usage scenarios
2. Trigger the tally process for these events
3. Measure time from event generation to notification message delivery
4. Identify bottlenecks and maximum sustainable throughput

