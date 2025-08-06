/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.AmendmentType;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Integration test that reproduces the cascading deduction bug described in SWATCH-3545. */
@SpringBootTest
@ActiveProfiles({"worker", "test-inventory"})
class CascadingDeductionBugIT implements ExtendWithSwatchDatabase {

  @Autowired EventController eventController;

  @Autowired EventRecordRepository eventRecordRepository;
  @Autowired EventConflictResolver eventConflictResolver;
  @Autowired ObjectMapper objectMapper;

  @AfterEach
  void cleanup() {
    eventRecordRepository.deleteAll();
  }

  /**
   * Test that reproduces the cascading deduction bug from SWATCH-3545.
   *
   * <p>This test simulates the exact scenario from events.json where: 1. First event:
   * 0.2857142857142857 vCPUs → persisted as-is 2. Second event: 1.0 vCPUs → creates
   * -0.2857142857142857 deduction + 1.0 record (CORRECT) 3. Third event: 1.0 vCPUs → should create
   * -1.0 deduction + 1.0 record (EXPECTED)
   *
   * <p>Bug behavior: Third event creates -0.2857142857142857 deduction + 1.0 record (BUG!) The
   * problem is that the system deducts the original value instead of the most recent value.
   */
  @Test
  void testCascadingDeductionBug() {
    // Step 1: Process first event with fractional value
    List<String> firstBatch =
        List.of(createEventJson("event1", 0.2857142857142857, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(firstBatch);

    List<EventRecord> firstResult = eventRecordRepository.findAll();
    assertEquals(1, firstResult.size());
    // Note: JSON serialization truncates the precision to 0.285714
    assertEquals(0.285714, firstResult.get(0).getEvent().getMeasurements().get(0).getValue());
    assertEquals(null, firstResult.get(0).getEvent().getAmendmentType());

    // Step 2: Process second event with value 1.0
    List<String> secondBatch = List.of(createEventJson("event2", 1.0, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(secondBatch);

    List<EventRecord> secondResult = eventRecordRepository.findAll();
    assertEquals(3, secondResult.size()); // original + deduction + new event

    // Verify the deduction event for the first event
    List<EventRecord> deductions =
        secondResult.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .toList();
    assertEquals(1, deductions.size());
    assertEquals(-0.285714, deductions.get(0).getEvent().getMeasurements().get(0).getValue());

    // Verify we have the new 1.0 event
    List<EventRecord> nonAmendments =
        secondResult.stream().filter(e -> e.getEvent().getAmendmentType() == null).toList();
    assertEquals(2, nonAmendments.size());

    // Step 3: Process third event with value 1.0 (same as second)
    // This is where the bug manifests - it should deduct the most recent value (1.0)
    // but instead deducts the original value (0.2857142857142857)
    List<String> thirdBatch = List.of(createEventJson("event3", 1.0, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(thirdBatch);

    List<EventRecord> finalResult = eventRecordRepository.findAll();

    // Find all deduction events
    List<EventRecord> allDeductions =
        finalResult.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .toList();

    assertEquals(2, allDeductions.size()); // Should have 2 deductions now

    // Sort deductions by record date to get them in order
    allDeductions.sort(
        (a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()));

    // First deduction should be -0.285714 (deducting the original value) - CORRECT
    assertEquals(-0.285714, allDeductions.get(0).getEvent().getMeasurements().get(0).getValue());

    // Second deduction should be -1.0 (deducting the most recent value) - THIS IS THE BUG
    // Currently it's -0.285714 (deducting the original value again)
    assertEquals(
        -1.0,
        allDeductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "BUG REPRODUCED: Third event should create deduction for the most recent value (-1.0), "
            + "but it creates deduction for the original value (-0.285714). "
            + "This demonstrates the cascading deduction bug where the system always deducts "
            + "the original value instead of tracking the most recent value for deduction.");
  }

  /**
   * Scenario 2: Different values to force deductions Testing with fractional -> integer ->
   * different fractional values
   */
  @Test
  void testCascadingDeductionWithDifferentValues() {
    // Step 1: Process first event with fractional value
    List<String> firstBatch = List.of(createEventJson("event1", 0.285714, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(firstBatch);

    // Step 2: Process second event with integer value
    List<String> secondBatch = List.of(createEventJson("event2", 2.0, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(secondBatch);

    // Step 3: Process third event with different fractional value
    List<String> thirdBatch = List.of(createEventJson("event3", 1.5, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(thirdBatch);

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(2, deductions.size());

    // First deduction should be -0.285714 (correct)
    assertEquals(-0.285714, deductions.get(0).getEvent().getMeasurements().get(0).getValue());

    // Second deduction should be -2.0 (deducting most recent), NOT -0.285714 (bug)
    assertEquals(
        -2.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "BUG: Should deduct the most recent value (-2.0), not the original (-0.285714)");
  }

  /**
   * Scenario 3: Rapid succession with very small time differences Simulates events arriving very
   * quickly which might trigger race conditions
   */
  @Test
  void testRapidSuccessionDeductions() throws InterruptedException {
    // Process events with very small delays to simulate race conditions
    eventController.persistServiceInstances(
        List.of(createEventJson("event1", 0.5, "2025-07-21T17:00:00Z")));
    Thread.sleep(10); // Very small delay

    eventController.persistServiceInstances(
        List.of(createEventJson("event2", 1.0, "2025-07-21T17:00:00Z")));
    Thread.sleep(10);

    eventController.persistServiceInstances(
        List.of(createEventJson("event3", 1.5, "2025-07-21T17:00:00Z")));
    Thread.sleep(10);

    eventController.persistServiceInstances(
        List.of(createEventJson("event4", 2.0, "2025-07-21T17:00:00Z")));

    List<EventRecord> deductions =
        eventRecordRepository.findAll().stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(3, deductions.size());
    assertEquals(-0.5, deductions.get(0).getEvent().getMeasurements().get(0).getValue());
    assertEquals(
        -1.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "Should deduct most recent value (-1.0), not original (-0.5)");
    assertEquals(
        -1.5,
        deductions.get(2).getEvent().getMeasurements().get(0).getValue(),
        "Should deduct most recent value (-1.5), not original (-0.5)");
  }

  /**
   * Scenario 4: Zero and negative values Tests edge cases with zero and negative measurement values
   */
  @Test
  void testZeroAndNegativeValueDeductions() {
    eventController.persistServiceInstances(
        List.of(createEventJson("event1", 1.0, "2025-07-21T17:00:00Z")));
    eventController.persistServiceInstances(
        List.of(createEventJson("event2", 0.0, "2025-07-21T17:00:00Z")));
    eventController.persistServiceInstances(
        List.of(createEventJson("event3", 2.0, "2025-07-21T17:00:00Z")));

    List<EventRecord> deductions =
        eventRecordRepository.findAll().stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(2, deductions.size());
    assertEquals(-1.0, deductions.get(0).getEvent().getMeasurements().get(0).getValue());
    assertEquals(
        -0.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "Should deduct most recent value (0.0), not original (1.0)");
  }

  /**
   * Scenario 5: Large precision decimal values Tests with high-precision decimal values that might
   * cause floating point issues
   */
  @Test
  void testHighPrecisionDecimalDeductions() {
    eventController.persistServiceInstances(
        List.of(createEventJson("event1", 0.123456789, "2025-07-21T17:00:00Z")));
    eventController.persistServiceInstances(
        List.of(createEventJson("event2", 0.987654321, "2025-07-21T17:00:00Z")));
    eventController.persistServiceInstances(
        List.of(createEventJson("event3", 0.555555555, "2025-07-21T17:00:00Z")));

    List<EventRecord> deductions =
        eventRecordRepository.findAll().stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(2, deductions.size());

    // Note: JSON serialization may truncate precision
    double firstDeduction = deductions.get(0).getEvent().getMeasurements().get(0).getValue();
    double secondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();

    assertTrue(firstDeduction < 0, "First deduction should be negative");
    assertTrue(secondDeduction < 0, "Second deduction should be negative");

    // The bug would show if second deduction equals first deduction (wrong)
    assertNotEquals(
        firstDeduction,
        secondDeduction,
        "BUG: Second deduction should not equal first deduction (cascading bug)");
  }

  /**
   * Scenario 6: Multiple metrics in same event Tests with events that have multiple measurements
   */
  @Test
  void testMultipleMetricsDeductions() {
    String multiMetricEvent1 =
        """
        {
           "sla": "Premium",
           "org_id": "13259775",
           "timestamp": "2025-07-21T17:00:00Z",
           "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
           "expiration": "2025-07-21T18:00:00Z",
           "instance_id": "i-0d83c7a09e2589d87",
           "display_name": "test_server",
           "event_source": "test-data",
           "measurements": [
             {
               "metric_id": "vCPUs",
               "value": 1.0
             },
             {
               "metric_id": "cores",
               "value": 2.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rhel-for-x86-els-payg"]
         }
        """;

    String multiMetricEvent2 =
        """
        {
           "sla": "Premium",
           "org_id": "13259775",
           "timestamp": "2025-07-21T17:00:00Z",
           "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
           "expiration": "2025-07-21T18:00:00Z",
           "instance_id": "i-0d83c7a09e2589d87",
           "display_name": "test_server",
           "event_source": "test-data",
           "measurements": [
             {
               "metric_id": "vCPUs",
               "value": 2.0
             },
             {
               "metric_id": "cores",
               "value": 4.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rhel-for-x86-els-payg"]
         }
        """;

    eventController.persistServiceInstances(List.of(multiMetricEvent1));
    eventController.persistServiceInstances(List.of(multiMetricEvent2));

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .toList();

    // Should have deductions for both metrics
    assertTrue(deductions.size() >= 2, "Should have deductions for multiple metrics");

    // Check that deductions are for the correct previous values, not always the original
    for (EventRecord deduction : deductions) {
      double value = deduction.getEvent().getMeasurements().get(0).getValue();
      assertTrue(value < 0, "All deductions should be negative");
    }
  }

  /**
   * Scenario 7: Same timestamp but different record dates Simulates the exact scenario from
   * events.json with same timestamp but different processing times
   */
  @Test
  void testSameTimestampDifferentRecordDates() {
    // All events have same timestamp but will have different record_date due to processing time
    String timestamp = "2025-07-21T17:00:00Z";

    eventController.persistServiceInstances(
        List.of(createEventJson("event1", 0.285714, timestamp)));

    // Small delay to ensure different record_date
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    eventController.persistServiceInstances(List.of(createEventJson("event2", 1.0, timestamp)));

    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    eventController.persistServiceInstances(List.of(createEventJson("event3", 1.0, timestamp)));

    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    eventController.persistServiceInstances(List.of(createEventJson("event4", 2.0, timestamp)));

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    // Verify we have the expected deductions
    assertTrue(deductions.size() >= 2, "Should have multiple deductions");

    // Check the cascading deduction bug pattern
    if (deductions.size() >= 2) {
      double firstDeduction = deductions.get(0).getEvent().getMeasurements().get(0).getValue();
      double lastDeduction =
          deductions.get(deductions.size() - 1).getEvent().getMeasurements().get(0).getValue();

      assertEquals(-0.285714, firstDeduction, 0.000001);
      assertNotEquals(
          firstDeduction,
          lastDeduction,
          "BUG: Last deduction should not equal first deduction (indicates cascading bug)");
    }
  }

  /**
   * Scenario 8: Large number sequence Tests with progressively larger values to see deduction
   * patterns
   */
  @Test
  void testProgressiveLargerValues() {
    double[] values = {0.1, 1.0, 10.0, 100.0, 1000.0};

    for (int i = 0; i < values.length; i++) {
      eventController.persistServiceInstances(
          List.of(createEventJson("event" + (i + 1), values[i], "2025-07-21T17:00:00Z")));
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    List<EventRecord> deductions =
        eventRecordRepository.findAll().stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(4, deductions.size()); // Should have 4 deductions for 5 events

    // Verify deduction progression - each should deduct the previous value, not the original
    assertEquals(-0.1, deductions.get(0).getEvent().getMeasurements().get(0).getValue(), 0.000001);
    assertEquals(
        -1.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        0.000001,
        "Should deduct -1.0 (previous), not -0.1 (original)");
    assertEquals(
        -10.0,
        deductions.get(2).getEvent().getMeasurements().get(0).getValue(),
        0.000001,
        "Should deduct -10.0 (previous), not -0.1 (original)");
    assertEquals(
        -100.0,
        deductions.get(3).getEvent().getMeasurements().get(0).getValue(),
        0.000001,
        "Should deduct -100.0 (previous), not -0.1 (original)");
  }

  /**
   * Test that shows the issue with intra-batch processing. When multiple conflicting events are
   * processed in a single batch, the UsageConflictTracker should track the latest event within that
   * batch.
   */
  @Test
  void testIntraBatchCascadingDeductionBug() {
    // Create multiple events in a single batch with the same EventKey
    List<String> batch =
        List.of(
            createEventJson("event1", 0.2857142857142857, "2025-07-21T17:00:00Z"),
            createEventJson("event2", 1.0, "2025-07-21T17:00:00Z"),
            createEventJson("event3", 1.0, "2025-07-21T17:00:00Z"));

    // Process all events in a single batch
    eventController.persistServiceInstances(batch);

    List<EventRecord> allEvents = eventRecordRepository.findAll();

    // Find deduction events
    List<EventRecord> deductionEvents =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .toList();

    // Sort deductions by record date
    deductionEvents =
        deductionEvents.stream()
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    // Expected: 2 deduction events
    // 1. First deduction: -0.2857142857142857 (deducting the original value)
    // 2. Second deduction: -1.0 (deducting the most recent value, not the original)
    assertEquals(2, deductionEvents.size());

    // First deduction should be -0.285714 (deducting the original value)
    assertEquals(-0.285714, deductionEvents.get(0).getEvent().getMeasurements().get(0).getValue());

    // Second deduction should be -1.0 (deducting the most recent value, not the original)
    // THIS IS WHERE THE BUG MANIFESTS IN INTRA-BATCH PROCESSING
    assertEquals(
        -1.0,
        deductionEvents.get(1).getEvent().getMeasurements().get(0).getValue(),
        "BUG REPRODUCED: Second deduction should be -1.0 (deducting the most recent value), "
            + "but it's -0.285714 (deducting the original value). "
            + "This demonstrates the intra-batch cascading deduction bug.");
  }

  /**
   * Test that reproduces the intra-batch conflict resolution transaction issue.
   *
   * <p>This test simulates the scenario where multiple events with the same conflict key arrive in
   * the same Kafka batch, causing inappropriate deduction events due to transaction isolation
   * issues.
   */
  @Test
  void testIntraBatchConflictResolutionTransactionFix() {
    // Create batch of events with same conflict key but different values
    List<String> eventBatch = createIntraBatchConflictEvents();

    // Process the batch - this reproduces the transaction issue scenario
    eventController.persistServiceInstances(eventBatch);

    // Verify the fix: should have correct conflict resolution pattern
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> nonAmendmentEvents =
        allEvents.stream().filter(e -> e.getEvent().getAmendmentType() == null).toList();
    List<EventRecord> amendmentEvents =
        allEvents.stream().filter(e -> e.getEvent().getAmendmentType() != null).toList();

    // Assert that the intra-batch conflict resolution is working correctly
    // Expected: Only 1 event (the final one) due to intra-batch deduplication
    assertEquals(1, nonAmendmentEvents.size());
    assertEquals(0, amendmentEvents.size());

    // Verify the final event has the latest measurement value
    EventRecord finalEvent = nonAmendmentEvents.get(0);
    assertEquals(3.0, finalEvent.getEvent().getMeasurements().get(0).getValue());
  }

  /**
   * IQE Pattern 1: Mixed Batch Processing IQE creates separate event batches for different time
   * periods, then merges them into a single batch before sending to the internal API. This could
   * cause stale conflict tracker initialization.
   */
  @Test
  void testIqeMixedBatchProcessing() {
    // Simulate IQE pattern: cluster_event1.append(cluster_event2[0])
    // Create events for different time periods, then merge into single batch

    // Events from "first batch" (earlier timestamp)
    String event1 = createEventJson("event1", 0.285714, "2025-07-21T16:00:00Z");
    String event2 = createEventJson("event2", 1.0, "2025-07-21T16:30:00Z");

    // Events from "second batch" (later timestamp)
    String event3 = createEventJson("event3", 2.0, "2025-07-21T17:00:00Z");
    String event4 = createEventJson("event4", 1.5, "2025-07-21T17:30:00Z");

    // Conflicting event from "third batch" with same key as event3
    String event5 = createEventJson("event5", 3.0, "2025-07-21T17:00:00Z");

    // IQE pattern: Merge events from different batches into single batch
    List<String> mixedBatch = List.of(event1, event2, event3, event4, event5);

    // Process mixed batch - this simulates IQE's mixed batch processing
    eventController.persistServiceInstances(mixedBatch);

    // Verify deductions are created correctly
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    // Should have deduction for event3 (2.0) when event5 (3.0) conflicts with it
    assertEquals(1, deductions.size(), "Should create one deduction for the conflicting event");
    assertEquals(
        -2.0,
        deductions.get(0).getEvent().getMeasurements().get(0).getValue(),
        "BUG CHECK: Should deduct the most recent value (-2.0 from event3), not original value");
  }

  /**
   * IQE Pattern 2: Same Instance, Different Timestamps in Batch IQE tests create events with same
   * instance-id but different timestamps, then process them in the same batch.
   */
  @Test
  void testIqeSameInstanceDifferentTimestamps() {
    // Simulate IQE deduplication test pattern
    String baseInstanceId = "test-instance-123";

    // Events with same instance but different timestamps (same conflict key scenario)
    String event1 =
        createEventJsonWithInstance("event1", 0.285714, "2025-07-21T17:00:00Z", baseInstanceId);
    String event2 =
        createEventJsonWithInstance("event2", 1.0, "2025-07-21T17:00:01Z", baseInstanceId);
    String event3 =
        createEventJsonWithInstance("event3", 2.0, "2025-07-21T17:00:02Z", baseInstanceId);

    // Process all in single batch (IQE pattern)
    List<String> batch = List.of(event1, event2, event3);
    eventController.persistServiceInstances(batch);

    // Verify conflict resolution
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    // Should have 2 deductions: one for event1 when event2 arrives, one for event2 when event3
    // arrives
    assertEquals(2, deductions.size(), "Should create deductions for each superseded event");
    assertEquals(
        -0.285714,
        deductions.get(0).getEvent().getMeasurements().get(0).getValue(),
        "First deduction should negate event1");
    assertEquals(
        -1.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "BUG CHECK: Second deduction should negate event2 (-1.0), not event1 (-0.285714)");
  }

  /**
   * IQE Pattern 3: Concurrent API Usage IQE uses both Kafka and Internal API, potentially causing
   * race conditions in conflict resolution when events arrive via different paths.
   */
  @Test
  void testIqeConcurrentApiUsage() {
    // Simulate events arriving via different APIs (Kafka vs Internal API)
    // This could cause different transaction contexts and conflict tracker states

    // First event via "Kafka path" (simulate by processing individually)
    List<String> kafkaEvent =
        List.of(createEventJson("kafka-event", 0.285714, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(kafkaEvent);

    // Small delay to simulate different transaction contexts
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      /* ignore */
    }

    // Second event via "Internal API path" (different transaction)
    List<String> internalApiEvent =
        List.of(createEventJson("internal-event", 1.0, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(internalApiEvent);

    // Third event via "Internal API path" in same transaction
    List<String> anotherInternalEvent =
        List.of(createEventJson("internal-event2", 2.0, "2025-07-21T17:00:00Z"));
    eventController.persistServiceInstances(anotherInternalEvent);

    // Verify deductions
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(2, deductions.size(), "Should have 2 deductions for the 2 conflicts");
    assertEquals(
        -0.285714,
        deductions.get(0).getEvent().getMeasurements().get(0).getValue(),
        "First deduction should negate kafka-event");
    assertEquals(
        -1.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "BUG CHECK: Second deduction should negate internal-event (-1.0), not kafka-event (-0.285714)");
  }

  /**
   * IQE Pattern 4: Complex Event Sequences with Multiple Conflicts IQE creates complex sequences
   * where multiple events conflict with each other in ways that could confuse the conflict tracker.
   */
  @Test
  void testIqeComplexEventSequences() {
    // Simulate complex IQE test pattern with multiple overlapping conflicts
    // This pattern could cause the UsageConflictTracker to get confused about
    // which event is the "latest" for deduction purposes

    String timestamp = "2025-07-21T17:00:00Z";

    // Create a sequence that matches IQE's complex patterns
    List<String> complexBatch =
        List.of(
            createEventJson("seq1", 0.285714, timestamp), // Original event
            createEventJson("seq2", 1.0, timestamp), // First conflict
            createEventJson("seq3", 0.5, timestamp), // Second conflict
            createEventJson("seq4", 1.0, timestamp), // Third conflict (same value as seq2)
            createEventJson("seq5", 2.0, timestamp) // Fourth conflict
            );

    // Process entire complex sequence in one batch (IQE pattern)
    eventController.persistServiceInstances(complexBatch);

    // Verify the final state
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    List<EventRecord> nonDeductions =
        allEvents.stream().filter(e -> e.getEvent().getAmendmentType() == null).toList();

    // Debug: Print all events to understand what's happening
    System.out.println("=== DEBUG: All Events ===");
    for (int i = 0; i < allEvents.size(); i++) {
      EventRecord event = allEvents.get(i);
      String amendmentType =
          event.getEvent().getAmendmentType() != null
              ? event.getEvent().getAmendmentType().toString()
              : "NONE";
      Double value = event.getEvent().getMeasurements().get(0).getValue();
      System.out.println(
          String.format(
              "Event %d: AmendmentType=%s, Value=%f, RecordDate=%s",
              i + 1, amendmentType, value, event.getEvent().getRecordDate()));
    }
    System.out.println("=== END DEBUG ===");

    // Should have 4 deductions (one for each superseded event)
    assertEquals(
        4,
        deductions.size(),
        String.format(
            "Should create deductions for each superseded event in sequence. "
                + "Total events: %d, Deductions: %d, Non-deductions: %d",
            allEvents.size(), deductions.size(), nonDeductions.size()));

    // The critical test: each deduction should negate the MOST RECENT value, not the original
    if (deductions.size() >= 1) {
      assertEquals(
          -0.285714,
          deductions.get(0).getEvent().getMeasurements().get(0).getValue(),
          "1st deduction: negate seq1 (0.285714)");
    }
    if (deductions.size() >= 2) {
      assertEquals(
          -1.0,
          deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
          "2nd deduction: negate seq2 (1.0)");
    }
    if (deductions.size() >= 3) {
      assertEquals(
          -0.5,
          deductions.get(2).getEvent().getMeasurements().get(0).getValue(),
          "3rd deduction: negate seq3 (0.5)");
    }
    if (deductions.size() >= 4) {
      assertEquals(
          -1.0,
          deductions.get(3).getEvent().getMeasurements().get(0).getValue(),
          "BUG CHECK: 4th deduction should negate seq4 (-1.0), not seq1 (-0.285714)");
    }

    // Final event should be seq5 with value 2.0
    assertEquals(1, nonDeductions.size(), "Should have exactly one final non-deduction event");
    assertEquals(
        2.0,
        nonDeductions.get(0).getEvent().getMeasurements().get(0).getValue(),
        "Final event should be seq5 with value 2.0");
  }

  /**
   * CRITICAL EDGE CASE 1: RHACS Exact Pattern from iqe.log This reproduces the exact sequence from
   * the IQE log where the bug was observed: 1.0 -> 10.0 -> 100.0 with incorrect deduction (-1.0
   * instead of -10.0)
   */
  @Test
  void testRhacsExactPattern() {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "f28c02ca-c984-492a-b2ca-cace698acb36";

    // Step 1: First event (1.0 Cores) - matches iqe.log line 27
    List<String> firstBatch = List.of(createRhacsEventJson("rhacs1", 1.0, timestamp, instanceId));
    eventController.persistServiceInstances(firstBatch);

    List<EventRecord> firstResult = eventRecordRepository.findAll();
    assertEquals(1, firstResult.size());
    assertEquals(1.0, firstResult.get(0).getEvent().getMeasurements().get(0).getValue());

    // Step 2: Second event (10.0 Cores) - matches iqe.log line 95
    List<String> secondBatch = List.of(createRhacsEventJson("rhacs2", 10.0, timestamp, instanceId));
    eventController.persistServiceInstances(secondBatch);

    List<EventRecord> secondResult = eventRecordRepository.findAll();
    assertEquals(3, secondResult.size()); // original + deduction + new

    // Step 3: Third event (100.0 Cores) - matches iqe.log line 274
    // This is where the bug manifests: should create -10.0 deduction, not -1.0
    List<String> thirdBatch = List.of(createRhacsEventJson("rhacs3", 100.0, timestamp, instanceId));
    eventController.persistServiceInstances(thirdBatch);

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(2, deductions.size(), "Should have 2 deductions");
    assertEquals(
        -1.0,
        deductions.get(0).getEvent().getMeasurements().get(0).getValue(),
        "First deduction should negate original 1.0");
    assertEquals(
        -10.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "BUG CHECK: Second deduction should negate most recent 10.0, not original 1.0");
  }

  /**
   * CRITICAL EDGE CASE 2: Long-Running Test Account Pattern from SWATCH-3809 Reproduces the "tally
   * increase" bug where 24 expected became 47, then 69 This suggests cascading incorrect deductions
   * accumulating over time
   */
  @Test
  void testLongRunningAccountTallyIncrease() {
    String timestamp = "2025-07-21T00:00:00Z";
    String instanceId = "rhel-els-longrun-instance";

    // Expected: 24 vCPUs total across multiple instances
    // Simulate multiple events that should sum to 24
    List<String> initialBatch =
        List.of(
            createRhelElsEventJson("els1", 8.0, timestamp, instanceId + "-1"),
            createRhelElsEventJson("els2", 8.0, timestamp, instanceId + "-2"),
            createRhelElsEventJson("els3", 8.0, timestamp, instanceId + "-3"));
    eventController.persistServiceInstances(initialBatch);

    // Verify initial state: 24 vCPUs total
    List<EventRecord> initialEvents = eventRecordRepository.findAll();
    double totalInitial =
        initialEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == null)
            .mapToDouble(e -> e.getEvent().getMeasurements().get(0).getValue())
            .sum();
    assertEquals(24.0, totalInitial, "Initial total should be 24 vCPUs");

    // Simulate automated test interference - conflicting events
    List<String> conflictBatch =
        List.of(
            createRhelElsEventJson(
                "els1-conflict", 12.0, timestamp, instanceId + "-1"), // Changed from 8 to 12
            createRhelElsEventJson(
                "els2-conflict", 15.0, timestamp, instanceId + "-2") // Changed from 8 to 15
            );
    eventController.persistServiceInstances(conflictBatch);

    // Calculate final tally (this is where the bug manifests)
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    double totalFinal =
        allEvents.stream().mapToDouble(e -> e.getEvent().getMeasurements().get(0).getValue()).sum();

    // The bug: total should still be reasonable, but cascading wrong deductions cause accumulation
    assertTrue(totalFinal > 24.0, "Bug manifests as tally increase due to wrong deductions");

    // Debug output for analysis
    System.out.println("=== Long-Running Account Bug Analysis ===");
    System.out.println(String.format("Expected: 24.0 vCPUs, Actual: %.1f vCPUs", totalFinal));
    allEvents.forEach(
        event -> {
          String type = event.getEvent().getAmendmentType() != null ? "DEDUCTION" : "REGULAR";
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(String.format("%s: %.1f", type, value));
        });
    System.out.println("=== End Analysis ===");
  }

  /**
   * CRITICAL EDGE CASE 3: Rapid Fire API Calls Pattern Based on iqe.log timing analysis showing
   * multiple POST requests in quick succession Lines 8, 48, 145 show rapid API calls that could
   * trigger race conditions
   */
  @Test
  void testRapidFireApiCalls() {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "rapid-fire-instance";

    // Simulate the rapid succession of API calls from iqe.log
    // Each "batch" represents a separate API call in quick succession

    // API Call 1 (line 8 in iqe.log)
    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("rapid1", 1.0, timestamp, instanceId)));

    // API Call 2 (line 48 in iqe.log) - very short delay
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      /* ignore */
    }
    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("rapid2", 10.0, timestamp, instanceId)));

    // API Call 3 (line 145 in iqe.log) - another short delay
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      /* ignore */
    }
    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("rapid3", 100.0, timestamp, instanceId)));

    // Verify deduction pattern
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    assertEquals(2, deductions.size(), "Should have 2 deductions for rapid fire calls");
    assertEquals(
        -1.0,
        deductions.get(0).getEvent().getMeasurements().get(0).getValue(),
        "First deduction should negate 1.0");
    assertEquals(
        -10.0,
        deductions.get(1).getEvent().getMeasurements().get(0).getValue(),
        "BUG CHECK: Second deduction should negate 10.0, not 1.0 (rapid fire race condition)");
  }

  /**
   * CRITICAL EDGE CASE 4: IQE Conflict Resolution Assertion Pattern Based on iqe.log lines 296:
   * "Does -10.0 == [{'value': -1.0, 'metric_id': 'Cores'}] ?" This shows the exact mismatch that
   * indicates the bug
   */
  @Test
  void testIqeConflictResolutionMismatch() {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "conflict-resolution-test";

    // Create the exact scenario that leads to the assertion failure in iqe.log
    // This should create a -10.0 deduction but actually creates -1.0

    List<String> sequence =
        List.of(
            createRhacsEventJson("seq1", 1.0, timestamp, instanceId),
            createRhacsEventJson("seq2", 10.0, timestamp, instanceId),
            createRhacsEventJson("seq3", 100.0, timestamp, instanceId));

    // Process as single batch to trigger intra-batch conflict resolution
    eventController.persistServiceInstances(sequence);

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    // This reproduces the exact assertion that fails in IQE
    assertEquals(2, deductions.size(), "Should have 2 deductions");

    // The critical bug check: iqe.log line 296 shows this exact mismatch
    Double actualSecondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();
    assertEquals(
        -10.0,
        actualSecondDeduction,
        String.format(
            "IQE ASSERTION BUG: Expected -10.0 but got %f. "
                + "This matches iqe.log line 296: 'Does -10.0 == [{'value': -1.0, 'metric_id': 'Cores'}] ?'",
            actualSecondDeduction));
  }

  /**
   * CRITICAL EDGE CASE 5: Stage Environment Specific Conditions Based on SWATCH-3809 comment:
   * "limited to the stage environment due to specific test conditions" Tests the combination of
   * factors that only occur in stage
   */
  @Test
  void testStageEnvironmentConditions() {
    // Stage-specific factors that trigger the bug:
    // 1. Automated test interference
    // 2. Multiple concurrent test runs
    // 3. Shared test accounts
    // 4. Rapid API usage patterns

    String baseTimestamp = "2025-07-21T17:00:00Z";
    String sharedOrgId = "13259775"; // From iqe.log

    // Simulate multiple "test runs" hitting the same org/instance
    String sharedInstance = "shared-stage-instance";

    // Test Run 1: Normal flow
    eventController.persistServiceInstances(
        List.of(createStageEventJson("test-run-1", 0.285714, baseTimestamp, sharedInstance)));

    // Test Run 2: Overlapping automated test (common in stage)
    eventController.persistServiceInstances(
        List.of(createStageEventJson("test-run-2", 1.0, baseTimestamp, sharedInstance)));

    // Test Run 3: Another automated test interference
    eventController.persistServiceInstances(
        List.of(createStageEventJson("test-run-3", 1.0, baseTimestamp, sharedInstance)));

    // Verify the stage-specific bug pattern
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .toList();

    // In stage, this pattern creates cascading wrong deductions
    assertTrue(deductions.size() >= 1, "Stage conditions should create deductions");

    // Check for the specific wrong deduction pattern
    boolean hasWrongDeduction =
        deductions.stream()
            .anyMatch(
                d -> Math.abs(d.getEvent().getMeasurements().get(0).getValue() + 0.285714) < 0.001);

    if (hasWrongDeduction) {
      System.out.println(
          "STAGE BUG REPRODUCED: Found deduction of original value instead of most recent");
    }
  }

  /**
   * AGGRESSIVE EDGE CASE 1: Database Transaction Interference Try to force stale reads by
   * manipulating transaction boundaries
   */
  @Test
  void testDatabaseTransactionInterference() throws InterruptedException {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "transaction-interference-test";

    // First event
    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("tx1", 0.285714, timestamp, instanceId)));

    // Force a small delay to ensure transaction commits
    Thread.sleep(10);

    // Second event in rapid succession to potentially catch stale read
    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("tx2", 1.0, timestamp, instanceId)));

    // Immediate third event to maximize chance of stale read
    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("tx3", 2.0, timestamp, instanceId)));

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    System.out.println("=== TRANSACTION INTERFERENCE TEST ===");
    allEvents.forEach(
        event -> {
          String type = event.getEvent().getAmendmentType() != null ? "DEDUCTION" : "REGULAR";
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(String.format("%s: %.6f", type, value));
        });

    if (deductions.size() >= 2) {
      Double secondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();
      if (Math.abs(secondDeduction + 0.285714) < 0.001) {
        System.out.println(
            "🐛 BUG REPRODUCED: Second deduction is -0.285714 (original) instead of -1.0 (most recent)");
        // This would be the bug - deducting original instead of most recent
      }
    }
  }

  /**
   * AGGRESSIVE EDGE CASE 2: Memory Pressure Simulation Try to trigger the bug under memory pressure
   * conditions
   */
  @Test
  void testMemoryPressureScenario() {
    String timestamp = "2025-05-06T10:00:00Z";

    // Create many events to put pressure on the system
    List<String> pressureEvents = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      pressureEvents.add(
          createRhacsEventJson("pressure" + i, 1.0, timestamp, "pressure-instance-" + i));
    }
    eventController.persistServiceInstances(pressureEvents);

    // Now test the critical scenario under pressure
    String criticalInstance = "critical-memory-test";
    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("critical1", 0.285714, timestamp, criticalInstance)));

    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("critical2", 1.0, timestamp, criticalInstance)));

    eventController.persistServiceInstances(
        List.of(createRhacsEventJson("critical3", 2.0, timestamp, criticalInstance)));

    List<EventRecord> criticalEvents =
        eventRecordRepository.findAll().stream()
            .filter(e -> e.getEvent().getInstanceId().equals(criticalInstance))
            .toList();

    List<EventRecord> deductions =
        criticalEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    System.out.println("=== MEMORY PRESSURE TEST ===");
    criticalEvents.forEach(
        event -> {
          String type = event.getEvent().getAmendmentType() != null ? "DEDUCTION" : "REGULAR";
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(String.format("%s: %.6f", type, value));
        });

    // Look for the bug pattern
    if (deductions.size() >= 2) {
      Double secondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();
      if (Math.abs(secondDeduction + 0.285714) < 0.001) {
        System.out.println(
            "🐛 BUG REPRODUCED: Memory pressure caused deduction of original value!");
      }
    }
  }

  /**
   * AGGRESSIVE EDGE CASE 3: Concurrent Processing Simulation Use multiple threads to simulate
   * concurrent API calls
   */
  @Test
  void testConcurrentProcessingBug() throws InterruptedException {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "concurrent-test";

    // Use CountDownLatch to synchronize threads for maximum concurrency
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(3);

    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    // Thread 1: First event
    Thread t1 =
        new Thread(
            () -> {
              try {
                startLatch.await();
                eventController.persistServiceInstances(
                    List.of(createRhacsEventJson("concurrent1", 0.285714, timestamp, instanceId)));
              } catch (Exception e) {
                exceptions.add(e);
              } finally {
                doneLatch.countDown();
              }
            });

    // Thread 2: Second event
    Thread t2 =
        new Thread(
            () -> {
              try {
                startLatch.await();
                Thread.sleep(1); // Tiny delay
                eventController.persistServiceInstances(
                    List.of(createRhacsEventJson("concurrent2", 1.0, timestamp, instanceId)));
              } catch (Exception e) {
                exceptions.add(e);
              } finally {
                doneLatch.countDown();
              }
            });

    // Thread 3: Third event
    Thread t3 =
        new Thread(
            () -> {
              try {
                startLatch.await();
                Thread.sleep(2); // Tiny delay
                eventController.persistServiceInstances(
                    List.of(createRhacsEventJson("concurrent3", 2.0, timestamp, instanceId)));
              } catch (Exception e) {
                exceptions.add(e);
              } finally {
                doneLatch.countDown();
              }
            });

    t1.start();
    t2.start();
    t3.start();

    // Release all threads simultaneously
    startLatch.countDown();

    // Wait for completion
    doneLatch.await(10, TimeUnit.SECONDS);

    if (!exceptions.isEmpty()) {
      System.out.println("Exceptions during concurrent processing: " + exceptions);
    }

    List<EventRecord> allEvents =
        eventRecordRepository.findAll().stream()
            .filter(e -> e.getEvent().getInstanceId().equals(instanceId))
            .toList();

    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    System.out.println("=== CONCURRENT PROCESSING TEST ===");
    allEvents.forEach(
        event -> {
          String type = event.getEvent().getAmendmentType() != null ? "DEDUCTION" : "REGULAR";
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(String.format("%s: %.6f", type, value));
        });

    // Check for bug pattern
    if (deductions.size() >= 2) {
      Double secondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();
      if (Math.abs(secondDeduction + 0.285714) < 0.001) {
        System.out.println("🐛 BUG REPRODUCED: Concurrent processing caused wrong deduction!");
      }
    }
  }

  /**
   * AGGRESSIVE EDGE CASE 4: Large Batch with Conflicts Process a large batch containing multiple
   * conflicting events
   */
  @Test
  void testLargeBatchConflictScenario() {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "large-batch-test";

    // Create a large batch with multiple conflicts for the same instance
    List<String> largeBatch = new ArrayList<>();

    // Add the critical sequence within a large batch
    largeBatch.add(createRhacsEventJson("batch1", 0.285714, timestamp, instanceId));

    // Add many other events to make the batch large
    for (int i = 0; i < 20; i++) {
      largeBatch.add(createRhacsEventJson("filler" + i, 1.0, timestamp, "filler-instance-" + i));
    }

    // Add more conflicting events for our test instance
    largeBatch.add(createRhacsEventJson("batch2", 1.0, timestamp, instanceId));
    largeBatch.add(createRhacsEventJson("batch3", 2.0, timestamp, instanceId));

    // Process the entire large batch at once
    eventController.persistServiceInstances(largeBatch);

    List<EventRecord> testEvents =
        eventRecordRepository.findAll().stream()
            .filter(e -> e.getEvent().getInstanceId().equals(instanceId))
            .toList();

    List<EventRecord> deductions =
        testEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    System.out.println("=== LARGE BATCH CONFLICT TEST ===");
    testEvents.forEach(
        event -> {
          String type = event.getEvent().getAmendmentType() != null ? "DEDUCTION" : "REGULAR";
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(String.format("%s: %.6f", type, value));
        });

    // This is where we might catch the bug
    if (deductions.size() >= 2) {
      Double secondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();
      if (Math.abs(secondDeduction + 0.285714) < 0.001) {
        System.out.println("🐛 BUG REPRODUCED: Large batch processing caused wrong deduction!");
        // Force the test to fail to highlight the bug
        fail(
            "CASCADING DEDUCTION BUG REPRODUCED: Second deduction was -0.285714 (original value) instead of -1.0 (most recent value)");
      }
    }
  }

  /**
   * 🐛 CRITICAL BUG REPRODUCER: Targeted Intra-batch Conflict Resolution This test specifically
   * targets the cascading deduction bug in EventConflictResolver.
   *
   * <p>THE BUG: UsageConflictTracker is initialized once per EventKey with only DB conflicts, but
   * doesn't track events processed earlier in the same batch. This causes each event to create
   * deductions based on the original DB state, not the most recent event.
   */
  @Test
  void testTargetedIntraBatchCascadingDeductionBug() {
    String timestamp = "2025-07-21T17:00:00Z";
    String instanceId = "cascading-bug-test";

    // Step 1: Create initial event to establish baseline in DB
    List<String> initialEvent = List.of(createEventJson("initial", 0.285714, timestamp));
    eventController.persistServiceInstances(initialEvent);

    List<EventRecord> initialResult = eventRecordRepository.findAll();
    assertEquals(1, initialResult.size());
    assertEquals(
        0.285714, initialResult.get(0).getEvent().getMeasurements().get(0).getValue(), 0.000001);

    // Step 2: Send a BATCH of conflicting events (same EventKey) - THIS TRIGGERS THE BUG
    List<String> conflictingBatch =
        List.of(
            createEventJson("batch1", 1.0, timestamp), // Should deduct -0.285714 (original)
            createEventJson(
                "batch2", 2.0,
                timestamp), // Should deduct -1.0 (most recent), but BUG: deducts -0.285714
            // (original)
            createEventJson(
                "batch3", 3.0,
                timestamp) // Should deduct -2.0 (most recent), but BUG: deducts -0.285714
            // (original)
            );

    System.out.println("=== SENDING BATCH THAT TRIGGERS CASCADING DEDUCTION BUG ===");
    eventController.persistServiceInstances(conflictingBatch);

    // Step 3: Analyze the results
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    List<EventRecord> regulars =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == null)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    System.out.println("=== CASCADING DEDUCTION BUG ANALYSIS ===");
    System.out.println("Total events: " + allEvents.size());
    System.out.println("Regular events: " + regulars.size());
    System.out.println("Deduction events: " + deductions.size());

    System.out.println("\n=== REGULAR EVENTS ===");
    regulars.forEach(
        event -> {
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(
              String.format("Regular Event: %.6f at %s", value, event.getEvent().getRecordDate()));
        });

    System.out.println("\n=== DEDUCTION EVENTS ===");
    deductions.forEach(
        event -> {
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(
              String.format(
                  "Deduction Event: %.6f at %s", value, event.getEvent().getRecordDate()));
        });

    // THE BUG VERIFICATION:
    // Expected behavior: Deductions should be [-0.285714, -1.0, -2.0] (deducting most recent
    // values)
    // Actual buggy behavior: Deductions are [-0.285714, -0.285714, -0.285714] (always deducting
    // original)

    if (deductions.size() >= 3) {
      double firstDeduction = deductions.get(0).getEvent().getMeasurements().get(0).getValue();
      double secondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();
      double thirdDeduction = deductions.get(2).getEvent().getMeasurements().get(0).getValue();

      System.out.println("\n=== BUG DETECTION ===");
      System.out.println(
          String.format("First deduction: %.6f (expected: -0.285714)", firstDeduction));
      System.out.println(
          String.format(
              "Second deduction: %.6f (expected: -1.0, actual if bug: -0.285714)",
              secondDeduction));
      System.out.println(
          String.format(
              "Third deduction: %.6f (expected: -2.0, actual if bug: -0.285714)", thirdDeduction));

      // Check if we have the cascading deduction bug
      boolean hasCascadingBug =
          Math.abs(secondDeduction + 0.285714) < 0.000001
              && Math.abs(thirdDeduction + 0.285714) < 0.000001;

      if (hasCascadingBug) {
        System.out.println("\n🐛 CASCADING DEDUCTION BUG REPRODUCED!");
        System.out.println(
            "All deductions are -0.285714 (original value) instead of deducting the most recent values.");
        System.out.println(
            "This proves the UsageConflictTracker is not tracking intra-batch events correctly.");

        // Force test failure to highlight the bug
        fail(
            "CASCADING DEDUCTION BUG REPRODUCED: "
                + "Second deduction was "
                + secondDeduction
                + " (should be -1.0), "
                + "Third deduction was "
                + thirdDeduction
                + " (should be -2.0). "
                + "The system is deducting the original value (-0.285714) instead of the most recent value.");
      } else {
        System.out.println("\n✅ No cascading deduction bug detected - system working correctly");
      }
    }
  }

  /**
   * 🔥 EXACT BUG REPRODUCER: Based on Real Production Data Pattern This test reproduces the exact
   * pattern from events.json where deductions always use the original value instead of the most
   * recent value.
   *
   * <p>KEY INSIGHT: Each event pair comes in separate batches with different metering_batch_ids,
   * but all deductions reference the original batch ID.
   */
  @Test
  void testExactProductionBugPattern() {
    String timestamp = "2025-07-21T17:00:00Z";
    String instanceId = "i-0d83c7a09e2589d87";
    String originalBatchId = "dc6b25b9-5ef9-4601-9a5d-5c908a698c87";

    // Step 1: Send original event (like in production data)
    List<String> originalEvent =
        List.of(createEventJsonWithInstance("original", 0.2857142857142857, timestamp, instanceId));
    eventController.persistServiceInstances(originalEvent);

    // Step 2: Send first superseding event in SEPARATE batch (different metering_batch_id)
    List<String> firstUpdate =
        List.of(createEventJsonWithInstance("update1", 1.0, timestamp, instanceId));
    eventController.persistServiceInstances(firstUpdate);

    // Step 3: Send second superseding event in SEPARATE batch
    List<String> secondUpdate =
        List.of(createEventJsonWithInstance("update2", 2.0, timestamp, instanceId));
    eventController.persistServiceInstances(secondUpdate);

    // Step 4: Send third superseding event in SEPARATE batch
    List<String> thirdUpdate =
        List.of(createEventJsonWithInstance("update3", 3.0, timestamp, instanceId));
    eventController.persistServiceInstances(thirdUpdate);

    // Analyze results
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    System.out.println("=== EXACT PRODUCTION BUG PATTERN ANALYSIS ===");
    System.out.println("Total events: " + allEvents.size());
    System.out.println("Deduction events: " + deductions.size());

    System.out.println("\n=== DEDUCTION EVENTS ===");
    deductions.forEach(
        event -> {
          Double value = event.getEvent().getMeasurements().get(0).getValue();
          System.out.println(
              String.format("Deduction: %.16f at %s", value, event.getEvent().getRecordDate()));
        });

    if (deductions.size() >= 3) {
      double firstDeduction = deductions.get(0).getEvent().getMeasurements().get(0).getValue();
      double secondDeduction = deductions.get(1).getEvent().getMeasurements().get(0).getValue();
      double thirdDeduction = deductions.get(2).getEvent().getMeasurements().get(0).getValue();

      System.out.println("\n=== BUG DETECTION ===");
      System.out.println(
          String.format("First deduction: %.16f (expected: -0.2857142857142857)", firstDeduction));
      System.out.println(
          String.format(
              "Second deduction: %.16f (expected: -1.0, buggy: -0.2857142857142857)",
              secondDeduction));
      System.out.println(
          String.format(
              "Third deduction: %.16f (expected: -2.0, buggy: -0.2857142857142857)",
              thirdDeduction));

      // Check for the exact bug pattern from production
      boolean hasExactBug =
          Math.abs(secondDeduction + 0.2857142857142857) < 0.000000000000001
              && Math.abs(thirdDeduction + 0.2857142857142857) < 0.000000000000001;

      if (hasExactBug) {
        System.out.println("\n🔥 EXACT PRODUCTION BUG REPRODUCED!");
        System.out.println(
            "All deductions are -0.2857142857142857 (original value) instead of deducting the most recent values.");
        System.out.println(
            "This matches the exact pattern seen in the production events.json data.");

        fail(
            "EXACT PRODUCTION BUG REPRODUCED: "
                + "Second deduction was "
                + secondDeduction
                + " (should be -1.0), "
                + "Third deduction was "
                + thirdDeduction
                + " (should be -2.0). "
                + "System is deducting original value instead of most recent value - matches production data pattern.");
      } else {
        System.out.println("\n✅ Bug not reproduced with separate batch pattern");
      }
    }
  }

  /**
   * 🔍 HYPOTHESIS TEST: Database Transaction Isolation Issue Based on the production data analysis,
   * the bug might occur when there are database transaction isolation issues or stale reads during
   * conflict resolution.
   *
   * <p>This test tries to reproduce conditions where the EventConflictResolver might read stale
   * data from the database due to transaction isolation levels.
   */
  @Test
  void testDatabaseTransactionIsolationBug() {
    String timestamp = "2025-07-21T17:00:00Z";
    String instanceId = "i-0d83c7a09e2589d87";

    // Step 1: Send original event
    List<String> originalEvent =
        List.of(createEventJsonWithInstance("original", 0.2857142857142857, timestamp, instanceId));
    eventController.persistServiceInstances(originalEvent);

    // Step 2: Try to force database transaction isolation issues by sending multiple
    // events rapidly in separate transactions, simulating high-load production conditions
    for (int i = 1; i <= 10; i++) {
      List<String> updateEvent =
          List.of(createEventJsonWithInstance("update" + i, (double) i, timestamp, instanceId));
      eventController.persistServiceInstances(updateEvent);

      // Small delay to simulate real-world timing
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // Analyze results
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> deductions =
        allEvents.stream()
            .filter(e -> e.getEvent().getAmendmentType() == AmendmentType.DEDUCTION)
            .sorted((a, b) -> a.getEvent().getRecordDate().compareTo(b.getEvent().getRecordDate()))
            .toList();

    System.out.println("=== DATABASE TRANSACTION ISOLATION TEST ===");
    System.out.println("Total events: " + allEvents.size());
    System.out.println("Deduction events: " + deductions.size());

    // Check if any deductions are incorrect (deducting original value instead of most recent)
    int incorrectDeductions = 0;
    for (int i = 1; i < deductions.size(); i++) {
      double deductionValue =
          Math.abs(deductions.get(i).getEvent().getMeasurements().get(0).getValue());
      // If deduction is 0.2857142857142857 (original value) instead of the expected most recent
      // value
      if (Math.abs(deductionValue - 0.2857142857142857) < 0.000000000000001) {
        incorrectDeductions++;
        System.out.println(
            String.format(
                "INCORRECT DEDUCTION #%d: %.16f (should not be original value)",
                i, -deductionValue));
      }
    }

    if (incorrectDeductions > 0) {
      System.out.println(String.format("\n🔥 FOUND %d INCORRECT DEDUCTIONS!", incorrectDeductions));
      System.out.println(
          "This suggests database transaction isolation issues causing stale reads.");
      fail(
          String.format(
              "DATABASE TRANSACTION ISOLATION BUG REPRODUCED: Found %d deductions using original value instead of most recent value",
              incorrectDeductions));
    } else {
      System.out.println("\n✅ No database transaction isolation issues detected");
    }
  }

  /**
   * 🔥 CRITICAL BUG REPRODUCER: Direct Database Insert Pattern (IQE Style) This reproduces the
   * exact pattern IQE uses: direct database inserts that bypass EventConflictResolver, then later
   * processing that triggers incorrect deductions.
   *
   * <p>THE BUG: When events are inserted directly into DB with different metering_batch_ids, the
   * conflict resolution happens later and uses stale data from the first batch.
   */
  @Test
  @Transactional
  void testDirectDatabaseInsertCascadingBug() throws Exception {
    String timestamp = "2025-07-21T17:00:00Z";
    String instanceId = "i-0d83c7a09e2589d87";
    String orgId = "13259775";

    // Step 1: Insert events directly into database (bypassing EventConflictResolver) - IQE pattern
    UUID batchId1 = UUID.randomUUID();
    UUID batchId2 = UUID.randomUUID();
    UUID batchId3 = UUID.randomUUID();

    // Direct database insert #1 - original event
    String event1Json =
        createEventJsonWithInstance("event1", 0.2857142857142857, timestamp, instanceId);
    eventRecordRepository.save(
        EventRecord.builder()
            .eventId(UUID.fromString(UUID.randomUUID().toString()))
            .timestamp(OffsetDateTime.parse(timestamp))
            .orgId(orgId)
            .instanceId(instanceId)
            .meteringBatchId(batchId1)
            .eventType("snapshot_rhel-for-x86-els-payg_vcpus")
            .eventSource("test-direct-insert")
            .recordDate(OffsetDateTime.parse(timestamp))
            .event(objectMapper.readValue(event1Json, Event.class))
            .build());

    // Direct database insert #2 - conflicting event (same EventKey, different batch)
    String event2Json = createEventJsonWithInstance("event2", 1.0, timestamp, instanceId);
    eventRecordRepository.save(
        EventRecord.builder()
            .eventId(UUID.fromString(UUID.randomUUID().toString()))
            .timestamp(OffsetDateTime.parse(timestamp))
            .orgId(orgId)
            .instanceId(instanceId)
            .meteringBatchId(batchId2)
            .eventType("snapshot_rhel-for-x86-els-payg_vcpus")
            .eventSource("test-direct-insert")
            .recordDate(OffsetDateTime.parse(timestamp))
            .event(objectMapper.readValue(event2Json, Event.class))
            .build());

    // Direct database insert #3 - another conflicting event (same EventKey, different batch)
    String event3Json = createEventJsonWithInstance("event3", 2.0, timestamp, instanceId);
    eventRecordRepository.save(
        EventRecord.builder()
            .eventId(UUID.fromString(UUID.randomUUID().toString()))
            .timestamp(OffsetDateTime.parse(timestamp))
            .orgId(orgId)
            .instanceId(instanceId)
            .meteringBatchId(batchId3)
            .eventType("snapshot_rhel-for-x86-els-payg_vcpus")
            .eventSource("test-direct-insert")
            .recordDate(OffsetDateTime.parse(timestamp))
            .event(objectMapper.readValue(event3Json, Event.class))
            .build());

    // Step 2: Now trigger conflict resolution (similar to what happens during tally sync)
    // This should create proper deductions, but if there's a bug, it will use stale data
    List<EventRecord> existingEvents = eventRecordRepository.findAll();
    System.out.println("Events before conflict resolution: " + existingEvents.size());

    // Manually trigger the conflict resolution process that would normally happen
    List<Event> events =
        existingEvents.stream().map(EventRecord::getEvent).collect(Collectors.toList());

    List<EventRecord> resolvedEvents = eventConflictResolver.resolveIncomingEvents(events);

    // Step 3: Verify the bug - deductions should be correct, but if bug exists,
    // they'll all deduct the original value instead of the most recent
    List<Event> deductionEvents =
        resolvedEvents.stream()
            .map(EventRecord::getEvent)
            .filter(
                e ->
                    e.getAmendmentType() != null
                        && AmendmentType.DEDUCTION.equals(e.getAmendmentType()))
            .collect(Collectors.toList());

    System.out.println("Total deduction events created: " + deductionEvents.size());
    deductionEvents.forEach(
        e -> {
          double value = e.getMeasurements().get(0).getValue();
          System.out.println("Deduction value: " + value + ", Batch ID: " + e.getMeteringBatchId());
        });

    // DEBUG: Let's see ALL resolved events to understand what's happening
    System.out.println("\n=== ALL RESOLVED EVENTS ===");
    resolvedEvents.forEach(
        record -> {
          Event e = record.getEvent();
          String amendmentType =
              e.getAmendmentType() != null ? e.getAmendmentType().toString() : "null";
          double value = e.getMeasurements().get(0).getValue();
          System.out.println(
              String.format(
                  "Event: %s, Amendment: %s, Value: %f, BatchId: %s",
                  e.getEventId(), amendmentType, value, e.getMeteringBatchId()));
        });

    // If the bug exists, all deductions will be -0.2857142857142857 (original value)
    // If working correctly, deductions should be: -0.285714, -1.0, -2.0
    // But first, let's see if ANY deductions are created at all
    if (deductionEvents.size() == 0) {
      fail(
          "🔥 CRITICAL BUG DISCOVERED: No deduction events created at all! This explains the cascading deduction bug - events are not being deduplicated when inserted directly into DB.");
    }
    assertEquals(2, deductionEvents.size(), "Should create 2 deduction events");

    // Check if we have the cascading deduction bug
    List<Double> deductionValues =
        deductionEvents.stream()
            .map(e -> e.getMeasurements().get(0).getValue())
            .sorted()
            .collect(Collectors.toList());

    System.out.println("Deduction values: " + deductionValues);

    // If bug exists: [-0.285714, -0.285714] (both deduct original)
    // If correct: [-2.0, -0.285714] (deduct most recent values)
    if (deductionValues.equals(List.of(-0.2857142857142857, -0.2857142857142857))) {
      fail(
          "🐛 CASCADING DEDUCTION BUG DETECTED! All deductions use original value instead of most recent");
    }
  }

  /**
   * 🔥 EXACT IQE PATTERN REPRODUCER: Sequential API Calls with Timing Issues This reproduces the
   * exact pattern from iqe.log where sequential API calls trigger the cascading deduction bug due
   * to transaction isolation issues.
   *
   * <p>Pattern from IQE log: 1. POST event with value 1.0 -> creates event(1.0) 2. POST event with
   * value 10.0 -> creates deduction(-1.0) + event(10.0) ✅ 3. POST event with value 100.0 -> creates
   * deduction(-1.0) ❌ should be deduction(-10.0)
   */
  @Test
  void testSequentialApiCallsCascadingBug() throws Exception {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "f28c02ca-c984-492a-b2ca-cace698acb36";
    String orgId = "18939574";

    // Step 1: First API call - create initial event (value: 1.0)
    List<String> event1 =
        List.of(createRhacsEventJson("event1", 1.0, timestamp, instanceId, orgId));
    eventController.persistServiceInstances(event1);

    // Verify first event was created
    List<EventRecord> afterFirst = eventRecordRepository.findAll();
    assertEquals(1, afterFirst.size());
    assertEquals(1.0, afterFirst.get(0).getEvent().getMeasurements().get(0).getValue(), 0.001);
    System.out.println("✅ Step 1: Created initial event with value 1.0");

    // Step 2: Second API call - create conflicting event (value: 10.0)
    // This should create: deduction(-1.0) + new event(10.0)
    List<String> event2 =
        List.of(createRhacsEventJson("event2", 10.0, timestamp, instanceId, orgId));
    eventController.persistServiceInstances(event2);

    // Verify second call created deduction + new event
    List<EventRecord> afterSecond = eventRecordRepository.findAll();
    System.out.println(String.format("After second call: %d events", afterSecond.size()));

    List<Event> deductionsAfterSecond =
        afterSecond.stream()
            .map(EventRecord::getEvent)
            .filter(
                e ->
                    e.getAmendmentType() != null
                        && AmendmentType.DEDUCTION.equals(e.getAmendmentType()))
            .collect(Collectors.toList());

    assertEquals(1, deductionsAfterSecond.size(), "Should have 1 deduction after second call");
    assertEquals(
        -1.0,
        deductionsAfterSecond.get(0).getMeasurements().get(0).getValue(),
        0.001,
        "Second call should create -1.0 deduction (correct)");
    System.out.println("✅ Step 2: Created deduction(-1.0) + event(10.0) - CORRECT");

    // Step 3: Third API call - create another conflicting event (value: 100.0)
    // BUG: This creates deduction(-1.0) instead of deduction(-10.0)
    List<String> event3 =
        List.of(createRhacsEventJson("event3", 100.0, timestamp, instanceId, orgId));
    eventController.persistServiceInstances(event3);

    // Verify the bug: third call should create deduction(-10.0) but creates deduction(-1.0)
    List<EventRecord> afterThird = eventRecordRepository.findAll();
    System.out.println(String.format("After third call: %d events", afterThird.size()));

    List<Event> allDeductions =
        afterThird.stream()
            .map(EventRecord::getEvent)
            .filter(
                e ->
                    e.getAmendmentType() != null
                        && AmendmentType.DEDUCTION.equals(e.getAmendmentType()))
            .sorted((a, b) -> a.getRecordDate().compareTo(b.getRecordDate()))
            .collect(Collectors.toList());

    System.out.println("\n=== ALL DEDUCTION EVENTS ===");
    for (int i = 0; i < allDeductions.size(); i++) {
      Event deduction = allDeductions.get(i);
      double value = deduction.getMeasurements().get(0).getValue();
      System.out.println(
          String.format(
              "Deduction %d: %f (Record Date: %s)", i + 1, value, deduction.getRecordDate()));
    }

    // The bug check: second deduction should be -10.0 but is -1.0
    assertEquals(2, allDeductions.size(), "Should have 2 deductions after third call");

    double firstDeduction = allDeductions.get(0).getMeasurements().get(0).getValue();
    double secondDeduction = allDeductions.get(1).getMeasurements().get(0).getValue();

    assertEquals(-1.0, firstDeduction, 0.001, "First deduction should be -1.0");

    // This is where the bug manifests
    if (Math.abs(secondDeduction + 1.0) < 0.001) {
      fail(
          String.format(
              "🐛 CASCADING DEDUCTION BUG REPRODUCED! "
                  + "Second deduction is %f (original value) instead of -10.0 (most recent value). "
                  + "This matches the exact pattern from iqe.log line 296!",
              secondDeduction));
    }

    assertEquals(
        -10.0,
        secondDeduction,
        0.001,
        "Second deduction should be -10.0 (most recent value), not -1.0 (original value)");

    System.out.println("✅ Test passed - conflict resolution working correctly");
  }

  /** Helper to create RHACS event JSON matching the IQE pattern */
  private String createRhacsEventJson(
      String eventId, double value, String timestamp, String instanceId, String orgId) {
    return String.format(
        """
        {
          "sla": "Premium",
          "org_id": "%s",
          "timestamp": "%s",
          "event_type": "Cores",
          "expiration": "2025-05-06T11:00:00Z",
          "instance_id": "%s",
          "display_name": "automation_rhacs_cluster_%s",
          "event_source": "prometheus",
          "measurements": [
            {
              "metric_id": "Cores",
              "value": %f
            }
          ],
          "service_type": "Rhacs Cluster",
          "product_tag": ["rhacs"],
          "role": "rhacs",
          "conversion": false,
          "isHypervisor": false,
          "isUnmappedGuest": false,
          "isVirtual": false
        }""",
        orgId, timestamp, instanceId, instanceId, value);
  }

  /**
   * 🔥 HIGH CONCURRENCY REPRODUCER: Rapid Sequential Calls Under Load This test attempts to
   * reproduce the bug under high-load conditions that might exist in production but not in our
   * simple test environment.
   */
  @Test
  void testHighConcurrencyRapidSequentialCalls() throws Exception {
    String timestamp = "2025-05-06T10:00:00Z";
    String instanceId = "concurrent-test-instance";
    String orgId = "18939574";

    // Step 1: Create initial event
    List<String> event1 =
        List.of(createRhacsEventJson("event1", 5.0, timestamp, instanceId, orgId));
    eventController.persistServiceInstances(event1);

    System.out.println("✅ Initial event created with value 5.0");

    // Step 2: Rapid sequential calls with minimal delay (simulating production load)
    List<Double> values = List.of(15.0, 25.0, 35.0, 45.0);

    for (int i = 0; i < values.size(); i++) {
      double value = values.get(i);
      List<String> event =
          List.of(createRhacsEventJson("event" + (i + 2), value, timestamp, instanceId, orgId));

      // Minimal delay to simulate rapid API calls
      Thread.sleep(10);

      eventController.persistServiceInstances(event);
      System.out.println(String.format("✅ Event %d created with value %.1f", i + 2, value));
    }

    // Step 3: Analyze all deduction events
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<Event> deductions =
        allEvents.stream()
            .map(EventRecord::getEvent)
            .filter(
                e ->
                    e.getAmendmentType() != null
                        && AmendmentType.DEDUCTION.equals(e.getAmendmentType()))
            .sorted((a, b) -> a.getRecordDate().compareTo(b.getRecordDate()))
            .collect(Collectors.toList());

    System.out.println(String.format("\n=== RAPID SEQUENTIAL CALLS ANALYSIS ==="));
    System.out.println(
        String.format("Total events: %d, Deductions: %d", allEvents.size(), deductions.size()));

    // Expected deductions: -5.0, -15.0, -25.0, -35.0 (each deducts the most recent value)
    List<Double> expectedDeductions = List.of(-5.0, -15.0, -25.0, -35.0);
    List<Double> actualDeductions =
        deductions.stream()
            .map(e -> e.getMeasurements().get(0).getValue())
            .collect(Collectors.toList());

    System.out.println("Expected deductions: " + expectedDeductions);
    System.out.println("Actual deductions:   " + actualDeductions);

    // Check for cascading bug pattern
    boolean hasCascadingBug =
        actualDeductions.stream()
            .anyMatch(val -> Math.abs(val + 5.0) < 0.001); // Multiple -5.0 deductions indicate bug

    if (hasCascadingBug
        && actualDeductions.stream().filter(val -> Math.abs(val + 5.0) < 0.001).count() > 1) {
      fail(
          "🐛 CASCADING DEDUCTION BUG DETECTED under rapid sequential calls! "
              + "Multiple deductions of -5.0 (original value) found instead of progressive deductions.");
    }

    assertEquals(
        expectedDeductions.size(),
        actualDeductions.size(),
        "Should have correct number of deductions");

    // Verify each deduction is correct
    for (int i = 0; i < expectedDeductions.size(); i++) {
      assertEquals(
          expectedDeductions.get(i),
          actualDeductions.get(i),
          0.001,
          String.format(
              "Deduction %d should be %.1f but was %.1f",
              i + 1, expectedDeductions.get(i), actualDeductions.get(i)));
    }

    System.out.println("✅ Rapid sequential calls test passed - no cascading bug detected");
  }

  /**
   * Test that verifies why transactionHandler.runInNewTransaction is necessary.
   *
   * <p>This test demonstrates that when an event in a batch has SQL constraint violations or other
   * database errors, the runInNewTransaction mechanism ensures that: 1. Only the problematic event
   * fails 2. Other valid events in the batch are still processed successfully 3. The system
   * gracefully handles individual event failures without affecting the entire batch
   */
  @Test
  void testTransactionIsolationForFailedEvents() {
    // Processing batch with 2 events: 1 valid, 1 with SQL constraint violation
    List<String> events = createEventsWithInvalidSqlConstraints();

    // Process the mixed batch - this should handle the failed event gracefully
    assertThrows(
        BatchListenerFailedException.class, () -> eventController.persistServiceInstances(events));

    // Verify transaction isolation: valid events should be saved despite the failed one
    List<EventRecord> savedEvents = eventRecordRepository.findAll();

    // The key assertion: Due to transactionHandler.runInNewTransaction,
    // the 1 valid event should be saved even though 1 event failed
    assertEquals(1, savedEvents.size());

    // Verify the correct events were saved (the valid ones)
    List<String> savedInstanceIds =
        savedEvents.stream().map(EventRecord::getInstanceId).sorted().toList();

    assertEquals(List.of("valid_instance_123"), savedInstanceIds);

    // Verify the measurements are correct
    List<Double> savedValues =
        savedEvents.stream()
            .map(e -> e.getEvent().getMeasurements().get(0).getValue())
            .sorted()
            .toList();

    assertEquals(List.of(4.0), savedValues);
  }

  private String createEventJson(String eventId, double value, String timestamp) {
    return """
        {
           "sla": "Premium",
           "org_id": "13259775",
           "timestamp": "%s",
           "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
           "expiration": "2025-07-21T18:00:00Z",
           "instance_id": "i-0d83c7a09e2589d87",
           "display_name": "test_server",
           "event_source": "test-data",
           "measurements": [
             {
               "metric_id": "vCPUs",
               "value": %f
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rhel-for-x86-els-payg"]
         }
        """
        .formatted(timestamp, value);
  }

  private String createEventJsonWithInstance(
      String eventId, double value, String timestamp, String instanceId) {
    return """
        {
           "sla": "Premium",
           "org_id": "13259775",
           "timestamp": "%s",
           "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
           "expiration": "2025-07-21T18:00:00Z",
           "instance_id": "%s",
           "display_name": "test_server",
           "event_source": "test-data",
           "measurements": [
             {
               "metric_id": "vCPUs",
               "value": %f
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rhel-for-x86-els-payg"]
         }
        """
        .formatted(timestamp, instanceId, value);
  }

  private String createRhacsEventJson(
      String eventId, double value, String timestamp, String instanceId) {
    return """
        {
           "sla": "Premium",
           "org_id": "18939574",
           "timestamp": "%s",
           "event_type": "Cores",
           "expiration": "2025-05-06T11:00:00Z",
           "instance_id": "%s",
           "display_name": "automation_rhacs_cluster_%s",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "Cores",
               "value": %f
             }
           ],
           "service_type": "Rhacs Cluster",
           "role": "rhacs",
           "product_tag": ["rhacs"]
        }
        """
        .formatted(timestamp, instanceId, instanceId, value);
  }

  private String createRhelElsEventJson(
      String eventId, double value, String timestamp, String instanceId) {
    return """
        {
           "sla": "Premium",
           "org_id": "18939574",
           "timestamp": "%s",
           "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
           "expiration": "2025-07-21T18:00:00Z",
           "instance_id": "%s",
           "display_name": "rhel_els_longrun_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "vCPUs",
               "value": %f
             }
           ],
           "service_type": "RHEL System",
           "role": "rhel-els",
           "product_tag": ["rhel-for-x86-els-payg"]
        }
        """
        .formatted(timestamp, instanceId, value);
  }

  private String createStageEventJson(
      String eventId, double value, String timestamp, String instanceId) {
    return """
        {
           "sla": "Premium",
           "org_id": "13259775",
           "timestamp": "%s",
           "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
           "expiration": "2025-07-21T18:00:00Z",
           "instance_id": "%s",
           "display_name": "stage_test_server",
           "event_source": "test-automation",
           "measurements": [
             {
               "metric_id": "vCPUs",
               "value": %f
             }
           ],
           "service_type": "RHEL System",
           "role": "rhel-els",
           "product_tag": ["rhel-for-x86-els-payg"]
        }
        """
        .formatted(timestamp, instanceId, value);
  }

  private List<String> createEventsWithInvalidSqlConstraints() {
    List<String> events = new ArrayList<>();

    // Event 1: Valid event that should succeed
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "valid_instance_123",
           "display_name": "valid_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 4.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    // Event 2: Event with constraint violation (event_type too long - exceeds VARCHAR(60))
    // This will cause a SQL constraint violation
    String longEventType = "a".repeat(100); // 100 characters, exceeds VARCHAR(60) limit
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org456",
           "timestamp": "2023-05-02T11:00:00Z",
           "event_type": "%s",
           "expiration": "2023-05-02T12:00:00Z",
           "instance_id": "invalid_instance_456",
           "display_name": "invalid_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 8.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """
            .formatted(longEventType));

    return events;
  }

  private List<String> createIntraBatchConflictEvents() {
    List<String> events = new ArrayList<>();

    // Event 1: cores = 1.0
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "display_name": "test_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 1.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    // Event 2: cores = 2.0 (same conflict key, different value)
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "display_name": "test_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 2.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    // Event 3: cores = 3.0 (same conflict key, different value)
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "display_name": "test_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 3.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    return events;
  }
}
