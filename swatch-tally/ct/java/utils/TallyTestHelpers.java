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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;

public class TallyTestHelpers {

  public TallyTestHelpers() {}

  public Event createEventPayload(
      String eventSource,
      String eventType,
      String orgId,
      String instanceId,
      String displayName,
      float value,
      String metricId) {
    OffsetDateTime prevHourStart = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime prevHourEnd = prevHourStart.plusHours(1).minusNanos(1);

    // Create Event
    var payload = new Event();

    // Set basic fields
    payload.setEventSource(eventSource);
    payload.setEventType(eventType);
    payload.setOrgId(orgId);
    payload.setInstanceId(instanceId);
    payload.setDisplayName(Optional.of(displayName));

    // Set timestamps
    payload.setTimestamp(prevHourStart.withOffsetSameInstant(ZoneOffset.UTC));
    // Set expiration to match working Python payload
    payload.setExpiration(Optional.of(prevHourEnd.withOffsetSameInstant(ZoneOffset.UTC)));

    // Create and set measurement
    var measurement = new Measurement();
    measurement.setValue((double) value);
    measurement.setMetricId(metricId);
    payload.setMeasurements(List.of(measurement));

    // Set additional fields
    payload.setSla(Event.Sla.PREMIUM);
    payload.setServiceType("RHEL System");
    payload.setRole(Event.Role.RED_HAT_ENTERPRISE_LINUX_SERVER);
    payload.setBillingProvider(Event.BillingProvider.AWS);
    // Set billing account ID to match working Python payload
    payload.setBillingAccountId(Optional.of("test-300"));
    // Set product tags to match working Python payload
    payload.setProductTag(Set.of("rhel-for-x86-els-payg-addon", "rhel-for-x86-els-payg"));
    // Set product IDs to match working Python payload
    payload.setProductIds(List.of("204", "69"));

    // Set optional fields that might cause serialization issues
    payload.setMeteringBatchId(UUID.randomUUID());
    payload.setEventId(UUID.randomUUID());

    return payload;
  }

  public void syncTallyByOrgId(
      String orgId, com.redhat.swatch.component.tests.api.SwatchService service) throws Exception {
    // Use the same endpoint as Python version for hourly tally processing
    String start =
        OffsetDateTime.now().minusHours(24).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    String end = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    // Use service.given() to call the tally sync endpoint
    service
        .given()
        .header("x-rh-swatch-psk", "placeholder")
        .queryParam("org", orgId)
        .queryParam("start", start)
        .queryParam("end", end)
        .when()
        .post("/api/rhsm-subscriptions/v1/internal/tally/hourly")
        .then()
        .statusCode(204);

    System.out.println("Tally sync endpoint called successfully for org: " + orgId);
  }
}
