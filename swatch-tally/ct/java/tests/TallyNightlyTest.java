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
import static utils.TallyTestHelpers.getSocketCount;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.tally.test.model.InstanceData;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Component tests for RHEL physical host tally with socket increase mapping.
 *
 * <p>Tests the RHEL per-socket increase behavior where certain socket counts are mapped to higher
 * values for licensing purposes: {1: 2, 2: 2, 4: 4, 7: 8}
 *
 * <p>Matches IQE test: test_validate_tally_on_physical_rhel_sockets
 */
public class TallyNightlyTest extends BaseTallyComponentTest {

  private HostStateManager hostManager;

  /**
   * Socket increase mapping for RHEL physical hosts. Maps actual socket count -> reported socket
   * count for tally.
   */
  private static final Map<Integer, Integer> RHEL_PER_SOCKET_INCREASE =
      Map.of(1, 2, 2, 2, 4, 4, 7, 8);

  /**
   * Provider for socket increase mapping test parameters. Matches
   * IQE's @pytest.mark.parametrize("sockets", rhel_per_socket_increase.keys())
   */
  static Stream<Arguments> socketMappingProvider() {
    return RHEL_PER_SOCKET_INCREASE.entrySet().stream()
        .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
  }

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
   * Validate tally on physical RHEL sockets with socket increase mapping.
   *
   * <p>Matches IQE test: test_validate_tally_on_physical_rhel_sockets
   *
   * <p>Test steps: 1. Read existing tally data (before state) 2. Add physical RHEL instance with N
   * sockets in HBI database 3. Sync Tally data for account 4. Read new tally data (after state) 5.
   * Verify tally data shows increased count for physical RHEL 6. Verify system table entry has
   * correct display_name, category, and labeled_measurements
   */
  @TestPlanName("nightly-tally-TC001")
  @ParameterizedTest(name = "Physical RHEL: {0} starting sockets -> {1} reported sockets")
  @MethodSource("socketMappingProvider")
  void test_validate_tally_on_physical_rhel_sockets(
      int startingSockets, int expectedReportedSockets) {

    // Given: Org is opted in
    service.createOptInConfig(orgId);

    // And: Define time range (today only)
    OffsetDateTime beginning =
        OffsetDateTime.now().toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime ending = OffsetDateTime.now();

    // When: Capture initial state
    double initialSockets =
        getSocketCount(service, orgId, RHEL_FOR_X86.productTag(), "Daily", beginning, ending);
    Log.info("Initial sockets: %.0f", initialSockets);

    // And: Create RHEL host
    int cores = startingSockets; // 1 core per socket (matches IQE)
    String displayName = String.format("RHEL-Physical-%dsockets-%dcores", startingSockets, cores);

    SeededHost host =
        hostManager
            .createHost(orgId)
            .displayName(displayName)
            .rhsmFacts(RhsmFacts.builder().defaultFacts().products(List.of("69")).build())
            .setSystemProfileFacts(
                SystemProfileFacts.builder()
                    .numberOfCpus(cores)
                    .numberOfSockets(startingSockets)
                    .build())
            .insert();

    Log.info("Inserted host %s: %d cores, %d sockets", host.hostId(), cores, startingSockets);

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
    var instanceResponse =
        service.getInstancesByProduct(orgId, RHEL_FOR_X86.productTag(), beginning, ending);
    assertNotNull(instanceResponse.getData(), "Instance response data should not be null");

    InstanceData instance =
        instanceResponse.getData().stream()
            .filter(i -> displayName.equals(i.getDisplayName()))
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("No instance found with displayName=" + displayName));

    // Verify instance details
    assertNotNull(instance, "Host should appear in instances API");
    assertEquals(displayName, instance.getDisplayName(), "Display name should match");
    assertEquals(
        "physical", instance.getCategory().toString().toLowerCase(), "Category should be physical");
    assertNotNull(instance.getMeasurements(), "Instance should have measurements");
    assertFalse(instance.getMeasurements().isEmpty(), "Measurements should not be empty");

    // Find the Sockets metric index from response metadata
    List<String> metricIds = instanceResponse.getMeta().getMeasurements();
    int socketsIndex = metricIds.indexOf("Sockets");
    assertTrue(socketsIndex >= 0, "Sockets metric should be present in metadata");

    assertEquals(
        (double) expectedReportedSockets,
        instance.getMeasurements().get(socketsIndex),
        String.format(
            "Labeled measurement should show %d sockets (increased from %d)",
            expectedReportedSockets, startingSockets));
  }
}
