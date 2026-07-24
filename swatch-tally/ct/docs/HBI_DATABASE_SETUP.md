# HBI Database Setup for Component Tests

This document describes the required HBI (Host-Based Inventory) database schema for running component tests with nightly tally.

## Overview

The component tests connect to two databases:
- **`insights`** database - HBI schema (read-only for tally)
- **`rhsm-subscriptions`** database - Swatch schema (read-write)

The nightly tally process reads from HBI and syncs data into the Swatch database.

## Required HBI Schema Tables

### 1. `hbi.hosts`

Main hosts table with facts and metadata.

**Required columns:**
```sql
CREATE TABLE hbi.hosts (
  id UUID PRIMARY KEY,
  org_id VARCHAR(255) NOT NULL,
  account VARCHAR(255),
  display_name VARCHAR(255),
  modified_on TIMESTAMP WITH TIME ZONE,
  facts JSONB DEFAULT '{}'::jsonb,
  stale_timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW() + INTERVAL '7 days',
  subscription_manager_id VARCHAR(255),
  insights_id UUID,
  provider_id VARCHAR(255),
  last_check_in TIMESTAMP WITH TIME ZONE DEFAULT NOW()  -- Required for staleness filtering
);
```

**Important fields:**
- `facts` - JSONB containing product information:
  ```json
  {
    "rhsm": {
      "RH_PROD": ["69"],           // Product IDs
      "IS_VIRTUAL": "false",
      "ARCHITECTURE": "x86_64",
      "CORES": "8",                // Deprecated - use system_profile instead
      "SOCKETS": "2"               // Deprecated - use system_profile instead
    }
  }
  ```
- `last_check_in` - Used for staleness filtering (must be recent or host is filtered out)
- `stale_timestamp` - Timestamp when host becomes stale (should be > NOW())

### 2. `hbi.system_profiles_static`

Capacity and system information per host.

**Required table:**
```sql
CREATE TABLE hbi.system_profiles_static (
  org_id VARCHAR(255) NOT NULL,
  host_id UUID NOT NULL,
  virtual_host_uuid UUID,
  infrastructure_type VARCHAR(255),        -- e.g., "physical", "virtual"
  cores_per_socket INTEGER,                -- Cores per socket
  number_of_sockets INTEGER,               -- Number of sockets (CRITICAL for RHEL tally)
  number_of_cpus INTEGER,
  threads_per_core INTEGER,
  cloud_provider VARCHAR(255),
  arch VARCHAR(255),                       -- e.g., "x86_64"
  is_marketplace BOOLEAN,
  conversions JSONB DEFAULT '{}'::jsonb,
  host_type VARCHAR(255),
  PRIMARY KEY (org_id, host_id)
);
```

**Note:** The HBI query does an **INNER JOIN** between `hosts` and `system_profiles_static`, so both records MUST exist for a host to be synced.

### 3. `hbi.staleness`

Staleness configuration per organization (optional but recommended).

**Required table:**
```sql
CREATE TABLE hbi.staleness (
  org_id VARCHAR(255) PRIMARY KEY,
  conventional_time_to_stale INTEGER,
  conventional_time_to_stale_warning INTEGER,
  conventional_time_to_delete INTEGER,
  immutable_time_to_stale INTEGER,
  immutable_time_to_stale_warning INTEGER,
  immutable_time_to_delete INTEGER
);
```

**Note:** The query LEFT JOINs this table, so it's optional. If missing, default staleness values are used.

## Database Setup for Tests

The component test framework creates these tables automatically in the `insights` database. However, if running tests manually, you may need to create them:

```bash
# Connect to the insights database
podman exec rhsm-subscriptions_db psql -U postgres -d insights

# Create the required tables (if not exists)
CREATE TABLE IF NOT EXISTS hbi.hosts (...);
CREATE TABLE IF NOT EXISTS hbi.system_profiles_static (...);
CREATE TABLE IF NOT EXISTS hbi.staleness (...);
```

## TallyHbiDbSeeder Usage

The `TallyHbiDbSeeder` class handles inserting test data into both required tables:

```java
TallyHbiDbSeeder hbiSeeder = new TallyHbiDbSeeder();

// Insert RHEL host with defaults (4 cores, 2 sockets)
SeededHost host = hbiSeeder.insertRhelHost(orgId);

// Insert RHEL host with custom capacity
SeededHost host = hbiSeeder.insertRhelHost(orgId, 8, 2); // 8 cores, 2 sockets

// Cleanup (should be called in @AfterEach)
hbiSeeder.deleteAllInsertedHosts();
```

The seeder automatically:
- Inserts into `hbi.hosts` with product facts
- Inserts into `hbi.system_profiles_static` with capacity data
- Sets `last_check_in` to NOW() (fresh host)
- Sets `stale_timestamp` to NOW() + 7 days (non-stale)
- Tracks inserted hosts for rollback on test failure

## Common Issues

### Issue: Host not syncing (0 hosts in Swatch DB)

**Causes:**
1. Missing `hbi.system_profiles_static` record (INNER JOIN fails)
2. `last_check_in` is NULL or too old (staleness filter)
3. `stale_timestamp` < NOW() (host marked stale)
4. Missing `hbi.staleness` table (query fails)

**Solution:**
- Ensure both `hosts` and `system_profiles_static` records exist
- Set `last_check_in = NOW()`
- Set `stale_timestamp = NOW() + INTERVAL '7 days'`

### Issue: Tally reports show 0 capacity

**Causes:**
1. Missing capacity data in `system_profiles_static`
2. Wrong product ID in `facts->'rhsm'->'RH_PROD'`

**Solution:**
- Verify `cores_per_socket` and `number_of_sockets` are set
- Use correct product ID (69 for RHEL, see `swatch-product-configuration`)

## Product ID Mapping

Common product IDs for testing:
- **69** - Red Hat Enterprise Linux Server (RHEL for x86)
- **479** - RHEL for x86 catch-all
- **71** - Red Hat Enterprise Linux Workstation
- **76** - Red Hat Enterprise Linux for Scientific Computing

See `swatch-product-configuration/src/main/resources/subscription_configs/` for full product mappings.
