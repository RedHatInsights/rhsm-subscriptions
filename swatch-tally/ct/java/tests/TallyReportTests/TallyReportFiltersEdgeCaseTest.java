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
import java.util.HashMap;
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

  // TC009: Aggregation test data
  private static OffsetDateTime tc009Timestamp;
  private static OffsetDateTime tc009Beginning;
  private static OffsetDateTime tc009Ending;

  // TC010: Three SLA values test data
  private static OffsetDateTime tc010Timestamp;
  private static OffsetDateTime tc010Beginning;
  private static OffsetDateTime tc010Ending;

  // TC015: Data gaps test data
  private static OffsetDateTime tc015BaseTime;
  private static OffsetDateTime tc015Beginning;
  private static OffsetDateTime tc015Ending;

  // TC024: Billing account change test data
  private static String tc024InstanceId;
  private static String tc024BillingAccount1;
  private static String tc024BillingAccount2;
  private static OffsetDateTime tc024Beginning;
  private static OffsetDateTime tc024Ending;

  @BeforeAll
  static void setupEdgeCaseEvents() {
    testOrgId = String.valueOf(10000 + (int) (Math.random() * 90000));
    service.createOptInConfig(testOrgId);

    // TC009: Multiple events with same filter attributes (aggregation)
    tc009Timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(5).truncatedTo(ChronoUnit.HOURS);
    tc009Beginning = tc009Timestamp.truncatedTo(ChronoUnit.HOURS);
    tc009Ending = tc009Beginning.plusHours(1).minusNanos(1);

    for (float value : new float[] {15.0f, 25.0f, 10.0f}) {
      Event event =
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              tc009Timestamp.toString(),
              UUID.randomUUID().toString(),
              METRIC_ID,
              value,
              Event.Sla.PREMIUM,
              Event.HardwareType.CLOUD,
              PRODUCT_ID,
              PRODUCT_TAG);
      kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
    }

    // TC010: Three distinct SLA values
    tc010Timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(4).truncatedTo(ChronoUnit.HOURS);
    tc010Beginning = tc010Timestamp.truncatedTo(ChronoUnit.HOURS);
    tc010Ending = tc010Beginning.plusHours(1).minusNanos(1);

    Event tc010Event1 =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            UUID.randomUUID().toString(),
            tc010Timestamp.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, tc010Event1);

    Event tc010Event2 =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            UUID.randomUUID().toString(),
            tc010Timestamp.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            20.0f,
            Event.Sla.STANDARD,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, tc010Event2);

    Event tc010Event3 =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            UUID.randomUUID().toString(),
            tc010Timestamp.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            30.0f,
            Event.Sla.SELF_SUPPORT,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, tc010Event3);

    // TC015: Data gaps with hasData field
    tc015BaseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(10).truncatedTo(ChronoUnit.HOURS);
    tc015Beginning = tc015BaseTime.truncatedTo(ChronoUnit.HOURS);
    tc015Ending = tc015Beginning.plusHours(4).minusNanos(1);

    // Event for first hour
    Event tc015Event1 =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            UUID.randomUUID().toString(),
            tc015BaseTime.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, tc015Event1);

    // Event for third hour (skip hour 2 - creates gap)
    Event tc015Event2 =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            UUID.randomUUID().toString(),
            tc015BaseTime.plusHours(2).toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            20.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, tc015Event2);

    // TC024: Billing account change for same instance
    tc024InstanceId = UUID.randomUUID().toString();
    tc024BillingAccount1 = UUID.randomUUID().toString();
    tc024BillingAccount2 = UUID.randomUUID().toString();
    OffsetDateTime tc024Now = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3);
    tc024Beginning = tc024Now.truncatedTo(ChronoUnit.DAYS);
    tc024Ending = tc024Beginning.plusDays(1).minusNanos(1);

    // First event with billing account 1
    Event tc024Event1 =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            tc024InstanceId,
            tc024Now.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            5.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);
    tc024Event1.setBillingAccountId(java.util.Optional.of(tc024BillingAccount1));
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, tc024Event1);

    // Second event with billing account 2 (same instance)
    Event tc024Event2 =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            tc024InstanceId,
            tc024Now.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            8.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);
    tc024Event2.setBillingAccountId(java.util.Optional.of(tc024BillingAccount2));
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, tc024Event2);

    // Tally all edge case events once
    service.performHourlyTallyForOrg(testOrgId);
    service.tallyOrg(testOrgId);
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC009")
  void shouldAggregateMultipleEventsWithSameFilters(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and multiple events with same filter attributes exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly report with Premium SLA filter
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Hourly");
    queryParams.put("beginning", tc009Beginning.toString());
    queryParams.put("ending", tc009Ending.toString());
    queryParams.put("sla", ServiceLevelType.PREMIUM.toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: All events with matching filters are aggregated
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
    // Given: Feature flag is configured and events with three different SLA values exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly report filtered by Self-Support SLA
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Hourly");
    queryParams.put("beginning", tc010Beginning.toString());
    queryParams.put("ending", tc010Ending.toString());
    queryParams.put("sla", ServiceLevelType.SELF_SUPPORT.toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: Only Self-Support SLA events are returned
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
    // Given: Feature flag is configured and events exist with time gaps
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly report across time range with data gaps
    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", tc015Beginning.toString(),
            "ending", tc015Ending.toString());

    TallyReportData response =
        AwaitilityUtils.until(
            () -> service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams),
            data -> data.getData() != null && !data.getData().isEmpty());

    // Then: Data points indicate presence of data with hasData field
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
    // Given: Feature flag is configured and same instance has events with different billing
    // accounts
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying daily reports filtered by each billing account
    Map<String, Object> queryParams1 = new HashMap<>();
    queryParams1.put("granularity", "Daily");
    queryParams1.put("beginning", tc024Beginning.toString());
    queryParams1.put("ending", tc024Ending.toString());
    queryParams1.put("billing_account_id", tc024BillingAccount1);

    TallyReportData response1 =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams1);

    double account1Total =
        response1.getData() == null
            ? 0.0
            : response1.getData().stream().mapToInt(d -> d.getValue()).sum();

    Map<String, Object> queryParams2 = new HashMap<>();
    queryParams2.put("granularity", "Daily");
    queryParams2.put("beginning", tc024Beginning.toString());
    queryParams2.put("ending", tc024Ending.toString());
    queryParams2.put("billing_account_id", tc024BillingAccount2);

    TallyReportData response2 =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams2);

    double account2Total =
        response2.getData() == null
            ? 0.0
            : response2.getData().stream().mapToInt(d -> d.getValue()).sum();

    // Then: Each billing account shows correct totals for its events
    assertEquals(5.0, account1Total, 0.0001, "Billing account 1 should total 5");
    assertEquals(8.0, account2Total, 0.0001, "Billing account 2 should total 8");
  }
}
