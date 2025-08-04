# SWATCH-3545: Event Conflict Resolution Bug Analysis

## Executive Summary

SWATCH-3545 was an intermittent bug in the event conflict resolution system that caused incorrect deduction event values during event sourcing operations. The bug manifested only under specific timing conditions involving Kafka batch processing, database transaction boundaries, and event arrival patterns.

## Root Cause Analysis

### The Core Issue: Timestamp vs RecordDate Logic

The bug was rooted in the `UsageConflictTracker.track()` method, which used **`recordDate`** (when an event was saved to the database) instead of **`timestamp`** (the actual event time) to determine which event was "latest" for conflict resolution purposes.

**Problematic Logic:**
```java
// BROKEN: Used recordDate (DB save time) instead of timestamp (event time)
Optional<OffsetDateTime> eventRecordDate = Optional.ofNullable(event.getRecordDate());
Optional<OffsetDateTime> latestEventRecordDate = 
    Optional.ofNullable(keyToLatestEvent.get(key).getRecordDate());

if (eventRecordDate.isEmpty() || 
    (latestEventRecordDate.isPresent() && 
     eventRecordDate.get().isAfter(latestEventRecordDate.get()))) {
    keyToLatestEvent.put(key, event);  // Wrong "latest" determination
}
```

**Impact:**
- Incoming events (with `recordDate = null`) were always considered "latest" regardless of their actual `timestamp`
- This caused deduction events to use wrong measurement values from chronologically older events
- Example: Event 3 (timestamp: 10:02) would create a deduction based on Event 1 (timestamp: 10:00) instead of Event 2 (timestamp: 10:01)

## The Perfect Storm: Why It Was Intermittent

The bug only manifested when multiple specific conditions aligned, creating a "perfect storm" scenario:

### 1. **Kafka Batch Size Effects**
- **High Volume Periods**: Larger Kafka batches (up to 500 events) increased probability of:
  - Multiple conflicting events in same batch
  - Transaction failures due to database constraints or deadlocks
  - Fallback to individual event processing

- **Low Volume Periods**: Smaller batches masked the issue:
  - Lower probability of conflicts within single batch
  - Successful batch processing meant proper intra-batch deduplication
  - Bug didn't surface even though logic was still wrong

### 2. **Transaction Boundary Timing**
- **Batch Processing Success**: All events get same `recordDate` → bug masked
- **Batch Processing Failure**: Events retry individually with different `recordDate` timestamps → bug exposed

### 3. **Event Arrival Patterns**
- **Chronological Order**: Events arriving in timestamp order masked the bug
- **Out-of-Order Arrival**: Events arriving with later `recordDate` but earlier `timestamp` exposed the bug

### 4. **Database Load Conditions**
- **Low Load**: Transactions succeed quickly, less timing variance
- **High Load**: Transaction delays, constraint violations, and retries amplified timing differences

## Database Transaction Analysis

### REQUIRES_NEW Transaction Boundaries

The system uses `@Transactional(propagation = Propagation.REQUIRES_NEW)` which created critical timing effects:

```java
// EventController.persistServiceInstances()

// Batch Processing (Single Transaction)
transactionHandler.runInNewTransaction(() -> {  
    List<EventRecord> resolved = resolveEventConflicts(allEventsInBatch);
    return repo.saveAll(resolved);  // All events get SAME recordDate
});

// Individual Processing (Multiple Transactions) 
result.indexedEvents.forEach(indexedPair -> {
    transactionHandler.runInNewTransaction(() ->    // Each event = separate transaction!
        repo.saveAll(eventConflictResolver.resolveIncomingEvents(List.of(event))));
        // Each event gets DIFFERENT recordDate
});
```

### Transaction Interaction Timeline

1. **Initial Batch Processing** (single transaction):
   ```
   Transaction T1: Events 1,2,3 → recordDate = 10:00:00.123
   ```

2. **Batch Fails** (constraint violation, deadlock, etc.)

3. **Individual Retry Processing** (separate transactions):
   ```
   Transaction T2: Event 1 → recordDate = 10:00:00.456
   Transaction T3: Event 2 → recordDate = 10:00:00.789  
   Transaction T4: Event 3 → recordDate = 10:00:01.012
   ```

4. **Bug Manifestation**:
   - Event 2 processes: sees Event 1 in DB, creates correct deduction (-1.0)
   - Event 3 processes: should see Event 2 as "latest", but `recordDate` logic sees Event 1
   - Result: Event 3 creates wrong deduction (-1.0 instead of -2.0)

## Solution Implementation

### 1. **Fixed Core Logic in UsageConflictTracker**

**Before (Broken):**
```java
// Used recordDate (DB save time) for "latest" determination
if (eventRecordDate.isEmpty() || eventRecordDate.get().isAfter(latestEventRecordDate.get()))
```

**After (Fixed):**
```java
// Use timestamp (event time) first, recordDate as tiebreaker
OffsetDateTime eventTimestamp = event.getTimestamp();
OffsetDateTime latestTimestamp = currentLatest.getTimestamp();

if (eventTimestamp.isAfter(latestTimestamp) || 
    (eventTimestamp.equals(latestTimestamp) && 
     compareRecordDates(event.getRecordDate(), currentLatest.getRecordDate()) > 0)) {
    keyToLatestEvent.put(key, event);
}
```

### 2. **Added Transaction-Aware RecordDate Comparison**
```java
private int compareRecordDates(OffsetDateTime first, OffsetDateTime second) {
    if (first == null && second == null) return 0;
    if (first == null) return 1;  // null (unpersisted) is considered "later"
    if (second == null) return -1;
    return first.compareTo(second);
}
```

## EventConflictType Enum Introduction

### Motivation
The existing code had implicit event conflict types that were hard to understand and debug. We introduced explicit classification to improve:
- **Code Readability**: Self-documenting event types
- **Debugging**: Clear logging of conflict scenarios  
- **Maintainability**: Easier to understand and modify conflict logic
- **Testing**: Comprehensive coverage of all scenarios

### Event Type Classifications

```java
public enum EventConflictType {
    /**
     * First time an event is seen for this EventKey.
     * No conflicts - event is saved as-is.
     */
    ORIGINAL,

    /**
     * Duplicate event with identical measurements and descriptors.
     * Ignored completely (idempotent behavior).  
     */
    IDENTICAL,

    /**
     * Same usage descriptors, different measurement values.
     * Creates deduction event + new measurement event.
     */
    CORRECTIVE,

    /**
     * Different usage descriptors, same measurement values.
     * Creates deduction event + new context event.
     */
    CONTEXTUAL,

    /**
     * Different usage descriptors AND different measurements.
     * Creates deduction event + new comprehensive event.
     */
    COMPREHENSIVE;
}
```

### Implementation Changes

**Enhanced EventConflictResolver:**
```java
// Old approach
private boolean amendmentRequired(Event incomingEvent, Event resolvedEvent, String metricId) {
    // Complex boolean logic - hard to understand
}

// New approach  
private EventConflictType determineConflictType(Event incomingEvent, 
                                               UsageConflictKey conflictKey, 
                                               UsageConflictTracker tracker) {
    if (!tracker.contains(conflictKey)) {
        return EventConflictType.ORIGINAL;
    }
    
    Event existingEvent = tracker.getLatest(conflictKey);
    UsageDescriptor incomingDescriptor = new UsageDescriptor(incomingEvent);
    UsageDescriptor existingDescriptor = new UsageDescriptor(existingEvent);
    
    Measurement incomingMeasurement = findMeasurement(incomingEvent, metricId);
    Measurement existingMeasurement = findMeasurement(existingEvent, metricId);
    
    boolean measurementEqual = Objects.equals(incomingMeasurement.getValue(), 
                                            existingMeasurement.getValue());
    boolean descriptorEqual = incomingDescriptor.equals(existingDescriptor);
    
    if (measurementEqual && descriptorEqual) {
        return EventConflictType.IDENTICAL;
    } else if (descriptorEqual && !measurementEqual) {
        return EventConflictType.CORRECTIVE;
    } else if (!descriptorEqual && measurementEqual) {
        return EventConflictType.CONTEXTUAL;
    } else {
        return EventConflictType.COMPREHENSIVE;
    }
}
```

**Improved Logging:**
```java
EventConflictType conflictType = determineConflictType(event, conflictKey, tracker);
log.debug("Processing {} event for conflict key: {}", conflictType, conflictKey);
```

## Testing Strategy

### Comprehensive Test Coverage
We created integration tests covering all EventConflictType scenarios:

1. **`testOriginalEventConflictType()`** - First event, no conflicts
2. **`testIdenticalEventConflictType()`** - Duplicate event ignored  
3. **`testCorrectiveEventConflictType()`** - Measurement change
4. **`testContextualEventConflictType()`** - Descriptor change
5. **`testComprehensiveEventConflictType()`** - Both measurement and descriptor change
6. **`testEventConflictTypeSequence()`** - Complex scenario with multiple types
7. **`testIntraBatchConflictResolutionTransactionFix()`** - Transaction boundary testing

### Transaction Boundary Validation
The tests validate behavior across both successful batch processing and failed batch processing scenarios, ensuring the fix works regardless of transaction boundaries.

## Impact and Resolution

### Before Fix
- **Intermittent incorrect deduction values** during high-volume periods
- **Difficult to reproduce** due to timing dependencies
- **Silent data corruption** in event sourcing audit trail
- **Customer impact** during peak usage periods

### After Fix  
- **Consistent deduction values** regardless of transaction timing
- **Deterministic behavior** based on actual event timestamps
- **Improved code maintainability** with explicit conflict types
- **Comprehensive test coverage** preventing regressions

## Key Takeaways

1. **Semantic vs Implementation Time**: Always use semantic timestamps (event time) rather than implementation timestamps (save time) for business logic
2. **Transaction Boundary Awareness**: Consider how transaction strategies like `REQUIRES_NEW` affect data consistency
3. **Intermittent Bug Investigation**: Look for timing-dependent conditions and "perfect storm" scenarios
4. **Explicit Classifications**: Use enums to make implicit behavior explicit and self-documenting
5. **Comprehensive Testing**: Test transaction boundary conditions, not just happy path scenarios

This fix resolves SWATCH-3545 and provides a foundation for more reliable event conflict resolution in high-volume, distributed transaction environments.