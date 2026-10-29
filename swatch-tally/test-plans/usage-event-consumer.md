# Usage Event Consumer

**Functional Area:** Kafka usage event consumption and message processing

This test plan covers the consumption and processing of usage events from Kafka topics:
- Messages IN from Kafka usage topics → Parse/Validate → Process Events → Log verification

**Related Components:**
- Kafka message consumption
- UsageEvent deserialization
- Event logging and verification
- Batch message processing

---

# Test Cases

## Usage Event Consumption

**usage-consumer-TC001 - Single usage event consumption from Kafka**

Test Method: `UsageEventConsumerComponentTest::shouldConsumeUsageEventFromKafka()`

- **Description**: Verify that a single usage event can be successfully consumed from Kafka and processed by the service
- **Setup**:
    - swatch-tally service is running
    - Kafka bridge subscribed to USAGE topic
    - Test organization (test-org-123) is ready to receive events
- **Action**:
    - Create a UsageEvent with:
        - orgId: "test-org-123"
        - serviceType: "RHEL System"
        - instanceId: "instance-001"
        - timestamp: current time
        - measurement: Cores = 4.0
    - Publish UsageEvent to USAGE Kafka topic via kafkaBridge
- **Verification**:
    - Service logs contain "Received" and "usage event(s) from kafka"
    - Service logs contain "Processing usage event for org_id: test-org-123"
    - Event is properly deserialized and processed
- **Expected Result**:
    - Service successfully consumes single usage event from Kafka
    - Event is logged with correct organization ID
    - No errors or exceptions during processing

**usage-consumer-TC002 - Multiple usage events consumption from Kafka**

Test Method: `UsageEventConsumerComponentTest::shouldConsumeMultipleUsageEvents()`

- **Description**: Verify that multiple usage events for the same organization can be consumed in sequence and each is processed correctly
- **Setup**:
    - swatch-tally service is running
    - Kafka bridge subscribed to USAGE topic
    - Test organization (test-org-456) is ready to receive events
    - Multiple events with different instance IDs and timestamps prepared
- **Action**:
    - Create 3 UsageEvents with:
        - Same orgId ("test-org-456") but different serviceTypes and instanceIds
        - Progressive timestamps (offset by hours: now, now+1h, now+2h)
        - serviceType: "OpenShift Cluster"
        - instanceIds: "cluster-0", "cluster-1", "cluster-2"
        - measurements: Cores = 8.0, 9.0, 10.0 respectively
    - Publish all 3 UsageEvents to USAGE Kafka topic via kafkaBridge in sequence
- **Verification**:
    - Service logs contain "Received" and "usage event(s) from kafka" indicating batch processing
    - Service logs contain "Processing usage event for org_id: test-org-456" for each event
    - All 3 events are individually processed and logged
    - Event sequence is maintained with correct measurement values
- **Expected Result**:
    - Service successfully consumes and processes multiple usage events
    - Batch processing works correctly for variable-sized message sets
    - Each event is individually logged with its associated data
    - Service handles variable measurement values and timestamps appropriately
