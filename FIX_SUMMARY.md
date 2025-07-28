# Intra-Batch Conflict Bug Fix Summary

## Problem Description

The "stuck event deduction cascade" bug was caused by `REQUIRES_NEW` transaction isolation in `EventController.persistServiceInstances()`. This caused the conflict resolver to miss uncommitted events in the same batch, leading to incorrect and cascading deductions.

**Bug Pattern:** `[1.0, -1.0, 2.0, -2.0, 3.0]` instead of expected `[1.0, -1.0, 3.0]`

## Root Cause

The issue was in `EventController.persistServiceInstances()`:

```java
// ❌ PROBLEM: This created a NEW transaction context
transactionHandler.runInNewTransaction(() -> {
    // Conflict resolution happened in ISOLATED transaction
    List<EventRecord> resolvedEvents = eventConflictResolver.resolveIncomingEvents(...);
    // Save events in this isolated transaction
});
```

**What This Caused:**
- Transaction Isolation: Conflict resolution happened in a completely separate transaction context
- Race Conditions: Events committed in the outer transaction were invisible to the inner transaction
- Missed Conflicts: The conflict resolver couldn't see recently committed events from the same batch
- "Stuck" Events: Events appeared to get "stuck" because they kept getting reprocessed without proper conflict detection

## The Fix

**Removed the REQUIRES_NEW Transaction:**

```java
// ✅ FIX: Single transaction context
List<EventRecord> resolvedEvents = eventConflictResolver.resolveIncomingEvents(...);
saveEventsIndividually(resolvedEvents);
// Only use REQUIRES_NEW for individual retries
```

**What This Achieves:**
- Single Transaction Context: Conflict resolution happens in the same transaction as event saving
- Proper Visibility: The conflict resolver can see ALL events in the current batch
- Immediate Conflict Detection: Conflicts are detected and resolved immediately, not later
- No "Stuck" Events: Events are properly processed once and don't get reprocessed

## Files Modified

1. **`src/main/java/org/candlepin/subscriptions/event/EventController.java`**
   - Removed `transactionHandler.runInNewTransaction()` from `persistServiceInstances()`
   - Ensured conflict resolution happens in the same transaction as event saving

## Testing the Fix

### Prerequisites
- Restart the `swatch-tally` service to load the updated code
- Ensure your database and infrastructure are running

### Test Commands

1. **Test the direct API path (current test):**
   ```bash
   python3 test_intra_batch_conflict.py
   ```
   - This tests the `InternalTallyDataController.saveEvents()` path
   - May still show the bug as it doesn't go through the fixed method

2. **Test the Kafka consumer path (where the fix applies):**
   ```bash
   # Enable dev mode or manual event editing
   export DEV_MODE=true

   # Restart the service
   ./mvnw -pl swatch-tally spring-boot:run

   # Run the Kafka test
   python3 test_kafka_intra_batch_conflict.py
   ```

3. **Test with the comprehensive reproducer:**
   ```bash
   pytest test_intra_batch_conflict_reproducer.py::TestIntraBatchConflictReproducer::test_intra_batch_conflict_cascade -v -s
   ```

### Expected Results

**Before Fix:**
```
Values: [1.0, -1.0, 2.0, -2.0, 3.0]
🎯 BUG REPRODUCED! Intra-batch conflict cascade detected!
```

**After Fix:**
```
Values: [1.0, -1.0, 3.0]
✅ Correct behavior - bug is fixed
```

## Code Paths

### Current Test Path (InternalTallyDataController)
```
POST /v1/internal/rpc/tally/events
→ InternalTallyResource.saveEvents()
→ InternalTallyDataController.saveEvents()
→ eventController.resolveEventConflicts() (direct call)
→ eventController.saveAllEventRecords()
```

### Kafka Consumer Path (where fix applies)
```
POST /v1/internal/rpc/tally/events (with dev mode enabled)
→ InternalTallyResource.saveEvents()
→ eventKafkaTemplate.send() (to Kafka)
→ ServiceInstanceMessageConsumer.receive()
→ EventController.persistServiceInstances() ← FIXED METHOD
```

## Verification Steps

1. **Restart the service** to ensure the fix is loaded
2. **Enable dev mode** to test the Kafka path: `export DEV_MODE=true`
3. **Run the Kafka test** to verify the fix works in the actual processing path
4. **Check logs** for any transaction-related errors
5. **Verify database state** after processing events

## Related Issues

- **SWATCH-3545**: Intra-batch conflict resolution bug
- **SWATCH-3809**: Stuck event deduction cascade

## Notes

- The fix only applies to the Kafka consumer path (`EventController.persistServiceInstances()`)
- The direct API path (`InternalTallyDataController.saveEvents()`) doesn't have the same transaction isolation issue
- To test the fix, you need to enable dev mode or manual event editing to use the Kafka path
- The fix maintains backward compatibility and doesn't affect other transaction patterns