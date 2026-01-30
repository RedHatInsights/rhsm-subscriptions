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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import utils.TallyDbHostSeeder;
import utils.TallyTestHelpers;

public class TallyHypervisorTests extends BaseTallyComponentTest {

  private static final TallyTestHelpers helpers = new TallyTestHelpers();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final String PRODUCT_TAG_RHEL_FOR_X86 = "RHEL for x86";
  private static final String METRIC_ID_SOCKETS = "Sockets";

  @Test
  public void testTallyReportOfHypervisorWithNoGuests() throws Exception {
    String orgId = RandomUtils.generateRandom();

    // Seed baseline usage (non-zero) so we can assert it doesn't change.
    helpers.seedNightlyTallyHostBuckets(
        orgId, PRODUCT_TAG_RHEL_FOR_X86, UUID.randomUUID().toString(), service);

    helpers.syncTallyNightly(orgId, service);

    OffsetDateTime startOfToday =
        OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

    int initialSockets = getDailySocketsTotal(orgId, startOfToday, endOfToday);

    // Seed a "hypervisor" host with no guests and no buckets.
    TallyDbHostSeeder.SeededHost hypervisorHost =
        TallyDbHostSeeder.insertHost(
            orgId, UUID.randomUUID().toString(), "VIRTUAL", false, false, true, 0, null);

    helpers.syncTallyNightly(orgId, service);

    int newSockets = getDailySocketsTotal(orgId, startOfToday, endOfToday);
    assertEquals(
        initialSockets, newSockets, "Hypervisor without guests should not change total sockets");

    // System table check: ensure the host does not show up in instances report.
    Response instancesResponse =
        helpers.getInstancesReport(
            orgId, PRODUCT_TAG_RHEL_FOR_X86, startOfToday, endOfToday, service);
    JsonNode data = objectMapper.readTree(instancesResponse.asString()).path("data");

    boolean found = containsSubscriptionManagerId(data, hypervisorHost.subscriptionManagerId());
    assertFalse(found, "Hypervisor without guests should not appear in instances report");
  }

  private int getDailySocketsTotal(String orgId, OffsetDateTime beginning, OffsetDateTime ending)
      throws Exception {
    Response resp =
        helpers.getTallyReport(
            service,
            orgId,
            PRODUCT_TAG_RHEL_FOR_X86,
            METRIC_ID_SOCKETS,
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString()));

    JsonNode json = objectMapper.readTree(resp.asString());
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
