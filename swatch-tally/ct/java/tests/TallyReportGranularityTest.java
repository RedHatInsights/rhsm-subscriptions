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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import utils.TallyTestHelpers;

public class TallyReportGranularityTest extends BaseTallyComponentTest {

  private static final TallyTestHelpers helpers = new TallyTestHelpers();
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String TEST_PRODUCT_TAG = "rhel-for-x86-els-payg";
  private static final String TEST_METRIC_ID = "vCPUs";
  private static final String TEST_SLA = "Premium";
  private static final String TEST_USAGE = "Production";
  private static final String TEST_BILLING_PROVIDER = "aws";
  private static final String TEST_BILLING_ACCOUNT_ID = "746157280291";

  @Test
  public void testTallyReportGranularityDailyAllFilters() throws Exception {
    String orgId = RandomUtils.generateRandom();
    helpers.createOptInConfig(orgId, service);

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

    String body =
        helpers
            .getTallyReport(service, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams)
            .asString();

    JsonNode json = objectMapper.readTree(body);
    JsonNode meta = json.path("meta");

    assertEquals(json.path("data").size(), meta.path("count").asInt());
    assertEquals("Daily", meta.path("granularity").asText());
    assertEquals(TEST_PRODUCT_TAG, meta.path("product").asText());
    assertEquals(TEST_METRIC_ID, meta.path("metric_id").asText());
    assertEquals(TEST_SLA, meta.path("service_level").asText());
    assertEquals(TEST_USAGE, meta.path("usage").asText());
    assertEquals(TEST_BILLING_PROVIDER, meta.path("billing_provider").asText());
    assertEquals(TEST_BILLING_ACCOUNT_ID, meta.path("billing_acount_id").asText());
  }

  @Test
  public void testTallyReportGranularityDailySomeFilters() throws Exception {
    String orgId = RandomUtils.generateRandom();
    helpers.createOptInConfig(orgId, service);

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

    String body =
        helpers
            .getTallyReport(service, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams)
            .asString();

    JsonNode json = objectMapper.readTree(body);
    JsonNode meta = json.path("meta");

    assertEquals(json.path("data").size(), meta.path("count").asInt());
    assertEquals("Daily", meta.path("granularity").asText());
    assertEquals(TEST_PRODUCT_TAG, meta.path("product").asText());
    assertEquals(TEST_METRIC_ID, meta.path("metric_id").asText());
    assertEquals(TEST_SLA, meta.path("service_level").asText());
    assertEquals(TEST_USAGE, meta.path("usage").asText());
    assertFalse(meta.has("billing_provider"), "meta should not contain billing_provider");
    assertFalse(meta.has("billing_acount_id"), "meta should not contain billing_acount_id");
  }

  @Test
  public void testTallyReportGranularityHourly() throws Exception {
    String orgId = RandomUtils.generateRandom();
    helpers.createOptInConfig(orgId, service);

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

    String body =
        helpers
            .getTallyReport(service, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams)
            .asString();

    JsonNode json = objectMapper.readTree(body);
    JsonNode meta = json.path("meta");

    assertEquals(json.path("data").size(), meta.path("count").asInt());
    assertEquals("Hourly", meta.path("granularity").asText());
    assertEquals(TEST_PRODUCT_TAG, meta.path("product").asText());
    assertEquals(TEST_METRIC_ID, meta.path("metric_id").asText());
    assertEquals(TEST_SLA, meta.path("service_level").asText());
    assertEquals(TEST_USAGE, meta.path("usage").asText());
    assertEquals(TEST_BILLING_PROVIDER, meta.path("billing_provider").asText());
    assertEquals(TEST_BILLING_ACCOUNT_ID, meta.path("billing_acount_id").asText());
  }

  @Test
  public void testTallyReportInvalidWithoutGranularity() {
    String orgId = RandomUtils.generateRandom();
    helpers.createOptInConfig(orgId, service);

    OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime ending = beginning.plusDays(1).minusNanos(1);

    Map<String, ?> queryParams =
        Map.of(
            "beginning", beginning.toString(),
            "ending", ending.toString());

    Response resp =
        helpers.getTallyReportRaw(service, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBody().asString().contains("granularity: must not be null"));
  }
}
