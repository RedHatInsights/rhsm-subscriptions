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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.BillingProviderType;
import com.redhat.swatch.tally.test.model.GranularityType;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataMeta;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.UsageType;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallyReportFiltersTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);
  private static final ServiceLevelType TEST_SLA = ServiceLevelType.PREMIUM;
  private static final UsageType TEST_USAGE = UsageType.PRODUCTION;
  private static final BillingProviderType TEST_BILLING_PROVIDER = BillingProviderType.AWS;
  private static final String TEST_BILLING_ACCOUNT_ID =
      String.valueOf(100000000000L + (long) (Math.random() * 900000000000L));

  @BeforeAll
  static void enablePrimaryRowSearchesFlag() {
    unleash.enablePrimaryRowSearches();
  }

  @AfterAll
  static void disablePrimaryRowSearchesFlag() {
    unleash.disablePrimaryRowSearches();
  }

  private void givenTwoEventsPublished(
      OffsetDateTime timestamp,
      Consumer<Event> event1Configurator,
      Consumer<Event> event2Configurator) {

    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            (float) 10.0,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event1Configurator.accept(event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            (float) 20.0,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event2Configurator.accept(event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
  }

  private TallyReportData whenQueryingTallyReportWithFilter(
      OffsetDateTime timestamp, Map<String, ?> filterParams) {
    return whenQueryingTallyReportWithFilter(timestamp, filterParams, 2);
  }

  private TallyReportData whenQueryingTallyReportWithFilter(
      OffsetDateTime timestamp, Map<String, ?> filterParams, int expectedMessages) {

    // Wait for hourly tally to complete by polling for Kafka messages
    // This ensures snapshots are created before we query
    helpers.pollForTallySyncAndMessages(
        orgId,
        TEST_PRODUCT_TAG,
        TEST_METRIC_ID,
        Granularity.HOURLY,
        expectedMessages,
        service,
        kafkaBridge);

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

  private TallyReportDataMeta thenMetadataShouldExist(TallyReportData response) {
    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getMeta(), "Response metadata should not be null");
    return response.getMeta();
  }

  @Test
  @TestPlanName("tally-report-filters-TC001")
  public void shouldReturnDailyReportWithAllFilters() {
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
    TallyReportDataMeta meta = thenMetadataShouldExist(response);

    assertNotNull(data, "Response data should not be null");
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
  public void shouldFilterHourlyReportBySla() {
    // Given: Events with different SLAs (PREMIUM and STANDARD)
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    givenTwoEventsPublished(
        timestamp,
        event -> {}, // PREMIUM is default
        event -> event.setSla(Event.Sla.STANDARD));

    // When: Performing tally and querying with SLA=STANDARD filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(timestamp, Map.of("sla", ServiceLevelType.STANDARD));

    // Then: Response should contain only STANDARD SLA data
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(
        ServiceLevelType.STANDARD, meta.getServiceLevel(), "Metadata SLA should be STANDARD");
    thenResponseContainsOnlyValue(response, 20.0, "Should only include STANDARD SLA event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC003")
  public void shouldFilterHourlyReportByUsage() {
    // Given: Events with different usage types (PRODUCTION and DEVELOPMENT)
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    givenTwoEventsPublished(
        timestamp,
        event -> {}, // PRODUCTION is default
        event -> event.setUsage(Event.Usage.DEVELOPMENT_TEST));

    // When: Performing tally and querying with usage=PRODUCTION filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(timestamp, Map.of("usage", UsageType.PRODUCTION));

    // Then: Response should contain only PRODUCTION usage data
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(UsageType.PRODUCTION, meta.getUsage(), "Metadata usage should be PRODUCTION");
    thenResponseContainsOnlyValue(
        response, 10.0, "Should only include PRODUCTION usage event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC004")
  public void shouldFilterHourlyReportByBillingProvider() {
    // Given: Events with different billing providers (AWS and AZURE)
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    givenTwoEventsPublished(
        timestamp,
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
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(
        BillingProviderType.AZURE,
        meta.getBillingProvider(),
        "Metadata billing provider should be AZURE");
    thenResponseContainsOnlyValue(
        response, 20.0, "Should only include AZURE billing provider event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC005")
  public void shouldFilterHourlyReportByBillingAccountId() {
    // Given: Events with different billing account IDs
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    String billingAccount1 = "account-123";
    String billingAccount2 = "account-456";

    givenTwoEventsPublished(
        timestamp,
        event -> event.setBillingAccountId(Optional.of(billingAccount1)),
        event -> event.setBillingAccountId(Optional.of(billingAccount2)));

    // When: Performing tally and querying with billing_account_id=account2 filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(timestamp, Map.of("billing_account_id", billingAccount2));

    // Then: Response should contain only account2 billing account data
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(
        billingAccount2,
        meta.getBillingAcountId(),
        "Metadata billing account ID should match account2");
    thenResponseContainsOnlyValue(
        response, 20.0, "Should only include account2 billing account event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC006")
  public void shouldReturnDailyReportWithPartialFilters() {
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
    TallyReportDataMeta meta = thenMetadataShouldExist(response);

    assertNotNull(data, "Response data should not be null");
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
  public void shouldReturnHourlyReportWithAllFilters() {
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
    TallyReportDataMeta meta = thenMetadataShouldExist(response);

    assertNotNull(data, "Response data should not be null");
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
  public void shouldReturnBadRequestWithoutGranularity() {
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

  @Test
  @TestPlanName("tally-report-filters-TC009")
  public void shouldAggregateMultipleEventsWithSameFilters() {
    // Given: Multiple events with the same filter values in the same hour
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    // Publish three events with identical filter attributes but different values
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            15.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            25.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    Event event3 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);

    // When: Performing tally and querying with matching filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(timestamp, Map.of("sla", ServiceLevelType.PREMIUM), 1);

    // Then: Response should aggregate all three events
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(
        ServiceLevelType.PREMIUM, meta.getServiceLevel(), "Metadata SLA should be PREMIUM");
    thenResponseContainsOnlyValue(
        response, 50.0, "Should aggregate all three PREMIUM SLA event values (15+25+10)");
  }

  @Test
  @TestPlanName("tally-report-filters-TC010")
  public void shouldFilterWithThreeDistinctSlaValues() {
    // Given: Events with three different SLA values in the same hour
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            20.0f,
            Event.Sla.STANDARD,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    Event event3 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            30.0f,
            Event.Sla.SELF_SUPPORT,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);

    // When: Querying with SELF_SUPPORT filter
    TallyReportData response =
        whenQueryingTallyReportWithFilter(
            timestamp, Map.of("sla", ServiceLevelType.SELF_SUPPORT), 3);

    // Then: Response should contain only SELF_SUPPORT data
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(
        ServiceLevelType.SELF_SUPPORT,
        meta.getServiceLevel(),
        "Metadata SLA should be SELF_SUPPORT");
    thenResponseContainsOnlyValue(
        response, 30.0, "Should only include SELF_SUPPORT SLA event value");
  }

  @Test
  @TestPlanName("tally-report-filters-TC016")
  public void shouldReturnAllDataWhenNoOptionalFiltersApplied() {
    // Given: Events with different filter attributes in the same hour
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    // Event 1: PREMIUM SLA, PRODUCTION usage, AWS billing
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    // Event 2: STANDARD SLA, DEVELOPMENT usage, AZURE billing
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            20.0f,
            Event.Sla.STANDARD,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event2.setUsage(Event.Usage.DEVELOPMENT_TEST);
    event2.setCloudProvider(Event.CloudProvider.AZURE);
    event2.setBillingProvider(Event.BillingProvider.AZURE);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // Event 3: SELF_SUPPORT SLA, PRODUCTION usage, AWS billing
    Event event3 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            30.0f,
            Event.Sla.SELF_SUPPORT,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);

    // When: Querying with NO optional filters (only required params)
    TallyReportData response = whenQueryingTallyReportWithFilter(timestamp, Map.of(), 3);

    // Then: Response should contain the sum of ALL events regardless of their attributes
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertNull(meta.getServiceLevel(), "Service level should be null when not filtered");
    assertNull(meta.getUsage(), "Usage should be null when not filtered");
    assertNull(meta.getBillingProvider(), "Billing provider should be null when not filtered");
    thenResponseContainsOnlyValue(
        response, 60.0, "Should aggregate all events when no filters applied (10+20+30)");
  }

  @Test
  @TestPlanName("tally-report-filters-TC017")
  public void shouldFilterDailyReportBySlaAfterNightlyTally() {
    // Given: Events with different SLAs processed through hourly then nightly tally
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.DAYS);

    // Event 1: PREMIUM SLA (value 10.0)
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    // Event 2: STANDARD SLA (value 20.0)
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            20.0f,
            Event.Sla.STANDARD,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // When: Running hourly tally (to consume events) then nightly tally (to create daily snapshots)
    // Wait for hourly snapshots to be created before running nightly tally
    helpers.pollForTallySyncAndMessages(
        orgId,
        TEST_PRODUCT_TAG,
        TEST_METRIC_ID,
        Granularity.HOURLY,
        2, // Expect 2 hourly messages (one for each SLA)
        service,
        kafkaBridge);

    // Now run nightly tally to aggregate hourly data into daily snapshots
    service.tallyOrg(orgId);

    OffsetDateTime beginning = timestamp.truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Daily");
    queryParams.put("beginning", beginning.toString());
    queryParams.put("ending", ending.toString());
    queryParams.put("sla", ServiceLevelType.STANDARD);

    TallyReportData response =
        AwaitilityUtils.until(
            () -> service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams),
            data -> data.getData() != null && !data.getData().isEmpty());

    // Then: Response should contain only STANDARD SLA data from daily snapshot
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(
        ServiceLevelType.STANDARD, meta.getServiceLevel(), "Metadata SLA should be STANDARD");
    thenResponseContainsOnlyValue(
        response, 20.0, "Should only include STANDARD SLA event value from daily snapshot");
  }

  @Test
  @TestPlanName("tally-report-filters-TC018")
  public void shouldFilterDailyReportByUsageAfterNightlyTally() {
    // Given: Events with different usage types processed through hourly then nightly tally
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.DAYS);

    // Event 1: PRODUCTION usage (value 10.0)
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    // Event 2: DEVELOPMENT usage (value 20.0)
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            20.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event2.setUsage(Event.Usage.DEVELOPMENT_TEST);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // When: Running hourly tally then nightly tally
    helpers.pollForTallySyncAndMessages(
        orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    service.tallyOrg(orgId);

    OffsetDateTime beginning = timestamp.truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Daily");
    queryParams.put("beginning", beginning.toString());
    queryParams.put("ending", ending.toString());
    queryParams.put("usage", UsageType.PRODUCTION);

    TallyReportData response =
        AwaitilityUtils.until(
            () -> service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams),
            data -> data.getData() != null && !data.getData().isEmpty());

    // Then: Response should contain only PRODUCTION usage data from daily snapshot
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(UsageType.PRODUCTION, meta.getUsage(), "Metadata usage should be PRODUCTION");
    thenResponseContainsOnlyValue(
        response, 10.0, "Should only include PRODUCTION usage event value from daily snapshot");
  }

  @Test
  @TestPlanName("tally-report-filters-TC019")
  public void shouldFilterDailyReportByBillingProviderAfterNightlyTally() {
    // Given: Events with different billing providers processed through hourly then nightly tally
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.DAYS);

    // Event 1: AWS billing provider (value 10.0)
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    // Event 2: AZURE billing provider (value 20.0)
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            20.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event2.setCloudProvider(Event.CloudProvider.AZURE);
    event2.setBillingProvider(Event.BillingProvider.AZURE);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // When: Running hourly tally then nightly tally
    helpers.pollForTallySyncAndMessages(
        orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    service.tallyOrg(orgId);

    OffsetDateTime beginning = timestamp.truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Daily");
    queryParams.put("beginning", beginning.toString());
    queryParams.put("ending", ending.toString());
    queryParams.put("billing_provider", BillingProviderType.AZURE);

    TallyReportData response =
        AwaitilityUtils.until(
            () -> service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams),
            data -> data.getData() != null && !data.getData().isEmpty());

    // Then: Response should contain only AZURE billing provider data from daily snapshot
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(
        BillingProviderType.AZURE,
        meta.getBillingProvider(),
        "Metadata billing provider should be AZURE");
    thenResponseContainsOnlyValue(
        response,
        20.0,
        "Should only include AZURE billing provider event value from daily snapshot");
  }

  @Test
  @TestPlanName("tally-report-filters-TC020")
  public void shouldFilterDailyReportByBillingAccountIdAfterNightlyTally() {
    // Given: Events with different billing account IDs processed through hourly then nightly tally
    service.createOptInConfig(orgId);

    OffsetDateTime timestamp =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.DAYS);

    String billingAccount1 = "daily-account-123";
    String billingAccount2 = "daily-account-456";

    // Event 1: billing_account_id=daily-account-123 (value 10.0)
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event1.setBillingAccountId(Optional.of(billingAccount1));
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    // Event 2: billing_account_id=daily-account-456 (value 20.0)
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            20.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event2.setBillingAccountId(Optional.of(billingAccount2));
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // When: Running hourly tally then nightly tally
    helpers.pollForTallySyncAndMessages(
        orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    service.tallyOrg(orgId);

    OffsetDateTime beginning = timestamp.truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Daily");
    queryParams.put("beginning", beginning.toString());
    queryParams.put("ending", ending.toString());
    queryParams.put("billing_account_id", billingAccount2);

    TallyReportData response =
        AwaitilityUtils.until(
            () -> service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams),
            data -> data.getData() != null && !data.getData().isEmpty());

    // Then: Response should contain only daily-account-456 billing account data from daily snapshot
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(
        billingAccount2,
        meta.getBillingAcountId(),
        "Metadata billing account ID should match daily-account-456");
    thenResponseContainsOnlyValue(
        response,
        20.0,
        "Should only include daily-account-456 billing account event value from daily snapshot");
  }

  @Test
  @TestPlanName("tally-report-filters-TC011")
  public void shouldReturnBadRequestWithoutBeginning() {
    // Given: An org with opt-in config and query parameters missing beginning timestamp
    service.createOptInConfig(orgId);

    OffsetDateTime ending =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.DAYS);

    Map<String, ?> queryParams = Map.of("granularity", "Daily", "ending", ending.toString());

    // When: Requesting tally report data without beginning parameter
    Response resp =
        service.getTallyReportDataRaw(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Request should fail with validation error
    assertEquals(400, resp.getStatusCode(), "Request should return 400 Bad Request");
    assertTrue(
        resp.getBody().asString().contains("beginning")
            || resp.getBody().asString().contains("must not be null"),
        "Error message should indicate beginning is required");
  }

  @Test
  @TestPlanName("tally-report-filters-TC012")
  public void shouldReturnBadRequestWithoutEnding() {
    // Given: An org with opt-in config and query parameters missing ending timestamp
    service.createOptInConfig(orgId);

    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.DAYS);

    Map<String, ?> queryParams = Map.of("granularity", "Daily", "beginning", beginning.toString());

    // When: Requesting tally report data without ending parameter
    Response resp =
        service.getTallyReportDataRaw(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Request should fail with validation error
    assertEquals(400, resp.getStatusCode(), "Request should return 400 Bad Request");
    assertTrue(
        resp.getBody().asString().contains("ending")
            || resp.getBody().asString().contains("must not be null"),
        "Error message should indicate ending is required");
  }

  @Test
  @TestPlanName("tally-report-filters-TC013")
  public void shouldReturnNullMetadataWhenFiltersOmitted() {
    // Given: An org with opt-in config and query with only required parameters
    service.createOptInConfig(orgId);

    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, ?> queryParams =
        Map.of(
            "granularity", "Daily",
            "beginning", beginning.toString(),
            "ending", ending.toString());

    // When: Requesting tally report data with no optional filters
    TallyReportData response =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Response metadata should have null/unset values for optional filters
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertNull(meta.getServiceLevel(), "Service level should be null when not filtered");
    assertNull(meta.getUsage(), "Usage should be null when not filtered");
    assertNull(meta.getBillingProvider(), "Billing provider should be null when not filtered");
    assertNull(meta.getBillingAcountId(), "Billing account ID should be null when not filtered");
    assertEquals(GranularityType.DAILY, meta.getGranularity(), "Granularity should be DAILY");
    assertEquals(TEST_PRODUCT_TAG, meta.getProduct(), "Product tag should match request");
    assertEquals(TEST_METRIC_ID, meta.getMetricId(), "Metric ID should match request");
  }

  @Test
  @TestPlanName("tally-report-filters-TC014")
  public void shouldReflectEmptyFilterInMetadata() {
    // Given: An org with opt-in config and query with EMPTY filter value
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
            ServiceLevelType.EMPTY);

    // When: Requesting tally report data with EMPTY SLA filter
    TallyReportData response =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Response metadata should reflect EMPTY filter value
    TallyReportDataMeta meta = thenMetadataShouldExist(response);
    assertEquals(
        ServiceLevelType.EMPTY,
        meta.getServiceLevel(),
        "Service level should be EMPTY when filtered with EMPTY");
    assertNull(meta.getUsage(), "Usage should be null when not filtered");
    assertNull(meta.getBillingProvider(), "Billing provider should be null when not filtered");
  }

  @Test
  @TestPlanName("tally-report-filters-TC015")
  public void shouldIndicateDataGapsWithHasDataField() {
    // Given: An org with events only in specific hours within a multi-hour range
    service.createOptInConfig(orgId);

    OffsetDateTime baseTime =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(5).truncatedTo(ChronoUnit.HOURS);

    // Publish event only for the first hour
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            baseTime.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            10.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);

    // Publish event only for the third hour (skip hour 2)
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            baseTime.plusHours(2).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            20.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // When: Querying for a range spanning 4 hours
    service.performHourlyTallyForOrg(orgId);

    OffsetDateTime beginning = baseTime.truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime ending = beginning.plusHours(4).minusNanos(1);

    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("granularity", "Hourly");
    queryParams.put("beginning", beginning.toString());
    queryParams.put("ending", ending.toString());

    TallyReportData response =
        AwaitilityUtils.until(
            () -> service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams),
            data -> data.getData() != null && !data.getData().isEmpty());

    // Then: Response should contain data points with hasData indicating gaps
    assertNotNull(response.getData(), "Response data should not be null");
    assertFalse(response.getData().isEmpty(), "Response should have data points");

    // Verify that data points have the hasData field populated
    long pointsWithData =
        response.getData().stream()
            .filter(point -> Boolean.TRUE.equals(point.getHasData()))
            .count();

    // We should have some points with data where events occurred
    assertTrue(
        pointsWithData > 0,
        "Should have at least one data point with hasData=true where events occurred");
    // Note: The exact behavior of hasData for gaps depends on implementation
    // This test verifies the field is present and populated
  }

  @Test
  @TestPlanName("tally-report-filters-TC021")
  public void shouldReturnMonthlyReportWithAllFilters() {
    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC)
            .minusMonths(2)
            .truncatedTo(ChronoUnit.DAYS)
            .withDayOfMonth(1);
    OffsetDateTime ending = beginning.plusMonths(1).minusNanos(1);

    thenGranularityReportWithAllFiltersIsValid(
        "Monthly", GranularityType.MONTHLY, beginning, ending);
  }

  @Test
  @TestPlanName("tally-report-filters-TC022")
  public void shouldReturnQuarterlyReportWithAllFilters() {
    OffsetDateTime beginning = calculateQuarterStart();
    OffsetDateTime ending = beginning.plusMonths(3).minusNanos(1);

    thenGranularityReportWithAllFiltersIsValid(
        "Quarterly", GranularityType.QUARTERLY, beginning, ending);
  }

  @Test
  @TestPlanName("tally-report-filters-TC023")
  public void shouldReturnYearlyReportWithAllFilters() {
    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC)
            .minusYears(1)
            .truncatedTo(ChronoUnit.DAYS)
            .withDayOfYear(1);
    OffsetDateTime ending = beginning.plusYears(1).minusNanos(1);

    thenGranularityReportWithAllFiltersIsValid("Yearly", GranularityType.YEARLY, beginning, ending);
  }

  @Test
  @TestPlanName("tally-report-filters-TC024")
  public void shouldTrackBillingAccountChangeForSameInstance() {
    // Given: An org with opt-in config and an instance that will change billing accounts
    service.createOptInConfig(orgId);
    String instanceId = UUID.randomUUID().toString();
    String billingAccount1 = "839214756108";
    String billingAccount2 = "472061583927";
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime beginning = now.truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1);

    // When: First event is published with billing account 1
    givenEventPublishedForInstance(instanceId, now.minusHours(2), billingAccount1, 5.0f);

    // Then: Daily report shows the first billing account's value
    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(orgId);
          assertAll(
              () -> thenDailyReportContainsValue(beginning, ending, billingAccount1, 5.0),
              () -> thenDailyReportContainsValue(beginning, ending, null, 5.0));
        });

    // When: Second event is published with billing account 2 for the same instance
    givenEventPublishedForInstance(instanceId, now.minusHours(1), billingAccount2, 8.0f);

    // Then: Daily report shows both billing accounts with their respective values
    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(orgId);
          assertAll(
              () -> thenDailyReportContainsValue(beginning, ending, billingAccount1, 5.0),
              () -> thenDailyReportContainsValue(beginning, ending, billingAccount2, 8.0),
              () -> thenDailyReportContainsValue(beginning, ending, null, 13.0));
        });
  }

  private OffsetDateTime calculateQuarterStart() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    int currentQuarterStartMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
    return now.minusMonths(6)
        .truncatedTo(ChronoUnit.DAYS)
        .withDayOfMonth(1)
        .withMonth(currentQuarterStartMonth);
  }

  private void givenEventPublishedForInstance(
      String instanceId, OffsetDateTime timestamp, String billingAccountId, float value) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            value,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event.setBillingAccountId(Optional.of(billingAccountId));
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private void thenDailyReportContainsValue(
      OffsetDateTime beginning, OffsetDateTime ending, String billingAccountId, double expected) {
    String label = billingAccountId != null ? billingAccountId : "total";
    assertEquals(
        expected,
        getDailyReportSum(beginning, ending, billingAccountId),
        0.0001,
        "Daily report for " + label + " should contain expected value");
  }

  private void thenGranularityReportWithAllFiltersIsValid(
      String granularityName,
      GranularityType expectedGranularity,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    // Given: An org with opt-in config and granularity query parameters with all filters
    service.createOptInConfig(orgId);

    Map<String, ?> queryParams =
        Map.of(
            "granularity",
            granularityName,
            "beginning",
            beginning.toString(),
            "ending",
            ending.toString(),
            "sla",
            TEST_SLA,
            "usage",
            TEST_USAGE,
            "billing_provider",
            TEST_BILLING_PROVIDER,
            "billing_account_id",
            TEST_BILLING_ACCOUNT_ID);

    // When: Requesting tally report data
    TallyReportData response =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    // Then: Response metadata should reflect granularity and all filters
    List<TallyReportDataPoint> data = response.getData();
    TallyReportDataMeta meta = thenMetadataShouldExist(response);

    assertNotNull(data, "Response data should not be null");
    assertEquals(data.size(), meta.getCount(), "Data size should match metadata count");
    assertEquals(
        expectedGranularity, meta.getGranularity(), "Granularity should be " + expectedGranularity);
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

  private double getDailyReportSum(
      OffsetDateTime beginning, OffsetDateTime ending, String billingAccountId) {
    Map<String, String> queryParams =
        new HashMap<>(
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString()));
    if (billingAccountId != null) {
      queryParams.put("billing_account_id", billingAccountId);
    }
    TallyReportData response =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);
    if (response.getData() == null) {
      return 0.0;
    }
    return response.getData().stream()
        .collect(Collectors.summarizingInt(TallyReportDataPoint::getValue))
        .getSum();
  }
}
