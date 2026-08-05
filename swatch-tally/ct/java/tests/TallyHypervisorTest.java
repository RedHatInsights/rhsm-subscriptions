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

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import utils.TallyDbHostSeeder;
import utils.TallyHbiDbSeeder;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TallyHypervisorTest extends BaseTallyComponentTest {

  private TallyHbiDbSeeder hbiSeeder;

  @BeforeEach
  void setupHbiSeeder() {
    hbiSeeder = new TallyHbiDbSeeder(hbiDatabase);
  }

  @AfterEach
  void cleanupHbiHosts() {
    if (hbiSeeder != null) {
      hbiSeeder.deleteAllInsertedHosts();
    }
  }

  @AfterAll
  void resetPrimaryBucketSearchesFlag() {
    givenPrimaryBucketSearchesEnabled(false);
  }

  @Test
  @TestPlanName("tally-hypervisor-TC003")
  public void testHypervisorWithNoGuestsDoesNotShowInInstancesReport() {
    // Given: Baseline tally data and a hypervisor host with no guests
    helpers.seedNightlyTallyHostBuckets(
        seeder, orgId, RHEL_FOR_X86.productTag(), UUID.randomUUID().toString(), service);
    service.tallyOrg(orgId);

    OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

    TallyDbHostSeeder.SeededHost hypervisorHost =
        seeder.insertHost(
            orgId, UUID.randomUUID().toString(), "VIRTUALIZED", false, false, true, 0, null);

    // When: Running tally for the org
    service.tallyOrg(orgId);

    // Then: Hypervisor without guests should not appear in instances report
    var instancesResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), startOfToday, endOfToday);
    var data = instancesResponse.getData();

    boolean found = containsSubscriptionManagerId(data, hypervisorHost.subscriptionManagerId());
    assertFalse(found, "Hypervisor without guests should not appear in instances report");
  }

  @Test
  @TestPlanName("tally-hypervisor-TC004")
  public void testHypervisorWithNoGuestsDoesNotChangeDailyTotal() {
    // Given: Baseline usage and a hypervisor host with no guests
    helpers.seedNightlyTallyHostBuckets(
        seeder, orgId, RHEL_FOR_X86.productTag(), UUID.randomUUID().toString(), service);
    service.tallyOrg(orgId);

    OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

    long initialSockets = getDailySocketsTotal(startOfToday, endOfToday);

    seeder.insertHost(
        orgId, UUID.randomUUID().toString(), "VIRTUALIZED", false, false, true, 0, null);

    // When: Running tally for the org
    service.tallyOrg(orgId);

    // Then: Hypervisor without guests should not change the total sockets
    long newSockets = getDailySocketsTotal(startOfToday, endOfToday);
    assertEquals(
        initialSockets, newSockets, "Hypervisor without guests should not change total sockets");
  }

  @ParameterizedTest(name = "Using primary bucket searches: {0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-hypervisor-TC007")
  void shouldReportHypervisorOnceWhenSlaAndUsageWildcarded(boolean usePrimaryBucketSearches) {
    // Given: Hypervisor with overlapping guest SLA/usage and primary-bucket flag configured
    givenPrimaryBucketSearchesEnabled(usePrimaryBucketSearches);
    service.createOptInConfig(orgId);
    TallyHbiDbSeeder.SeededHost hypervisor = givenHypervisorWithOverlappingGuestSla();
    OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime end = start.plusDays(1).minusNanos(1);

    // When: Fetching instances with category=hypervisor (SLA and usage wildcarded)
    InstanceResponse response =
        service.getInstancesByProduct(
            orgId, RHEL_FOR_X86.productTag(), start, end, Map.of("category", "hypervisor"));

    // Then: Hypervisor appears exactly once despite multiple primary HYPERVISOR buckets
    long count =
        response.getData() == null
            ? 0
            : response.getData().stream()
                .filter(
                    i -> hypervisor.subscriptionManagerId().equals(i.getSubscriptionManagerId()))
                .count();
    assertEquals(1, count, "Hypervisor must appear once in instances report");
  }

  // --- Given helper methods ---

  private TallyHbiDbSeeder.SeededHost givenHypervisorWithOverlappingGuestSla() {
    // Hypervisor subscription_manager_id must be a UUID so guests can set virtual_host_uuid
    String hypervisorSubManId = UUID.randomUUID().toString();
    TallyHbiDbSeeder.SeededHost hypervisor =
        hbiSeeder
            .rhelHost(orgId)
            .subscriptionManagerId(hypervisorSubManId)
            .displayName("hypervisor-overlapping-guest-sla")
            .cores(8)
            .sockets(2)
            .insert();

    // Guests mapped to the hypervisor with overlapping SLA and different usages
    hbiSeeder
        .rhelHost(orgId)
        .hypervisorUuid(hypervisorSubManId)
        .sla("Premium")
        .usage("Production")
        .cores(4)
        .sockets(1)
        .insert();
    hbiSeeder
        .rhelHost(orgId)
        .hypervisorUuid(hypervisorSubManId)
        .sla("Premium")
        .usage("Development/Test")
        .cores(4)
        .sockets(1)
        .insert();

    service.tallyOrg(orgId);
    return hypervisor;
  }

  // --- Then helper methods ---

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
