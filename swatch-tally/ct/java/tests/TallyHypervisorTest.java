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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import com.redhat.swatch.tally.test.model.TallySnapshot;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import utils.TallyDbHostSeeder;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TallyHypervisorTest extends BaseTallyComponentTest {

  private HostStateManager hostManager;

  public OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
  public OffsetDateTime end = start.plusDays(1).minusNanos(1);

  @BeforeEach
  void setupHostManager() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
  }

  @AfterEach
  void cleanupHosts() {
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  @AfterAll
  void resetPrimaryBucketSearchesFlag() {
    givenPrimaryBucketSearchesEnabled(false);
  }

  @Test
  @TestPlanName("tally-hypervisor-TC001")
  public void testRHELHypervisorWithoutGuestsInInstancesReport() {
    // Given: You have an org opted in and a hypervisor host with no guests
    service.createOptInConfig(orgId);
    service.tallyOrg(orgId);

    SeededHost hypervisor =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 1))
            .insert();

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);
    var instanceResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), beginning, ending);

    // Then: The instance appears in the instances API
    var hypervisorIR =
        instanceResponse.getData().stream()
            .filter(i -> hypervisor.hostId().toString().equalsIgnoreCase(i.getInstanceId()))
            .findFirst();

    assertNotNull(hypervisorIR, "Hypervisor should be in the instances report");
    assertEquals(
        hypervisorIR.get().getSubscriptionManagerId(),
        hypervisor.subscriptionManagerId(),
        String.format(
            "Hypervisor should match the expected subscription manager id but found %s not %s",
            hypervisorIR.get().getSubscriptionManagerId(), hypervisor.subscriptionManagerId()));

    assertEquals(
        2.0,
        hypervisorIR.get().getMeasurements().get(0),
        String.format(
            "Was expecting the hypervisor's measurement %s to be in the instances report data but found %s",
            2, instanceResponse.getData().get(0).getMeasurements().get(0)));
  }

  @Test
  @TestPlanName("tally-hypervisor-TC002")
  public void testRHELHypervisorWithoutGuestsContributesToDailyTotal() {
    int testSocketCount = 2;

    // Given: Org is opted in and a nightly tally is preformed
    service.createOptInConfig(orgId);
    service.tallyOrg(orgId);

    var initalTallyReport =
        service.getTallyReportData(
            orgId, RHEL_FOR_X86.productTag(), "Sockets", getDailyTallyTimeParameters(start, end));

    double beforeHostTotal =
        initalTallyReport.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();

    SeededHost hypervisor =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(testSocketCount, 1))
            .insert();

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    var endTallyReport =
        service.getTallyReportData(
            orgId, RHEL_FOR_X86.productTag(), "Sockets", getDailyTallyTimeParameters(start, end));
    Log.info("endTallyReport: %s", endTallyReport);

    double afterHostTotal =
        endTallyReport.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();

    // Then: The daily total should increase by the number of sockets of the hypervisor
    assertEquals(
        beforeHostTotal + testSocketCount,
        afterHostTotal,
        "Daily total should increase by the number of sockets of the hypervisor");
  }

  @Test
  @TestPlanName("tally-hypervisor-TC003")
  public void testHypervisorWithNoGuestsDoesNotShowInInstancesReport() {
    // Given: Baseline tally data and a hypervisor host with no guests
    helpers.seedNightlyTallyHostBuckets(
        seeder, orgId, RHEL_FOR_X86.productTag(), UUID.randomUUID().toString(), service);
    service.tallyOrg(orgId);

    TallyDbHostSeeder.SeededHost hypervisorHost =
        seeder.insertHost(
            orgId, UUID.randomUUID().toString(), "VIRTUALIZED", false, false, true, 0, null);

    // When: Running tally for the org
    service.tallyOrg(orgId);

    // Then: Hypervisor without guests should not appear in instances report
    var instancesResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), start, end);
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

    long initialSockets = getDailySocketsTotal(start, end);

    seeder.insertHost(
        orgId, UUID.randomUUID().toString(), "VIRTUALIZED", false, false, true, 0, null);

    // When: Running tally for the org
    service.tallyOrg(orgId);

    // Then: Hypervisor without guests should not change the total sockets
    long newSockets = getDailySocketsTotal(start, end);
    assertEquals(
        initialSockets, newSockets, "Hypervisor without guests should not change total sockets");
  }

  @TestPlanName("tally-hypervisor-TC005")
  @Test
  public void testRHELHypervisorWithGuestsIncreasesTotalSockets() {
    String sla = "Premium";
    String usage = "Production";

    // Given: Org is opted in, Tally has been preformed, and the initial state reports have been
    // fetched
    service.createOptInConfig(orgId);
    service.tallyOrg(orgId);

    var initialHypervisorTally =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            mapWith(getDailyTallyTimeParameters(start, end), "category", "hypervisor"));
    Log.info("initialHypervisorTally: %s", initialHypervisorTally);

    var initialTallyCloudSockets =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            mapWith(getDailyTallyTimeParameters(start, end), "category", "cloud"));
    Log.info("initialTallyCloudSockets: %s", initialTallyCloudSockets);

    var initialTallyCloudCores =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Cores",
            mapWith(getDailyTallyTimeParameters(start, end), "category", "cloud"));
    Log.info("initialTallyCloudCores: %s", initialTallyCloudCores);

    var initTallySum =
        initialHypervisorTally.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();
    var initTallyCloudSocketsSum =
        initialTallyCloudSockets.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();
    var initTallyCloudCoresSum =
        initialTallyCloudCores.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();

    // Create a hypervisor with 2 guests
    SeededHost phyHypervisor =
        hostManager
            .createHost(orgId)
            .displayName("Hypervisor-1")
            .apply(HostTemplates.conduitReportedPhysicalRhel(1, 4))
            .insert();

    hostManager
        .createHost(orgId)
        .displayName("guest1")
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                phyHypervisor.subscriptionManagerId(), sla, usage, 1, 1))
        .insert();

    hostManager
        .createHost(orgId)
        .displayName("guest2")
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                phyHypervisor.subscriptionManagerId(), sla, usage, 1, 1))
        .insert();

    // Then: Run a post hypervisor tally and fetch the updated tally data
    service.tallyOrg(orgId);

    start = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    end = start.plusDays(1).minusNanos(1);
    // Query for instances report by NAME containing "Hypervisor-1" and category "hypervisor"
    var instanceResponse =
        service.getInstancesByProduct(
            orgId,
            RHEL_FOR_X86.productTag(),
            start,
            end,
            Map.of("display_name_contains", "Hypervisor-1", "category", "hypervisor"));

    Log.info("Instance Report: %s", instanceResponse);

    var endHypervisorTally =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            mapWith(getDailyTallyTimeParameters(start, end), "category", "hypervisor"));
    Log.info("endHypervisorTally: %s", endHypervisorTally.getData());

    var endTallyCloudSockets =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            mapWith(getDailyTallyTimeParameters(start, end), "category", "cloud"));
    Log.info("endTallyCloudSockets: %s", endTallyCloudSockets.getData());

    var endTallyCloudCores =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Cores",
            mapWith(getDailyTallyTimeParameters(start, end), "category", "cloud"));
    Log.info("endTallyCloudCores: %s", endTallyCloudCores.getData());

    var endHypervisorTallySum =
        endHypervisorTally.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();
    var endTallyCloudSocketsSum =
        endTallyCloudSockets.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();
    var endTallyCloudCoresSum =
        endTallyCloudCores.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();

    double hypervisorCount =
        instanceResponse.getData().stream()
            .filter(i -> "hypervisor".equalsIgnoreCase(i.getCategory().toString()))
            .flatMapToDouble(i -> i.getMeasurements().stream().mapToDouble(Double::doubleValue))
            .sum();

    Integer guestCount =
        instanceResponse.getData().stream()
            .filter(i -> "Hypervisor-1".equalsIgnoreCase(i.getDisplayName().toString()))
            .findFirst()
            .get()
            .getNumberOfGuests();

    // Assert that hypervisor socket measurements increased as expected
    assertEquals(2, hypervisorCount, "Hypervisor sockets should be equal to 2");

    // Asser the guest count is 2
    assertEquals(2, guestCount, "Hypervisor should have 2 guests");

    // Verify the tally increased by 2
    assertEquals(
        initTallySum + 2,
        endHypervisorTallySum,
        String.format(
            "Daily total should increase by 2 sockets. Started with %s and ended with %s",
            initTallySum, endHypervisorTallySum));

    // Verify that the Cloud sockets should NOT have changed
    assertEquals(
        initTallyCloudSocketsSum,
        endTallyCloudSocketsSum,
        "There should be no change in cloud sockets");
    assertEquals(
        initTallyCloudCoresSum, endTallyCloudCoresSum, "There should be no change in cloud cores");
  }

  @Test
  @TestPlanName("tally-hypervisor-TC006")
  @Disabled("This test uncovered bug SWATCH-5585. Should be reactivated when the bug is resolved")
  public void testGuestMappingUpdateChangesInstancesPresence() {
    String sla = "Premium";
    String usage = "Production";

    service.createOptInConfig(orgId);

    // Create a hypervisor Host A
    SeededHost hostASeededHost =
        hostManager
            .createHost(orgId)
            .displayName("HostA")
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 4))
            .insert();

    // Create a guest on Host A
    // Creating a builder because we are updating the host for this guest later
    HostBuilder guestBuilder =
        hostManager
            .createHost(orgId)
            .displayName("guest1")
            .apply(
                HostTemplates.conduitReportedVirtualRhelGuest(
                    hostASeededHost.subscriptionManagerId(), sla, usage, 1, 1));
    guestBuilder.insert();

    // Create a hypervisor Host B without a guest
    SeededHost hostBSeededHost =
        hostManager
            .createHost(orgId)
            .displayName("HostB")
            .apply(HostTemplates.conduitReportedPhysicalRhel(1, 4))
            .insert();

    service.tallyOrg(orgId);

    // Verify that the system table (instance report) has mapped guest to hypervisor A
    var instanceResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), start, end);
    Log.info("Instance Report: %s", instanceResponse);

    // Verify that hypervisor Host A is in the Instance report and has a guest
    var hostAGuestCount =
        instanceResponse.getData().stream()
            .filter(i -> hostASeededHost.hostId().toString().equalsIgnoreCase(i.getInstanceId()))
            .findFirst()
            .get()
            .getNumberOfGuests();

    var hostBGuestCount =
        instanceResponse.getData().stream()
            .filter(i -> hostBSeededHost.hostId().toString().equalsIgnoreCase(i.getInstanceId()))
            .findFirst()
            .get()
            .getNumberOfGuests();

    assertEquals(
        1,
        hostAGuestCount,
        String.format(
            "Hypervisor with guest should have 1 guest but has %s guests", hostAGuestCount));
    assertEquals(
        0,
        hostBGuestCount,
        String.format(
            "Hypervisor without guest should have 0 guest but has %s guests", hostBGuestCount));

    // Update the guest1 to have hypervisor_uuid to point to Host B
    guestBuilder
        .systemProfileFacts(
            guestBuilder.getSystemProfileFacts().toBuilder()
                .hypervisorUuid(hostBSeededHost.subscriptionManagerId())
                .build())
        .update();

    service.tallyOrg(orgId);

    // Fetch fresh instance reports
    InstanceResponse response =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), start, end);
    Log.info("Instance Report after update: %s", response);

    // Get the Host A guest count after the update
    var hostAIRAfterUpdateCount =
        response.getData().stream()
            .filter(i -> hostASeededHost.hostId().toString().equalsIgnoreCase(i.getInstanceId()))
            .findFirst()
            .get()
            .getNumberOfGuests();

    var hostBIRAfterUpdateCount =
        response.getData().stream()
            .filter(i -> hostBSeededHost.hostId().toString().equalsIgnoreCase(i.getInstanceId()))
            .findFirst()
            .get()
            .getNumberOfGuests();

    // Assert Host A lost its guest and Host B gained it
    assertEquals(0, hostAIRAfterUpdateCount);
    assertEquals(1, hostBIRAfterUpdateCount);
  }

  @Test
  @TestPlanName("tally-hypervisor-TC011")
  public void testGuestMappingUpdateChangesInstanceGuestReport() {
    String sla = "Premium";
    String usage = "Production";

    service.createOptInConfig(orgId);

    // Create a hypervisor A with 1 guest
    SeededHost hostA =
        hostManager
            .createHost(orgId)
            .displayName("Host A")
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 4))
            .insert();

    // Creating a builder because we are updating the host later
    HostBuilder guestBuilder =
        hostManager
            .createHost(orgId)
            .displayName("guest1")
            .apply(
                HostTemplates.conduitReportedVirtualRhelGuest(
                    hostA.subscriptionManagerId(), sla, usage, 1, 1));
    guestBuilder.insert();

    // Create a hypervisor b without a guest
    SeededHost hostB =
        hostManager
            .createHost(orgId)
            .displayName("Host B")
            .apply(HostTemplates.conduitReportedPhysicalRhel(1, 4))
            .insert();

    // Verify that the system table (instance report) has mapped guest to hypervisor A
    service.tallyOrg(orgId);

    // Query for instances Guest report
    var instanceGuestReportHostA =
        service.getInstanceGuestReportData(orgId, start, end, hostA.hostId().toString());
    Log.info("Instance Guest report Host a: %s", instanceGuestReportHostA);

    var instanceGuestReportHostB =
        service.getInstanceGuestReportData(orgId, start, end, hostB.hostId().toString());
    Log.info("Instance Guest report Host a: %s", instanceGuestReportHostB);

    assertEquals(
        1, instanceGuestReportHostA.getMeta().getCount(), "Guest count should be 1 for Host A");
    assertEquals(
        0, instanceGuestReportHostB.getMeta().getCount(), "Guest count should be 0 for Host B");

    // Update the guest to have hypervisor_uuid to point to guest 1
    guestBuilder
        .systemProfileFacts(
            guestBuilder.getSystemProfileFacts().toBuilder()
                .hypervisorUuid(hostB.subscriptionManagerId())
                .build())
        .update();

    service.tallyOrg(orgId);

    // Verify that the Instance Guest report has the updated guest count
    var instanceGuestReportHostAAfterUpdate =
        service.getInstanceGuestReportData(orgId, start, end, hostA.hostId().toString());
    Log.info("Instance Guest report Host a: %s", instanceGuestReportHostAAfterUpdate);

    var instanceGuestReportHostBAfterUpdate =
        service.getInstanceGuestReportData(orgId, start, end, hostB.hostId().toString());
    Log.info("Instance Guest report Host a: %s", instanceGuestReportHostBAfterUpdate);

    assertEquals(
        0,
        instanceGuestReportHostAAfterUpdate.getMeta().getCount(),
        "Guest count should be 0 for Host A");
    assertEquals(
        1,
        instanceGuestReportHostBAfterUpdate.getMeta().getCount(),
        "Guest count should be 1 for Host B");
  }

  @ParameterizedTest(name = "Using primary bucket searches: {0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-hypervisor-TC007")
  void shouldReportHypervisorOnceWhenSlaAndUsageWildcarded(boolean usePrimaryBucketSearches) {
    // Given: Hypervisor with overlapping guest SLA/usage and primary-bucket flag configured
    givenPrimaryBucketSearchesEnabled(usePrimaryBucketSearches);
    service.createOptInConfig(orgId);
    SeededHost hypervisor = givenHypervisorWithOverlappingGuestSla();

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

  private SeededHost givenHypervisorWithOverlappingGuestSla() {
    // Hypervisor subscription_manager_id must be a UUID so guests can set virtual_host_uuid
    SeededHost hypervisor =
        hostManager
            .createHost(orgId)
            .displayName("hypervisor-overlapping-guest-sla")
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 8))
            .insert();

    // Guests mapped to the hypervisor with overlapping SLA and different usages
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor.subscriptionManagerId(), "Premium", "Production", 1, 4))
        .insert();

    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor.subscriptionManagerId(), "Premium", "Development/Test", 1, 4))
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
                "granularity", TallySnapshot.Granularity.DAILY.toString(),
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

  private Map<String, Object> getDailyTallyTimeParameters(
      OffsetDateTime start, OffsetDateTime end) {

    return Map.of(
        "granularity", "Daily",
        "beginning", start.toString(),
        "ending", end.toString());
  }

  private Map<String, Object> mapWith(Map<String, Object> baseMap, String key, Object value) {
    Map<String, Object> newMap = new HashMap<>(baseMap);
    newMap.put(key, value);
    return newMap;
  }
}
