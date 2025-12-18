# Introduction

The **swatch-metrics-hbi** module is a service within the Subscription Watch platform that translates HBI host events into SWatch event messages that can later be used for consumption by the tally service.

This document outlines the test plan for swatch-metrics-hbi, including ingestion of all HBI event types.

**Purpose:** To ensure the swatch-metrics-hbi service is functional, reliable, and meets all defined requirements.

**Scope:**

* HBI event consumer/ingestion.
* HBI fact normalization.
* Host relationship tracking.

**Assumptions:**

* The swatch-metrics-hbi service is a stable and functional platform.
* HBI provides accurate changes in host event data.

**Constraints:**

* Testing is limited to the functionality of the swatch-metrics-hbi at a component level.
* End-to-end testing in ephemeral or stage environments is out of scope for this test plan.

# Test Strategy

This test plan focuses on covering the test scenarios for component-level tests utilizing the Java component test framework.

**Testing Strategy:**

Test cases should be testable locally and in deployed environments.

- Kafka messages can be injected for event-driven testing.
- External services' APIs can be mocked.
- System state can be verified through internal API calls.

# Test Cases

## Service and API Checks

**metrics-hbi-api-TC001 \- Service up and running**

- **Description**: Verify that the swatch-metrics-hbi service reports itself as running
- **Setup**:
    - Ensure the swatch-metrics-hbi component test environment is started and accessible
    - New `SwatchMetricsHbiRestService` is created in component test base class
- **Action**:
    - Invoke the internal health/running check via the component test `SwatchMetricsHbiRestService` API helper `isRunning`
- **Verification**:
    - Confirm that the health/running check returns true
- **Expected Result**:
    - Service is considered up and running and ready to process requests/events

**metrics-hbi-api-TC002 \- Flush outbox API succeeding**

- **Description**: Verify that the synchronous flush outbox API succeeds and runs in non-async mode.
- **Setup**:
    - Ensure the swatch-metrics-hbi component test environment is started and accessible
    - Outbox is ready to be flushed
- **Action**:
    - Call the synchronous outbox flush API via the component test `SwatchMetricsHbiRestService` API helper `flushOutboxSynchronously`
- **Verification**:
    - Confirm response body status is `StatusEnum.SUCCESS`
    - Verify response body async flag is false
- **Expected Result**:
    - Flush outbox API completes successfully and indicates a synchronous execution mode

**metrics-hbi-api-TC003 \- Unleash flag toggles correctly**

- **Description**: Verify that the Unleash feature flag for the service can be enabled and disabled correctly.
- **Setup**:
    - Ensure the swatch-metrics-hbi component test environment is started
    - New Unleash service is created and available in component test base class
- **Action**:
    - Enable the `EMIT_EVENTS` flag via Unleash
    - Check that the flag is reported as enabled
    - Disable the `EMIT_EVENTS` flag via Unleash
    - Check that the flag is reported as disabled
- **Verification**:
    - `isFlagEnabled(EMIT_EVENTS)` returns true after enabling the flag
    - `isFlagEnabled(EMIT_EVENTS)` returns false after disabling the flag
- **Expected Result**:
    - The Unleash feature flag `EMIT_EVENTS` can be toggled on and off reliably, and its state is reflected correctly in the service

## Create/Update HBI Event Ingestion

**metrics-hbi-create-update-TC001 \- Physical RHEL host event**

- **Description**: Verify that the service ingests HBI Create/Update events for a physical RHEL for x86 host and produces the corresponding SWatch event with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing a physical RHEL host
- **Action**:
    - Two different test runs for event types `created` and `updated`: Produce `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox record is created after the event is ingested
    - Message is consumed from the service instance ingress Kafka topic and captured in a SWatch event message
    - Verify SWatch event message exists and matches the expected values derived from the HBI event
- **Expected Result**:
    - Service successfully ingests both `created` and `updated` HBI events for physical RHEL host
    - Exactly one SWatch event is produced after being written to the outbox and published to the service instance ingress topic
    - SWatch event contains correct identifiers, product tags, and measurements for the physical host
    - Resulting SWatch event is consistent between `created` and `updated` besides for the event type

**metrics-hbi-create-update-TC002 \- Virtual RHEL unmapped guest host event from threads per core**

- **Description**: Verify that the service ingests HBI Create/Update events for a virtual RHEL for x86 unmapped guest host with a set threads per core value and produces the corresponding SWatch event with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing a virtual RHEL host with a threads per core value
- **Action**:
    - Two different test runs for event types `created` and `updated`: Produce `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox record is created after the event is ingested
    - Message is consumed from the service instance ingress Kafka topic and captured in a SWatch event message
    - Verify SWatch event message exists and matches the expected values derived from the HBI event
- **Expected Result**:
    - Service successfully ingests both `created` and `updated` HBI events for virtual RHEL unmapped guest host
    - Exactly one SWatch event is produced after being written to the outbox and published to the service instance ingress topic
    - SWatch event contains correct identifiers, product tags, and measurements for the virtual host based on threads per core value
    - Resulting SWatch event is consistent between `created` and `updated` besides for the event type

**metrics-hbi-create-update-TC003 \- Virtual RHEL unmapped guest host event from CPUs**

- **Description**: Verify that the service ingests HBI Create/Update events for a virtual RHEL for x86 unmapped guest host with a set CPUs value and produces the corresponding SWatch event with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing a virtual RHEL host with a CPUs value
- **Action**:
    - Two different test runs for event types `created` and `updated`: Produce `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox record is created after the event is ingested
    - Message is consumed from the service instance ingress Kafka topic and captured in a SWatch event message
    - Verify SWatch event message exists and matches the expected values derived from the HBI event
- **Expected Result**:
    - Service successfully ingests both `created` and `updated` HBI events for virtual RHEL unmapped guest host
    - Exactly one SWatch event is produced after being written to the outbox and published to the service instance ingress topic
    - SWatch event contains correct identifiers, product tags, and measurements for the virtual host based on CPUs value
    - Resulting SWatch event is consistent between `created` and `updated` besides for the event type

**metrics-hbi-create-update-TC004 \- Virtual ARM host event**

- **Description**: Verify that the service ingests HBI Create/Update events for a virtual RHEL for ARM host and produces the corresponding SWatch event with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing a virtual ARM host
- **Action**:
    - Two different test runs for event types `created` and `updated`: Produce `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox record is created after the event is ingested
    - Message is consumed from the service instance ingress Kafka topic and captured in a SWatch event message
    - Verify SWatch event message exists and matches the expected values derived from the HBI event
- **Expected Result**:
    - Service successfully ingests both `created` and `updated` HBI events for virtual ARM host
    - Exactly one SWatch event is produced after being written to the outbox and published to the service instance ingress topic
    - SWatch event contains correct identifiers, product tags, and measurements for the ARM host
    - Resulting SWatch event is consistent between `created` and `updated` besides for the event type

**metrics-hbi-create-update-TC005 \- Virtual cloud provider host event**

- **Description**: Verify that the service ingests HBI Create/Update events for a virtual cloud provider host and produces the corresponding SWatch event with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing a virtual cloud provider host
- **Action**:
    - Two different test runs for event types `created` and `updated`: Produce `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox record is created after the event is ingested
    - Message is consumed from the service instance ingress Kafka topic and captured in a SWatch event message
    - Verify SWatch event message exists and matches the expected values derived from the HBI event
- **Expected Result**:
    - Service successfully ingests both `created` and `updated` HBI events for virtual cloud provider host
    - Exactly one SWatch event is produced after being written to the outbox and published to the service instance ingress topic
    - SWatch event contains correct identifiers, product tags, and measurements for the cloud provider host
    - Resulting SWatch event is consistent between `created` and `updated` besides for the event type

**metrics-hbi-create-update-TC006 \- Physical hypervisor host transition once first guest is known**

- **Description**: Verify that the service transitions a physical RHEL host to a hypervisor when the first guest becomes known via HBI Create/Update events, and emits the correct Swatch events for both the physical host and the mapped guest.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing both a physical RHEL hypervisor host and a virtual guest host
- **Action**:
    - Two different test runs for event types `created` and `updated`:
        - Produce physical host `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
        - Produce virtual guest host `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox records are created after the events are ingested
    - Messages consumed from the service instance ingress Kafka topic and captured in SWatch event messages
    - Verify SWatch event messages exist and match the expected values derived from the HBI events
- **Expected Result**:
    - Service successfully ingests both `created` and `updated` HBI events for each of the hosts
    - Exactly three SWatch events are produced after being written to the outbox and published to the service instance ingress topic
        - First message for the physical host
        - Second message for the mapped guest host
        - Third message for the updated physical host to transition it to a hypervisor and showcase its relationship to its mapped guest
    - SWatch events contain the correct identifiers, product tags, and measurements for each host
    - Resulting SWatch events are consistent between `created` and `updated` besides for the event type

**metrics-hbi-create-update-TC007 \- Virtual unmapped guest to mapped guest transition**

- **Description**: Verify that the service transitions a virtual RHEL guest from unmapped to mapped when its hypervisor becomes known via HBI Create/Update events, and emits the correct Swatch events for the guest and hypervisor.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing both a virtual guest host and physical RHEL hypervisor host
- **Action**:
    - Two different test runs for event types `created` and `updated`:
        - Produce virtual guest host `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
        - Produce physical host `HbiHostCreateUpdateEvent` with event type to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox records are created after the events are ingested
    - Messages consumed from the service instance ingress Kafka topic and captured in SWatch event messages
    - Verify SWatch event messages exist and match the expected values derived from the HBI events
- **Expected Result**:
    - Service successfully ingests both `created` and `updated` HBI events for each of the hosts
    - Exactly three SWatch events are produced after being written to the outbox and published to the service instance ingress topic
        - First message for the unmapped guest host
        - Second message for the hypervisor host
        - Third message for the updated guest host to transition it to be mapped and showcase its relationship to its hypervisor
    - SWatch events contain the correct identifiers, product tags, and measurements for each host
    - Resulting SWatch events are consistent between `created` and `updated` besides for the event type

## Delete HBI Event Ingestion

**metrics-hbi-delete-TC001 \- Delete physical RHEL host event**

- **Description**: Verify that the service ingests HBI Delete event for a physical RHEL for x86 host and produces the corresponding SWatch event with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing an existing physical RHEL host
    - Prepare test host data representing a deleted event corresponding to the existing physical RHEL host
- **Action**:
    - Produce `HbiHostDeleteEvent` to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox record is created after the delete event is ingested
    - Message is consumed from the service instance ingress Kafka topic and captured in a SWatch event message
    - Verify SWatch event message exists and matches the expected values derived from the HBI delete event
- **Expected Result**:
    - Service successfully ingests `deleted` HBI event for physical RHEL host
    - Exactly one SWatch event is produced after being written to the outbox and published to the service instance ingress topic
    - SWatch event contains correct identifiers, product tags, and measurements for the physical host

**metrics-hbi-delete-TC002 \- Delete physical hypervisor host**

- **Description**: Verify that the service ingests HBI Delete event for an existing physical RHEL hypervisor host and produces the corresponding SWatch events to delete the hypervisor instance and update its mapped guest to an unmapped state with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing both an existing physical RHEL hypervisor host and virtual mapped guest host
    - Prepare test host data representing a deleted event corresponding to the existing hypervisor host
- **Action**:
    - Produce `HbiHostDeleteEvent` for hypervisor to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox records are created after the delete event is ingested
    - Messages are consumed from the service instance ingress Kafka topic and captured in SWatch event messages
    - Verify SWatch event messages exist and match the expected values derived from the HBI delete event
- **Expected Result**:
    - Service successfully ingests `deleted` HBI event for physical RHEL hypervisor host
    - Exactly two SWatch events are produced after being written to the outbox and published to the service instance ingress topic
        - First message with `INSTANCE_DELETED` type for the hypervisor
        - Second message with `INSTANCE_UPDATED` type for the guest, which should now be unmapped
    - SWatch events contain correct identifiers, product tags, and measurements for the hosts

**metrics-hbi-delete-TC003 \- Delete mapped virtual guest host**

- **Description**: Verify that the service ingests HBI Delete event for an existing virtual mapped guest host and produces the corresponding SWatch events to delete the guest instance and update the previous hypervisor host so it is no longer treated as a hypervisor, with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing both an existing physical RHEL hypervisor host and virtual mapped guest host
    - Prepare test host data representing a deleted event corresponding to the existing mapped guest host 
- **Action**:
    - Produce `HbiHostDeleteEvent` for mapped guest to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox records are created after the delete event is ingested
    - Messages are consumed from the service instance ingress Kafka topic and captured in SWatch event messages
    - Verify SWatch event messages exist and match the expected values derived from the HBI delete event
- **Expected Result**:
    - Service successfully ingests `deleted` HBI event for virtual mapped guest host
    - Exactly two SWatch events are produced after being written to the outbox and published to the service instance ingress topic
        - First message with `INSTANCE_DELETED` type for the guest
        - Second message with `INSTANCE_UPDATED` type for the previous hypervisor, which should now not be a hypervisor
    - SWatch events contain correct identifiers, product tags, and measurements for the hosts

**metrics-hbi-delete-TC004 \- Delete event when guest host has not been seen**

- **Description**: Verify that the service ingests HBI Delete event for an unseen guest host and produces the corresponding SWatch event with correct measurements.
- **Setup**:
    - Ensure `EMIT_EVENTS` feature flag is enabled
    - Kafka topics for HBI events and service instance ingress are available
    - Prepare test host data representing a deleted guest host previously unknown (not created or updated before)
- **Action**:
    - Produce `HbiHostDeleteEvent` to HBI event Kafka topic
    - Trigger outbox flush via internal API
- **Verification**:
    - Confirm outbox record is created after the delete event is ingested
    - Message is consumed from the service instance ingress Kafka topic and captured in a SWatch event message
    - Verify SWatch event message exists and matches the expected values derived from the HBI delete event
- **Expected Result**:
    - Service successfully ingests `deleted` HBI event for unseen guest host
    - Exactly one SWatch event is produced after being written to the outbox and published to the service instance ingress topic
    - SWatch event contains correct bare minimum host metadata
