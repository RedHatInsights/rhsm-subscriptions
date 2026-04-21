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
import static org.junit.jupiter.api.Assertions.assertNull;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.BillingProviderType;
import com.redhat.swatch.tally.test.model.GranularityType;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataMeta;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import com.redhat.swatch.tally.test.model.UsageType;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PAYG product tests with 4 events covering multiple filter combinations.
 *
 * <p>Event Data: - Event 1: sla=Premium, usage=Production, bp=AWS, baccid=123, vCPUs=4 - Event 2:
 * sla=Standard, usage=Development, bp=AWS, baccid=123, vCPUs=6 - Event 3: sla=Standard,
 * usage=Production, bp=AWS, baccid=123, vCPUs=8 - Event 4: sla=Standard, usage=Production, bp=AWS,
 * baccid=223, vCPUs=1
 *
 * <p>Expected totals for various filters: - No filters: 19 - sla=Premium: 4 - sla=Standard: 15 -
 * usage=Production: 13 - usage=Development: 6 - billing_provider=AWS: 19 - billing_account_id=123:
 * 18 - billing_account_id=223: 1
 */
public class TallyReportFiltersPaygTest extends BaseTallyComponentTest {
  private static String testOrgId;
  private static final String PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final String PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0); // vCPUs

  record PaygEventConfig(
      Event.Sla sla,
      Event.Usage usage,
      Event.BillingProvider billingProvider,
      String billingAccountId,
      float value) {}

  record TimeRanges(
      OffsetDateTime testDate,
      OffsetDateTime hourlyBeginning,
      OffsetDateTime hourlyEnding,
      OffsetDateTime dailyBeginning,
      OffsetDateTime dailyEnding,
      OffsetDateTime monthlyBeginning,
      OffsetDateTime monthlyEnding) {}

  private static final String BILLING_ACCOUNT_123 = UUID.randomUUID().toString();
  private static final String BILLING_ACCOUNT_223 = UUID.randomUUID().toString();
  private static TimeRanges timeRanges;

  @BeforeAll
  static void setupPaygEvents() {
    testOrgId = String.valueOf(10000 + (int) (Math.random() * 90000));
    service.createOptInConfig(testOrgId);

    OffsetDateTime testDate = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
    timeRanges =
        new TimeRanges(
            testDate,
            testDate.truncatedTo(ChronoUnit.HOURS),
            testDate.truncatedTo(ChronoUnit.HOURS).plusHours(1).minusNanos(1),
            testDate.truncatedTo(ChronoUnit.DAYS),
            testDate.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusNanos(1),
            testDate.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1),
            testDate.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1).plusMonths(1).minusNanos(1));

    List.of(
            new PaygEventConfig(
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                BILLING_ACCOUNT_123,
                4.0f),
            new PaygEventConfig(
                Event.Sla.STANDARD,
                Event.Usage.DEVELOPMENT_TEST,
                Event.BillingProvider.AWS,
                BILLING_ACCOUNT_123,
                6.0f),
            new PaygEventConfig(
                Event.Sla.STANDARD,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                BILLING_ACCOUNT_123,
                8.0f),
            new PaygEventConfig(
                Event.Sla.STANDARD,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                BILLING_ACCOUNT_223,
                1.0f))
        .forEach(config -> publishEvent(timeRanges.testDate(), config));

    service.performHourlyTallyForOrg(testOrgId);
  }

  private static void publishEvent(OffsetDateTime timestamp, PaygEventConfig config) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            config.value(),
            config.sla(),
            config.usage(),
            config.billingProvider(),
            config.billingAccountId(),
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG);

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private double getReportSum(
      String orgId,
      String productTag,
      String metricId,
      String granularity,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      Map<String, String> filters) {

    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", granularity);
    queryParams.put("beginning", beginning.toString());
    queryParams.put("ending", ending.toString());

    if (filters != null) {
      queryParams.putAll(filters);
    }

    TallyReportData response = service.getTallyReportData(orgId, productTag, metricId, queryParams);

    if (response.getData() == null) {
      return 0.0;
    }

    return response.getData().stream().mapToInt(TallyReportDataPoint::getValue).sum();
  }

  private OffsetDateTime calculateQuarterStart() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    int currentMonth = now.getMonthValue();
    int quarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1;
    return OffsetDateTime.of(now.getYear(), quarterStartMonth, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        .truncatedTo(ChronoUnit.DAYS);
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC001")
  void shouldReturnDailyReportWithAllFilters(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and events with various attributes exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying daily report with all filter parameters
    Map<String, Object> queryParams =
        Map.of(
            "granularity",
            "Daily",
            "beginning",
            timeRanges.dailyBeginning().toString(),
            "ending",
            timeRanges.dailyEnding().toString(),
            "sla",
            ServiceLevelType.PREMIUM,
            "usage",
            UsageType.PRODUCTION,
            "billing_provider",
            BillingProviderType.AWS,
            "billing_account_id",
            BILLING_ACCOUNT_123);

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: Response metadata reflects all requested filters
    TallyReportDataMeta meta = response.getMeta();
    assertNotNull(meta, "Response metadata should not be null");
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(PRODUCT_TAG, meta.getProduct(), "Product tag should match request");
    assertEquals(METRIC_ID, meta.getMetricId(), "Metric ID should match request");
    assertEquals(ServiceLevelType.PREMIUM, meta.getServiceLevel(), "SLA should match request");
    assertEquals(UsageType.PRODUCTION, meta.getUsage(), "Usage should match request");
    assertEquals(
        BillingProviderType.AWS, meta.getBillingProvider(), "Billing provider should match");
    assertEquals(BILLING_ACCOUNT_123, meta.getBillingAcountId(), "Billing account should match");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC002")
  void shouldFilterHourlyReportBySla(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and events with different SLA values exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly reports filtered by SLA
    double premiumTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            Map.of("sla", ServiceLevelType.PREMIUM.toString()));

    double standardTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            Map.of("sla", ServiceLevelType.STANDARD.toString()));

    // Then: Totals match expected values for each SLA
    assertEquals(4.0, premiumTotal, 0.0001, "Premium SLA should total 4");
    assertEquals(15.0, standardTotal, 0.0001, "Standard SLA should total 15 (6+8+1)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC003")
  void shouldFilterHourlyReportByUsage(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and events with different usage types exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly reports filtered by usage type
    double productionTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            Map.of("usage", UsageType.PRODUCTION.toString()));

    double developmentTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            Map.of("usage", UsageType.DEVELOPMENT_TEST.toString()));

    // Then: Totals match expected values for each usage type
    assertEquals(13.0, productionTotal, 0.0001, "Production usage should total 13 (4+8+1)");
    assertEquals(6.0, developmentTotal, 0.0001, "Development usage should total 6");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC004")
  void shouldFilterHourlyReportByBillingProvider(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and events with billing provider exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly report filtered by billing provider
    double awsTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            Map.of("billing_provider", BillingProviderType.AWS.toString()));

    // Then: Total matches all events for the billing provider
    assertEquals(19.0, awsTotal, 0.0001, "AWS billing provider should total 19 (all events)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC005")
  void shouldFilterHourlyReportByBillingAccountId(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and events with different billing accounts exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly reports filtered by billing account ID
    double account123Total =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            Map.of("billing_account_id", BILLING_ACCOUNT_123));

    double account223Total =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            Map.of("billing_account_id", BILLING_ACCOUNT_223));

    // Then: Totals match expected values for each billing account
    assertEquals(18.0, account123Total, 0.0001, "Billing account 123 should total 18 (4+6+8)");
    assertEquals(1.0, account223Total, 0.0001, "Billing account 223 should total 1");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC006")
  void shouldReturnDailyReportWithPartialFilters(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and events with various attributes exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying daily report with only SLA and usage filters (no billing filters)
    double total =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of(
                "sla",
                ServiceLevelType.STANDARD.toString(),
                "usage",
                UsageType.PRODUCTION.toString()));

    // Then: Total matches events that satisfy both filters
    assertEquals(9.0, total, 0.0001, "Standard SLA + Production usage should total 9 (8+1)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC007")
  void shouldReturnHourlyReportWithAllFilters(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and events with various attributes exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying hourly report with all possible filters
    Map<String, String> filters =
        Map.of(
            "sla", ServiceLevelType.STANDARD.toString(),
            "usage", UsageType.PRODUCTION.toString(),
            "billing_provider", BillingProviderType.AWS.toString(),
            "billing_account_id", BILLING_ACCOUNT_123);

    double total =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Hourly",
            timeRanges.hourlyBeginning(),
            timeRanges.hourlyEnding(),
            filters);

    // Then: Total matches event that satisfies all filters
    assertEquals(8.0, total, 0.0001, "All filters combined should total 8 (Event 3 only)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC008")
  void shouldReturnBadRequestWithoutGranularity(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying report without required granularity parameter
    Map<String, Object> queryParams =
        Map.of(
            "beginning",
            timeRanges.dailyBeginning().toString(),
            "ending",
            timeRanges.dailyEnding().toString());

    Response response =
        service.getTallyReportDataRaw(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: API returns 400 Bad Request
    assertEquals(
        400, response.getStatusCode(), "Should return 400 Bad Request without granularity");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC011")
  void shouldReturnBadRequestWithoutBeginning(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying report without required beginning parameter
    Map<String, Object> queryParams =
        Map.of("granularity", "Daily", "ending", timeRanges.dailyEnding().toString());

    Response response =
        service.getTallyReportDataRaw(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: API returns 400 Bad Request
    assertEquals(400, response.getStatusCode(), "Should return 400 Bad Request without beginning");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC012")
  void shouldReturnBadRequestWithoutEnding(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying report without required ending parameter
    Map<String, Object> queryParams =
        Map.of("granularity", "Daily", "beginning", timeRanges.dailyBeginning().toString());

    Response response =
        service.getTallyReportDataRaw(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: API returns 400 Bad Request
    assertEquals(400, response.getStatusCode(), "Should return 400 Bad Request without ending");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC013")
  void shouldReturnNullMetadataWhenFiltersOmitted(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying report without optional filter parameters
    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Daily",
            "beginning", timeRanges.dailyBeginning().toString(),
            "ending", timeRanges.dailyEnding().toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: Metadata filter fields are null
    TallyReportDataMeta meta = response.getMeta();
    assertNotNull(meta, "Metadata should not be null");
    assertNull(meta.getServiceLevel(), "SLA should be null when not filtered");
    assertNull(meta.getUsage(), "Usage should be null when not filtered");
    assertNull(meta.getBillingProvider(), "Billing provider should be null when not filtered");
    assertNull(meta.getBillingAcountId(), "Billing account should be null when not filtered");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC014")
  void shouldReflectEmptyFilterInMetadata(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying report with empty filter values
    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Daily",
            "beginning", timeRanges.dailyBeginning().toString(),
            "ending", timeRanges.dailyEnding().toString(),
            "sla", "",
            "usage", "");

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: Response metadata is still present
    TallyReportDataMeta meta = response.getMeta();
    assertNotNull(meta, "Metadata should not be null");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC016")
  void shouldReturnAllDataWhenNoOptionalFiltersApplied(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and multiple events exist
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying report without any optional filters
    double total =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            null);

    // Then: All events are included in the total
    assertEquals(19.0, total, 0.0001, "Total without filters should be 19 (4+6+8+1)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC017")
  void shouldFilterDailyReportBySlaAfterNightlyTally(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and nightly tally has run
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying daily reports filtered by SLA
    double premiumTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of("sla", ServiceLevelType.PREMIUM.toString()));

    double standardTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of("sla", ServiceLevelType.STANDARD.toString()));

    // Then: Daily totals match expected values for each SLA
    assertEquals(4.0, premiumTotal, 0.0001, "Daily Premium SLA should total 4");
    assertEquals(15.0, standardTotal, 0.0001, "Daily Standard SLA should total 15 (6+8+1)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC018")
  void shouldFilterDailyReportByUsageAfterNightlyTally(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and nightly tally has run
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying daily reports filtered by usage type
    double productionTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of("usage", UsageType.PRODUCTION.toString()));

    double developmentTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of("usage", UsageType.DEVELOPMENT_TEST.toString()));

    // Then: Daily totals match expected values for each usage type
    assertEquals(13.0, productionTotal, 0.0001, "Daily Production usage should total 13 (4+8+1)");
    assertEquals(6.0, developmentTotal, 0.0001, "Daily Development usage should total 6");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC019")
  void shouldFilterDailyByBillingProviderAfterNightly(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and nightly tally has run
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying daily report filtered by billing provider
    double awsTotal =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of("billing_provider", BillingProviderType.AWS.toString()));

    // Then: Daily total matches all events for the billing provider
    assertEquals(19.0, awsTotal, 0.0001, "Daily AWS billing provider should total 19 (all events)");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC020")
  void shouldFilterDailyByBillingAccountAfterNightly(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured and nightly tally has run
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying daily reports filtered by billing account ID
    double account123Total =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of("billing_account_id", BILLING_ACCOUNT_123));

    double account223Total =
        getReportSum(
            testOrgId,
            PRODUCT_TAG,
            METRIC_ID,
            "Daily",
            timeRanges.dailyBeginning(),
            timeRanges.dailyEnding(),
            Map.of("billing_account_id", BILLING_ACCOUNT_223));

    // Then: Daily totals match expected values for each billing account
    assertEquals(
        18.0, account123Total, 0.0001, "Daily billing account 123 should total 18 (4+6+8)");
    assertEquals(1.0, account223Total, 0.0001, "Daily billing account 223 should total 1");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC021")
  void shouldReturnMonthlyReportWithAllFilters(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying monthly report
    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Monthly",
            "beginning", timeRanges.monthlyBeginning().toString(),
            "ending", timeRanges.monthlyEnding().toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: Response metadata reflects monthly granularity
    TallyReportDataMeta meta = response.getMeta();
    assertNotNull(meta, "Metadata should not be null");
    assertEquals(GranularityType.MONTHLY, meta.getGranularity(), "Granularity should be MONTHLY");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC022")
  void shouldReturnQuarterlyReportWithAllFilters(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying quarterly report
    OffsetDateTime beginning = calculateQuarterStart();
    OffsetDateTime ending = beginning.plusMonths(3).minusNanos(1);

    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Quarterly",
            "beginning", beginning.toString(),
            "ending", ending.toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: Response metadata reflects quarterly granularity
    TallyReportDataMeta meta = response.getMeta();
    assertNotNull(meta, "Metadata should not be null");
    assertEquals(
        GranularityType.QUARTERLY, meta.getGranularity(), "Granularity should be QUARTERLY");
  }

  @ParameterizedTest(name = "with primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-TC023")
  void shouldReturnYearlyReportWithAllFilters(boolean enablePrimaryRowSearches) {
    // Given: Feature flag is configured
    givenFeatureFlagIsConfigured(enablePrimaryRowSearches);

    // When: Querying yearly report
    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).withDayOfYear(1);
    OffsetDateTime ending = beginning.plusYears(1).minusNanos(1);

    Map<String, Object> queryParams =
        Map.of(
            "granularity", "Yearly",
            "beginning", beginning.toString(),
            "ending", ending.toString());

    TallyReportData response =
        service.getTallyReportData(testOrgId, PRODUCT_TAG, METRIC_ID, queryParams);

    // Then: Response metadata reflects yearly granularity
    TallyReportDataMeta meta = response.getMeta();
    assertNotNull(meta, "Metadata should not be null");
    assertEquals(GranularityType.YEARLY, meta.getGranularity(), "Granularity should be YEARLY");
  }
}
