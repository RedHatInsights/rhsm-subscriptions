# SWATCH-3545 Bug Reproducer Analysis

## Overview

This document contains the analysis and reproducers for SWATCH-3545: "Conflict resolution periodically creates incorrect amendment event".

## Bug Description

From the JIRA ticket, the bug manifests as:
- **Expected behavior**: Second deduction should be `-10.0` (deducting the previous value of 10.0)
- **Bug behavior**: Second deduction is `-1.0` (incorrectly deducting the original value of 1.0)

## Test Steps from Original Bug Report

1. Send event with cores: 1.0
   - Creates event: cores: 1.0

2. Send event with cores: 10.0
   - Original event remains: cores: 1.0
   - Amendment event created: cores: -1.0
   - New event created: cores: 10.0

3. Send event with cores: 100.0
   - Existing events remain: cores: 1.0, -1.0, 10.0
   - **Expected**: New amendment event: cores: -10.0
   - **Bug**: New amendment event: cores: -1.0 (incorrect)
   - New event created: cores: 100.0

## Reproducers Created

### 1. Basic Kafka Reproducer (`test_cascade_bug.py`)
- Sends events via Kafka Bridge API
- Uses the exact values from SWATCH-3545 (1.0, 10.0, 100.0)
- Includes analysis logic to detect the bug

### 2. Internal API Reproducer (`test_cascade_bug_internal_api.py`)
- Attempts to use internal API (matching original test conditions)
- Requires swatch-tally service to be running on localhost:8080

### 3. Race Condition Reproducer (`test_cascade_bug_race_condition.py`)
- Tests multiple scenarios to trigger race conditions:
  - Quick succession events
  - Parallel thread execution
  - Specific timing patterns
- Most comprehensive test for transaction isolation issues

## Test Results

All reproducers consistently show **correct behavior**:

```
Found 2 deduction events:
  Deduction 1: cores = -1.0 (record_date: ...)
  Deduction 2: cores = -10.0 (record_date: ...)

✅ CORRECT BEHAVIOR: Second deduction is -10.0
Conflict resolution is working correctly
```

**Note**: Despite the user indicating that "nothing has been resolved in the current codebase for this bug," all reproducers show correct behavior. This suggests the bug may require very specific conditions not yet replicated.

## Analysis

### Why the Bug is Not Reproducing

1. **Very Specific Conditions**: The bug may require very specific timing, load, or configuration conditions that we haven't replicated
2. **Environment Differences**: The original bug occurred in IQE environment which may have different load patterns, configurations, or infrastructure
3. **Intermittent Nature**: The bug may be truly intermittent and require many more runs or specific conditions to trigger
4. **Different Code Paths**: The bug might only manifest under certain code paths or configurations not present in our test setup
5. **Transaction Isolation**: The race condition that caused the bug may have been addressed through improved transaction handling

### Key Differences from Original Test

1. **Environment**: Original test was in IQE environment, our tests are in local environment
2. **API Path**: Original used internal API, our tests use Kafka Bridge API
3. **Timing**: Original may have had specific timing conditions that triggered the race condition
4. **Data Volume**: Original may have been under different load conditions

## Usage Instructions

### Prerequisites
- Kafka Bridge running on localhost:9080
- PostgreSQL running with rhsm-subscriptions database
- swatch-tally service running to consume Kafka messages

### Running the Tests

```bash
# Basic Kafka reproducer
python3 test_cascade_bug.py

# Race condition reproducer (most comprehensive)
python3 test_cascade_bug_race_condition.py

# Internal API reproducer (requires service on localhost:8080)
python3 test_cascade_bug_internal_api.py
```

### Expected Output (Correct Behavior)

```
=== Found 5 events ===
Event 1: Cores Value: 1.0
Event 2: Cores Value: -1.0 (Amendment Type: deduction)
Event 3: Cores Value: 10.0
Event 4: Cores Value: -10.0 (Amendment Type: deduction)
Event 5: Cores Value: 100.0

✅ CORRECT BEHAVIOR: Second deduction is -10.0
```

### Bug Output (If Reproduced)

```
=== Found 5 events ===
Event 1: Cores Value: 1.0
Event 2: Cores Value: -1.0 (Amendment Type: deduction)
Event 3: Cores Value: 10.0
Event 4: Cores Value: -1.0 (Amendment Type: deduction)  # BUG: Should be -10.0
Event 5: Cores Value: 100.0

🚨 SWATCH-3545 BUG DETECTED! 🚨
Second deduction is -1.0 (should be -10.0)
```

## Conclusion

Despite extensive testing with multiple approaches, the SWATCH-3545 bug is not reproducing in our local environment. All reproducers consistently show correct conflict resolution behavior, with proper deduction values being created.

This suggests the bug may require very specific conditions, environment setup, or load patterns that we haven't replicated. The reproducers remain valuable for:

1. **Regression testing** to ensure the fix remains in place
2. **Future debugging** if similar issues arise
3. **Understanding** the conflict resolution logic
4. **Investigating** the specific conditions that trigger the bug

## Files

- `test_cascade_bug.py` - Basic Kafka reproducer
- `test_cascade_bug_internal_api.py` - Internal API reproducer
- `test_cascade_bug_race_condition.py` - Race condition reproducer
- `SWATCH-3545-Reproducer-Analysis.md` - This analysis document 