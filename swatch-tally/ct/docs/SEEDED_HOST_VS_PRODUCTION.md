# Seeded Host vs Production / Host-Inventory Alignment

This document compares **TallyDbHostSeeder** (component-test DB seeding) with how production populates the tally service's `hosts` and `host_tally_buckets` tables. Production data ultimately comes from **Host Based Inventory (HBI)** via **swatch-metrics-hbi** (Kafka events) and **MetricUsageCollector** in the tally service.

## Data flow in production

1. **HBI** (insights-host-inventory) stores hosts with `facts` (JSONB), `system_profiles_static`, etc.
2. **swatch-metrics-hbi** consumes HBI events, normalizes facts, and produces **Events** to Kafka.
3. **Tally service** (MetricUsageCollector) consumes Events and updates **Host** and **HostTallyBucket** in the tally DB.

So the seeder is mimicking the **result** of that pipeline (what ends up in the tally DB), not the HBI schema itself.

---

## Host table (`hosts`)

| Column | Production source | Seeder | Match? |
|--------|-------------------|--------|--------|
| `id` | UUID (generated) | UUID.randomUUID() | ✅ |
| `instance_id` | event.getInstanceId() | inventoryId | ✅ |
| `inventory_id` | event.getInventoryId() | inventoryId | ✅ |
| `insights_id` | event.getInsightsId() | inventoryId | ✅ |
| `display_name` | event.getDisplayName() / instanceId | inventoryId | ✅ (required non-null) |
| `org_id` | event.getOrgId() | orgId | ✅ |
| `subscription_manager_id` | event.getSubscriptionManagerId() | random UUID string | ✅ |
| `is_guest` | derived from hardwareType == VIRTUALIZED | param | ✅ |
| `is_unmapped_guest` | event | param | ✅ |
| `is_hypervisor` | event | param | ✅ |
| `hardware_type` | event.getHardwareType() → HostHardwareType | param (e.g. "PHYSICAL", "CLOUD") | ✅ (enum **name**) |
| `num_of_guests` | — | param / null | ✅ |
| `last_seen` | event.getTimestamp() | OffsetDateTime.now() | ✅ |
| `instance_type` | "HBI_HOST" for HBI-sourced | "HBI_HOST" | ✅ |
| `billing_provider` | event.getBillingProvider() → BillingProvider | null or param | ✅ |
| `billing_account_id` | event.getBillingAccountId() | null or param | ✅ |
| `hypervisor_uuid` | event.getHypervisorUuid() | param | ✅ |
| `cloud_provider` | event.getCloudProvider() (when CLOUD) | set when hardware_type=CLOUD | ✅ |

### BillingProvider and enum storage

- **Host.billing_provider** uses `BillingProvider` with an `AttributeConverter` that stores the enum **value** (e.g. `"aws"`, `"azure"`, `"red hat"`, `""`), not the enum name.
- The seeder must write these same values. Using `"AWS"` would not match production; use `"aws"` (and similarly for other providers).

---

## Host tally buckets table (`host_tally_buckets`)

| Column | Production source | Seeder | Match? |
|--------|-------------------|--------|--------|
| `host_id` | host.getId() | hostId | ✅ |
| `product_id` | event productTag | productId | ✅ |
| `usage` | Event.Usage → Usage enum **value** | "Production", "_ANY", etc. | ✅ (value: "Production", "_ANY", "") |
| `sla` | Event.Sla → ServiceLevel enum **value** | "Premium", "_ANY", etc. | ✅ (value: "Premium", "_ANY", "") |
| `as_hypervisor` | false (for HBI) | false | ✅ |
| `cores` | host measurements / event | cores | ✅ |
| `sockets` | host measurements / event | sockets | ✅ |
| `measurement_type` | HardwareMeasurementType enum **name** | "PHYSICAL", "AWS", "CLOUD", etc. | ✅ |
| `billing_provider` | Event → BillingProvider **value** | "" or param | ⚠️ **Use enum value** (e.g. `"aws"`, `""`) |
| `billing_account_id` | Optional.ofNullable(event.getBillingAccountId()).orElse("") | null in some paths | ⚠️ Use **""** when absent (production never stores null here; HostBucketKey.equals expects non-null) |
| `version` | 0 (optimistic locking) | 0 | ✅ |

### Production bucket multiplicity

Production creates **multiple buckets per product** via a cartesian product of:

- SLA: `{ effectiveSla, _ANY }`
- Usage: `{ effectiveUsage, _ANY }`
- BillingProvider: `{ effectiveProvider, _ANY }`
- BillingAccountId: `{ effectiveId, "" }` (when applicable)

So for one product you get several rows. The seeder currently inserts **one** bucket per `insertBuckets` call (one sla/usage pair). For tests that only need a single bucket to drive nightly tally, this is sufficient. If a test needs exact parity with production’s multiple buckets, the seeder could be extended to insert the same combinations.

---

## Enum storage reference (tally DB)

| Type | JPA storage | Example DB value |
|------|-------------|------------------|
| **BillingProvider** | AttributeConverter → **value** | `"aws"`, `"red hat"`, `""` |
| **ServiceLevel** | AttributeConverter → **value** | `"Premium"`, `"_ANY"`, `""` |
| **Usage** | AttributeConverter → **value** | `"Production"`, `"_ANY"`, `""` |
| **HostHardwareType** | EnumType.STRING → **name** | `"PHYSICAL"`, `"CLOUD"`, `"VIRTUALIZED"` |
| **HardwareMeasurementType** | EnumType.STRING → **name** | `"PHYSICAL"`, `"AWS"`, `"CLOUD"` |

---

## Summary of seeder alignment with production

The seeder now properly aligns with production behavior:

1. **billing_provider** (hosts and host_tally_buckets): Stores the **value** form (e.g. `"aws"`, `"azure"`, `""`), not the enum name (e.g. `"AWS"`).
2. **billing_account_id** (host_tally_buckets): Uses **empty string `""`** when there is no billing account, not SQL NULL, matching production key comparison behavior.
3. **cloud_provider** (hosts): Now properly set when `hardware_type = CLOUD` to mirror production.

Seeded data now matches what production (MetricUsageCollector + HBI events) would write for the same logical host and buckets.
