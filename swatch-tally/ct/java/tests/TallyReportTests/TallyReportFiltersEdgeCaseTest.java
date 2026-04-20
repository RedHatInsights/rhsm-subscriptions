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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.TallyReportData;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Edge case tests requiring special event patterns.
 *
 * <p>Tests: - TC009: Multiple events aggregation (same filter attributes) - TC010: Three distinct
 * SLA values - TC015: Data gaps with hasData field - TC024: Billing account change for same
 * instance
 */
public class TallyReportFiltersEdgeCaseTest extends BaseTallyComponentTest {
  private static String testOrgId;
  private static final String PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final String PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

  // Test scenario data structures
  record AggregationScenario(
      OffsetDateTime timestamp,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      List<Float> valuesToAggregate) {}

  record ThreeSlaScenario(
      OffsetDateTime timestamp,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      float premiumValue,
      float standardValue,
      float selfSupportValue) {}

  record DataGapScenario(
      OffsetDateTime baseTime,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      float valueAtHour0,
      float valueAtHour2) {}

  record BillingAccountChangeScenario(
      String instanceId,
      String billingAccount1,
      String billingAccount2,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      OffsetDateTime firstEventTime,
      OffsetDateTime secondEventTime,
      float firstEventValue,
      float secondEventValue) {}

  private static AggregationScenario aggregationTest;
  private static ThreeSlaScenario threeSlaTest;
  private static DataGapScenario dataGapTest;
  private static BillingAccountChangeScenario billingChangeTest;

  @BeforeAll
  static void setupEdgeCaseEvents() {
    testOrgId = String.valueOf(10000 + (int) (Math.random() * 90000));
    service.createOptInConfig(testOrgId);

    // TC009: Multiple events with same filter attributes (aggregation)
    OffsetDateTime aggregationTime =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(5).truncatedTo(ChronoUnit.HOURS);
    aggregationTest =
        new AggregationScenario(
            aggregationTime,
            aggregationTime,
            aggregationTime.plusHours(1).minusNanos(1),
            List.of(15.0f, 25.0f, 10.0f));

    for (float value : aggregationTest.valuesToAggregate()) {
      publishEvent(aggregationTest.timestamp(), value, Event.Sla.PREMIUM);
    }

    // TC010: Three distinct SLA values
    OffsetDateTime threeSlaTime =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(4).truncatedTo(ChronoUnit.HOURS);
    threeSlaTest =
        new ThreeSlaScenario(
            threeSlaTime,
            threeSlaTime,
            threeSlaTime.plusHours(1).minusNanos(1),
            10.0f,
            20.0f,
            30.0f);

    publishEvent(threeSlaTest.timestamp(), threeSlaTest.premiumValue(), Event.Sla.PREMIUM);
    publishEvent(threeSlaTest.timestamp(), threeSlaTest.standardValue(), Event.Sla.STANDARD);
    publishEvent(
        threeSlaTest.timestamp(), threeSlaTest.selfSupportValue(), Event.Sla.SELF_SUPPORT);

    // TC015: Data gaps with hasData field
    OffsetDateTime gapBaseTime =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(10).truncatedTo(ChronoUnit.HOURS);
    dataGapTest =
        new DataGapScenario(
            gapBaseTime,
            gapBaseTime,
            gapBaseTime.plusHours(4).minusNanos(1),
            10.0f,
            20.0f);

    publishEvent(dataGapTest.baseTime(), dataGapTest.valueAtHour0(), Event.Sla.PREMIUM);
    publishEvent(
        dataGapTest.baseTime().plusHours(2), dataGapTest.valueAtHour2(), Event.Sla.PREMIUM);

    // TC024: Billing account change for same instance
    OffsetDateTime changeTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3);
    billingChangeTest =
        new BillingAccountChangeScenario(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            changeTime.truncatedTo(ChronoUnit.DAYS),
            changeTime.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusNanos(1),
            changeTime.minusHours(1),
            changeTime,
            5.0f,
            8.0f);

    publishEvent(
        billingChangeTest.instanceId(),
        billingChangeTest.firstEventTime(),
        billingChangeTest.firstEventValue(),
        Event.Sla.PREMIUM,
        billingChangeTest.billingAccount1());

    publishEvent(
        billingChangeTest.instanceId(),
        billingChangeTest.secondEventTime(),
        billingChangeTest.secondEventValue(),
        Event.Sla.PREMIUM,
        billingChangeTest.billingAccount2());

    service.performHourlyTallyForOrg(testOrgId);
    service.tallyOrg(testOrgId);
  }

  // Overloaded publishEvent methods - basic case with random instance, no billing account
  private static void publishEvent(OffsetDateTime timestamp, float value, Event.Sla sla) {
    publishEvent(UUID.randomUUID().toString(), timestamp, value, sla, null);
  }

  // With specific instance ID and billing account
  private static void publishEvent(
      String instanceId,
      OffsetDateTime timestamp,
      float value,
      Event.Sla sla,
      String billingAccountId) {

    Event event =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            value,
            sla,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);

    if (billingAccountId != null) {
      event.setBillingAccountId(java.util.Optional.of(billingAccountId));
    }

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC009")
  void shouldAggregateMultipleEventsWithSameFilters(boolean enablePrimaryRowSearches) {
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", aggregationTest.beginning().toString(),
            "ending", aggregationTest.ending().toString(),
            "sla", ServiceLevelType.PREMIUM.toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    double total =
        response.getData() == null
            ? 0.0
            : response.getData().stream().mapToInt(d -> d.getValue()).sum();

    assertEquals(50.0, total, 0.0001, "Should aggregate all three PREMIUM events (15+25+10)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC010")
  void shouldFilterWithThreeDistinctSlaValues(boolean enablePrimaryRowSearches) {
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", threeSlaTest.beginning().toString(),
            "ending", threeSlaTest.ending().toString(),
            "sla", ServiceLevelType.SELF_SUPPORT.toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    double total =
        response.getData() == null
            ? 0.0
            : response.getData().stream().mapToInt(d -> d.getValue()).sum();

    assertEquals(30.0, total, 0.0001, "Self-Support SLA should total 30");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC015")
  void shouldIndicateDataGapsWithHasDataField(boolean enablePrimaryRowSearches) {
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", dataGapTest.beginning().toString(),
            "ending", dataGapTest.ending().toString());

    TallyReportData response =
        AwaitilityUtils.until(
            () -> service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams),
            data -> data.getData() != null && !data.getData().isEmpty());

    assertNotNull(response.getData(), "Response data should not be null");

    long pointsWithData =
        response.getData().stream()
            .filter(point -> Boolean.TRUE.equals(point.getHasData()))
            .count();

    assertTrue(
        pointsWithData > 0,
        "Should have at least one data point with hasData=true where events occurred");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC024")
  void shouldTrackBillingAccountChangeForSameInstance(boolean enablePrimaryRowSearches) {
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    Map<String, Object> queryParams1 =
        Map.of(
            "granularity", "Daily",
            "beginning", billingChangeTest.beginning().toString(),
            "ending", billingChangeTest.ending().toString(),
            "billing_account_id", billingChangeTest.billingAccount1());

    TallyReportData response1 =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams1);

    double account1Total =
        response1.getData() == null
            ? 0.0
            : response1.getData().stream().mapToInt(d -> d.getValue()).sum();

    Map<String, Object> queryParams2 =
        Map.of(
            "granularity", "Daily",
            "beginning", billingChangeTest.beginning().toString(),
            "ending", billingChangeTest.ending().toString(),
            "billing_account_id", billingChangeTest.billingAccount2());

    TallyReportData response2 =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams2);

    double account2Total =
        response2.getData() == null
            ? 0.0
            : response2.getData().stream().mapToInt(d -> d.getValue()).sum();

    assertEquals(5.0, account1Total, 0.0001, "Billing account 1 should total 5");
    assertEquals(8.0, account2Total, 0.0001, "Billing account 2 should total 8");
  }
}
