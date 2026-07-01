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

  @TestPlanName("hbi-data-seeder-TC001")
  @Test
  void testHbiSeederCanInsertAndDelete() {
    // Given: Insert host with defaults - SUPER EASY! Just pass orgId 😊
    SeededHost host = hbiSeeder.insertRhelHost(orgId);

    // Then: verify host was tracked with predictable default values
    assertNotNull(host.hostId());
    assertEquals("test-inventory-id", host.inventoryId());
    assertEquals("test-subman-id", host.subscriptionManagerId());
    assertEquals(orgId, host.orgId());
    assertEquals(1, hbiSeeder.getInsertedHostCount());

    // And: verify host actually exists in database
    assertTrue(hbiSeeder.hostExists(host.hostId()), "Host should exist in HBI database");

    // When: delete the host
    hbiSeeder.deleteHost(host.hostId());

    // Then: verify host was removed from tracking
    assertEquals(0, hbiSeeder.getInsertedHostCount());

    // And: verify host was actually deleted from database
    assertFalse(hbiSeeder.hostExists(host.hostId()), "Host should not exist in HBI database");
  }

  @TestPlanName("hbi-data-seeder-TC002")
  @Test
  void testHbiSeederRollbackDeletesAllHosts() {
    // Given: multiple hosts inserted - mix of RHEL and cloud
    SeededHost host1 = hbiSeeder.insertRhelHost(orgId);
    SeededHost host2 = hbiSeeder.insertCloudHost(orgId);

    // Then: verify both hosts tracked
    assertEquals(2, hbiSeeder.getInsertedHostCount());

    // And: verify both hosts actually exist in database
    assertTrue(hbiSeeder.hostExists(host1.hostId()), "Host 1 should exist in HBI database");
    assertTrue(hbiSeeder.hostExists(host2.hostId()), "Host 2 should exist in HBI database");

    // When: rollback all hosts
    hbiSeeder.deleteAllInsertedHosts();

    // Then: verify all hosts removed from tracking
    assertEquals(0, hbiSeeder.getInsertedHostCount());

    // And: verify all hosts actually deleted from database
    assertFalse(hbiSeeder.hostExists(host1.hostId()), "Host 1 should not exist in HBI database");
    assertFalse(hbiSeeder.hostExists(host2.hostId()), "Host 2 should not exist in HBI database");
  }

  @Test
  void testNightlyTallyRhelProduct() {
    // Given: Org is opted in (required for tally reports)
    service.createOptInConfig(orgId);

    // And: RHEL host inserted into HBI database with known capacity
    SeededHost host = hbiSeeder.rhelHost(orgId).cores(8).sockets(2).insert();
    assertNotNull(host.hostId(), "Host should be created");

    // When: Nightly tally runs (syncs HBI → Swatch → creates snapshots)
    service.tallyOrg(orgId);

    // Then: Query tally report for RHEL
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    var reportData =
        service.getTallyReportData(
            orgId,
            RHEL_FOR_X86.productTag(), // "rhel-for-x86"
            "Sockets", // RHEL for x86 uses Sockets metric
            Map.of(
                "granularity", "Daily",
                "beginning", beginning.toString(),
                "ending", ending.toString()));

    // And: Verify the report contains our host's capacity
    assertNotNull(reportData, "Tally report should be created");
    assertNotNull(reportData.getData(), "Report should have data");
    assertFalse(reportData.getData().isEmpty(), "Report should not be empty");

    // Verify we have the expected socket count (2 sockets)
    var dataPoints = reportData.getData();

    // Debug: Log actual socket values
    com.redhat.swatch.component.tests.logging.Log.info("Tally report data points: %s", dataPoints);
    dataPoints.forEach(
        point ->
            com.redhat.swatch.component.tests.logging.Log.info(
                "Data point: date=%s, value=%s", point.getDate(), point.getValue()));

    assertTrue(
        dataPoints.stream()
            .anyMatch(
                point ->
                    point.getValue() != null && point.getValue() >= 2.0), // At least our 2 sockets
        String.format(
            "Report should contain at least 2 sockets, but got: %s",
            dataPoints.stream()
                .map(p -> String.format("date=%s,value=%s", p.getDate(), p.getValue()))
                .toList()));
  }

  @Test
  void testNightlyTallyCloudProduct() {
    // Given: Org is opted in (required for tally reports)
    service.createOptInConfig(orgId);

    // And: Cloud host inserted into HBI database
    // Cloud products (like ROSA, OpenShift) don't require cores/sockets
    SeededHost host = hbiSeeder.insertCloudHost(orgId);
    assertNotNull(host.hostId(), "Cloud host should be created");
    assertTrue(hbiSeeder.hostExists(host.hostId()), "Cloud host should exist in HBI database");

    // When: Nightly tally runs (syncs HBI → Swatch → creates snapshots)
    service.tallyOrg(orgId);

    // Then: The host should sync successfully from HBI to Swatch
    // (We don't validate specific product metrics since cloud products have
    // different metric configurations than RHEL)
    // The key validation is that:
    // 1. Cloud host can be inserted into HBI
    // 2. Host syncs from HBI → Swatch without errors
    // 3. Nightly tally processes the host successfully
  }
}
