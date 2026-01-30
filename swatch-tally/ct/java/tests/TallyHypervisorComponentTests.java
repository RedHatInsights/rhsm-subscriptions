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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import utils.TallyDbHostSeeder;
import utils.TallyTestHelpers;

public class TallyHypervisorComponentTests extends BaseTallyComponentTest {

  private static final TallyTestHelpers helpers = new TallyTestHelpers();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final String PRODUCT_TAG_RHEL_FOR_X86 = "RHEL for x86";
  private static final String METRIC_ID_SOCKETS = "Sockets";

  @Test
  public void testTallyReportOfHypervisorWithNoGuests() throws Exception {
    String orgId = RandomUtils.generateRandom();
    helpers.createOptInConfig(orgId, service);

    // Seed baseline usage (non-zero) so we can assert it doesn't change.
    String baselineInventoryId = UUID.randomUUID().toString();
    var baselineHostId = TallyDbHostSeeder.insertHbiHost(orgId, baselineInventoryId);
    TallyDbHostSeeder.insertBuckets(
        baselineHostId, PRODUCT_TAG_RHEL_FOR_X86, "Premium", "Production", 4, 2, "PHYSICAL");

    helpers.syncTallyNightly(orgId, service);

    OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

    int initialSockets = getDailyTotalSockets(orgId, startOfToday, endOfToday);

    // Seed a "hypervisor" host with no guests, but DO NOT seed any buckets for it. This should:
    // - not contribute to tally totals
    // - not appear in the instances ("system table") report
    String hypervisorInventoryId = UUID.randomUUID().toString();
    TallyDbHostSeeder.SeededHost hypervisorHost =
        TallyDbHostSeeder.insertHost(
            orgId, hypervisorInventoryId, "VIRTUAL", false, false, true, 0, null);

    helpers.syncTallyNightly(orgId, service);

    int newSockets = getDailyTotalSockets(orgId, startOfToday, endOfToday);
    assertEquals(
        initialSockets, newSockets, "Hypervisor without guests should not change total sockets");

    // System table check: ensure the host does not show up in instances report.
    JsonNode instances =
        getInstancesReportJson(orgId, PRODUCT_TAG_RHEL_FOR_X86, startOfToday, endOfToday);
    JsonNode data = instances.path("data");

    // Prefer an exact filter by sub-man id if present. Otherwise count==0 is still acceptable.
    boolean found = containsSubscriptionManagerId(data, hypervisorHost.subscriptionManagerId());
    assertTrue(!found, "Hypervisor without guests should not appear in instances report");
  }

  private int getDailyTotalSockets(String orgId, OffsetDateTime beginning, OffsetDateTime ending)
      throws Exception {
    // Use path params to safely handle product tags with spaces.
    String body =
        service
            .given()
            .header("x-rh-identity", SwatchUtils.createUserIdentityHeader(orgId))
            .queryParam("granularity", "Daily")
            .queryParam("beginning", beginning.toString())
            .queryParam("ending", ending.toString())
            .get(
                "/api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}",
                PRODUCT_TAG_RHEL_FOR_X86,
                METRIC_ID_SOCKETS)
            .then()
            .statusCode(200)
            .extract()
            .response()
            .asString();

    JsonNode json = objectMapper.readTree(body);
    JsonNode data = json.path("data");
    if (!data.isArray() || data.isEmpty()) {
      return 0;
    }
    // When a range is requested, the report filler may include multiple points; sum them.
    int total = 0;
    for (JsonNode point : data) {
      total += point.path("value").asInt(0);
    }
    return total;
  }

  private JsonNode getInstancesReportJson(
      String orgId, String productTag, OffsetDateTime beginning, OffsetDateTime ending)
      throws Exception {
    // TallyTestHelpers has an instances helper, but it concatenates the product into the URL.
    // Use explicit encoding to safely handle tags with spaces.
    String encodedProduct = URLEncoder.encode(productTag, StandardCharsets.UTF_8);
    String body =
        service
            .given()
            .header("x-rh-identity", SwatchUtils.createUserIdentityHeader(orgId))
            .queryParam("beginning", beginning.toString())
            .queryParam("ending", ending.toString())
            .get("/api/rhsm-subscriptions/v1/instances/products/" + encodedProduct)
            .then()
            .statusCode(200)
            .extract()
            .response()
            .asString();
    return objectMapper.readTree(body);
  }

  private boolean containsSubscriptionManagerId(JsonNode data, String subscriptionManagerId) {
    if (data == null || !data.isArray()) {
      return false;
    }
    Iterator<JsonNode> it = data.elements();
    while (it.hasNext()) {
      JsonNode row = it.next();
      if (subscriptionManagerId.equals(row.path("subscription_manager_id").asText(null))) {
        return true;
      }
      // Some serializers use camelCase in CT fixtures; tolerate both.
      if (subscriptionManagerId.equals(row.path("subscriptionManagerId").asText(null))) {
        return true;
      }
    }
    return false;
  }
}
