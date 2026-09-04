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

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SWATCH-5161: Verify hypervisor socket counts are not inflated.
 *
 * <p>Tests the scenario where multiple ESX hypervisor hosts have virtual guests. The tally report
 * filtered by category=hypervisor should reflect only the sum of the hypervisors' own socket
 * counts, not be inflated by guest VM counts or double-counting.
 */
public class TallyReportHypervisorSocketsTest extends BaseTallyComponentTest {

  private HostStateManager hostManager;

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
  @TestPlanName("tally-hypervisor-TC008")
  void testHypervisorSocketCountNotInflated() {
    givenFeatureFlagIsConfigured(true);
    service.createOptInConfig(orgId);

    SeededHost hypervisor1 =
        hostManager
            .createHost(orgId)
            .displayName("ESX-Host-1")
            .apply(HostTemplates.conduitReportedPhysicalRhel(4, 16))
            .insert();

    SeededHost hypervisor2 =
        hostManager
            .createHost(orgId)
            .displayName("ESX-Host-2")
            .apply(HostTemplates.conduitReportedPhysicalRhel(4, 16))
            .insert();

    SeededHost hypervisor3 =
        hostManager
            .createHost(orgId)
            .displayName("ESX-Host-3")
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 8))
            .insert();

    // 4 virtual guests linked to the hypervisors via virtualHostUuid
    // 3 hypervisors: 4 + 4 + 2 = 10 total sockets
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor1.subscriptionManagerId(), "Premium", "Production", 2, 4))
        .displayName("VM-Guest-1")
        .insert();

    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor1.subscriptionManagerId(), "Premium", "Production", 2, 4))
        .displayName("VM-Guest-2")
        .insert();

    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor2.subscriptionManagerId(), "Premium", "Production", 4, 8))
        .displayName("VM-Guest-3")
        .insert();

    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor3.subscriptionManagerId(), "Premium", "Production", 2, 2))
        .displayName("VM-Guest-4")
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
            + reportData.getData().stream().map(TallyReportDataPoint::getValue).toList());
  }

  /**
   * SWATCH-5161: Virtual guests with known hypervisors should not contribute sockets to the virtual
   * category.
   *
   * <p>For RHEL VDC products, guests mapped to a known hypervisor should not produce their own
   * tally buckets. Only unmapped guests contribute to the virtual category.
   */
  @Test
  @TestPlanName("tally-hypervisor-TC009")
  void testMappedGuestsDoNotContributeToVirtualCategory() {
    givenFeatureFlagIsConfigured(true);
    service.createOptInConfig(orgId);

    // 1 hypervisor with 4 sockets
    SeededHost hypervisor =
        hostManager
            .createHost(orgId)
            .displayName("ESX-Hypervisor")
            .apply(HostTemplates.conduitReportedPhysicalRhel(4, 16))
            .insert();

    // 2 guests mapped to the hypervisor
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor.subscriptionManagerId(), "Premium", "Production", 2, 4))
        .displayName("Mapped-Guest-1")
        .insert();

    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor.subscriptionManagerId(), "Premium", "Production", 2, 4))
        .displayName("Mapped-Guest-2")
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
    assertNotNull(virtualReport.getData(), "Virtual category report should have data");
    int virtualSockets =
        virtualReport.getData().stream()
            .filter(p -> p.getValue() != null)
            .mapToInt(TallyReportDataPoint::getValue)
            .sum();
    assertEquals(
        0,
        virtualSockets,
        "Guests mapped to a known hypervisor should not contribute sockets to virtual category");
  }

  /**
   * Demonstrates that a hypervisor with guests of different SLAs/usages produces multiple tally
   * buckets, each carrying the hypervisor's full socket count. Individual SLA-filtered queries may
   * overlap (e.g. Premium=8, Standard=12, sum=20 > actual total of 12), but the wildcard query (no
   * SLA filter) correctly returns the actual hypervisor socket total because it reads from the _ANY
   * rows rather than summing per-SLA buckets.
   */
  @Test
  @TestPlanName("tally-hypervisor-TC010")
  void testHypervisorSocketCountNotInflatedByMixedSlaUsage() {
    givenFeatureFlagIsConfigured(true);
    service.createOptInConfig(orgId);

    // Hypervisor 1 with 8 sockets
    HostBuilder hypervisorBuilder =
        hostManager
            .createHost(orgId)
            .displayName("ESX-Mixed-SLA-Host-1")
            .apply(HostTemplates.conduitReportedPhysicalRhel(8, 16));
    hypervisorBuilder.rhsmFacts(
        hypervisorBuilder.rhsmFacts().toBuilder().sla("").usage("").build());
    SeededHost hypervisor1 = hypervisorBuilder.insert();

    // Hypervisor 2 with 4 sockets
    HostBuilder hypervisor2Builder =
        hostManager
            .createHost(orgId)
            .displayName("ESX-Mixed-SLA-Host-1")
            .apply(HostTemplates.conduitReportedPhysicalRhel(4, 16));
    hypervisor2Builder.rhsmFacts(
        hypervisor2Builder.rhsmFacts().toBuilder().sla("").usage("").build());
    SeededHost hypervisor2 = hypervisor2Builder.insert();

    // Guest on Hyp1 with SLA=Premium, Usage=Production
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor1.subscriptionManagerId(), "Premium", "Production", 2, 4))
        .displayName("Guest-Hyp1-Premium")
        .insert();

    // Guest on Hyp1 with SLA=Standard, Usage=Development/Test
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor1.subscriptionManagerId(), "Standard", "Development/Test", 2, 4))
        .displayName("Guest-Hyp1-Standard-Dev")
        .insert();

    // Guest on Hyp2 with SLA=Standard, Usage=Development/Test
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor2.subscriptionManagerId(), "Standard", "Development/Test", 2, 4))
        .displayName("Guest-Hyp2-Standard-Dev")
        .insert();

    // Guest on Hyp1 with SLA=Standard, Usage=Production
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor1.subscriptionManagerId(), "Standard", "Production", 2, 4))
        .displayName("Guest-Hyp1-Standard-Production")
        .insert();

    // Guest on Hyp2 with SLA=Standard, Usage=""
    hostManager
        .createHost(orgId)
        .apply(
            HostTemplates.conduitReportedVirtualRhelGuest(
                hypervisor2.subscriptionManagerId(), "Standard", "", 2, 4))
        .displayName("Guest-Hyp2-Standard-Empty")
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

    int actualPremiumHypervisorSockets = 8; // 1 hypervisor with 8 sockets
    int totalHypervisorSockets = 12; // 2 hypervisors with 4 and 8 sockets

    // Each SLA-specific query reports hypervisors' sockets
    assertEquals(
        actualPremiumHypervisorSockets,
        premiumSockets,
        "Premium SLA hypervisor report shows Hyp1's socket count (only hypervisor with Premium guests)");
    assertEquals(
        totalHypervisorSockets,
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

    // The wildcard (no SLA filter) report correctly shows the combined hypervisor total
    assertEquals(
        totalHypervisorSockets,
        noSlaSockets,
        String.format(
            "Wildcard SLA report (%d) should equal actual hypervisor sockets (%d)",
            noSlaSockets, totalHypervisorSockets));
  }
}
