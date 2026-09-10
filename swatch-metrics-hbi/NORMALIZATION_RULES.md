# swatch-metrics-hbi Normalization

## Introduction

This document outlines the normalization rules applied by swatch-metrics-hbi when transforming HBI host events into Swatch events

Currently these rules are organized into three different normalizer classes for everything extracted from the HBI events:

- **FactNormalizer** Normalizes facts involving identity, classification, and metadata.
- **MeasurementNormalizer**: Normalizes measurement values for cores and sockets.
- **ProductNormalizer**: Involves product tag resolution and applying the correct facts based on the product.

## Pre-normalization

### Event skipping

These checks happen before any normalization. If any match, the event is dropped with no swatch event produced.

Sources:
- [CreateUpdateHostHandler](https://github.com/RedHatInsights/rhsm-subscriptions/blob/17595c22f5084a1bf954dae5b7c764bb5405188e/swatch-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/processing/handlers/CreateUpdateHostHandler.java)

| Rule | Condition |
| ------ | ----------- |
| Marketplace billing | RHSM `BILLING_MODEL = "marketplace"` |
| Edge host type | System profile `host_type = "edge"` |
| Stale host | `stale_timestamp + culling_offset < now` |
| Feature flag off | `EMIT_EVENTS` unleash flag is disabled |

## Facts Normalization

Sources:
- [FactNormalizer](https://github.com/RedHatInsights/rhsm-subscriptions/blob/main/swatch-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/normalization/FactNormalizer.java)
- [HbiHostRelationshipService](https://github.com/RedHatInsights/rhsm-subscriptions/blob/17595c22f5084a1bf954dae5b7c764bb5405188e/swatch-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/services/HbiHostRelationshipService.java): For hypervisor-guest relationship facts

### Identity

| Field | Rule |
|-------|------|
| instanceId | Use `provider_id` if present; otherwise `inventory_id` |
| lastSeen | Parsed from HBI host `updated` field |

### Virtual/Physical Classification

| Field | Rule |
|-------|------|
| isVirtual | `true` if ANY of: RHSM `IS_VIRTUAL=true`, satellite `virtual_host_uuid` is non-empty, system profile `infrastructure_type="virtual"` |
| hardwareType | `CLOUD` if supported cloud provider; else `VIRTUAL` if isVirtual; else `PHYSICAL` |
| cloudProviderType | From system profile `cloud_provider` if supported (`aws`, `azure`, `alibaba`, `google`) |
| cloudProvider | Mapped from cloudProviderType → Event.CloudProvider enum |

### Hypervisor/Guest Classification

| Field | Rule |
|-------|------|
| hypervisorUuid | Satellite `virtual_host_uuid` preferred; fall back to system profile `virtual_host_uuid` |
| isHypervisor | `true` if any guest has this host's subscriptionManagerId as its hypervisor |
| isUnmappedGuest | `true` if virtual AND has hypervisorUuid AND no hypervisor relationship found |

### SLA and Usage

| Field | Rule |
|-------|------|
| SLA | RHSM `SYSPURPOSE_SLA` preferred (if valid and not skipped); fall back to satellite `system_purpose_sla` |
| Usage | RHSM `SYSPURPOSE_USAGE` preferred (if valid and not skipped); fall back to satellite `system_purpose_usage` |
| skipRhsmFacts | `true` when RHSM `SYNC_TIMESTAMP` is present AND `syncTimestamp < startOfToday - hostLastSyncThreshold` — when true, RHSM SLA/Usage/products are all ignored |

### 3rd-party Migration

| Field | Rule |
|-------|------|
| is3rdPartyMigrated | System profile `conversions.activity = true` |

## Measurements Normalization

Sources:
- [MeasurementNormalizer](https://github.com/RedHatInsights/rhsm-subscriptions/blob/main/swatch-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/normalization/MeasurementNormalizer.java)

### Cores

| Scenario | Rule |
|----------|------|
| Physical (or non-x86_64 virtual) | `cores = cores_per_socket × number_of_sockets` (both must be > 0, else null) |
| x86_64 virtual | `cores = ceil((cores_per_socket × sockets) / threadsPerCore)` |
| Marketplace host | Cores forced to `0` regardless of hardware values |
| Null fallback | If computed cores is null but `cores_per_socket` is non-null/non-zero, use `cores_per_socket` |

### Determination of threadsPerCore Value (x86_64 virtual only)

| Priority | Condition | Value |
|----------|-----------|-------|
| 1 | `useCpuSystemFactsForAllProducts` config enabled OR product is "OpenShift Container Platform", AND `threads_per_core > 0` | Use `threads_per_core` directly |
| 2 | Same config/product condition, AND `cpus`, `sockets`, `cores_per_socket` all > 0 | Calculate: `cpus / (sockets × cores_per_socket)` |
| 3 | Default (all other cases) | `2.0` |

### Sockets

| Scenario | Rule |
|----------|------|
| Base | `sockets = number_of_sockets` (if > 0, else null) |
| Physical or hypervisor | Round odd sockets UP to next even value: if `sockets % 2 == 1`, then `sockets + 1` | 
| Virtual + cloud provider (non-marketplace) | `sockets = 1` |
| Virtual + cloud provider + marketplace | `sockets = 0` |
| Virtual unmapped RHEL guest | `sockets = 1` (when isVirtual, isUnmappedGuest, and any product tag starts with "RHEL") |
| Marketplace host | Sockets forced to `0` |
| Null fallback | If computed sockets is null but `number_of_sockets` is non-null/non-zero, use `number_of_sockets` |

### SYSPURPOSE_UNITS Override (applied last)

| Value | Effect |
|-------|--------|
| `"Sockets"` | Cores → null; if sockets is null, fall back to raw `number_of_sockets` |
| `"Cores/vCPU"` | Sockets → null; if cores is null, fall back to raw `cores_per_socket` |
| Other/null | No effect (unknown values logged as warning) |

## Products Normalization

Sources:
- [ProductNormalizer](https://github.com/RedHatInsights/rhsm-subscriptions/blob/main/swatch-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/normalization/ProductNormalizer.java)
- [SystemProfileFacts](https://github.com/RedHatInsights/rhsm-subscriptions/blob/5321e890e0769ea9fe6ebd71e97a34a9a2164e17/swatch-model-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/normalization/facts/SystemProfileFacts.java)
- [SatelliteFacts](https://github.com/RedHatInsights/rhsm-subscriptions/blob/5321e890e0769ea9fe6ebd71e97a34a9a2164e17/swatch-model-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/normalization/facts/SatelliteFacts.java)
- [RhsmFacts](https://github.com/RedHatInsights/rhsm-subscriptions/blob/5321e890e0769ea9fe6ebd71e97a34a9a2164e17/swatch-model-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/normalization/facts/RhsmFacts.java)
- [QpcFacts](https://github.com/RedHatInsights/rhsm-subscriptions/blob/5321e890e0769ea9fe6ebd71e97a34a9a2164e17/swatch-model-metrics-hbi/src/main/java/com/redhat/swatch/hbi/events/normalization/facts/QpcFacts.java)

### Tag Sources

This determines what product tags to assign to a certain host. All sources below are touched in ProductNormalization and their resulting product tags are merged into one host event. The order between the first three sources does not affect the outcome. QPC is a conditional fallback that only runs when the system profile has no installed products.

| Source | Condition |
|--------|-----------|
| SystemProfileFacts `installed_products` | Engineering IDs looked up via ProductTagLookupParams |
| SatelliteFacts `system_purpose_role` | Role looked up via ProductTagLookupParams |
| RhsmFacts `SYSPURPOSE_ROLE` + `RH_PROD` | Skipped if RHSM facts are stale; role + engineering IDs looked up |
| QpcFacts `rh_products_installed` | **Fallback only**: runs only if contains "RHEL" AND no system profile installed products |

### QPC Arch-to-variant Mapping

When QPC reports RHEL and no system profile products exist:

| Architecture | Product Tag |
|--------------|------------|
| x86_64, i686, i386 | "RHEL for x86" |
| aarch64 | "RHEL for ARM" |
| ppc64le | "RHEL for IBM Power" |
| other/null | (no arch-specific tag) |

Always adds "RHEL" tag regardless of arch.

### Post-processing Product Tag

| Rule | Description |
|------|-------------|
| RHEL variant normalization | If RHEL is present with 0 RHEL variants, OR >1 RHEL variants → add "RHEL Ungrouped". A "RHEL variant" is a tag starting with "RHEL " but NOT "RHEL for " (e.g. "RHEL Workstation") |
| Prune included products | `SubscriptionDefinition.pruneIncludedProducts()` removes tags that are "included" by other, more specific tags |

### Product IDs

Collected from all applicable sources (system profile installed_products IDs + RHSM RH_PROD IDs), included in the swatch event as `productIds`.

## Event Type Mapping

| HBI Event Type | Swatch Event Type |
| ------ | ----------- |
| "created" | `INSTANCE_CREATED` |
| "updated" | `INSTANCE_UPDATED` |
| "deleted" | `INSTANCE_DELETED` |
