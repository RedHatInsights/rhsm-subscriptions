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
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import utils.TallyDbHostSeeder;

public class TallyHypervisorTests extends BaseTallyComponentTest {

  @Test
  public void testHypervisorWithNoGuestsDoesNotShowInInstancesReport() {
    helpers.seedNightlyTallyHostBuckets(
        orgId, RHEL_FOR_X86.productTag(), UUID.randomUUID().toString(), service);

    service.tallyOrg(orgId);

    OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

    // Seed a "hypervisor" host with no guests and no buckets.
    TallyDbHostSeeder.SeededHost hypervisorHost =
        TallyDbHostSeeder.insertHost(
            orgId, UUID.randomUUID().toString(), "VIRTUAL", false, false, true, 0, null);

    service.tallyOrg(orgId);

    // System table check: ensure the host does not show up in instances report.
    var instancesResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), startOfToday, endOfToday);
    var data = instancesResponse.getData();

    boolean found = containsSubscriptionManagerId(data, hypervisorHost.subscriptionManagerId());
    assertFalse(found, "Hypervisor without guests should not appear in instances report");
  }

  @Test
  public void testHypervisorWithNoGuestsDoesNotChangeDailyTotal() {
    // Seed baseline usage (non-zero) so we can assert it doesn't change.
    helpers.seedNightlyTallyHostBuckets(
        orgId, RHEL_FOR_X86.productTag(), UUID.randomUUID().toString(), service);

    service.tallyOrg(orgId);

    OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

    long initialSockets = getDailySocketsTotal(startOfToday, endOfToday);

    // Seed a "hypervisor" host with no guests and no buckets.
    TallyDbHostSeeder.insertHost(
        orgId, UUID.randomUUID().toString(), "VIRTUAL", false, false, true, 0, null);

    service.tallyOrg(orgId);

    long newSockets = getDailySocketsTotal(startOfToday, endOfToday);
    assertEquals(
        initialSockets, newSockets, "Hypervisor without guests should not change total sockets");
  }

  private long getDailySocketsTotal(OffsetDateTime beginning, OffsetDateTime ending) {
    var resp =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            RHEL_FOR_X86.metricIds().get(0),
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString()));

    if (resp.getData() == null) {
      return 0;
    }
    // When a range is requested, the report filler may include multiple points; sum them.
    return resp.getData().stream()
        .collect(Collectors.summarizingInt(TallyReportDataPoint::getValue))
        .getSum();
  }

  private boolean containsSubscriptionManagerId(
      List<InstanceData> data, String subscriptionManagerId) {
    if (data == null) {
      return false;
    }

    return data.stream()
        .anyMatch(i -> Objects.equals(i.getSubscriptionManagerId(), subscriptionManagerId));
  }
}
