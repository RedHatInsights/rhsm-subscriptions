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

import static com.redhat.swatch.component.tests.utils.Topics.INVENTORY_HOST_INGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.MessageValidators;
import api.RhsmApiStubs;
import java.util.List;
import java.util.Map;
import models.CreateUpdateHostMessage;
import org.candlepin.subscriptions.conduit.json.inventory.HbiFactSet;
import org.candlepin.subscriptions.conduit.json.inventory.HbiHost;
import org.candlepin.subscriptions.conduit.json.inventory.HbiSystemProfile;
import org.junit.jupiter.api.Test;
import utils.ConduitTestHelpers;

/** Component tests that validate the flow of candlepin records through system-conduit. */
public class PublicCloudConduitComponentTest extends BaseConduitComponentTest {

  private final ConduitTestHelpers helpers = new ConduitTestHelpers();

  // Expected values used in the stubbed RHSM consumer and asserted on the HbiHost response
  private static final String EXPECTED_CONSUMER_ID = "test-consumer-1";
  private static final String EXPECTED_DISPLAY_NAME = "test-display-name";
  private static final String EXPECTED_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String EXPECTED_FQDN = "host1.openshift.test.com";
  private static final String EXPECTED_BIOS_UUID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
  private static final String EXPECTED_OPENSHIFT_CLUSTER_ID =
      "5dd23807-2bf1-4670-9d6a-42364dc4fddb";
  private static final String EXPECTED_INSIGHTS_ID = "e7f8c9d0-1a2b-3c4d-5e6f-7890abcdef12";
  private static final List<String> EXPECTED_IP_ADDRESSES = List.of("192.168.1.10", "10.0.0.5");
  private static final List<String> EXPECTED_MAC_ADDRESSES = List.of("00:11:22:33:44:55");
  private static final String EXPECTED_ARCH = "x86_64";
  private static final int EXPECTED_CPU_SOCKETS = 2;
  private static final int EXPECTED_CORES_PER_SOCKET = 4;
  private static final long EXPECTED_MEMORY_BYTES = 16_384_000_000L;
  private static final String EXPECTED_REPORTER = "rhsm-conduit";

  @Test
  void shouldEmitAddHostWithAllFields_whenConduitSyncsRhsmConsumer() {
    // Given: RHSM API stubbed with a full consumer (org_id, fqdn, openshift_cluster_id, etc.)
    givenRhsmConsumerWithFullData();

    // When: Conduit syncs for the test org
    whenConduitSyncsForOrg(orgId);

    // Then: Inventory host ingress receives add_host and all HbiHost fields match RHSM consumer
    CreateUpdateHostMessage message = thenAddHostMessageReceived();
    verifyAddHostMessage(message);
  }

  private void givenRhsmConsumerWithFullData() {
    Map<String, Object> consumer =
        RhsmApiStubs.buildFullConsumer(
            orgId,
            EXPECTED_CONSUMER_ID,
            EXPECTED_DISPLAY_NAME,
            EXPECTED_UUID,
            EXPECTED_FQDN,
            EXPECTED_BIOS_UUID,
            EXPECTED_OPENSHIFT_CLUSTER_ID,
            null,
            EXPECTED_INSIGHTS_ID,
            EXPECTED_IP_ADDRESSES,
            EXPECTED_MAC_ADDRESSES,
            EXPECTED_ARCH,
            EXPECTED_CPU_SOCKETS,
            EXPECTED_CORES_PER_SOCKET,
            EXPECTED_MEMORY_BYTES);
    wiremock.forRhsmApi().stubConsumersForOrg(orgId, List.of(consumer));
  }

  private void whenConduitSyncsForOrg(String orgId) {
    helpers.syncConduitByOrgId(service, orgId);
  }

  private CreateUpdateHostMessage thenAddHostMessageReceived() {
    CreateUpdateHostMessage message =
        kafkaBridge.waitForKafkaMessage(
            INVENTORY_HOST_INGRESS, MessageValidators.addHostMessageMatchesOrgId(orgId));
    assertNotNull(message, "Expected one add_host message");
    assertNotNull(message.getData(), "Message data (HbiHost) must not be null");
    return message;
  }

  private void verifyAddHostMessage(CreateUpdateHostMessage message) {
    assertEquals(
        "add_host",
        message.getOperation(),
        "Message operation should be add_host for new host from RHSM consumer");
    HbiHost data = message.getData();
    verifyHbiHostIdentityFields(data);
    verifyHbiHostReporterAndTimestamps(data);
    verifyHbiHostNetwork(data);
    verifyHbiHostRhsmFacts(data);
    verifyHbiHostSystemProfile(data);
  }

  private void verifyHbiHostIdentityFields(HbiHost data) {
    assertEquals(orgId, data.getOrgId(), "HbiHost orgId should match stubbed consumer");
    assertEquals(
        EXPECTED_DISPLAY_NAME, data.getDisplayName(), "Display name should match consumer name");
    assertEquals(EXPECTED_FQDN, data.getFqdn(), "FQDN should match network.fqdn fact");
    assertEquals(
        EXPECTED_UUID,
        data.getSubscriptionManagerId(),
        "Subscription manager ID should match consumer uuid");
    assertEquals(EXPECTED_BIOS_UUID, data.getBiosUuid(), "BIOS UUID should match dmi.system.uuid");
    assertEquals(
        EXPECTED_INSIGHTS_ID, data.getInsightsId(), "Insights ID should match insights_id fact");
    assertEquals(
        EXPECTED_OPENSHIFT_CLUSTER_ID,
        data.getOpenshiftClusterId(),
        "OpenShift cluster ID should match openshift.cluster_id fact");
  }

  private void verifyHbiHostReporterAndTimestamps(HbiHost data) {
    assertEquals(
        EXPECTED_REPORTER,
        data.getReporter(),
        "Reporter should be rhsm-conduit for conduit-synced hosts");
    assertNotNull(data.getStaleTimestamp(), "stale_timestamp must be set");
  }

  private void verifyHbiHostNetwork(HbiHost data) {
    assertEquals(
        EXPECTED_IP_ADDRESSES,
        data.getIpAddresses(),
        "IP addresses should match net.interface.eth0.ipv4_address_list fact");
    assertEquals(
        EXPECTED_MAC_ADDRESSES,
        data.getMacAddresses(),
        "MAC addresses should match net.interface.eth0.mac_address fact");
  }

  private void verifyHbiHostRhsmFacts(HbiHost data) {
    assertNotNull(data.getFacts(), "HbiHost should have at least one fact set");
    assertEquals(
        1, data.getFacts().size(), "Should have exactly one fact set (rhsm) for conduit host");
    HbiFactSet rhsmFactSet = data.getFacts().get(0);
    assertEquals(
        "rhsm", rhsmFactSet.getNamespace(), "Fact set namespace should be rhsm for RHSM facts");
    assertNotNull(rhsmFactSet.getFacts(), "RHSM fact set should have facts map");
    @SuppressWarnings("unchecked")
    Map<String, Object> rhsmFacts = (Map<String, Object>) rhsmFactSet.getFacts();
    assertEquals(orgId, rhsmFacts.get("orgId"), "RHSM facts should include orgId");
    assertNotNull(
        rhsmFacts.get("SYNC_TIMESTAMP"), "RHSM facts should include SYNC_TIMESTAMP from conduit");
  }

  private void verifyHbiHostSystemProfile(HbiHost data) {
    HbiSystemProfile systemProfile = data.getSystemProfile();
    assertNotNull(systemProfile, "HbiHost should have system_profile");
    assertEquals(EXPECTED_ARCH, systemProfile.getArch(), "Arch should match uname.machine fact");
    assertEquals(
        EXPECTED_CPU_SOCKETS,
        systemProfile.getNumberOfSockets(),
        "Number of sockets should match cpu.cpu_socket(s) fact");
    assertEquals(
        EXPECTED_CORES_PER_SOCKET,
        systemProfile.getCoresPerSocket(),
        "Cores per socket should match cpu.core(s)_per_socket fact");
    assertEquals(
        EXPECTED_UUID,
        systemProfile.getOwnerId(),
        "System profile owner_id should match consumer uuid");
    assertEquals(
        EXPECTED_MEMORY_BYTES,
        ((Number) systemProfile.getSystemMemoryBytes()).longValue(),
        "System memory bytes should match memory.memtotal (converted from KiB)");
  }
}
