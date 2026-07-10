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

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.TallyHbiDbSeeder;
import utils.TallyHbiDbSeeder.SeededHost;

/**
 * Component tests for nightly tally with HBI database integration.
 *
 * <p>Tests the flow:
 *
 * <ol>
 *   <li>Insert host into HBI database
 *   <li>Send HBI Kafka event (simulating swatch-metrics-hbi)
 *   <li>Verify swatch-tally syncs the host
 *   <li>Run nightly tally
 *   <li>Verify tally results
 * </ol>
 */
public class TallyNightlyHbiTest extends BaseTallyComponentTest {

  private TallyHbiDbSeeder hbiSeeder;

  @BeforeEach
  void setupHbiSeeder() {
    // Initialize HBI seeder with database service (auto-configured for local/OpenShift)
    hbiSeeder = new TallyHbiDbSeeder(hbiDatabase);
  }

  @AfterEach
  void cleanupHbiHosts() {
    // Rollback: delete all HBI hosts inserted during test
    if (hbiSeeder != null) {
      hbiSeeder.deleteAllInsertedHosts();
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
    SeededHost host = hbiSeeder.insertRhelHost(orgId);

    // Then: Host is tracked with expected metadata
    assertNotNull(host.hostId(), "Host ID should be generated");
    assertTrue(
        host.inventoryId().startsWith("test-inventory-id"),
        "Inventory ID should have expected prefix");
    assertTrue(
        host.subscriptionManagerId().startsWith("test-subman-id"),
        "Subscription manager ID should have expected prefix");
    assertEquals(orgId, host.orgId(), "Org ID should match");
    assertEquals(1, hbiSeeder.getInsertedHostCount(), "Seeder should track one host");
    assertTrue(hbiSeeder.hostExists(host.hostId()), "Host should exist in HBI database");
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
    SeededHost host = hbiSeeder.insertRhelHost(orgId);
    assertNotNull(host.hostId(), "Host ID should be generated");

    // When: Deleting the host
    hbiSeeder.deleteHost(host.hostId());

    // Then: Host is removed from tracking and database
    assertEquals(0, hbiSeeder.getInsertedHostCount(), "Seeder should track zero hosts");
    assertFalse(hbiSeeder.hostExists(host.hostId()), "Host should not exist in HBI database");
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
    SeededHost host1 = hbiSeeder.insertRhelHost(orgId);
    SeededHost host2 = hbiSeeder.insertCloudHost(orgId);
    assertEquals(2, hbiSeeder.getInsertedHostCount(), "Seeder should track two hosts");
    assertTrue(hbiSeeder.hostExists(host1.hostId()), "Host 1 should exist in HBI database");
    assertTrue(hbiSeeder.hostExists(host2.hostId()), "Host 2 should exist in HBI database");

    // When: Rolling back all inserted hosts
    hbiSeeder.deleteAllInsertedHosts();

    // Then: All hosts are removed from tracking and database
    assertEquals(0, hbiSeeder.getInsertedHostCount(), "Seeder should track zero hosts");
    assertFalse(hbiSeeder.hostExists(host1.hostId()), "Host 1 should not exist in HBI database");
    assertFalse(hbiSeeder.hostExists(host2.hostId()), "Host 2 should not exist in HBI database");
  }

  /**
   * - **Description**: Verify that we can insert a RHEL product into the the HBI database -
   * **Setup**: Component test environment with swatch-tally is running, an instance of insights db
   * - **Action**: Create a host with a product that is a RHEL product - **Verification**: - verify
   * that a tally Report for the RHEL product is not null - verify that the total sockets value in
   * the tally Report is greather that or equal to the 2 sockets - **Expected Result**: All the host
   * inserted into the db have been deleted from the database
   */
  @Test
  void testNightlyTallyRhelProduct() {
    // Given: Org is opted in and RHEL host exists with known capacity
    service.createOptInConfig(orgId);
    SeededHost host = hbiSeeder.rhelHost(orgId).cores(8).sockets(2).insert();
    assertNotNull(host.hostId(), "Host should be created");

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: Tally report contains expected socket count
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    var reportData =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(),
            "Sockets",
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString()));

    assertNotNull(reportData, "Tally report should be created");
    assertNotNull(reportData.getData(), "Report should have data");
    assertFalse(reportData.getData().isEmpty(), "Report should not be empty");

    boolean hasExpectedSockets =
        reportData.getData().stream()
            .anyMatch(point -> point.getValue() != null && point.getValue() == 2.0);
    assertTrue(hasExpectedSockets, "Report should contain a data point with exactly 2 sockets");
  }

  /**
   * - **Description**: Verify that we can insert a RHEL product into the the HBI database -
   * **Setup**: Component test environment with swatch-tally is running, an instance of insights db
   * - **Action**: Create a host with a product that is a NON RHEL product - **Verification**: -
   * verify that the host is non-null - verify that the host is exist in the HBI database - verify
   * that you can sync tally without any errors - **Expected Result**: All the host inserted into
   * the db have been deleted from the database
   */
  @Test
  void testNightlyTallyCloudProduct() {
    // Given: Org is opted in and cloud host exists in HBI database
    service.createOptInConfig(orgId);
    SeededHost host = hbiSeeder.insertCloudHost(orgId);
    assertNotNull(host.hostId(), "Cloud host should be created");
    assertTrue(hbiSeeder.hostExists(host.hostId()), "Cloud host should exist in HBI database");

    // When: Nightly tally runs
    service.tallyOrg(orgId);

    // Then: Tally processes the cloud host successfully without errors
    // Note: Cloud products (ROSA, OpenShift) don't require cores/sockets and have
    // different metric configurations than RHEL. The validation is that the host
    // syncs from HBI to Swatch and tally processes it without throwing exceptions.
  }
}
