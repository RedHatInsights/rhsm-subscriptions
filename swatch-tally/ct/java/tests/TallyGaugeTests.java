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
package tests;

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static utils.TallyTestProducts.ANSIBLE_AAP_MANAGED;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.TallyMeasurement;
import com.redhat.swatch.tally.test.model.TallySnapshot;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for gauge metric behavior in tally snapshots.
 *
 * <p>Verifies that gauge metrics (Managed-nodes, Cores, Sockets) correctly set currentTotal equal
 * to value, as opposed to counter metrics which accumulate values over time.
 *
 * <p>Related to SWATCH-4953: Performance improvement to reduce currentTotal calls for gauge
 * metrics.
 */
public class TallyGaugeTests extends BaseTallyComponentTest {

  private static final String MANAGED_NODES_METRIC = ANSIBLE_AAP_MANAGED.metricIds().get(0);
  private static final String PRODUCT_TAG = ANSIBLE_AAP_MANAGED.productTag();
  private static final String PRODUCT_ID = ANSIBLE_AAP_MANAGED.productId();

  @BeforeEach
  @Override
  void setUp() {
    super.setUp();
    service.createOptInConfig(orgId);
  }

  @Test
  @TestPlanName("tally-gauge-TC001")
  public void testHourlyTallyGaugeCurrentTotalEqualsValue() {
    // Given: An event with Managed-nodes gauge metric value of 4
    OffsetDateTime eventTime =
        OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).minusHours(1);
    double expectedManagedNodes = 4.0;

    Event event = createAnsibleEvent(orgId, eventTime, expectedManagedNodes);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);

    // When: Running hourly tally and polling for tally summary messages
    List<TallySummary> summaries =
        helpers.pollForTallySyncAndMessages(
            orgId,
            PRODUCT_TAG,
            MANAGED_NODES_METRIC,
            TallySnapshot.Granularity.HOURLY,
            1,
            service,
            kafkaBridge);

    // Then: Hourly snapshot should have currentTotal == value for Managed-nodes gauge metric
    assertFalse(summaries.isEmpty(), "Should have received tally summary messages");

    TallySnapshot snapshot = summaries.get(0).getTallySnapshots().get(0);
    assertNotNull(snapshot.getTallyMeasurements(), "Snapshot should have measurements");

    TallyMeasurement managedNodesMeasurement =
        snapshot.getTallyMeasurements().stream()
            .filter(m -> MANAGED_NODES_METRIC.equals(m.getMetricId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Managed-nodes measurement not found"));

    assertEquals(
        expectedManagedNodes,
        managedNodesMeasurement.getValue(),
        "Managed-nodes value should match expected");
    assertEquals(
        managedNodesMeasurement.getValue(),
        managedNodesMeasurement.getCurrentTotal(),
        "For gauge metrics, currentTotal should equal value");
  }

  @Test
  @TestPlanName("tally-gauge-TC002")
  public void testGaugeMetricZeroValueHandling() {
    // Given: An event with Managed-nodes gauge metric value of 0 (edge case)
    OffsetDateTime eventTime =
        OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).minusHours(1);
    double expectedManagedNodes = 0.0;

    Event event = createAnsibleEvent(orgId, eventTime, expectedManagedNodes);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);

    // When: Running hourly tally and polling for tally summary messages
    List<TallySummary> summaries =
        helpers.pollForTallySyncAndMessages(
            orgId,
            PRODUCT_TAG,
            MANAGED_NODES_METRIC,
            TallySnapshot.Granularity.HOURLY,
            1,
            service,
            kafkaBridge);

    // Then: Snapshot should have currentTotal == value == 0
    assertFalse(summaries.isEmpty(), "Should have received tally summary messages");

    TallySnapshot snapshot = summaries.get(0).getTallySnapshots().get(0);
    assertNotNull(snapshot.getTallyMeasurements(), "Snapshot should have measurements");

    TallyMeasurement managedNodesMeasurement =
        snapshot.getTallyMeasurements().stream()
            .filter(m -> MANAGED_NODES_METRIC.equals(m.getMetricId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Managed-nodes measurement not found"));

    assertEquals(0.0, managedNodesMeasurement.getValue(), "Managed-nodes value should be 0");
    assertEquals(
        managedNodesMeasurement.getValue(),
        managedNodesMeasurement.getCurrentTotal(),
        "For zero-value gauge metrics, currentTotal should equal value (0)");
  }

  @Test
  @TestPlanName("tally-gauge-TC003")
  public void testGaugeMetricCurrentTotalPerSlaSegment() {
    // Given: Events with different SLAs - Premium: 2 nodes, Standard: 4 nodes
    OffsetDateTime eventTime =
        OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).minusHours(1);

    Event premiumEvent = createAnsibleEvent(orgId, eventTime, 2.0);
    premiumEvent.setSla(Event.Sla.PREMIUM);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, premiumEvent);

    Event standardEvent = createAnsibleEvent(orgId, eventTime, 4.0);
    standardEvent.setSla(Event.Sla.STANDARD);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, standardEvent);

    // When: Running hourly tally and polling for tally summary messages
    List<TallySummary> summaries =
        helpers.pollForTallySyncAndMessages(
            orgId,
            PRODUCT_TAG,
            MANAGED_NODES_METRIC,
            TallySnapshot.Granularity.HOURLY,
            2,
            service,
            kafkaBridge);

    // Then: Each SLA snapshot should have currentTotal == value (not accumulated across SLAs)
    assertFalse(summaries.isEmpty(), "Should have received tally summary messages");

    TallySnapshot premiumSnapshot = findSnapshotBySla(summaries, "Premium");
    TallySnapshot standardSnapshot = findSnapshotBySla(summaries, "Standard");

    assertNotNull(premiumSnapshot, "Should have Premium SLA snapshot");
    assertNotNull(standardSnapshot, "Should have Standard SLA snapshot");

    // Verify Premium SLA snapshot
    TallyMeasurement premiumNodes =
        premiumSnapshot.getTallyMeasurements().stream()
            .filter(m -> MANAGED_NODES_METRIC.equals(m.getMetricId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Premium Managed-nodes measurement not found"));

    assertEquals(2.0, premiumNodes.getValue(), "Premium SLA should have 2 managed nodes");
    assertEquals(
        premiumNodes.getValue(),
        premiumNodes.getCurrentTotal(),
        "Premium snapshot currentTotal should equal value, not accumulate across SLAs");

    // Verify Standard SLA snapshot
    TallyMeasurement standardNodes =
        standardSnapshot.getTallyMeasurements().stream()
            .filter(m -> MANAGED_NODES_METRIC.equals(m.getMetricId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Standard Managed-nodes measurement not found"));

    assertEquals(4.0, standardNodes.getValue(), "Standard SLA should have 4 managed nodes");
    assertEquals(
        standardNodes.getValue(),
        standardNodes.getCurrentTotal(),
        "Standard snapshot currentTotal should equal value, not accumulate across SLAs");
  }

  // --- Given helper methods ---

  private Event createAnsibleEvent(
      String orgId, OffsetDateTime timestamp, double managedNodesValue) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            MANAGED_NODES_METRIC,
            (float) managedNodesValue,
            PRODUCT_ID,
            PRODUCT_TAG);

    event.setServiceType("Ansible Managed Node");
    event.setDisplayName(Optional.of("Test Ansible Managed Node"));

    return event;
  }

  // --- Then helper methods ---

  private TallySnapshot findSnapshotBySla(List<TallySummary> summaries, String sla) {
    return summaries.stream()
        .flatMap(summary -> summary.getTallySnapshots().stream())
        .filter(
            snapshot ->
                snapshot.getSla() != null
                    && snapshot.getSla().name().equalsIgnoreCase(sla.toUpperCase()))
        .findFirst()
        .orElse(null);
  }
}
