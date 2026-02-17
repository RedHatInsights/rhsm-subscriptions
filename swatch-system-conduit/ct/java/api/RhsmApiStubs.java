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
package api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Facade for stubbing RHSM API (consumers/feeds) endpoints. */
public class RhsmApiStubs {

  private final ConduitWiremockService wiremockService;

  protected RhsmApiStubs(ConduitWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  /**
   * Stub the GET /candlepin/consumers/feeds endpoint to return a list of consumers for the given
   * org. Used by system-conduit to fetch host data from the RHSM API.
   *
   * @param orgId the organization ID (X-RhsmApi-AccountID header)
   * @param consumers list of consumer payloads to return (each a Map with id, uuid, orgId, type,
   *     name, lastCheckin, installedProducts, sysPurposeRole, sysPurposeUsage, sysPurposeAddons,
   *     facts, etc.)
   */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public void stubConsumersForOrg(String orgId, List<Map<String, Object>> consumers) {
    Map<String, Object> pagination =
        Map.of("offset", "", "limit", 1000L, "count", (long) consumers.size());

    Map<String, Object> responseBody = new LinkedHashMap<>();
    responseBody.put("body", consumers);
    responseBody.put("pagination", pagination);

    String responseBodyJson;
    try {
      responseBodyJson = OBJECT_MAPPER.writeValueAsString(responseBody);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize stub response", e);
    }

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put(
        "request",
        Map.of(
            "method", "GET",
            "urlPathPattern", "/candlepin/consumers/feeds.*",
            "headers", Map.of("X-RhsmApi-AccountID", Map.of("equalTo", orgId))));
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", 200);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("body", responseBodyJson);
    mapping.put("response", response);
    mapping.put("priority", 9);
    mapping.put("metadata", wiremockService.getMetadataTags());

    wiremockService
        .given()
        .contentType("application/json")
        .body(mapping)
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /**
   * Stub the GET /candlepin/consumers/feeds endpoint to return a single canned consumer for the
   * given org. Convenience method for tests that only need one host.
   *
   * @param orgId the organization ID
   */
  public void stubConsumersForOrg(String orgId) {
    stubConsumersForOrg(
        orgId, List.of(buildMinimalConsumer(orgId, "consumer-1", "host1.test.com")));
  }

  /** Build a minimal consumer map suitable for add_host processing. */
  public static Map<String, Object> buildMinimalConsumer(
      String orgId, String consumerId, String fqdn) {
    Map<String, Object> consumer = new HashMap<>();
    consumer.put("type", "system");
    consumer.put("id", consumerId);
    consumer.put("uuid", java.util.UUID.randomUUID().toString());
    consumer.put("name", consumerId);
    consumer.put("orgId", orgId);
    consumer.put("lastCheckin", "2024-01-01T12:00:00Z");
    consumer.put("sysPurposeRole", "");
    consumer.put("sysPurposeUsage", "");
    consumer.put("sysPurposeAddons", new ArrayList<String>());
    consumer.put(
        "installedProducts",
        List.of(Map.of("productId", "72", "productName", "RHEL", "productVersion", "8")));
    consumer.put(
        "facts",
        Map.of("network.fqdn", fqdn, "dmi.system.uuid", java.util.UUID.randomUUID().toString()));
    return consumer;
  }

  /**
   * Build a full consumer map with all fields needed to validate the resulting HbiHost message,
   * including openshift_cluster_id. Use fixed UUIDs/values when you need to assert exact match in
   * the test.
   */
  public static Map<String, Object> buildFullConsumer(
      String orgId,
      String consumerId,
      String name,
      String uuid,
      String fqdn,
      String biosUuid,
      String openshiftClusterId,
      String openshiftClusterUuid,
      String insightsId,
      List<String> ipAddresses,
      List<String> macAddresses,
      String arch,
      Integer cpuSockets,
      Integer coresPerSocket,
      Long memoryBytes) {
    // Use LinkedHashMap so "name" and other fields are serialized in consistent order for WireMock
    Map<String, Object> consumer = new LinkedHashMap<>();
    consumer.put("type", "system");
    consumer.put("id", consumerId);
    consumer.put("uuid", uuid);
    consumer.put("name", name != null ? name : consumerId);
    consumer.put("orgId", orgId);
    consumer.put("lastCheckin", "2024-01-01T12:00:00Z");
    consumer.put("sysPurposeRole", "");
    consumer.put("sysPurposeUsage", "");
    consumer.put("sysPurposeAddons", new ArrayList<String>());
    consumer.put(
        "installedProducts",
        List.of(Map.of("productId", "72", "productName", "RHEL", "productVersion", "8")));

    Map<String, String> facts = new HashMap<>();
    facts.put("network.fqdn", fqdn);
    facts.put("dmi.system.uuid", biosUuid);
    if (openshiftClusterId != null) {
      facts.put("openshift.cluster_id", openshiftClusterId);
    }
    if (openshiftClusterUuid != null) {
      facts.put("openshift.cluster_uuid", openshiftClusterUuid);
    }
    if (insightsId != null) {
      facts.put("insights_id", insightsId);
    }
    if (arch != null) {
      facts.put("uname.machine", arch);
    }
    if (cpuSockets != null) {
      facts.put("cpu.cpu_socket(s)", String.valueOf(cpuSockets));
    }
    if (coresPerSocket != null) {
      facts.put("cpu.core(s)_per_socket", String.valueOf(coresPerSocket));
    }
    if (memoryBytes != null) {
      // RHSM memory.memtotal is in KiB (as in /proc/meminfo); conduit converts to bytes for
      // system_profile
      facts.put("memory.memtotal", String.valueOf(memoryBytes / 1024));
    }
    if (ipAddresses != null && !ipAddresses.isEmpty()) {
      facts.put("net.interface.eth0.ipv4_address_list", String.join(", ", ipAddresses));
    }
    if (macAddresses != null && !macAddresses.isEmpty()) {
      // Conduit expects net.interface.<name>.mac_address (see
      // InventoryController.NIC_PREFIX/MAC_SUFFIX)
      facts.put("net.interface.eth0.mac_address", String.join(", ", macAddresses));
    }
    consumer.put("facts", facts);
    return consumer;
  }
}
