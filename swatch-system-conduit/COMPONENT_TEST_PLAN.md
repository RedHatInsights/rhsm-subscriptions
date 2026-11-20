# Introduction

The **swatch-system-conduit** module is a service within the Subscription Watch platform that synchronizes RHSM consumer records into Host Based Inventory (HBI). It retrieves consumers from the RHSM API, translates their identities and facts into HBI host records, and publishes `add_host` messages to the inventory host ingress Kafka topic.

This document outlines the component test plan for swatch-system-conduit, including the requirement that conduit omit `display_name` from inventory payloads.

**Purpose:** To verify RHSM consumer synchronization, host fact mapping, marketplace classification, and omission of display names from inventory messages.

**Scope:**

* Organization synchronization through the internal sync API.
* RHSM consumer retrieval and translation into HBI host messages.
* Host identity, network, RHSM facts, and system profile mapping.
* AWS PAYG and BYOS marketplace classification.
* Omission of `display_name`, regardless of consumer name, OpenShift cluster UUID, or an existing display-name value in conduit facts.

**Assumptions:**

* The component test environment provides swatch-system-conduit, PostgreSQL, Kafka, Kafka Bridge, and WireMock.
* RHSM consumer feed responses are stubbed through WireMock.
* Synchronous conduit operations are enabled for component tests.
* Each test uses a unique organization ID to correlate its stubbed RHSM response and Kafka messages.

**Constraints:**

* Testing is limited to swatch-system-conduit at a component level, with the existing display-name serialization regression test documented as supporting coverage.
* Processing by HBI and preservation of a display name already stored in HBI require integration testing and are outside this plan's component boundary.

# Test Strategy

This test plan uses the Java component test framework. Test cases should be runnable locally and against a deployed component test environment.

**Testing Strategy:**

- Stub `GET /candlepin/consumers/feeds` for the test organization using `RhsmApiStubs`.
- Trigger `POST /api/rhsm-subscriptions/v1/internal/rpc/syncOrg` with an `org_id` request body and `x-rh-swatch-synchronous-request: true`, using `ConduitSwatchService.syncConduitByOrgId`.
- Expect HTTP 200 and consume the organization's `add_host` message from `platform.inventory.host-ingress` (`INVENTORY_HOST_INGRESS`).
- Verify the translated host fields against the stubbed consumer.
- For display-name checks, inspect the serialized message's `data` JSON object and assert that the `display_name` property is absent. A property present with a null or empty value does not satisfy this requirement.

See [the component testing guide](ct/README.md) for environment setup and execution commands.

**Current Automated Coverage:**

- Implemented component test methods use `@TestPlanName` with the exact case ID below, linking their test reports to this plan. Add the corresponding annotation when implementing a planned component case.
- `PublicCloudConduitComponentTest` implements the synchronization, marketplace, and display-name omission component cases below.
- The branch adds `KafkaEnabledInventoryServiceTest.ensureHostPayloadNeverIncludesDisplayName`, a parameterized test under `src/test/java` that verifies omission from serialized JSON for six display-name inputs. It supplies supporting coverage for `system-conduit-display-name-TC001` with a mocked Kafka producer.
- The component scenarios in `system-conduit-display-name-TC002` and `system-conduit-display-name-TC003` inspect the Kafka message as JSON and explicitly assert that the `display_name` property is absent while identity fields retain their expected values.

# Test Cases

## RHSM Consumer Synchronization

**system-conduit-sync-TC001 - Publish an inventory host from an RHSM consumer**

- **Description**: Verify that synchronizing an organization translates its RHSM consumer into an `add_host` message with the expected host fields.
- **Setup**:
  - Stub one system consumer for a unique organization using `RhsmApiStubs.buildFullConsumer`.
  - Include a consumer UUID, FQDN, BIOS UUID, Insights ID, OpenShift cluster ID and cluster UUID, IP and MAC addresses, architecture, socket count, cores per socket, and memory facts.
- **Action**:
  - Call `syncConduitByOrgId` for the organization.
- **Verification**:
  - Confirm the sync API returns HTTP 200.
  - Consume the organization's inventory message and verify operation `add_host` and non-null host data.
  - Verify `org_id`, `fqdn`, `subscription_manager_id`, `bios_uuid`, and `insights_id` match the stubbed consumer.
  - Verify `openshift_cluster_id` uses the `openshift.cluster_uuid` fact.
  - Verify reporter `rhsm-conduit` and a non-null `stale_timestamp`.
  - Verify IP and MAC addresses match the network facts.
  - Verify one `rhsm` fact set containing the organization ID and a non-null `SYNC_TIMESTAMP`.
  - Verify system profile architecture, sockets, cores per socket, and owner ID match the consumer facts and UUID.
  - Verify `memory.memtotal` is converted from KiB to `system_memory_bytes`.
- **Expected Result**:
  - Synchronization succeeds and emits the correctly mapped host message.
- **Automated Coverage**:
  - `PublicCloudConduitComponentTest.shouldEmitAddHostWhenConduitSyncsRhsmConsumer`.

## AWS Marketplace Classification

**system-conduit-marketplace-TC001 - Mark an AWS PAYG host as marketplace**

- **Description**: Verify that an AWS billing product indicating PAYG sets the host's marketplace flag.
- **Setup**:
  - Stub a full RHSM consumer with fact `aws_billing_products` set to `bp-6fa54006`.
- **Action**:
  - Synchronize the organization and consume its `add_host` message.
- **Verification**:
  - Verify `data.system_profile.is_marketplace` is true.
- **Expected Result**:
  - The PAYG host is classified as a marketplace host.
- **Automated Coverage**:
  - `PublicCloudConduitComponentTest.shouldSetIsMarketplaceWhenAwsBillingCodeIndicatesPayg`.

**system-conduit-marketplace-TC002 - Mark an AWS BYOS host as non-marketplace**

- **Description**: Verify that an AWS billing product indicating BYOS leaves the host's marketplace flag false.
- **Setup**:
  - Stub a full RHSM consumer with fact `aws_billing_products` set to `bp-63a5400a`.
- **Action**:
  - Synchronize the organization and consume its `add_host` message.
- **Verification**:
  - Verify `data.system_profile.is_marketplace` is false.
- **Expected Result**:
  - The BYOS host is classified as a non-marketplace host.
- **Automated Coverage**:
  - `PublicCloudConduitComponentTest.shouldNotSetIsMarketplaceWhenAwsBillingCodeIndicatesByos`.

## Display Name Omission

Conduit must omit `data.display_name` from inventory messages. Neither an RHSM consumer name nor an OpenShift cluster UUID should populate this property. The host's FQDN and OpenShift cluster identity remain independently mapped to their corresponding fields.

**system-conduit-display-name-TC001 - Omit display_name for every supplied conduit display-name value**

- **Description**: Verify that host serialization never includes `display_name`, even if a display name is explicitly supplied in `ConduitFacts`.
- **Setup**:
  - Create `ConduitFacts` with an organization ID.
  - Parameterize its display name with the six inputs used by the branch's regression test: null, an empty string, a single space, `custom display name`, `host.example.com`, and `550e8400-e29b-41d4-a716-446655440000`.
  - Use a mocked Kafka producer to capture the outgoing `CreateUpdateHostMessage`.
- **Action**:
  - Call `KafkaEnabledInventoryService.sendHostUpdate` for each input.
  - Serialize the captured message to a JSON tree with the configured `hbiObjectMapper`.
- **Verification**:
  - Verify `data.org_id` matches the supplied organization ID.
  - Assert `data.has("display_name")` is false for every input.
- **Expected Result**:
  - All six inputs produce host payloads without a `display_name` property, including inputs containing a non-empty name, hostname, or UUID.
- **Automated Coverage**:
  - `KafkaEnabledInventoryServiceTest.ensureHostPayloadNeverIncludesDisplayName` (parameterized unit-level regression coverage added on this branch; no running Kafka broker required).

**system-conduit-display-name-TC002 - Omit display_name when the RHSM consumer has a name**

- **Description**: Verify through the component boundary that a consumer name is not copied into the inventory host's display name.
- **Setup**:
  - Stub a full RHSM consumer and explicitly set its top-level `name` to `custom display name`.
  - Set `network.fqdn` to a distinct hostname such as `host.example.com`.
  - Omit `openshift.cluster_uuid` to exercise the consumer-name scenario independently.
- **Action**:
  - Synchronize the organization and capture its `add_host` message as raw JSON.
- **Verification**:
  - Verify `data.org_id` and `data.subscription_manager_id` match the consumer.
  - Verify `data.fqdn` matches `network.fqdn`.
  - Assert the `data` object has no `display_name` property.
- **Expected Result**:
  - The host is published with its FQDN and identity fields, without a display name derived from either the consumer name or hostname.
- **Automated Coverage**:
  - `PublicCloudConduitComponentTest.shouldOmitDisplayNameWhenRhsmConsumerHasName`.

**system-conduit-display-name-TC003 - Preserve OpenShift cluster identity without setting display_name**

- **Description**: Verify through the component boundary that an OpenShift cluster UUID is mapped only to its identity field and is not used as a display name.
- **Setup**:
  - Stub a full RHSM consumer with a top-level `name`, `network.fqdn`, and valid `openshift.cluster_uuid`.
  - Give the consumer name, FQDN, and cluster UUID distinct values so their mappings can be distinguished.
  - Include `openshift.cluster_id` with a value distinct from `openshift.cluster_uuid`, as in the existing full-consumer fixture.
- **Action**:
  - Synchronize the organization and capture its `add_host` message as raw JSON.
- **Verification**:
  - Verify `data.openshift_cluster_id` matches `openshift.cluster_uuid`.
  - Verify `data.fqdn` and `data.subscription_manager_id` retain their expected mappings.
  - Assert the `data` object has no `display_name` property.
- **Expected Result**:
  - The host retains its OpenShift cluster identity and other mapped fields while omitting `display_name` entirely.
- **Automated Coverage**:
  - `PublicCloudConduitComponentTest.shouldOmitDisplayNameWhenRhsmConsumerHasOpenShiftClusterUuid`.
