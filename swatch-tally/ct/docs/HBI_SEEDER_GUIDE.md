# TallyHbiDbSeeder Usage Guide

**Purpose:** Practical guide for using `TallyHbiDbSeeder` to create test data for HBI (Host-Based Inventory) component tests.

**Audience:** Developers writing component tests that need to test the HBI → Swatch-Tally sync flow.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Understanding HBI Fields](#understanding-hbi-fields)
3. [Nightly vs Hourly Tally](#nightly-vs-hourly-tally)
4. [Common Test Scenarios](#common-test-scenarios)
5. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Basic RHEL Host

```java
@Inject
@HbiDatabase 
DatabaseService hbiDatabase;

private TallyHbiDbSeeder hbiSeeder;

@BeforeEach
void setup() {
    hbiSeeder = new TallyHbiDbSeeder(hbiDatabase);
}

@AfterEach
void cleanup() {
    hbiSeeder.deleteAllInsertedHosts();
}

@Test
void testRhelHost() {
    // Create RHEL host with defaults (4 cores, 2 sockets)
    SeededHost host = hbiSeeder.insertRhelHost(orgId);
    
    // Host is now in HBI database and ready for tally sync
    service.tallyOrg(orgId);
}
```

### Using the Builder Pattern

```java
@Test
void testCustomRhelHost() {
    SeededHost host = hbiSeeder.rhelHost(orgId)
        .inventoryId("my-custom-inventory-id")
        .subscriptionManagerId("my-subman-id")
        .displayName("Production Web Server")
        .cores(16)
        .sockets(4)
        .insert();
    
    // Now sync and verify
    service.tallyOrg(orgId);
}
```

### Cloud Host

```java
@Test
void testCloudHost() {
    SeededHost host = hbiSeeder.cloudHost(orgId)
        .providerId("i-0abc123def456")  // AWS instance ID
        .displayName("AWS Production Instance")
        .insert();
    
    service.tallyOrg(orgId);
}
```

### Custom Reporters

```java
@Test
void testCustomReporter() {
    // Single reporter (default: "component-test")
    SeededHost host1 = hbiSeeder.rhelHost(orgId)
        .reporter("rhsm-conduit")
        .reporters("rhsm-conduit")
        .insert();
    
    // Multiple reporters (e.g., host reported by multiple systems)
    SeededHost host2 = hbiSeeder.rhelHost(orgId)
        .reporter("rhsm-conduit")  // Primary reporter
        .reporters("rhsm-conduit", "puptoo", "satellite")  // All reporters (varargs)
        .insert();
    
    service.tallyOrg(orgId);
}
```

**Note on reporters:**
- `reporter` (singular): VARCHAR field - the primary/last reporter
- `reporters` (plural): ARRAY field - all reporters that have reported this host
- The `.reporters()` method accepts 1+ strings via varargs and converts to PostgreSQL array

---

## Understanding HBI Fields

### Critical Fields for Tally Sync

The HBI → Swatch sync query filters and uses specific fields. Here's what matters:

#### 🔑 Identity Fields (Sync Keys)

| Field | Required | Purpose | Used By |
|-------|----------|---------|---------|
| `insights_id` | **YES** | Primary host identifier | Hourly & Nightly Tally |
| `subscription_manager_id` | For RHEL | RHSM identity, matches RHEL hosts | Hourly & Nightly Tally |
| `provider_id` | For Cloud | Cloud provider instance ID | Hourly & Nightly Tally |
| `org_id` | **YES** | Organization identifier | Sync query filter |

**Why these matter:**
- Swatch-Tally uses these to **match hosts** between HBI and the Swatch database
- The sync query joins on these fields: `WHERE h.org_id = :orgId`
- Hourly tally relies on these for host reconciliation

**What the seeder does:**
```java
// Seeder automatically generates UUIDs:
insights_id = UUID.randomUUID()
subscription_manager_id = "test-subman-id" (RHEL only)
provider_id = providerId parameter (Cloud only)
```

#### ⏰ Staleness Filter Fields

| Field | Required | Purpose | Impact if Missing |
|-------|----------|---------|-------------------|
| `last_check_in` | **CRITICAL** | Timestamp when host last reported | Host filtered out of sync! |
| `reporter` | **YES** | Which system reported the host | Insert fails (NOT NULL constraint) |

**Why `last_check_in` is CRITICAL:**

The sync query includes a staleness filter:
```sql
WHERE h.last_check_in + make_interval(...) > NOW() - make_interval(days => :culledOffsetDays)
```

**If `last_check_in` is NULL → Host is excluded from sync → Won't appear in tally!**

**What the seeder does:**
```java
last_check_in = NOW()  // Always set to current timestamp
reporter = "component-test"
```

#### 📊 Capacity Fields (system_profiles_static)

| Field | Required | Purpose | Used By |
|-------|----------|---------|---------|
| `cores_per_socket` | For capacity calc | CPU cores per socket | Nightly Tally |
| `number_of_sockets` | For capacity calc | Number of CPU sockets | Nightly Tally |
| `infrastructure_type` | No, but affects logic | "physical" or "virtual" | Socket increase mapping |

**Why these matter:**

Nightly tally calculates capacity:
```
total_cores = cores_per_socket × number_of_sockets
```

For RHEL physical hosts, there's a **socket increase mapping**:
```java
// TallyHbiDbSeeder.RHEL_PER_SOCKET_INCREASE
{1 → 2, 2 → 2, 4 → 4, 7 → 8}
```

**Example:**
```java
// You insert:
hbiSeeder.rhelHost(orgId).sockets(1).insert();

// Tally reports:
sockets = 2  // Increased from 1 to 2 for physical RHEL
```

**What the seeder does:**
```java
// RHEL host defaults:
cores_per_socket = cores / sockets (calculated)
number_of_sockets = sockets parameter (default: 2)
infrastructure_type = "physical"

// Cloud host:
cores_per_socket = null (not required for cloud)
number_of_sockets = null
infrastructure_type = "virtual"
```

#### 🔗 Join Requirement

**CRITICAL:** The sync query uses `INNER JOIN` between `hbi.hosts` and `hbi.system_profiles_static`:

```sql
FROM hbi.hosts h
INNER JOIN hbi.system_profiles_static sp ON sp.host_id = h.id
```

**This means:** 
- ❌ Host in `hosts` but NOT in `system_profiles_static` → **Excluded from sync**
- ✅ Host in BOTH tables → Syncs successfully

**What the seeder does:**
```java
// ALWAYS inserts into BOTH tables:
1. INSERT INTO hbi.hosts (...)
2. INSERT INTO hbi.system_profiles_static (...)
```

---

## Nightly vs Hourly Tally

### Nightly Tally

**Purpose:** Syncs hosts from HBI → Swatch database, calculates daily snapshots

**Data Source:** HBI database (via `InventoryHost` query)

**What it does:**
1. Query HBI for hosts in the org (filtered by `last_check_in`)
2. Reconcile hosts in Swatch DB (insert/update)
3. Calculate capacity snapshots (cores, sockets)
4. Create daily tally snapshots

**Fields used:**
- `org_id` - Filter which hosts to sync
- `insights_id`, `subscription_manager_id`, `provider_id` - Match hosts
- `last_check_in` - Filter stale hosts
- `cores_per_socket`, `number_of_sockets` - Capacity calculation

**Trigger in tests:**
```java
service.tallyOrg(orgId);  // Triggers nightly tally sync
```

**Typical test flow:**
```java
@Test
void testNightlyTally() {
    // 1. Insert host into HBI
    hbiSeeder.insertRhelHost(orgId);
    
    // 2. Trigger nightly tally (syncs HBI → Swatch)
    service.tallyOrg(orgId);
    
    // 3. Verify capacity in tally report
    var report = service.getTallyReportData(orgId, "rhel-for-x86", "Sockets", ...);
    assertEquals(2, report.getData().get(0).getValue());
}
```

### Hourly Tally

**Purpose:** Processes real-time RHSM events (from Kafka), updates hourly snapshots

**Data Source:** Kafka events (NOT HBI database)

**What it does:**
1. Consume events from `platform.rhsm-subscriptions.service-instance-ingress` topic
2. Update host metadata in Swatch DB
3. Create hourly tally snapshots

**Fields used:**
- Events contain their own host identifiers
- May reference hosts that were synced from HBI in nightly tally
- Uses `insights_id`, `subscription_manager_id` to match existing hosts

**Trigger in tests:**
```java
service.performHourlyTallyForOrg(orgId);  // Process hourly events
```

**Typical test flow (PAYG/Events):**
```java
@Test
void testHourlyTally() {
    // 1. Seed Swatch DB directly (NOT HBI)
    seeder.insertHbiHost(orgId, inventoryId);
    seeder.insertBuckets(hostId, productId, sla, usage, cores, sockets, measurementType);
    
    // 2. Send Kafka event
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
    
    // 3. Trigger hourly tally
    service.performHourlyTallyForOrg(orgId);
}
```

### When to Use HBI Seeder vs Direct Swatch Seeding

| Scenario | Use | Reason |
|----------|-----|--------|
| Testing nightly tally sync | **HBI Seeder** | Tests HBI → Swatch flow |
| Testing hourly event processing | **Swatch Seeder** | Events don't come from HBI |
| Testing HBI schema compatibility | **HBI Seeder** | Validates schema fields |
| Testing PAYG/metering | **Swatch Seeder** | PAYG events bypass HBI sync |
| Integration test (full flow) | **Both** | HBI seeder for base hosts, events for metering |

---

## Common Test Scenarios

### Scenario 1: Test Socket Increase Mapping

```java
@ParameterizedTest
@CsvSource({
    "1, 2",  // 1 socket → reported as 2
    "2, 2",  // 2 sockets → reported as 2
    "4, 4",  // 4 sockets → reported as 4
    "7, 8"   // 7 sockets → reported as 8
})
void testSocketIncreaseMapping(int actualSockets, int expectedReported) {
    // Given: RHEL host with specific socket count
    SeededHost host = hbiSeeder.rhelHost(orgId)
        .sockets(actualSockets)
        .cores(actualSockets * 4)  // 4 cores per socket
        .displayName("socket-test-" + actualSockets)
        .insert();
    
    // When: Nightly tally runs
    service.tallyOrg(orgId);
    
    // Then: Verify reported socket count matches mapping
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);
    
    double reported = TallyTestHelpers.getSocketCount(
        service, orgId, "rhel-for-x86", "Daily", beginning, ending);
    
    assertEquals(expectedReported, reported);
}
```

### Scenario 2: Test Staleness Filtering

```java
@Test
void testStaleHostsNotSynced() {
    // Given: Host with last_check_in = NULL (simulating no staleness data)
    // NOTE: TallyHbiDbSeeder always sets last_check_in to NOW()
    // To test staleness, you'd need to manually UPDATE the record:
    
    SeededHost host = hbiSeeder.insertRhelHost(orgId);
    
    // Manually set last_check_in to 30 days ago (stale)
    hbiSeeder.updateLastCheckIn(host.hostId(), OffsetDateTime.now().minusDays(30));
    
    // When: Nightly tally runs
    service.tallyOrg(orgId);
    
    // Then: Host should NOT appear in tally (filtered out as stale)
    var report = service.getTallyReportData(orgId, "rhel-for-x86", "Sockets", ...);
    assertEquals(0, report.getData().get(0).getValue());
}
```

### Scenario 3: Multiple Host Types

```java
@Test
void testMixedHostTypes() {
    service.createOptInConfig(orgId);
    
    // Given: Mix of RHEL and cloud hosts
    SeededHost rhel1 = hbiSeeder.rhelHost(orgId).sockets(2).insert();
    SeededHost rhel2 = hbiSeeder.rhelHost(orgId).sockets(4).insert();
    SeededHost cloud = hbiSeeder.cloudHost(orgId)
        .providerId("i-aws-instance")
        .insert();
    
    // When: Nightly tally runs
    service.tallyOrg(orgId);
    
    // Then: All hosts synced
    assertEquals(3, hbiSeeder.getInsertedHostCount());
    
    // RHEL hosts contribute to socket count
    var rhelReport = service.getTallyReportData(orgId, "rhel-for-x86", "Sockets", ...);
    assertTrue(rhelReport.getData().get(0).getValue() >= 6);  // 2 + 4 sockets
}
```

### Scenario 4: Cleanup Between Tests

```java
public class MyHbiTest extends BaseTallyComponentTest {
    
    @Inject
    @HbiDatabase 
    DatabaseService hbiDatabase;
    
    private TallyHbiDbSeeder hbiSeeder;
    
    @BeforeEach
    void setupHbi() {
        hbiSeeder = new TallyHbiDbSeeder(hbiDatabase);
    }
    
    @AfterEach
    void cleanupHbi() {
        // CRITICAL: Always clean up to avoid test pollution
        hbiSeeder.deleteAllInsertedHosts();
    }
    
    @Test
    void test1() {
        hbiSeeder.insertRhelHost(orgId);
        // Test logic...
    }
    
    @Test
    void test2() {
        // Fresh start - test1's hosts are cleaned up
        hbiSeeder.insertRhelHost(orgId);
        assertEquals(1, hbiSeeder.getInsertedHostCount());
    }
}
```

---

## Troubleshooting

### Host Not Appearing in Tally

**Symptom:** Inserted host into HBI, but tally report shows 0 hosts/capacity

**Common causes:**

1. **Missing `last_check_in`**
   - TallyHbiDbSeeder sets this automatically
   - If you manually INSERT, ensure it's set to NOW()

2. **Missing `system_profiles_static` record**
   - The sync uses `INNER JOIN`
   - TallyHbiDbSeeder inserts into BOTH tables
   - If you manually INSERT, ensure both records exist

3. **Org not opted in**
   ```java
   service.createOptInConfig(orgId);  // Required before tally
   ```

4. **Tally not triggered**
   ```java
   service.tallyOrg(orgId);  // Must call this to sync HBI → Swatch
   ```

5. **Wrong product tag in query**
   ```java
   // RHEL hosts:
   service.getTallyReportData(orgId, "rhel-for-x86", "Sockets", ...);
   
   // Cloud hosts (different products):
   // Check swatch-product-configuration for correct tags
   ```

### Schema Errors

**Symptom:** 
```
RuntimeException: Failed to insert RHEL host into HBI database

SCHEMA ERROR: Required HBI table or column is missing.
```

**Solutions:**

**Local environment:**
1. Check if HBI migrations have run:
   ```bash
   podman exec rhsm-subscriptions-db-1 psql -U insights -d insights -c "\dt hbi.*"
   ```
2. If tables missing, see main README for HBI database setup

**EE environment:**
1. Check if `host-inventory-run-db-migrations` job completed:
   ```bash
   oc get jobs -n ephemeral-XXXX | grep migrations
   ```
2. If job failed, check pod logs for migration errors

### Socket Count Doesn't Match Expected

**Symptom:** Inserted host with 1 socket, but tally reports 2

**This is expected behavior!** RHEL physical hosts have socket increase mapping:

```java
// TallyHbiDbSeeder.RHEL_PER_SOCKET_INCREASE
Map.of(1, 2, 2, 2, 4, 4, 7, 8)
```

**To get the expected value:**
```java
int reported = TallyHbiDbSeeder.applyRhelSocketIncrease(actualSockets);
assertEquals(reported, tallyReportValue);
```

### Tests Leave Data Behind

**Symptom:** Next test sees hosts from previous test

**Solution:** Always use `@AfterEach` cleanup:
```java
@AfterEach
void cleanup() {
    hbiSeeder.deleteAllInsertedHosts();
}
```

**Verify cleanup:**
```java
@Test
void testCleanup() {
    hbiSeeder.insertRhelHost(orgId);
    assertEquals(1, hbiSeeder.getInsertedHostCount());
    
    hbiSeeder.deleteAllInsertedHosts();
    assertEquals(0, hbiSeeder.getInsertedHostCount());
}
```

---

## Best Practices

### ✅ DO

- **Always** call `deleteAllInsertedHosts()` in `@AfterEach`
- Use the builder pattern for readable tests
- Set `displayName` to something meaningful for debugging
- Use `service.createOptInConfig(orgId)` before tally operations
- Verify host exists: `assertTrue(hbiSeeder.hostExists(host.hostId()))`

### ❌ DON'T

- Don't skip cleanup - causes test pollution
- Don't manually INSERT into HBI - use the seeder (it handles both tables)
- Don't forget to trigger tally: `service.tallyOrg(orgId)`
- Don't assume socket count = inserted value (check socket increase mapping)
- Don't use HBI seeder for PAYG event tests (use Swatch seeder instead)

---

## Field Reference Quick Lookup

| Field | Table | Seeder Sets | Required | Purpose |
|-------|-------|-------------|----------|---------|
| `org_id` | hosts | ✅ | YES | Org identifier |
| `id` | hosts | ✅ (UUID) | YES | Host PK |
| `insights_id` | hosts | ✅ (UUID) | YES | Sync key |
| `subscription_manager_id` | hosts | ✅ | For RHEL | Sync key |
| `provider_id` | hosts | ✅ | For Cloud | Sync key |
| `last_check_in` | hosts | ✅ (NOW) | **CRITICAL** | Staleness filter |
| `reporter` | hosts | ✅ ("component-test") | YES | Primary reporter (VARCHAR) |
| `reporters` | hosts | ✅ (["component-test"]) | YES | All reporters (ARRAY) |
| `groups` | hosts | ✅ ([]) | YES | Host groups |
| `display_name` | hosts | ✅ | No | Human-readable name |
| `cores_per_socket` | system_profiles_static | ✅ | No | Capacity calc |
| `number_of_sockets` | system_profiles_static | ✅ | No | Capacity calc |
| `infrastructure_type` | system_profiles_static | ✅ | No | Socket increase logic |
| `cloud_provider` | system_profiles_static | ✅ | For Cloud | Provider metadata |

---

## Related Documentation

- **SEEDED_HOST_VS_PRODUCTION.md** - Covers the **Swatch database** side (hosts, host_tally_buckets)
- **HBI_DATABASE_SETUP.md** - Legacy JSONB-only schema (outdated)
- **TallyHbiDbSeeder.java** - Source code with full API documentation

---

**Last Updated:** 2026-07-02  
**Maintained By:** Swatch Team
