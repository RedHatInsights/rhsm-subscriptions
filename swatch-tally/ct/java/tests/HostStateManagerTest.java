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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.HbiDbConnector;
import utils.Host;
import utils.HostConnector.SeededHost;
import utils.HostStateManager;

/**
 * Component tests for new HostStateManager API.
 *
 * <p>Tests the refactored host seeding framework with:
 *
 * <ul>
 *   <li>Host.java - Data model
 *   <li>HostConnector - Interface
 *   <li>HbiDbConnector - DB implementation
 *   <li>HostStateManager - Orchestrator with Satellite defaults & templates
 * </ul>
 */
public class HostStateManagerTest extends BaseTallyComponentTest {

  private HostStateManager hostManager;

  @BeforeEach
  void setupHostManager() {
    // Initialize new HostStateManager for refactored API
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
  }

  @AfterEach
  void cleanupHosts() {
    // Cleanup hosts from new API
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  /**
   * - **Description**: Verify Satellite host with custom facts using new API - **Setup**: Component
   * test environment with swatch-tally running - **Action**: Create Satellite host with custom
   * system purpose facts - **Verification**: - Host is created with custom SLA override - Other
   * defaults are still applied - **Expected Result**: Custom facts override defaults while keeping
   * other defaults
   */
  @Test
  void testSatelliteHostWithCustomFacts() {
    // Given: Org is opted in
    service.createOptInConfig(orgId);

    // When: Creating Satellite host with custom SLA (overriding default "Premium")
    SeededHost host =
        hostManager
            .satellite(orgId)
            .systemPurposeSla("Standard") // Override default "Premium"
            .systemPurposeUsage("Development/Test") // Override default "Production"
            .hypervisorUuid("hypervisor-test-123")
            .sockets(2)
            .cores(2)
            .displayName("Test Satellite Host")
            .insert();

    // Then: run tally and accertions etc...
  }

  /**
   * - **Description**: Verify pre-configured host pattern with template override - **Setup**:
   * Component test environment with swatch-tally running - **Action**: Create pre-configured Host,
   * then apply template that overrides some fields - **Verification**: - Pre-configured fields are
   * preserved when not overridden - Template fields override as expected - **Expected Result**:
   * Layering of pre-configured + defaults + template works correctly
   */
  @Test
  void testPreConfiguredHostForSatelliteandQpcHostPattern() {
    // Given: Org is opted in
    service.createOptInConfig(orgId);

    // And: Pre-configure a host with specific IDs and initial socket count
    Host preConfigured =
        new Host(orgId)
            .inventoryId("custom-inventory-123")  // gen at insert
            .subscriptionManagerId("custom-subman-456"); // ? persit host first and get sub man back

    // When: Apply Satellite defaults + template (which overrides cores/sockets)
    SeededHost hostOne =
        hostManager
            .satellite(preConfigured) // Copies host + applies Satellite defaults
            .physicalRhel8SocketsAnd8Cores()
            .displayName("Pre-configured Test")
            .insert();

    SeededHost hostTwo =
        hostManager
            .setReporterQPC(preConfigured) // Copies host + applies Satellite defaults
            .awsRhel6Sockets64Cores()
            .displayName("Pre-configured Test 4 cores")
            .insert();


    SeededHost hostTwo =
          hostManager
            .setReporterQPC(preConfigured) // Copies host + applies Satellite defaults
            .satellite(preConfigured)
            .awsRhel6Sockets64Cores()
            .displayName("Pre-configured Test 4 cores")
            .insert();
    // Then: run tally and accertions etc...

  }
}
