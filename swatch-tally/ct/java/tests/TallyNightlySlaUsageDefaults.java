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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Component tests for nightly tally with SLA/Usage defaults.
 *
 * <p>Tests the flow:
 *
 * <ol>
 *   <li>Insert hosts with and without SLA/Usage facts into HBI database
 *   <li>Run nightly tally (reads from HBI)
 *   <li>Verify tally results reflect correct defaults or explicit values
 * </ol>
 */
public class TallyNightlySlaUsageDefaults extends BaseTallyComponentTest {

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

  @Test
  @TestPlanName("tally-sla-and-usage-defaults-TC001")
  void testNightlyTallyDefaultsToPremiumProductionWhenSlaUsageNotSet() {
    // Given: Primary row searches are enabled
    givenFeatureFlagIsConfigured(true);

    // Given: Org is opted in and host without SLA/Usage facts exists
    service.createOptInConfig(orgId);
    SeededHost host =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 8))
            .insert();
    assertNotNull(host.hostId(), "Host should be created");

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: Host is tallied with Premium SLA and Production Usage (defaults)
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    // Query with Premium SLA filter - should return data
    var premiumReport =
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
                "sla",
                "Premium"));

    assertNotNull(premiumReport, "Tally report with Premium SLA should exist");
    assertNotNull(premiumReport.getData(), "Premium report should have data points");
    assertFalse(premiumReport.getData().isEmpty(), "Premium report should have data points");
    // At least one data point should have hasData=true (actual data exists for Premium SLA)
    assertTrue(
        premiumReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Premium report should have at least one data point with data");

    // Query with Production Usage filter - should return data
    var productionReport =
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
                "usage",
                "Production"));

    assertNotNull(productionReport, "Tally report with Production Usage should exist");
    assertNotNull(productionReport.getData(), "Production report should have data points");
    assertFalse(productionReport.getData().isEmpty(), "Production report should have data points");
    // At least one data point should have hasData=true (actual data exists for Production)
    assertTrue(
        productionReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Production report should have at least one data point with data");

    // Query with Standard SLA filter - should return no data (host has Premium)
    var standardReport =
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
                "sla",
                "Standard"));

    assertNotNull(standardReport, "Tally report with Standard SLA should exist");
    assertNotNull(standardReport.getData(), "Standard report should have data points");
    assertFalse(standardReport.getData().isEmpty(), "Standard report should have data points");
    // All data points should have hasData=false (no actual data for Standard SLA)
    assertTrue(
        standardReport.getData().stream().noneMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Standard SLA report should have no data (host defaulted to Premium)");

    // Query with Development/Test Usage filter - should return no data (host has Production)
    var devTestReport =
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
                "usage",
                "Development/Test"));

    assertNotNull(devTestReport, "Tally report with Development/Test Usage should exist");
    assertNotNull(devTestReport.getData(), "Development/Test report should have data points");
    assertFalse(
        devTestReport.getData().isEmpty(), "Development/Test report should have data points");
    // All data points should have hasData=false (no actual data for Development/Test)
    assertTrue(
        devTestReport.getData().stream().noneMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Development/Test Usage report should have no data (host defaulted to Production)");
  }

  @Test
  @TestPlanName("tally-sla-and-usage-defaults-TC002")
  void testNightlyTallyRespectsExplicitSlaUsage() {
    // Given: Primary row searches are enabled
    givenFeatureFlagIsConfigured(true);

    // Given: Org is opted in and host with explicit SLA/Usage exists
    service.createOptInConfig(orgId);
    SeededHost host =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 8))
            .rhsmFacts(
                RhsmFacts.builder()
                    .isVirtual(false)
                    .products(List.of("69"))
                    .sla("Standard")
                    .usage("Development/Test")
                    .build())
            .insert();
    assertNotNull(host.hostId(), "Host should be created");

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: Host is tallied with Standard SLA and Development/Test Usage (explicit values)
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    // Query with Standard SLA filter - should return data
    var standardReport =
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
                "sla",
                "Standard"));

    assertNotNull(standardReport, "Tally report with Standard SLA should exist");
    assertNotNull(standardReport.getData(), "Standard report should have data points");
    assertFalse(standardReport.getData().isEmpty(), "Standard report should have data points");
    // At least one data point should have hasData=true (actual data exists for Standard SLA)
    assertTrue(
        standardReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Standard report should have at least one data point with data");

    // Query with Development/Test Usage filter - should return data
    var devTestReport =
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
                "usage",
                "Development/Test"));

    assertNotNull(devTestReport, "Tally report with Development/Test Usage should exist");
    assertNotNull(devTestReport.getData(), "Development/Test report should have data points");
    assertFalse(
        devTestReport.getData().isEmpty(), "Development/Test report should have data points");
    // At least one data point should have hasData=true (actual data exists for Development/Test)
    assertTrue(
        devTestReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Development/Test report should have at least one data point with data");

    // Query with Premium SLA filter - should return no data (host has Standard)
    var premiumReport =
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
                "sla",
                "Premium"));

    assertNotNull(premiumReport, "Tally report with Premium SLA should exist");
    assertNotNull(premiumReport.getData(), "Premium report should have data points");
    assertFalse(premiumReport.getData().isEmpty(), "Premium report should have data points");
    // All data points should have hasData=false (no actual data for Premium SLA)
    assertTrue(
        premiumReport.getData().stream().noneMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Premium SLA report should have no data (host has Standard)");

    // Query with Production Usage filter - should return no data (host has Development/Test)
    var productionReport =
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
                "usage",
                "Production"));

    assertNotNull(productionReport, "Tally report with Production Usage should exist");
    assertNotNull(productionReport.getData(), "Production report should have data points");
    assertFalse(productionReport.getData().isEmpty(), "Production report should have data points");
    // All data points should have hasData=false (no actual data for Production Usage)
    assertTrue(
        productionReport.getData().stream().noneMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Production Usage report should have no data (host has Development/Test)");
  }
}
