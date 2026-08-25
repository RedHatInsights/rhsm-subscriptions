# Introduction

The **swatch-metrics** module is a service within the Subscription Watch platform that collects usage metrics from Prometheus for Red Hat products deployed in cloud marketplace environments and converts them into metering events for billing purposes.

The service queries Prometheus for usage data (such as vCPUs), enriches the metrics with subscription metadata (organization, billing provider, product identifiers), and publishes metering events to Kafka for downstream consumption by billing systems.

This document outlines the test plan for swatch-metrics, which involves Prometheus-based metering for RHEL PAYG products.

**Purpose:** To ensure the swatch-metrics service is functional, reliable, and meets all defined requirements for Prometheus metric collection and metering event generation.

**Scope:**

* Prometheus metric import and querying
* Internal metering API
* Metering event production to Kafka
* RHEL PAYG addon product metering

**Assumptions:**

* The swatch-metrics service is a stable and functional platform.
* Prometheus provides accurate metric data.
* Kafka is available for event delivery.

# Test Strategy

This test plan focuses on covering test scenarios for component-level tests, utilizing the Java component test framework.

**Testing Strategy:**

Test cases should be testable locally and in ephemeral environments.

- Prometheus metrics can be imported via the /import endpoint for event-driven testing.
- The service's internal metering API can be triggered directly.
- System state can be verified through Kafka message consumption.

# Test Cases

## Prometheus-Based Metering

**swatch-metrics-prometheus-TC001 - Process RHEL PAYG addon metrics from Prometheus**

- **Description**: Verify swatch-metrics translates Prometheus metrics to swatch-events for RHEL PAYG products.
- **Setup**:
  - Prometheus and Kafka services running
  - Prepare RHEL PAYG addon metrics with vCPU data for AWS billing provider
  - Import metrics to Prometheus
- **Action**:
  - Trigger internal metering API
- **Verification**:
  - Wait for metering events on Kafka topic
  - Verify event contains correct metadata and measurements
- **Expected Result**:
  - HTTP 204 response from metering API
  - Events produced with accurate billing information, product data, and vCPU measurements
