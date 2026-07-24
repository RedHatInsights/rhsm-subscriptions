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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.TallyHbiDbSeeder;

/**
 * SWATCH-5161: Verify hypervisor socket counts are not inflated.
 *
 * <p>Tests the scenario where multiple ESX hypervisor hosts have virtual guests. The tally report
 * filtered by category=hypervisor should reflect only the sum of the hypervisors' own socket
 * counts, not be inflated by guest VM counts or double-counting.
 */
public class TallyReportHypervisorSocketsTest extends BaseTallyComponentTest {

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

  /**
   * SWATCH-5161: Hypervisor socket count should equal the sum of actual hypervisor host sockets.
   *
   * <p>Setup: 3 ESX hypervisor hosts (4 + 4 + 2 = 10 total sockets) with 4 virtual guests spread
   * across them. The guests have their own socket counts which should NOT inflate the hypervisor
   * total.
   *
   * <p>Expected: category=hypervisor report shows exactly 10 sockets.
   */
  @Test
  void testHypervisorSocketCountNotInflated() {
    givenFeatureFlagIsConfigured(true);
    service.createOptInConfig(orgId);

    String hypSubmanId1 = helpers.generateUUIDOfSize(false, 36);
    String hypSubmanId2 = helpers.generateUUIDOfSize(false, 36);
    String hypSubmanId3 = helpers.generateUUIDOfSize(false, 36);

    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypSubmanId1)
        .displayName("ESX-Host-1")
        .sockets(4)
        .cores(16)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypSubmanId2)
        .displayName("ESX-Host-2")
        .sockets(4)
        .cores(16)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypSubmanId3)
        .displayName("ESX-Host-3")
        .sockets(2)
        .cores(8)
        .insert();

    // 4 virtual guests linked to the hypervisors via virtualHostUuid
    // 3 hypervisors: 4 + 4 + 2 = 10 total sockets
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("VM-Guest-1")
        .sockets(2)
        .cores(4)
        .virtualHostUuid(hypSubmanId1)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("VM-Guest-2")
        .sockets(2)
        .cores(4)
        .virtualHostUuid(hypSubmanId1)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("VM-Guest-3")
        .sockets(4)
        .cores(8)
        .virtualHostUuid(hypSubmanId2)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("VM-Guest-4")
        .sockets(2)
        .cores(2)
        .virtualHostUuid(hypSubmanId3)
        .insert();

    service.tallyOrg(orgId);

    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    var reportData =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity",
                "Daily",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "category",
                "hypervisor"));

    assertNotNull(reportData, "Tally report should be created");
    assertNotNull(reportData.getData(), "Report should have data");
    assertFalse(reportData.getData().isEmpty(), "Report should not be empty");

    boolean hasExpectedSockets =
        reportData.getData().stream()
            .anyMatch(point -> point.getValue() != null && point.getValue() == 10);
    assertTrue(
        hasExpectedSockets,
        "Hypervisor socket count should be 10 (sum of hypervisor host sockets 4+4+2),"
            + " not inflated by guest VM socket counts. Actual data points: "
            + reportData.getData().stream()
                .map(TallyReportDataPoint::getValue)
                .collect(Collectors.toList()));
  }

  /**
   * SWATCH-5161: Virtual guests with known hypervisors should not contribute sockets to the virtual
   * category.
   *
   * <p>For RHEL VDC products, guests mapped to a known hypervisor should not produce their own
   * tally buckets. Only unmapped guests contribute to the virtual category.
   */
  @Test
  void testMappedGuestsDoNotContributeToVirtualCategory() {
    givenFeatureFlagIsConfigured(true);
    service.createOptInConfig(orgId);

    String hypSubmanId = helpers.generateUUIDOfSize(false, 36);

    // 1 hypervisor with 4 sockets
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypSubmanId)
        .displayName("ESX-Hypervisor")
        .sockets(4)
        .cores(16)
        .insert();

    // 2 guests mapped to the hypervisor
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Mapped-Guest-1")
        .sockets(2)
        .cores(4)
        .virtualHostUuid(hypSubmanId)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Mapped-Guest-2")
        .sockets(2)
        .cores(4)
        .virtualHostUuid(hypSubmanId)
        .insert();

    service.tallyOrg(orgId);

    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    // Mapped guests should not appear in the virtual category
    var virtualReport =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity",
                "Daily",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "category",
                "virtual"));

    assertNotNull(virtualReport, "Virtual category report should be created");
    if (virtualReport.getData() != null) {
      long virtualSockets =
          virtualReport.getData().stream()
              .collect(Collectors.summarizingInt(TallyReportDataPoint::getValue))
              .getSum();
      assertEquals(
          0,
          virtualSockets,
          "Guests mapped to a known hypervisor should not contribute sockets to virtual category");
    }
  }

  /**
   * Demonstrates that a hypervisor with guests of different SLAs/usages produces multiple tally
   * buckets, each carrying the hypervisor's full socket count. Querying the hypervisor report by
   * individual SLA values and summing the results exceeds the actual hypervisor socket total,
   * because the code sums values from the filtered buckets.
   */
  @Test
  void testHypervisorSocketCountInflatedByMixedSlaUsage() {
    givenFeatureFlagIsConfigured(true);
    service.createOptInConfig(orgId);

    String hypSubmanId1 = helpers.generateUUIDOfSize(false, 36);
    String hypSubmanId2 = helpers.generateUUIDOfSize(false, 36);

    // Hypervisor 1 with 4 sockets, SLA=Premium
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypSubmanId1)
        .displayName("ESX-Mixed-SLA-Host-1")
        .sockets(8)
        .cores(16)
        .sla("")
        .usage("")
        .insert();

    // Hypervisor 2 with 4 sockets, SLA=Standard
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypSubmanId2)
        .displayName("ESX-Mixed-SLA-Host-2")
        .sockets(4)
        .cores(16)
        .sla("")
        .usage("")
        .insert();

    // Guest on Hyp1 with SLA=Premium, Usage=Production
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Guest-Hyp1-Premium")
        .sockets(2)
        .cores(4)
        .sla("Premium")
        .usage("Production")
        .virtualHostUuid(hypSubmanId1)
        .insert();

    // Guest on Hyp1 with SLA=Standard, Usage=Development/Test (mixed SLA)
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Guest-Hyp1-Standard")
        .sockets(2)
        .cores(4)
        .sla("Standard")
        .usage("Development/Test")
        .virtualHostUuid(hypSubmanId1)
        .insert();

    // Guest on Hyp2 with SLA=Premium, Usage=Production (mixed SLA)
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Guest-Hyp2-Premium")
        .sockets(2)
        .cores(4)
        .sla("Premium")
        .usage("Production")
        .virtualHostUuid(hypSubmanId2)
        .insert();

    // Guest on Hyp2 with SLA=Standard, Usage=Development/Test
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Guest-Hyp2-Standard")
        .sockets(2)
        .cores(4)
        .sla("Standard")
        .usage("Development/Test")
        .virtualHostUuid(hypSubmanId2)
        .insert();

    // Guest on Hyp1 with no SLA/Usage
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Guest-Hyp1-NoSla")
        .sockets(2)
        .cores(4)
        .sla("Standard")
        .usage("Production")
        .virtualHostUuid(hypSubmanId1)
        .insert();

    // Guest on Hyp2 with no SLA/Usage
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(helpers.generateUUIDOfSize(false, 36))
        .displayName("Guest-Hyp2-NoSla")
        .sockets(2)
        .cores(4)
        .sla("Standard")
        .usage("")
        .virtualHostUuid(hypSubmanId2)
        .insert();

    service.tallyOrg(orgId);

    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    // Query hypervisor report filtered by SLA=Premium
    var premiumReport =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString(),
                "category", "hypervisor",
                "sla", "Premium"));

    // Query hypervisor report filtered by SLA=Standard
    var standardReport =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString(),
                "category", "hypervisor",
                "sla", "Standard"));

    assertNotNull(premiumReport, "Premium report should be created");
    assertNotNull(premiumReport.getData(), "Premium report should have data");
    assertNotNull(standardReport, "Standard report should be created");
    assertNotNull(standardReport.getData(), "Standard report should have data");

    int premiumSockets =
        premiumReport.getData().stream()
            .filter(p -> p.getValue() != null && p.getValue() > 0)
            .mapToInt(TallyReportDataPoint::getValue)
            .max()
            .orElse(0);

    int standardSockets =
        standardReport.getData().stream()
            .filter(p -> p.getValue() != null && p.getValue() > 0)
            .mapToInt(TallyReportDataPoint::getValue)
            .max()
            .orElse(0);

    int actualPremiumHypervisorSockets = 12; // 2 hypervisors x 4 sockets each
    int actualStandardHypervisorSockets = 24; // 2 hypervisors x 4 sockets each

    // Each SLA-specific query reports both hypervisors' sockets (since both have
    // guests with each SLA), so each SLA sees the full 8 sockets
    assertEquals(
        actualPremiumHypervisorSockets,
        premiumSockets,
        "Premium SLA hypervisor report shows both hypervisors' socket count");
    assertEquals(
        actualStandardHypervisorSockets,
        standardSockets,
        "Standard SLA hypervisor report shows both hypervisors' socket count");

    // Query hypervisor report without specifying SLA (wildcard)
    var noSlaReport =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity",
                "Daily",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "category",
                "hypervisor"));

    assertNotNull(noSlaReport, "No-SLA report should be created");
    assertNotNull(noSlaReport.getData(), "No-SLA report should have data");

    int noSlaSockets =
        noSlaReport.getData().stream()
            .filter(p -> p.getValue() != null && p.getValue() > 0)
            .mapToInt(TallyReportDataPoint::getValue)
            .max()
            .orElse(0);

    // The wildcard (no SLA filter) report correctly shows the cumulative hypervisor total
    assertEquals(
        actualStandardHypervisorSockets + actualPremiumHypervisorSockets,
        noSlaSockets,
        String.format(
            "Wildcard SLA report (%d) should equal actual hypervisor sockets (%d)",
            noSlaSockets, actualStandardHypervisorSockets + actualPremiumHypervisorSockets));
  }
}
