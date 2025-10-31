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

  // Test configuration constants
  private static final String TEST_PSK = "placeholder";
  private static final String DEFAULT_BILLING_ACCOUNT = "746157280291";
  private static final String TEST_PRODUCT_ID = "204";
  private static final String DEFAULT_PRODUCT_TAG = "rhel-for-x86-els-payg";
  private static final int EVENT_EXPIRATION_DAYS = 25;

  /** Default constructor. */
  public TallyTestHelpers() {}

  public Event createEventWithTimestamp(
      String orgId, String instanceId, String timestampStr, String eventIdStr, float value) {

    OffsetDateTime timestamp = OffsetDateTime.parse(timestampStr);
    OffsetDateTime expiration = timestamp.plusDays(EVENT_EXPIRATION_DAYS);

    Event event = new Event();
    event.setEventId(UUID.fromString(eventIdStr));
    event.setOrgId(orgId);
    event.setInstanceId(instanceId);
    event.setDisplayName(Optional.of("Test Instance"));
    event.setTimestamp(timestamp);
    event.setRecordDate(timestamp);
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
    event.setBillingAccountId(Optional.of(DEFAULT_BILLING_ACCOUNT));
    event.setConversion(true);
    event.setProductIds(List.of(TEST_PRODUCT_ID));
    event.setProductTag(Set.of(DEFAULT_PRODUCT_TAG));

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
            .header("x-rh-swatch-psk", TEST_PSK)
            .header("x-rh-swatch-synchronous-request", "true")
            .put("/api/rhsm-subscriptions/v1/internal/rpc/tally/snapshots/" + orgId)
            .then()
            .extract()
            .response();

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
            .header("x-rh-swatch-psk", TEST_PSK)
            .queryParam("org", orgId)
            .post("/api/rhsm-subscriptions/v1/internal/tally/hourly")
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 204) {
      throw new RuntimeException(
          "Hourly tally sync failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Hourly tally endpoint called successfully for org: %s", orgId);
  }

  public String generateRandomOrgId() {
    return String.format("%d", Math.abs(UUID.randomUUID().hashCode()) % 100000000);
  }
}
