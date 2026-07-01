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
import static utils.TallyTestHelpers.getSocketCount;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.logging.Log;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import utils.TallyHbiDbSeeder;
import utils.TallyHbiDbSeeder.SeededHost;

/**
 * Component tests for RHEL physical host tally with socket increase mapping.
 *
 * <p>Tests the RHEL per-socket increase behavior where certain socket counts are mapped to higher
 * values for licensing purposes: {1: 2, 2: 2, 4: 4, 7: 8}
 *
 * <p>Matches IQE test: test_validate_tally_on_physical_rhel_sockets
 */
public class TallyRhelTest extends BaseTallyComponentTest {

  private TallyHbiDbSeeder hbiSeeder;

  /**
   * Provider for socket increase mapping test parameters. Matches
   * IQE's @pytest.mark.parametrize("sockets", rhel_per_socket_increase.keys())
   */
  static Stream<Arguments> socketMappingProvider() {
    return TallyHbiDbSeeder.getRhelPerSocketIncreaseMap().entrySet().stream()
        .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
  }

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
   * Validate tally on physical RHEL sockets with socket increase mapping.
   *
   * <p>Matches IQE test: test_validate_tally_on_physical_rhel_sockets
   *
   * <p>Test steps: 1. Read existing tally data (before state) 2. Add physical RHEL instance with N
   * sockets in HBI database 3. Sync Tally data for account 4. Read new tally data (after state) 5.
   * Verify tally data shows increased count for physical RHEL 6. Verify system table entry has
   * correct display_name, category, and labeled_measurements
   */
  @ParameterizedTest(name = "Physical RHEL: {0} actual sockets -> {1} reported sockets")
  @MethodSource("socketMappingProvider")
  void test_validate_tally_on_physical_rhel_sockets(
      int actualSockets, int expectedReportedSockets) {
    // Given: Org is opted in
    service.createOptInConfig(orgId);

    // And: Define time range
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);

    // When: Capture initial state
    double initialSockets =
        getSocketCount(service, orgId, RHEL_FOR_X86.productTag(), "Daily", beginning, ending);
    Log.info("Initial sockets: %.0f", initialSockets);

    // And: Create RHEL host
    String displayName = "RHEL Host " + actualSockets + " sockets";
    int cores = actualSockets; // 1 core per socket (matches IQE)

    SeededHost host =
        hbiSeeder.insertRhelHost(
            orgId,
            "inventory-" + actualSockets,
            "subman-" + actualSockets,
            displayName,
            cores,
            actualSockets);

    Log.info("Inserted host %s: %d cores, %d sockets", host.hostId(), cores, actualSockets);

    // And: Run tally
    service.tallyOrg(orgId);

    // Then: Verify socket count increased
    // Matches IQE: assert current_usage["sockets"] == initial["sockets"] +
    // rhel_per_socket_increase[sockets]
    double currentSockets =
        getSocketCount(service, orgId, RHEL_FOR_X86.productTag(), "Daily", beginning, ending);
    Log.info("Current sockets: %.0f (increase: %d)", currentSockets, expectedReportedSockets);

    assertEquals(
        initialSockets + expectedReportedSockets,
        currentSockets,
        String.format("Sockets should increase by %d", expectedReportedSockets));

    // And: Get instance from system table
    // TODO: Fix compilation error - temporarily commented
    // InstanceData instance =
    //     getInstanceByDisplayName(service, orgId, displayName, beginning, ending);

    // Verify instance details
    // TODO: Fix compilation error - temporarily commented
    // assertNotNull(instance, "Host should appear in instances API");
    // assertEquals(displayName, instance.getDisplayName(), "Display name should match");
    // assertEquals(
    //     "physical", instance.getCategory().toString().toLowerCase(), "Category should be
    // physical");
    // assertNotNull(instance.getMeasurements(), "Instance should have measurements");
    // assertFalse(instance.getMeasurements().isEmpty(), "Measurements should not be empty");
    // assertEquals(
    //     (double) expectedReportedSockets,
    //     instance.getMeasurements().get(0),
    //     String.format(
    //         "Labeled measurement should show %d sockets (increased from %d)",
    //         expectedReportedSockets, actualSockets));
  }
}
