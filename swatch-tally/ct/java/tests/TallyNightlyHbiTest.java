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
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    SeededHost host = hostManager.createRhsmHost(orgId).physicalRhel1Socket0Cores().insert();

    // Then: Host is tracked with expected metadata
    assertNotNull(host.hostId(), "Host ID should be generated");
    assertEquals(orgId, host.orgId(), "Org ID should match");
    assertEquals(1, hostManager.getTrackedCount(), "Seeder should track one host");
    assertTrue(hostManager.hostExists(host.hostId()), "Host should exist in HBI database");
  }

  @Test
  void testCanInsertMultipleHosts() {
    // Given: No specific setup needed beyond @BeforeEach

    // When: Inserting multiple hosts into HBI database
    SeededHost host1 = hostManager.createRhsmHost(orgId).physicalRhel1Socket0Cores().insert();

    SeededHost host2 =
        hostManager
            .createRhsmHost(orgId)
            .infrastructureType("physical")
            .arch("x86_64")
            .rhsmFact("IS_VIRTUAL", "false")
            .rhsmFact("RH_PROD", List.of(RHEL_PRODUCT_ID))
            .rhsmFact("ARCHITECTURE", "x86_64")
            .sockets(1)
            .cores(0)
            .displayName("Test Host - 1")
            .insert();

    // Then: Hosts are tracked with expected metadata
    assertNotNull(host1.hostId(), "Host ID should be generated");
    assertNotNull(host2.hostId(), "Host ID should be generated");
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
    SeededHost host = hostManager.createRhsmHost(orgId).physicalRhel1Socket0Cores().insert();
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
    SeededHost host1 = hostManager.createRhsmHost(orgId).insert();
    SeededHost host2 = hostManager.createRhsmHost(orgId).insert();
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
        hostManager.createRhsmHost(orgId).physicalRhel2Socket2Cores().cores(8).insert();
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
    SeededHost host = hostManager.createRhsmHost(orgId).awsRhelSockets1Cores1().insert();
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
}
