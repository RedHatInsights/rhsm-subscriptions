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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TallyReportGranularityTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);
  private static final ServiceLevelType TEST_SLA = ServiceLevelType.PREMIUM;
  private static final UsageType TEST_USAGE = UsageType.PRODUCTION;
  private static final BillingProviderType TEST_BILLING_PROVIDER = BillingProviderType.AWS;
  private static final String TEST_BILLING_ACCOUNT_ID = "746157280291";

  @Test
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
