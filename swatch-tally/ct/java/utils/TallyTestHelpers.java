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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;

public class TallyTestHelpers {

  public TallyTestHelpers() {}

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

    // Set product IDs as specified in PR: ["69","204"]
    event.setProductIds(List.of("69", "204"));

    // Add product tags for RHEL for x86 - this is crucial for processing
    event.setProductTag(Set.of("hel-for-x86-els-payg"));

    // Create measurement with vCPUs metric
    var measurement = new org.candlepin.subscriptions.json.Measurement();
    measurement.setValue((double) value);
    measurement.setMetricId(TEST_METRIC_ID);
    event.setMeasurements(List.of(measurement));

    return event;
  }

  public void syncTallyNightly(
      String orgId, com.redhat.swatch.component.tests.api.SwatchService service) throws Exception {
    // Build the service URL
    String baseUrl = "http://" + service.getHost() + ":" + service.getMappedPort(8080);
    String endpoint = baseUrl + "/api/rhsm-subscriptions/v1/internal/rpc/tally/snapshots/" + orgId;

    // Create HTTP client
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

    // Create HTTP request
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("x-rh-swatch-psk", "placeholder")
            .PUT(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(60))
            .build();

    // Send the request
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // Check response status
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "Tally sync failed with status code: "
              + response.statusCode()
              + ", response body: "
              + response.body());
    }

    System.out.println("Tally sync endpoint called successfully for org: " + orgId);
  }

  public void syncTallyHourly(
      String orgId, com.redhat.swatch.component.tests.api.SwatchService service) throws Exception {
    // Build the service URL
    String baseUrl = "http://" + service.getHost() + ":" + service.getMappedPort(8080);
    String endpoint = baseUrl + "/api/rhsm-subscriptions/v1/internal/tally/hourly?org=" + orgId;

    // Create HTTP client
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

    // Create HTTP request
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("x-rh-swatch-psk", "placeholder")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(60))
            .build();

    // Send the request
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // Check response status
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "Hourly tally sync failed with status code: "
              + response.statusCode()
              + ", response body: "
              + response.body());
    }

    System.out.println("Hourly tally endpoint called successfully for org: " + orgId);
  }

  public HttpResponse<String> makeHttpGetRequest(String url) throws Exception {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("x-rh-swatch-psk", "placeholder")
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
