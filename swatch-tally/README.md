# Swatch Tally - Cascading Deductions Fix

This directory contains the implementation and testing for the cascading deductions bug fix in the RHSM Subscriptions system.

## Overview

The cascading deductions bug was a transaction isolation issue that occurred when multiple conflicting events with the same instance ID and timestamp were processed in a single Kafka batch. This caused incorrect deduction events to be created, resulting in data integrity issues.

## Files

### `test_cascade_bug.py`
A Python test script that reproduces the cascading deductions bug using synthetic data and Kafka events.

**Usage:**
```bash
python3 test_cascade_bug.py
```

**Prerequisites:**
- Kafka Bridge running on localhost:9080
- PostgreSQL running with rhsm-subscriptions database
- swatch-tally service running to consume Kafka messages

**Expected behavior (after fix):**
- Only 1 event should be saved (the highest value: 4.0)
- No deduction events should be created
- Total events: 1

**Bug behavior (before fix):**
- Multiple events would be saved: [2.0, -2.0, 3.0, -3.0, 4.0]
- Deduction events would be created incorrectly
- Total events: 5

## The Fix

The fix involved implementing batch processing logic in `EventConflictResolver`:

1. **Intra-batch Deduplication**: Remove exact duplicates within a batch
2. **Batch Conflict Resolution**: Process multiple events with the same conflict key together
3. **Highest Value Selection**: When multiple events have the same conflict key but different values, select the highest value
4. **Single Event Processing**: Only process one event per conflict key against the database

### Key Methods Added/Modified:

- `resolveIntraBatchConflicts()`: Handles conflicts within a batch
- `resolveToFinalEvent()`: Selects the final event from conflicting events
- `processEventAgainstDatabase()`: Processes a single event against the database
- `deduplicateIntraBatchEvents()`: Removes exact duplicates within a batch

### Updated Flow:

1. **Before**: Individual event processing → cascading deductions
2. **After**: Batch processing → single highest value event

## Testing

The fix is verified through:

1. **Integration Test**: `EventControllerIT#testCascadingDeductionsBug()`
2. **Manual Test**: `test_cascade_bug.py` script
3. **Unit Tests**: Existing conflict resolution tests

## Related Documentation

See `event_transformation_diagrams.md` for detailed visual diagrams showing the event processing flow before and after the fix. 