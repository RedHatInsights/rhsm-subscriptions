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
        hostManager.createHost(orgId).apply(HostTemplates.physicalRhel(1, 0)).insert();

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
        hostManager.createHost(orgId).apply(HostTemplates.physicalRhel(1, 0)).insert();

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

    assertThatExpectedReportPopulated(orgId, RHEL_FOR_X86.productTag(), beginning, ending, true);
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

    assertThatExpectedReportPopulated(orgId, RHEL_FOR_X86.productTag(), beginning, ending, false);
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
        hostManager.createHost(orgId).apply(HostTemplates.physicalRhel(1, 0)).insert();
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
        hostManager.createHost(orgId).apply(HostTemplates.physicalRhel(2, 8)).insert();
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
    HostBuilder hostBuilder = hostManager.createHost(orgId).apply(HostTemplates.physicalRhel(2, 2));
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
    SeededHost host = hostManager.createHost(orgId).apply(HostTemplates.awsRhel(1, 1)).insert();
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

  private void assertThatExpectedReportPopulated(
      String orgId,
      String productTag,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      Boolean isPremium) {
    var premiumData =
        service.getTallyReportData(
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
                "Premium"));

    var standardData =
        service.getTallyReportData(
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
                "Standard"));

    if (isPremium) {
      // Verify Premium has data (RHSM facts won)
      assertNotNull(premiumData.getData(), "Premium SLA should have data");
      assertTrue(
          premiumData.getData().stream().anyMatch(point -> point.getHasData()),
          "Premium SLA should have at least one data point with hasData=true");
      assertEquals(
          ServiceLevelType.PREMIUM,
          premiumData.getMeta().getServiceLevel(),
          "Meta should show Premium SLA");

      // Verify Standard has NO data (Satellite facts were overridden)
      assertNotNull(standardData.getData(), "Standard SLA should return data structure");
      assertTrue(
          standardData.getData().stream().noneMatch(point -> point.getHasData()),
          "Standard SLA should have NO actual data (Satellite SLA was overridden by RHSM Premium)");
      assertEquals(
          ServiceLevelType.STANDARD,
          standardData.getMeta().getServiceLevel(),
          "Meta should show Standard SLA");
    } else {
      // Verify Standard has data (Standard facts being used)
      assertNotNull(standardData.getData(), "Standard SLA should have data");
      assertTrue(
          standardData.getData().stream().anyMatch(point -> point.getHasData()),
          "Standard SLA should have at least one data point with hasData=true");
      assertEquals(
          ServiceLevelType.STANDARD,
          standardData.getMeta().getServiceLevel(),
          "Meta should show Standard SLA");

      // Verify Premium has NO data
      assertNotNull(premiumData.getData(), "Premium SLA should return data structure");
      assertTrue(
          premiumData.getData().stream().noneMatch(point -> point.getHasData()),
          "Premium SLA should have NO actual data");
      assertEquals(
          ServiceLevelType.PREMIUM,
          premiumData.getMeta().getServiceLevel(),
          "Meta should show Premium SLA");
    }
  }
}
