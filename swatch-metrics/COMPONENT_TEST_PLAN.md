# Introduction

The **swatch-metrics** module is a service within the Subscription Watch platform that collects usage metrics from Prometheus for Red Hat products deployed in cloud marketplace environments and converts them into metering events for billing purposes.

The service queries Prometheus for usage data (such as vCPUs), enriches the metrics with subscription metadata (organization, billing provider, product identifiers), and publishes metering events to Kafka for downstream consumption by billing systems.

This document outlines the test plan for swatch-metrics, which involves Prometheus-based metering for RHEL PAYG and ACM products.

**Purpose:** To ensure the swatch-metrics service is functional, reliable, and meets all defined requirements for Prometheus metric collection and metering event generation.

**Scope:**

* Prometheus metric import and querying
* Internal metering API
* Metering event production to Kafka
* RHEL PAYG addon product metering
* ACM (Advanced Cluster Management) managed and self-managed worker cores metering

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

- **Description**: Verify swatch-metrics translates `system_cpu_logical_count` Prometheus metrics to metering events for RHEL PAYG addon products with AWS billing.
- **Setup**:
  - Mock Prometheus returns `system_cpu_logical_count` metric with value 4.0
  - Labels include: `product=204`, `billing_model=marketplace`, `support=Premium`, `usage=Production`
  - Instance ID, org ID, AWS billing provider and account ID are randomly generated
- **Action**:
  - Trigger internal metering API for `rhel-for-x86-els-payg-addon` product
- **Verification**:
  - Wait for metering event on `service-instance-ingress` Kafka topic
  - Verify event metadata: org ID matches, instance ID matches
  - Verify billing provider is AWS and billing account ID matches
  - Verify product tag contains `rhel-for-x86-els-payg-addon`
  - Verify measurement contains metric ID `vCPUs` with value 4.0
- **Expected Result**:
  - HTTP 204 response from metering API
  - Event produced with vCPUs metric value of 4.0, AWS billing provider, and RHEL PAYG product tag

**swatch-metrics-prometheus-TC002 - Process ACM managed worker cores metrics from Prometheus (AWS)**

- **Description**: Verify swatch-metrics translates `acm:managed_cluster_worker_cores:managed:sum` Prometheus metrics to metering events for ACM products with AWS billing.
- **Setup**:
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:managed:sum` with value 8.0
  - Labels include: `_id` (cluster ID), `product=moa`, `billing_marketplace=aws`, `billing_model=marketplace`
  - Cluster ID, org ID, and AWS billing account ID are randomly generated
- **Action**:
  - Trigger internal metering API for `rhacm` product tag
- **Verification**:
  - Wait for metering event on `service-instance-ingress` Kafka topic filtered by cluster ID and metric ID `vCPUs`
  - Verify event metadata: org ID matches, instance ID equals cluster ID, display name equals cluster ID
  - Verify billing provider is `AWS` and billing account ID matches stubbed value
  - Verify product tag contains `rhacm`
  - Verify measurement contains metric ID `vCPUs` with value 8.0
- **Expected Result**:
  - HTTP 204 response from metering API
  - Event produced with vCPUs metric value of 8.0, AWS billing provider, cluster ID as instance ID, and rhacm product tag

**swatch-metrics-prometheus-TC003 - Process ACM managed worker cores metrics from Prometheus (Azure)**

- **Description**: Verify swatch-metrics translates `acm:managed_cluster_worker_cores:managed:sum` Prometheus metrics to metering events for ACM products with Azure billing.
- **Setup**:
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:managed:sum` with value 16.0
  - Labels include: `_id` (cluster ID), `product=moa`, `billing_marketplace=azure`, `billing_model=marketplace`
  - Cluster ID, org ID, and Azure billing account ID are randomly generated
- **Action**:
  - Trigger internal metering API for `rhacm` product tag
- **Verification**:
  - Wait for metering event on `service-instance-ingress` Kafka topic filtered by cluster ID and metric ID `vCPUs`
  - Verify event metadata: org ID matches, instance ID equals cluster ID, display name equals cluster ID
  - Verify billing provider is `AZURE` and billing account ID matches stubbed value
  - Verify product tag contains `rhacm`
  - Verify measurement contains metric ID `vCPUs` with value 16.0
- **Expected Result**:
  - HTTP 204 response from metering API
  - Event produced with vCPUs metric value of 16.0, Azure billing provider, cluster ID as instance ID, and rhacm product tag

**swatch-metrics-prometheus-TC004 - Process ACM self-managed worker cores metrics from Prometheus (AWS)**

- **Description**: Verify swatch-metrics translates `acm:managed_cluster_worker_cores:self_managed:sum` Prometheus metrics to metering events for ACM products with AWS billing.
- **Setup**:
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:self_managed:sum` with value 12.0
  - Labels include: `_id` (cluster ID), `product=moa`, `billing_marketplace=aws`, `billing_model=marketplace`
  - Cluster ID, org ID, and AWS billing account ID are randomly generated
- **Action**:
  - Trigger internal metering API for `rhacm` product tag
- **Verification**:
  - Wait for metering event on `service-instance-ingress` Kafka topic filtered by cluster ID and metric ID `vCPUs-self-managed`
  - Verify event metadata: org ID matches, instance ID equals cluster ID, display name equals cluster ID
  - Verify billing provider is `AWS` and billing account ID matches stubbed value
  - Verify product tag contains `rhacm`
  - Verify measurement contains metric ID `vCPUs-self-managed` with value 12.0
- **Expected Result**:
  - HTTP 204 response from metering API
  - Event produced with vCPUs-self-managed metric value of 12.0, AWS billing provider, cluster ID as instance ID, and rhacm product tag

**swatch-metrics-prometheus-TC005 - Process ACM self-managed worker cores metrics from Prometheus (Azure)**

- **Description**: Verify swatch-metrics translates `acm:managed_cluster_worker_cores:self_managed:sum` Prometheus metrics to metering events for ACM products with Azure billing.
- **Setup**:
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:self_managed:sum` with value 20.0
  - Labels include: `_id` (cluster ID), `product=moa`, `billing_marketplace=azure`, `billing_model=marketplace`
  - Cluster ID, org ID, and Azure billing account ID are randomly generated
- **Action**:
  - Trigger internal metering API for `rhacm` product tag
- **Verification**:
  - Wait for metering event on `service-instance-ingress` Kafka topic filtered by cluster ID and metric ID `vCPUs-self-managed`
  - Verify event metadata: org ID matches, instance ID equals cluster ID, display name equals cluster ID
  - Verify billing provider is `AZURE` and billing account ID matches stubbed value
  - Verify product tag contains `rhacm`
  - Verify measurement contains metric ID `vCPUs-self-managed` with value 20.0
- **Expected Result**:
  - HTTP 204 response from metering API
  - Event produced with vCPUs-self-managed metric value of 20.0, Azure billing provider, cluster ID as instance ID, and rhacm product tag

**swatch-metrics-prometheus-TC006 - Process ACM managed and self-managed cores for same cluster**

- **Description**: Verify swatch-metrics correctly produces separate metering events when a single cluster reports both `acm:managed_cluster_worker_cores:managed:sum` and `acm:managed_cluster_worker_cores:self_managed:sum` metrics in one metering run.
- **Setup**:
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:managed:sum` with value 10.0
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:self_managed:sum` with value 15.0
  - Both metrics share the same `_id` (cluster ID), org ID, and AWS billing details
  - Labels include: `product=moa`, `billing_marketplace=aws`, `billing_model=marketplace`
- **Action**:
  - Trigger internal metering API for `rhacm` product tag once
- **Verification**:
  - Wait for metering events on `service-instance-ingress` Kafka topic filtered by cluster ID
  - Verify events are produced for both dimensions on the cluster
  - Managed event: metric ID `vCPUs` with value 10.0
  - Self-managed event: metric ID `vCPUs-self-managed` with value 15.0
  - Both events have matching metadata (org ID, instance ID equals cluster ID, AWS billing provider)
  - Verify values do not cross-contaminate between dimensions (managed reports 10.0, self-managed reports 15.0)
- **Expected Result**:
  - HTTP 204 response from metering API
  - Distinct events produced for each dimension: managed (vCPUs=10.0) and self-managed (vCPUs-self-managed=15.0), both for the same cluster

**swatch-metrics-prometheus-TC007 - ACM product label regex filters self-managed-only products**

- **Description**: Verify swatch-metrics respects dimension-specific `productLabelRegex` filters. The self-managed dimension includes `ocp-assistedinstall` while the managed dimension does not, so clusters with `product=ocp-assistedinstall` should only produce self-managed events. The mock Prometheus matches the generated PromQL's `product=~"(...)"` selector, so the managed query (which excludes `ocp-assistedinstall`) returns no data — mirroring real Prometheus label filtering.
- **Setup**:
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:managed:sum` with value 8.0, `product=ocp-assistedinstall`
  - Mock Prometheus returns metric `acm:managed_cluster_worker_cores:self_managed:sum` with value 12.0, `product=ocp-assistedinstall`
  - Both metrics share the same `_id` (cluster ID), org ID, and AWS billing details
  - Labels include: `billing_marketplace=aws`, `billing_model=marketplace`
- **Action**:
  - Trigger internal metering API for `rhacm` product tag once
- **Verification**:
  - Wait for self-managed events on `service-instance-ingress` Kafka topic filtered by cluster ID
  - Verify self-managed events are produced with metric ID `vCPUs-self-managed` and value 12.0
  - Event metadata: org ID matches, instance ID equals cluster ID, AWS billing provider, rhacm product tag
  - After the self-managed sync point, verify NO managed event (metric ID `vCPUs`) is produced despite the managed metric being stubbed — the managed `productLabelRegex` excludes `ocp-assistedinstall`
- **Expected Result**:
  - HTTP 204 response from metering API
  - Self-managed events produced with vCPUs-self-managed metric value of 12.0
  - No managed (vCPUs) event produced, demonstrating productLabelRegex filtering
