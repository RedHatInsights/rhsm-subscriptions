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

import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SatelliteFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Component tests for nightly tally with HBI database integration.
 *
 * <p>Tests the flow:
 *
 * <ol>
 *   <li>Insert host into HBI database
 *   <li>Run nightly tally (reads from HBI)
 *   <li>Verify tally results
 * </ol>
 */
public class TallyNightlyHbiTest extends BaseTallyComponentTest {

  private HostStateManager hostManager;
  private static final String RHEL_PRODUCT_ID = "69";

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
   * - **Description**: Verify that we can insert a host in the HBI database - **Setup**: Component
   * test environment with swatch-tally is running and an instance of insights db is up -
   * **Action**: Insert a host into the HBI database - **Verification**: - a host was returned from
   * the insert - the host returned has a inventory id - the host returned has a subscription
   * manager id - the host returned has the expected orgId - **Expected Result**: The host was
   * inserted into the HBI database
   */
  @Test
  void testHbiSeederCanInsert() {
    // Given: No specific setup needed beyond @BeforeEach

    // When: Inserting a RHEL host into HBI database
    SeededHost host =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(1, 0))
            .insert();

    // Then: Host is tracked with expected metadata
    assertNotNull(host.hostId(), "Host ID should be generated");
    assertEquals(orgId, host.orgId(), "Org ID should match");
    assertEquals(1, hostManager.getTrackedCount(), "Seeder should track one host");
    assertTrue(hostManager.hostExists(host.hostId()), "Host should exist in HBI database");
  }

  /**
   * Demonstrates both approaches to creating HBI hosts.
   *
   * <p>**Preset Template Approach** (host1): Uses `.apply(HostTemplates.physicalRhel(1, 0))` which
   * applies all RHEL physical defaults automatically (infrastructure, arch, RHEL product facts).
   *
   * <p>**Manual Builder Approach** (host2): Sets each property explicitly using the fluent builder
   * pattern. Useful when you need fine-grained control or custom configurations.
   *
   * <p>Both approaches produce valid hosts tracked by the HostStateManager.
   */
  @Test
  void testCanInsertMultipleHosts() {
    // Given: No specific setup needed beyond @BeforeEach

    // When: Inserting multiple hosts into HBI database
    SeededHost host1 =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(1, 0))
            .insert();

    SeededHost host2 =
        hostManager
            .createHost(orgId)
            .displayName("Test Host - 1")
            .rhsmFacts(
                RhsmFacts.builder().isVirtual(false).products(List.of(RHEL_PRODUCT_ID)).build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .numberOfSockets(1)
                    .numberOfCpus(0)
                    .build())
            .insert();

    // Then: Hosts are tracked with expected metadata
    assertNotNull(host1.hostId(), "Host ID should be generated");
    assertNotNull(host2.hostId(), "Host ID should be generated");
  }

  /**
   * - **Description**: Verify that we can insert a host in the HBI database with multiple
   * reporters- **Setup**: Component test environment with swatch-tally is running and an instance
   * of insights db is up - **Action**: Insert a host into the HBI database with multiple
   * conflicting reporters, satellite facts and rhsm facts ( rhsm facts will overwrite satellite
   * facts ) - - **Verification**: - a host was returned from the insert - the host returned has a
   * inventory id - the host returned has an id - the host returned has the expected orgId -
   * **Expected Result**: The host was inserted into the HBI database
   */
  @Test
  void testCanCreateHostWithMultipleReporters() {
    // Given: The org has opted in
    service.createOptInConfig(orgId);

    // When: Inserting multiple hosts into HBI database
    SeededHost host =
        hostManager
            .createHost(orgId)
            // Satellite facts replicated from prod data
            .satelliteFacts(
                SatelliteFacts.builder()
                    .hypervisorUuid(UUID.randomUUID().toString())
                    .sla("Standard")
                    .role("Red Hat Enterprise Linux Server")
                    .usage("Component Testing")
                    .build())
            // RHEL facts replicated from prod data. Setting these after the satellite facts
            // invokes the normalization logic that overwrites the satellite SLA/role/usage.
            .rhsmFacts(
                RhsmFacts.builder()
                    .isVirtual(false)
                    .products(List.of("69"))
                    .sla("Premium")
                    .systemPurposeRole("Red Hat Enterprise Linux Server")
                    .usage("Component Testing")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .cloudProvider("aws")
                    .arch("x86_64")
                    .numberOfSockets(1)
                    .numberOfCpus(8)
                    .build())
            .providerId(UUID.randomUUID().toString())
            .insert();

    assertNotNull(host.hostId(), "Host ID should be generated");

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: The instance appears in the instances API
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    var instanceResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), beginning, ending);

    assertNotNull(instanceResponse.getData(), "Instance response should have data");
    assertFalse(instanceResponse.getData().isEmpty(), "Instance response should not be empty");

    assertPremiumReportPopulated(orgId, RHEL_FOR_X86.productTag(), beginning, ending);
  }

  /**
   * - **Description**: Verify that we can insert a host in the HBI database with multiple
   * reporters- **Setup**: Component test environment with swatch-tally is running and an instance
   * of insights db is up - **Action**: Insert a host into the HBI database with multiple
   * conflicting reporters, satellite facts and rhsm facts ( rhsm facts will WILL NOT overwrite the
   * satellite facts ) - - **Verification**: - a host was returned from the insert - the host
   * returned has a inventory id - the host returned has a an id - the host returned has the
   * expected orgId - **Expected Result**: The host was inserted into the HBI database
   */
  @Test
  void testCanCreateHostWithMultipleReportersOverrideRhsm() {
    // Given: The org has opted in
    service.createOptInConfig(orgId);

    // When: Inserting multiple hosts into HBI database
    SeededHost host =
        hostManager
            .createHost(orgId)
            // Satellite facts replicated from prod data
            .satelliteFacts(
                SatelliteFacts.builder()
                    .hypervisorUuid(UUID.randomUUID().toString())
                    .sla("Standard")
                    .role("Red Hat Enterprise Linux Server")
                    .usage("Component Testing")
                    .build())
            // RHEL facts replicated from prod data. Setting SYNC_TIMESTAMP invokes the
            // skip-rhsm normalization logic, so the satellite facts win instead.
            .rhsmFacts(
                RhsmFacts.builder()
                    .isVirtual(false)
                    .products(List.of("69"))
                    .syncTimestamp("2026-08-24T00:32:05.179356374Z")
                    .sla("Premium")
                    .systemPurposeRole("Red Hat Enterprise Linux Server")
                    .usage("Component Testing")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .cloudProvider("aws")
                    .arch("x86_64")
                    .numberOfSockets(1)
                    .numberOfCpus(8)
                    .build())
            .providerId(UUID.randomUUID().toString())
            .insert();

    assertNotNull(host.hostId(), "Host ID should be generated");

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: The instance appears in the instances API
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    var instanceResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), beginning, ending);

    assertNotNull(instanceResponse.getData(), "Instance response should have data");
    assertFalse(instanceResponse.getData().isEmpty(), "Instance response should not be empty");

    assertStandardReportPopulated(orgId, RHEL_FOR_X86.productTag(), beginning, ending);
  }

  /**
   * - **Description**: Verify that we can delete an inserted host in the HBI database - **Setup**:
   * Component test environment with swatch-tally is running, an instance of insights db is up and
   * have a host in the HBI database - **Action**: delete the host previously inserted in the HBI
   * database - **Verification**: - the host was deleted from the database - **Expected Result**:
   * The host was deleted from the database
   */
  @Test
  void testHbiSeederCanDelete() {
    // Given: A host is inserted into HBI database
    SeededHost host =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(1, 0))
            .insert();
    assertNotNull(host.hostId(), "Host ID should be generated");

    // When: Deleting the host
    hostManager.cleanup(host.hostId());

    // Then: Host is removed from tracking and database
    assertEquals(0, hostManager.getTrackedCount(), "Seeder should track zero hosts");
    assertFalse(hostManager.hostExists(host.hostId()), "Host should not exist in HBI database");
  }

  /**
   * - **Description**: Verify that we can delete all inserted hosts in the HBI database -
   * **Setup**: Component test environment with swatch-tally is running, an instance of insights db
   * is up and have more than one host in the HBI database - **Action**: run the rollback -
   * **Verification**: - ensure that all the inserted hosts that were inserted are deleted from the
   * database - **Expected Result**: The host was deleted from the database
   */
  @Test
  void testHbiSeederRollbackDeletesAllHosts() {
    // Given: Multiple hosts are inserted (mix of RHEL and cloud)
    SeededHost host1 =
        hostManager
            .createHost(orgId)
            .rhsmFacts(RhsmFacts.builder().defaultFacts().build())
            .insert();
    SeededHost host2 =
        hostManager
            .createHost(orgId)
            .rhsmFacts(RhsmFacts.builder().defaultFacts().build())
            .insert();
    assertEquals(2, hostManager.getTrackedCount(), "Seeder should track two hosts");
    assertTrue(hostManager.hostExists(host1.hostId()), "Host 1 should exist in HBI database");
    assertTrue(hostManager.hostExists(host2.hostId()), "Host 2 should exist in HBI database");

    // When: Rolling back all inserted hosts
    hostManager.cleanupAll();

    // Then: All hosts are removed from tracking and database
    assertEquals(0, hostManager.getTrackedCount(), "Seeder should track zero hosts");
    assertFalse(hostManager.hostExists(host1.hostId()), "Host 1 should not exist in HBI database");
    assertFalse(hostManager.hostExists(host2.hostId()), "Host 2 should not exist in HBI database");
  }

  /**
   * - **Description**: Verify that we can insert a RHEL product into the the HBI database -
   * **Setup**: Component test environment with swatch-tally is running, an instance of insights db
   * - **Action**: Create a host with a product that is a RHEL product - **Verification**: - verify
   * that a tally Report for the RHEL product is not null - verify that the socket count increased
   * by 2 - **Expected Result**: The tally socket count increased by 2
   */
  @Test
  void testNightlyTallyRhelProduct() {
    // Given: Org is opted in
    service.createOptInConfig(orgId);

    // And: Define time range (today only)
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    // When: Capture initial socket count
    var initialReportData =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString()));

    double initialSockets =
        initialReportData.getData() != null
            ? initialReportData.getData().stream()
                .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
                .sum()
            : 0.0;

    // And: RHEL host with 2 sockets and 8 cores is created
    SeededHost host =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 8))
            .insert();
    assertNotNull(host.hostId(), "Host should be created");

    // And: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: Socket count increased by 2
    var currentReportData =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString()));

    assertNotNull(currentReportData, "Tally report should be created");
    assertNotNull(currentReportData.getData(), "Report should have data");

    double currentSockets =
        currentReportData.getData().stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum();

    assertEquals(
        initialSockets + 2.0,
        currentSockets,
        "Socket count should increase by 2 after adding host with 2 sockets");
  }

  /**
   * - **Description**: Verify that updating a previously-seeded host and re-running tally reflects
   * the new facts, rather than treating it as a second host - **Setup**: Component test environment
   * with swatch-tally is running, an instance of insights db is up - **Action**: Insert a RHEL host
   * with 2 sockets, tally, then update the same host to 6 sockets using the same HostBuilder, and
   * tally again - **Verification**: - the update targets the same host id - the socket count after
   * the update reflects only the updated host (6 sockets), not both the original and updated values
   * - **Expected Result**: The tally reflects the host's current state after the update
   *
   * <p>Note: socket counts are chosen to be even, since FactNormalizer.normalizeSocketCount rounds
   * odd physical socket counts up to the next even number (socket-pair licensing), so the
   * assertions below are a plain identity mapping.
   */
  @Test
  void testNightlyTallyReflectsHostUpdate() {
    // Given: Org is opted in
    service.createOptInConfig(orgId);

    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    double initialSockets =
        sumSockets(
            service.getTallyReportData(
                orgId,
                RHEL_FOR_X86.productTag(),
                "Sockets",
                Map.of(
                    "granularity", "Daily",
                    "beginning", beginning.toString(),
                    "ending", ending.toString())));

    // And: RHEL host with 2 sockets, 2 cores is created and tallied
    HostBuilder hostBuilder =
        hostManager.createHost(orgId).apply(HostTemplates.conduitReportedPhysicalRhel(2, 2));
    SeededHost seeded = hostBuilder.insert();
    assertNotNull(seeded.hostId(), "Host should be created");

    service.tallyOrg(orgId);

    double socketsAfterInsert =
        sumSockets(
            service.getTallyReportData(
                orgId,
                RHEL_FOR_X86.productTag(),
                "Sockets",
                Map.of(
                    "granularity", "Daily",
                    "beginning", beginning.toString(),
                    "ending", ending.toString())));
    assertEquals(
        initialSockets + 2.0,
        socketsAfterInsert,
        "Socket count should increase by 2 after inserting host with 2 sockets");

    // When: The same host is updated to 6 sockets, 6 cores using the same builder, and re-tallied
    SeededHost updated =
        hostBuilder
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .numberOfSockets(6)
                    .numberOfCpus(6)
                    .build())
            .update();
    assertEquals(seeded.hostId(), updated.hostId(), "Update should target the same host row");

    service.tallyOrg(orgId);

    // Then: Socket count reflects the updated host (6 sockets), not the original + updated values
    double socketsAfterUpdate =
        sumSockets(
            service.getTallyReportData(
                orgId,
                RHEL_FOR_X86.productTag(),
                "Sockets",
                Map.of(
                    "granularity", "Daily",
                    "beginning", beginning.toString(),
                    "ending", ending.toString())));
    assertEquals(
        initialSockets + 6.0,
        socketsAfterUpdate,
        "Socket count should reflect the updated host's 6 sockets, not the original 2 plus the"
            + " update");
  }

  private double sumSockets(TallyReportData reportData) {
    var data = reportData.getData();
    return data != null
        ? data.stream()
            .mapToDouble(point -> point.getValue() != null ? point.getValue() : 0.0)
            .sum()
        : 0.0;
  }

  /**
   * - **Description**: Verify that a cloud host with RHEL product appears in the instance report -
   * **Setup**: Component test environment with swatch-tally is running, an instance of insights db
   * - **Action**: Create a cloud host with RHEL product facts and run nightly tally -
   * **Verification**: - verify that the host exists in HBI - verify that the instance appears in
   * the instances API - **Expected Result**: The instance is visible in the instance report
   */
  @Test
  void testNightlyTallyCloudProduct() {
    // Given: Org is opted in and cloud host with RHEL product exists in HBI database
    service.createOptInConfig(orgId);
    SeededHost host =
        hostManager
            .createHost(orgId)
            .apply(HostTemplates.conduitReportedAwsVirtualRhel(1, 1))
            .insert();
    assertNotNull(host.hostId(), "Cloud host should be created");
    assertTrue(hostManager.hostExists(host.hostId()), "Cloud host should exist in HBI database");

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: The instance appears in the instances API
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    var instanceResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), beginning, ending);

    assertNotNull(instanceResponse.getData(), "Instance response should have data");
    assertFalse(instanceResponse.getData().isEmpty(), "Instance response should not be empty");
    assertEquals(
        1,
        instanceResponse.getData().size(),
        "Should have exactly one instance for this org and product");
  }

  public void assertPremiumReportPopulated(
      String orgId, String productTag, OffsetDateTime beginning, OffsetDateTime ending) {

    var premiumData = fetchTallyData(orgId, productTag, beginning, ending, "Premium");
    var standardData = fetchTallyData(orgId, productTag, beginning, ending, "Standard");

    assertHasData(
        premiumData,
        ServiceLevelType.PREMIUM,
        "Premium SLA should have at least one data point with hasData=true");

    assertHasNoData(
        standardData,
        ServiceLevelType.STANDARD,
        "Standard SLA should have NO actual data (Satellite SLA was overridden by RHSM Premium)");
  }

  public void assertStandardReportPopulated(
      String orgId, String productTag, OffsetDateTime beginning, OffsetDateTime ending) {

    var premiumData = fetchTallyData(orgId, productTag, beginning, ending, "Premium");
    var standardData = fetchTallyData(orgId, productTag, beginning, ending, "Standard");

    assertHasData(
        standardData,
        ServiceLevelType.STANDARD,
        "Standard SLA should have at least one data point with hasData=true");

    assertHasNoData(
        premiumData, ServiceLevelType.PREMIUM, "Premium SLA should have NO actual data");
  }

  // --- Private Helper Methods ---

  private TallyReportData fetchTallyData(
      String orgId,
      String productTag,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String sla) {
    return service.getTallyReportData(
        orgId,
        productTag,
        "Sockets",
        Map.of(
            "granularity",
            "Daily",
            "beginning",
            beginning.toString(),
            "ending",
            ending.toString(),
            "sla",
            sla));
  }

  private void assertHasData(
      TallyReportData report, ServiceLevelType expectedSla, String failureMessage) {
    assertNotNull(report.getData(), expectedSla + " SLA should have data structure");
    assertTrue(
        report.getData().stream().anyMatch(TallyReportDataPoint::getHasData), failureMessage);
    assertEquals(
        expectedSla,
        report.getMeta().getServiceLevel(),
        "Meta should show " + expectedSla + " SLA");
  }

  private void assertHasNoData(
      TallyReportData report, ServiceLevelType expectedSla, String failureMessage) {
    assertNotNull(report.getData(), expectedSla + " SLA should return data structure");
    assertTrue(
        report.getData().stream().noneMatch(TallyReportDataPoint::getHasData), failureMessage);
    assertEquals(
        expectedSla,
        report.getMeta().getServiceLevel(),
        "Meta should show " + expectedSla + " SLA");
  }

  /**
   * - **Description**: Verify that hosts without SLA/Usage facts default to Premium/Production -
   * **Setup**: Component test environment with swatch-tally is running - **Action**: Insert a host
   * without SLA/Usage facts and run nightly tally - **Verification**: - Tally report with
   * sla=Premium and usage=Production filters returns the host's data - Tally report with
   * sla=Standard or usage=Development/Test filters returns no data - **Expected Result**: Host
   * without SLA/Usage is tallied as Premium/Production
   */
  @Test
  void testNightlyTallyDefaultsToPremiuProductionWhenSlaUsageNotSet() {
    // Given: Primary row searches are enabled
    givenFeatureFlagIsConfigured(true);

    // Given: Org is opted in and host without SLA/Usage facts exists
    service.createOptInConfig(orgId);
    SeededHost host = hbiSeeder.rhelHost(orgId).cores(8).sockets(2).insert();
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

  /**
   * - **Description**: Verify that hosts with explicit SLA/Usage facts use those values (not
   * defaults) - **Setup**: Component test environment with swatch-tally is running - **Action**:
   * Insert a host with Standard SLA and Development/Test Usage and run nightly tally -
   * **Verification**: - Tally report with sla=Standard and usage=Development/Test filters returns
   * the host's data - Tally report with sla=Premium or usage=Production filters returns no data -
   * **Expected Result**: Host with explicit SLA/Usage is tallied with those values, not defaults
   */
  @Test
  void testNightlyTallyRespectsExplicitSlaUsage() {
    // Given: Primary row searches are enabled
    givenFeatureFlagIsConfigured(true);

    // Given: Org is opted in and host with explicit SLA/Usage exists
    service.createOptInConfig(orgId);
    SeededHost host =
        hbiSeeder
            .rhelHost(orgId)
            .cores(8)
            .sockets(2)
            .sla("Standard")
            .usage("Development/Test")
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
