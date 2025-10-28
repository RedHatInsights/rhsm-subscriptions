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
package utils;

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.logging.Log;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;

public class TallyTestHelpers {

  private void executeSql(String sql, String operationName) throws Exception {
    List<String> command =
        List.of(
            "podman",
            "exec",
            "rhsm-subscriptions_db_1",
            "psql",
            "-U",
            "rhsm-subscriptions",
            "rhsm-subscriptions",
            "-c",
            sql);

    Process process =
        com.redhat.swatch.component.tests.utils.ProcessBuilderProvider.command(command).start();

    int exitCode = process.waitFor();

    if (exitCode != 0) {
      String error = new String(process.getErrorStream().readAllBytes());
      String output = new String(process.getInputStream().readAllBytes());
      throw new RuntimeException(
          String.format(
              "Failed to %s. Exit code: %d, Error: %s, Output: %s",
              operationName, exitCode, error, output));
    }
  }

  public Event createEventWithTimestamp(
      String orgId, String instanceId, String timestampStr, String eventIdStr, float value) {

    OffsetDateTime timestamp = OffsetDateTime.parse(timestampStr);
    OffsetDateTime expiration = timestamp.plusDays(25); // Set expiration as in PR

    Event event = new Event();
    event.setEventId(UUID.fromString(eventIdStr));
    event.setOrgId(orgId);
    event.setInstanceId(instanceId);
    event.setDisplayName(Optional.of("Test Instance")); // Add display name
    event.setTimestamp(timestamp);
    event.setRecordDate(timestamp); // Add record date for processing
    event.setExpiration(Optional.of(expiration));
    event.setEventSource("cost-management");
    event.setEventType("snapshot");
    event.setSla(Event.Sla.PREMIUM);
    event.setRole(Event.Role.RED_HAT_ENTERPRISE_LINUX_SERVER);
    event.setUsage(Event.Usage.PRODUCTION);
    event.setServiceType("RHEL System");
    event.setHardwareType(Event.HardwareType.CLOUD);
    event.setCloudProvider(Event.CloudProvider.AWS);
    event.setBillingProvider(Event.BillingProvider.AWS);
    event.setBillingAccountId(Optional.of("746157280291"));
    event.setConversion(true);

    // Set product IDs for RHEL ELS for x86: engineering ID 204 from rhel-for-x86-els-payg config
    event.setProductIds(List.of("204"));

    // Add product tags for RHEL ELS for x86
    event.setProductTag(Set.of("rhel-for-x86-els-payg"));

    // Create measurement with vCPUs metric
    var measurement = new org.candlepin.subscriptions.json.Measurement();
    measurement.setValue((double) value);
    measurement.setMetricId("VCPUS");
    event.setMeasurements(List.of(measurement));

    return event;
  }

  public void syncTallyNightly(String orgId, SwatchService service) throws Exception {
    Response response =
        service
            .given()
            .header("x-rh-swatch-psk", "placeholder")
            .header("x-rh-swatch-synchronous-request", "true")
            .put("/api/rhsm-subscriptions/v1/internal/rpc/tally/snapshots/" + orgId)
            .then()
            .extract()
            .response();

    // Check response status
    if (response.getStatusCode() != 200) {
      throw new RuntimeException(
          "Tally sync failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Sync nightly tally endpoint called successfully for org: %s", orgId);
  }

  public void syncTallyHourly(String orgId, SwatchService service) throws Exception {
    Response response =
        service
            .given()
            .header("x-rh-swatch-psk", "placeholder")
            .queryParam("org", orgId)
            .post("/api/rhsm-subscriptions/v1/internal/tally/hourly")
            .then()
            .extract()
            .response();

    // Check response status - hourly tally returns 204 No Content on success
    if (response.getStatusCode() != 204) {
      throw new RuntimeException(
          "Hourly tally sync failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Hourly tally endpoint called successfully for org: %s", orgId);
  }

  /**
   * Create a mock host in the database for testing purposes. This is needed for nightly tally tests
   * since MaxSeenSnapshotStrategy reads from existing hosts.
   */
  public void createMockHost(String orgId, String instanceId, SwatchService service)
      throws Exception {
    // First, insert into account_services table to satisfy foreign key constraint
    executeSql(
        String.format(
            "INSERT INTO account_services (org_id, service_type) "
                + "VALUES ('%s', 'HBI_HOST') "
                + "ON CONFLICT (org_id, service_type) DO NOTHING",
            orgId),
        "create account service");

    // Now insert the host
    executeSql(
        String.format(
            "INSERT INTO hosts (id, org_id, display_name, insights_id, subscription_manager_id, instance_id, billing_provider, billing_account_id, cloud_provider, last_seen) "
                + "VALUES ('%s', '%s', 'test-host', 'insights-%s', 'sm-%s', '%s', 'aws', '746157280291', 'aws', NOW() + INTERVAL '1 day')",
            instanceId, orgId, instanceId, instanceId, instanceId),
        "create mock host");

    Log.info("Mock host created successfully for org: %s, instance: %s", orgId, instanceId);
  }

  public String generateRandomOrgId() {
    // Generate a random 8-digit org ID using UUID
    return String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000);
  }

  public void waitForProcessing(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting", e);
    }
  }
}
