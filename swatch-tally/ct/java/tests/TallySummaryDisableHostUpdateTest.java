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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.TallyMeasurement;
import com.redhat.swatch.tally.test.model.TallySnapshot;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.BillingProvider;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Component tests for verifying that tally summaries produce correct data regardless of the
 * DISABLE_HOURLY_HOST_UPDATE feature flag state.
 *
 * <p>Tests ensure that disabling host updates during hourly tally runs does not affect the
 * correctness of tally summary measurements and metadata.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TallySummaryDisableHostUpdateTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final double EXPECTED_VALUE = 10.0;

  @AfterAll
  void resetFlagAfterTests() {
    unleash.setDisableHourlyHostUpdate(false);
  }

  /**
   * Helper method to extract a TallySnapshot from a TallySummary for a given product and
   * granularity.
   *
   * @param summary the TallySummary to search
   * @param productId the product ID to match
   * @param granularity the granularity to match
   * @return the matching TallySnapshot
   */
  private TallySnapshot getSnapshot(
      TallySummary summary, String productId, Granularity granularity) {
    assertNotNull(summary.getTallySnapshots(), "Tally snapshots should not be null");
    assertFalse(summary.getTallySnapshots().isEmpty(), "Tally snapshots should not be empty");

    Optional<TallySnapshot> snapshot =
        summary.getTallySnapshots().stream()
            .filter(s -> productId.equals(s.getProductId()))
            .filter(s -> granularity.equals(s.getGranularity()))
            .findFirst();

    assertNotNull(snapshot.orElse(null), "Should find matching snapshot");
    return snapshot.get();
  }

  /**
   * Helper method to get the measurement value for a specific metric ID from a TallySnapshot.
   *
   * @param snapshot the TallySnapshot to search
   * @param metricId the metric ID to find
   * @return the measurement value
   */
  private double getMeasurementValue(TallySnapshot snapshot, String metricId) {
    assertNotNull(snapshot.getTallyMeasurements(), "Tally measurements should not be null");

    Optional<TallyMeasurement> measurement =
        snapshot.getTallyMeasurements().stream()
            .filter(m -> metricId.equals(m.getMetricId()))
            .findFirst();

    assertNotNull(measurement.orElse(null), "Should find matching measurement for " + metricId);
    return measurement.get().getValue();
  }

  @ParameterizedTest(name = "flag_disabled={0}")
  @ValueSource(booleans = {false, true})
  @TestPlanName("tally-summary-disable-host-update-TC001")
  void testTallySummaryCorrectWithFlagState(boolean flagEnabled) {
    // Given: Flag is set to the specified state
    unleash.setDisableHourlyHostUpdate(flagEnabled);

    OffsetDateTime timestamp = OffsetDateTime.now();
    String instanceId = UUID.randomUUID().toString();

    // Create and publish an event with specific metric value
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            instanceId,
            timestamp.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            (float) EXPECTED_VALUE,
            Sla.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "test-billing-account",
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 1, service, kafkaBridge);

    // Then: Verify tally summary has correct data regardless of flag state
    assertNotNull(tallySummaries, "Tally summaries should not be null");
    assertEquals(1, tallySummaries.size(), "Should receive exactly 1 tally summary");

    TallySummary summary = tallySummaries.get(0);
    TallySnapshot snapshot = getSnapshot(summary, TEST_PRODUCT_TAG, Granularity.HOURLY);
    double actualValue = getMeasurementValue(snapshot, TEST_METRIC_ID);

    assertAll(
        "Tally summary should have correct metadata and value regardless of flag state",
        () -> assertEquals(orgId, summary.getOrgId(), "Org ID should match"),
        () -> assertEquals(TEST_PRODUCT_TAG, snapshot.getProductId(), "Product tag should match"),
        () ->
            assertEquals(Granularity.HOURLY, snapshot.getGranularity(), "Granularity should match"),
        () ->
            assertEquals(
                EXPECTED_VALUE, actualValue, 0.0001, "Tally value should match event value"),
        () -> assertEquals("Premium", snapshot.getSla().value(), "SLA should match"),
        () -> assertEquals("Production", snapshot.getUsage().value(), "Usage should match"),
        () ->
            assertEquals(
                "test-billing-account",
                snapshot.getBillingAccountId(),
                "Billing account ID should match"),
        () ->
            assertEquals(
                "aws", snapshot.getBillingProvider().value(), "Billing provider should match"));
  }

  @Test
  @TestPlanName("tally-summary-disable-host-update-TC002")
  void testMultipleTallySummariesCorrectWithFlagToggled() {
    // Given: Multiple events with different attributes
    OffsetDateTime timestamp = OffsetDateTime.now();

    // First run with flag disabled
    unleash.setDisableHourlyHostUpdate(false);

    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            5.0f,
            Sla.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "account-1",
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    // When: Polling for first tally summary with flag disabled
    List<TallySummary> summaries1 =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 1, service, kafkaBridge);

    // Then: Verify first summary
    assertEquals(1, summaries1.size(), "Should receive exactly 1 tally summary");
    TallySummary summary1 = summaries1.get(0);
    TallySnapshot snapshot1 = getSnapshot(summary1, TEST_PRODUCT_TAG, Granularity.HOURLY);
    double value1 = getMeasurementValue(snapshot1, TEST_METRIC_ID);
    assertEquals(5.0, value1, 0.0001, "First tally value should be 5.0");
    assertEquals("account-1", snapshot1.getBillingAccountId(), "Billing account should match");

    // Given: Now enable the flag and publish another event in a different hour
    unleash.setDisableHourlyHostUpdate(true);

    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.minusHours(2).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            15.0f,
            Sla.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AZURE,
            "account-2",
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event2.setCloudProvider(Event.CloudProvider.AZURE);

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // When: Polling for second tally summary with flag enabled
    // Note: This will return both summaries since we're querying by org/product/metric
    List<TallySummary> summaries2 =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    // Then: Verify we got both summaries and find the one for account-2
    assertEquals(2, summaries2.size(), "Should receive 2 tally summaries (one per event)");

    // Find the summary with billing account "account-2" (from event2)
    Optional<TallySummary> summary2Opt =
        summaries2.stream()
            .filter(s -> {
              TallySnapshot snap = getSnapshot(s, TEST_PRODUCT_TAG, Granularity.HOURLY);
              return "account-2".equals(snap.getBillingAccountId());
            })
            .findFirst();

    assertNotNull(summary2Opt.orElse(null), "Should find summary for account-2");
    TallySummary summary2 = summary2Opt.get();
    TallySnapshot snapshot2 = getSnapshot(summary2, TEST_PRODUCT_TAG, Granularity.HOURLY);
    double value2 = getMeasurementValue(snapshot2, TEST_METRIC_ID);
    assertAll(
        "Second tally summary should have correct data with flag enabled",
        () -> assertEquals(15.0, value2, 0.0001, "Second tally value should be 15.0"),
        () ->
            assertEquals(
                "account-2", snapshot2.getBillingAccountId(), "Billing account should match"),
        () ->
            assertEquals(
                "azure", snapshot2.getBillingProvider().value(), "Billing provider should match"));
  }

  @Test
  @TestPlanName("tally-summary-disable-host-update-TC003")
  void testTallySummaryAggregationCorrectWithFlagEnabled() {
    // Given: Flag is enabled and multiple events for the same hour
    unleash.setDisableHourlyHostUpdate(true);

    OffsetDateTime timestamp = OffsetDateTime.now();

    // Publish 3 events with different instances but same attributes
    for (int i = 0; i < 3; i++) {
      Event event =
          helpers.createPaygEventWithTimestamp(
              orgId,
              UUID.randomUUID().toString(),
              timestamp.minusHours(1).toString(),
              UUID.randomUUID().toString(),
              TEST_METRIC_ID,
              3.0f,
              Sla.PREMIUM,
              Usage.PRODUCTION,
              BillingProvider.AWS,
              "shared-billing-account",
              HardwareType.CLOUD,
              TEST_PRODUCT_ID,
              TEST_PRODUCT_TAG);

      kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
    }

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 1, service, kafkaBridge);

    // Then: Verify aggregation is correct (3 instances × 3.0 = 9.0)
    assertNotNull(tallySummaries, "Tally summaries should not be null");
    assertEquals(1, tallySummaries.size(), "Should receive exactly 1 aggregated tally summary");

    TallySummary summary = tallySummaries.get(0);
    TallySnapshot snapshot = getSnapshot(summary, TEST_PRODUCT_TAG, Granularity.HOURLY);
    double actualValue = getMeasurementValue(snapshot, TEST_METRIC_ID);

    assertAll(
        "Aggregated tally summary should sum values correctly with flag enabled",
        () ->
            assertEquals(
                9.0, actualValue, 0.0001, "Aggregated value should be sum of all events (3 × 3.0)"),
        () ->
            assertEquals(
                "shared-billing-account",
                snapshot.getBillingAccountId(),
                "Billing account should match"),
        () -> assertEquals("Premium", snapshot.getSla().value(), "SLA should match"),
        () -> assertEquals("Production", snapshot.getUsage().value(), "Usage should match"));
  }
}
