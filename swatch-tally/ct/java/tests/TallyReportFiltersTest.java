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

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.BillingProviderType;
import com.redhat.swatch.tally.test.model.GranularityType;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataMeta;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import com.redhat.swatch.tally.test.model.UsageType;
import io.restassured.response.Response;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

public class TallyReportFiltersTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);
  private static final ServiceLevelType TEST_SLA = ServiceLevelType.PREMIUM;
  private static final UsageType TEST_USAGE = UsageType.PRODUCTION;
  private static final BillingProviderType TEST_BILLING_PROVIDER = BillingProviderType.AWS;
  private static final String TEST_BILLING_ACCOUNT_ID =
      String.valueOf(100000000000L + (long) (Math.random() * 900000000000L));

  private void givenTwoEventsPublished(
      OffsetDateTime timestamp,
      float value1,
      float value2,
      Consumer<Event> event1Configurator,
      Consumer<Event> event2Configurator) {

    Event event1 =
        helpers.createEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            value1,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event1Configurator.accept(event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    Event event2 =
        helpers.createEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            value2,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event2Configurator.accept(event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
  }

  private TallyReportData whenQueryingTallyReportWithFilter(
      OffsetDateTime timestamp, Map<String, ?> filterParams) {

    service.performHourlyTallyForOrg(orgId);

    OffsetDateTime beginning = timestamp.truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime ending = beginning.plusHours(1).minusNanos(1);

    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Hourly");
    queryParams.put("beginning", beginning.toString());
    queryParams.put("ending", ending.toString());
    queryParams.putAll(filterParams);

    return AwaitilityUtils.until(
        () -> service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams),
        data -> data.getData() != null && !data.getData().isEmpty());
  }

  private void thenResponseContainsOnlyValue(
      TallyReportData response, double expectedValue, String assertionMessage) {

    assertNotNull(response.getData(), "Response data should not be null");
    assertFalse(response.getData().isEmpty(), "Response data should not be empty");

    double totalValue = response.getData().stream().mapToInt(TallyReportDataPoint::getValue).sum();
    assertEquals(expectedValue, totalValue, 0.0001, assertionMessage);
  }

  @Test
  @TestPlanName("tally-report-filters-TC001")
  public void testTallyReportGranularityDailyAllFilters() {
    // Given: An org with opt-in config and daily granularity query parameters with all filters
    service.createOptInConfig(orgId);

    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, ?> queryParams =
        Map.of(
            "granularity", "Daily",
            "beginning", beginning.toString(),
            "ending", ending.toString(),
            "sla", TEST_SLA,
            "usage", TEST_USAGE,
            "billing_provider", TEST_BILLING_PROVIDER,
            "billing_account_id", TEST_BILLING_ACCOUNT_ID);

    // When: Requesting tally report data with all filters
    TallyReportData response =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Response metadata should reflect all specified filters
    List<TallyReportDataPoint> data = response.getData();
    TallyReportDataMeta meta = response.getMeta();

    assertNotNull(data, "Response data should not be null");
    assertNotNull(meta, "Response metadata should not be null");
    assertEquals(data.size(), meta.getCount(), "Data size should match metadata count");
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(TEST_PRODUCT_TAG, meta.getProduct(), "Product tag should match request");
    assertEquals(TEST_METRIC_ID, meta.getMetricId(), "Metric ID should match request");
    assertEquals(TEST_SLA, meta.getServiceLevel(), "Service level should match request");
    assertEquals(TEST_USAGE, meta.getUsage(), "Usage should match request");
    assertEquals(
        TEST_BILLING_PROVIDER, meta.getBillingProvider(), "Billing provider should match request");
    assertEquals(
        TEST_BILLING_ACCOUNT_ID,
        meta.getBillingAcountId(),
        "Billing account ID should match request");
  }

  @Test
  @TestPlanName("tally-report-filters-TC002")
  public void testHourlyGranularityFilteredBySla() {
    // Given: Events with different SLAs (PREMIUM and STANDARD)
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    givenTwoEventsPublished(
        timestamp,
        10.0f,
        20.0f,
        event -> {}, // PREMIUM is default
        event -> event.setSla(Event.Sla.STANDARD));

    // When: Performing tally and querying with SLA=STANDARD filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(timestamp, Map.of("sla", ServiceLevelType.STANDARD));

    // Then: Response should contain only STANDARD SLA data
    assertEquals(
        ServiceLevelType.STANDARD,
        response.getMeta().getServiceLevel(),
        "Metadata SLA should be STANDARD");
    thenResponseContainsOnlyValue(response, 20.0, "Should only include STANDARD SLA event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC003")
  public void testHourlyGranularityFilteredByUsage() {
    // Given: Events with different usage types (PRODUCTION and DEVELOPMENT)
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    givenTwoEventsPublished(
        timestamp,
        10.0f,
        20.0f,
        event -> {}, // PRODUCTION is default
        event -> event.setUsage(Event.Usage.DEVELOPMENT_TEST));

    // When: Performing tally and querying with usage=PRODUCTION filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(timestamp, Map.of("usage", UsageType.PRODUCTION));

    // Then: Response should contain only PRODUCTION usage data
    assertEquals(
        UsageType.PRODUCTION, response.getMeta().getUsage(), "Metadata usage should be PRODUCTION");
    thenResponseContainsOnlyValue(
        response, 10.0, "Should only include PRODUCTION usage event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC004")
  public void testHourlyGranularityFilteredByBillingProvider() {
    // Given: Events with different billing providers (AWS and AZURE)
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    givenTwoEventsPublished(
        timestamp,
        10.0f,
        20.0f,
        event -> {}, // AWS is default
        event -> {
          event.setCloudProvider(Event.CloudProvider.AZURE);
          event.setBillingProvider(Event.BillingProvider.AZURE);
        });

    // When: Performing tally and querying with billing_provider=AZURE filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(
            timestamp, Map.of("billing_provider", BillingProviderType.AZURE));

    // Then: Response should contain only AZURE billing provider data
    assertEquals(
        BillingProviderType.AZURE,
        response.getMeta().getBillingProvider(),
        "Metadata billing provider should be AZURE");
    thenResponseContainsOnlyValue(
        response, 20.0, "Should only include AZURE billing provider event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC005")
  public void testHourlyGranularityFilteredByBillingAccountId() {
    // Given: Events with different billing account IDs
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    String billingAccount1 = "account-123";
    String billingAccount2 = "account-456";

    givenTwoEventsPublished(
        timestamp,
        10.0f,
        20.0f,
        event -> event.setBillingAccountId(Optional.of(billingAccount1)),
        event -> event.setBillingAccountId(Optional.of(billingAccount2)));

    // When: Performing tally and querying with billing_account_id=account2 filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(timestamp, Map.of("billing_account_id", billingAccount2));

    // Then: Response should contain only account2 billing account data
    assertEquals(
        billingAccount2,
        response.getMeta().getBillingAcountId(),
        "Metadata billing account ID should match account2");
    thenResponseContainsOnlyValue(
        response, 20.0, "Should only include account2 billing account event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC006")
  public void testTallyReportGranularityDailySomeFilters() {
    // Given: An org with opt-in config and daily granularity query parameters with partial filters
    service.createOptInConfig(orgId);

    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, ?> queryParams =
        Map.of(
            "granularity",
            "Daily",
            "beginning",
            beginning.toString(),
            "ending",
            ending.toString(),
            "sla",
            TEST_SLA,
            "usage",
            TEST_USAGE);

    // When: Requesting tally report data with some filters
    TallyReportData response =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Response metadata should reflect specified filters and omit unspecified ones
    List<TallyReportDataPoint> data = response.getData();
    TallyReportDataMeta meta = response.getMeta();

    assertNotNull(data, "Response data should not be null");
    assertNotNull(meta, "Response metadata should not be null");
    assertEquals(data.size(), meta.getCount(), "Data size should match metadata count");
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(TEST_PRODUCT_TAG, meta.getProduct(), "Product tag should match request");
    assertEquals(TEST_METRIC_ID, meta.getMetricId(), "Metric ID should match request");
    assertEquals(TEST_SLA, meta.getServiceLevel(), "Service level should match request");
    assertEquals(TEST_USAGE, meta.getUsage(), "Usage should match request");
    assertNull(meta.getBillingProvider(), "Billing provider should not be present");
    assertNull(meta.getBillingAcountId(), "Billing account ID should not be present");
  }

  @Test
  @TestPlanName("tally-report-filters-TC007")
  public void testTallyReportGranularityHourly() {
    // Given: An org with opt-in config and hourly granularity query parameters with all filters
    service.createOptInConfig(orgId);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime beginning = now.minusHours(4);
    OffsetDateTime ending = now.plusHours(1);

    Map<String, ?> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", beginning.toString(),
            "ending", ending.toString(),
            "sla", TEST_SLA,
            "usage", TEST_USAGE,
            "billing_provider", TEST_BILLING_PROVIDER,
            "billing_account_id", TEST_BILLING_ACCOUNT_ID);

    // When: Requesting tally report data with hourly granularity
    TallyReportData response =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Response metadata should reflect hourly granularity and all filters
    List<TallyReportDataPoint> data = response.getData();
    TallyReportDataMeta meta = response.getMeta();

    assertNotNull(data, "Response data should not be null");
    assertNotNull(meta, "Response metadata should not be null");
    assertEquals(data.size(), meta.getCount(), "Data size should match metadata count");
    assertEquals(GranularityType.HOURLY, meta.getGranularity(), "Granularity should be HOURLY");
    assertEquals(TEST_PRODUCT_TAG, meta.getProduct(), "Product tag should match request");
    assertEquals(TEST_METRIC_ID, meta.getMetricId(), "Metric ID should match request");
    assertEquals(TEST_SLA, meta.getServiceLevel(), "Service level should match request");
    assertEquals(TEST_USAGE, meta.getUsage(), "Usage should match request");
    assertEquals(
        TEST_BILLING_PROVIDER, meta.getBillingProvider(), "Billing provider should match request");
    assertEquals(
        TEST_BILLING_ACCOUNT_ID,
        meta.getBillingAcountId(),
        "Billing account ID should match request");
  }

  @Test
  @TestPlanName("tally-report-filters-TC008")
  public void testTallyReportInvalidWithoutGranularity() {
    // Given: An org with opt-in config and query parameters missing granularity
    service.createOptInConfig(orgId);

    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, ?> queryParams =
        Map.of(
            "beginning", beginning.toString(),
            "ending", ending.toString());

    // When: Requesting tally report data without granularity parameter
    Response resp =
        service.getTallyReportDataRaw(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Request should fail with validation error
    assertEquals(400, resp.getStatusCode(), "Request should return 400 Bad Request");
    assertTrue(
        resp.getBody().asString().contains("granularity: must not be null"),
        "Error message should indicate granularity is required");
  }
}
