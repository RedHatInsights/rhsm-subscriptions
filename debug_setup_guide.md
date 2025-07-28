# Debug Setup Guide: Intra-Batch Conflict Bug

## Overview
This guide shows you how to manually reproduce the intra-batch conflict bug using multiple replicas and a debugger.

## Prerequisites
- Your services are running (swatch-tally, swatch-metrics, database)
- IDE with Java debugging capabilities (IntelliJ IDEA, Eclipse, VS Code)
- Python 3 with requests and psycopg2

## Step 1: Start Multiple swatch-tally Instances

### Option A: Multiple Ports (Recommended)
```bash
# Terminal 1: Instance 1
SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2 \
MANAGEMENT_SERVER_PORT=9003 \
SERVER_PORT=8003 \
DEV_MODE=true \
./mvnw -pl swatch-tally spring-boot:run

# Terminal 2: Instance 2
SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2 \
MANAGEMENT_SERVER_PORT=9004 \
SERVER_PORT=8004 \
DEV_MODE=true \
./mvnw -pl swatch-tally spring-boot:run
```

### Option B: Multiple JVMs (Advanced)
```bash
# Terminal 1: Instance 1
JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005" \
SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2 \
MANAGEMENT_SERVER_PORT=9003 \
SERVER_PORT=8003 \
DEV_MODE=true \
./mvnw -pl swatch-tally spring-boot:run

# Terminal 2: Instance 2
JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5006" \
SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2 \
MANAGEMENT_SERVER_PORT=9004 \
SERVER_PORT=8004 \
DEV_MODE=true \
./mvnw -pl swatch-tally spring-boot:run
```

## Step 2: Set Up Debugger

### IntelliJ IDEA Setup
1. **Create Run Configuration:**
   - Go to Run → Edit Configurations
   - Add new "Remote JVM Debug" configuration
   - Set host: `localhost`, port: `5005` (for Instance 1)
   - Repeat for port `5006` (for Instance 2)

2. **Set Breakpoints:**
   ```
   EventController.persistServiceInstances() - Line 1
   EventController.persistServiceInstances() - Inside REQUIRES_NEW transaction
   EventConflictResolver.resolveIncomingEvents() - Start of method
   ```

### VS Code Setup
1. **Create launch.json:**
   ```json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "type": "java",
         "name": "Debug Instance 1",
         "request": "attach",
         "hostName": "localhost",
         "port": 5005
       },
       {
         "type": "java",
         "name": "Debug Instance 2",
         "request": "attach",
         "hostName": "localhost",
         "port": 5006
       }
     ]
   }
   ```

## Step 3: Run the Debug Script

```bash
python3 debug_intra_batch_conflict.py
```

## Step 4: Debug Scenarios

### Scenario 1: Sequential Events (Control)
- **Purpose:** Verify correct behavior
- **Expected:** `[1.0, -1.0, 3.0]`
- **Debug Points:** Watch single transaction flow

### Scenario 2: Batch Events (Bug Reproduction)
- **Purpose:** Reproduce the bug
- **Expected:** `[1.0, -1.0, 2.0, -2.0, 3.0]`
- **Debug Points:**
  - Set breakpoint at start of `persistServiceInstances()`
  - Step through REQUIRES_NEW transaction
  - Watch conflict resolution miss uncommitted events

### Scenario 3: Race Condition
- **Purpose:** Create race condition between instances
- **Debug Points:**
  - Set breakpoints in both instances
  - Watch how multiple threads interact
  - Observe transaction isolation

### Scenario 4: Step-by-Step
- **Purpose:** Manual control over each step
- **Debug Points:**
  - Control timing manually
  - Step through each transaction
  - Observe database state changes

## Step 5: Key Debug Points

### Breakpoint Locations
```java
// EventController.java
@Transactional(noRollbackFor = RuntimeException.class)
public void persistServiceInstances(List<Event> events) {
    // ⏸️ BREAKPOINT 1: Start of method
    // ... parse events ...

    transactionHandler.runInNewTransaction(() -> {
        // ⏸️ BREAKPOINT 2: Inside REQUIRES_NEW transaction
        List<EventRecord> resolvedEvents = eventConflictResolver.resolveIncomingEvents(...);
        // ⏸️ BREAKPOINT 3: After conflict resolution
    });

    // ⏸️ BREAKPOINT 4: After REQUIRES_NEW transaction
    saveEventsIndividually(resolvedEvents);
}
```

### What to Watch For
1. **Transaction Isolation:** Events in outer transaction not visible to inner transaction
2. **Conflict Resolution:** Missing conflicts due to isolation
3. **Database State:** Uncommitted events not visible to queries
4. **Race Conditions:** Multiple threads accessing same data

## Step 6: Database Monitoring

### Real-time Event Monitoring
```sql
-- Monitor events as they're created
SELECT
    data->>'event_id' as event_id,
    data->>'timestamp' as timestamp,
    data->>'instance_id' as instance_id,
    data->'measurements' as measurements,
    data->>'record_date' as record_date
FROM events
WHERE data->>'org_id' = '13259775'
  AND data->>'instance_id' LIKE 'debug-%'
ORDER BY data->>'record_date' DESC
LIMIT 10;
```

### Transaction Monitoring
```sql
-- Monitor active transactions
SELECT
    pid,
    usename,
    application_name,
    client_addr,
    state,
    query_start,
    query
FROM pg_stat_activity
WHERE state = 'active'
  AND query NOT LIKE '%pg_stat_activity%';
```

## Step 7: Expected Results

### When Bug Occurs
```
Values: [1.0, -1.0, 2.0, -2.0, 3.0]
🎯 BUG REPRODUCED! Intra-batch conflict cascade detected!
```

### When Bug is Fixed
```
Values: [1.0, -1.0, 3.0]
✅ Correct behavior - bug is fixed
```

## Troubleshooting

### Common Issues
1. **Port conflicts:** Use different ports for each instance
2. **Database locks:** Monitor for deadlocks or long-running transactions
3. **Kafka issues:** Check Kafka consumer group status
4. **Debugger connection:** Verify debug ports are available

### Debug Tips
1. **Use small batch sizes** (`SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2`)
2. **Monitor database state** in real-time
3. **Step through transactions** slowly
4. **Watch for race conditions** between instances
5. **Check transaction isolation levels**

## Next Steps
Once you've reproduced the bug:
1. **Identify the exact transaction flow** that causes the issue
2. **Test the fix** by removing REQUIRES_NEW
3. **Verify the fix** using the same debug scenarios
4. **Document the findings** for the development team