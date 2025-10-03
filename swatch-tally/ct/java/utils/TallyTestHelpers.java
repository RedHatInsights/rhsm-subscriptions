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

import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.events.payloads.EventPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TallyTestHelpers {
  private static final String SERVICE_INSTANCE_INGRESS =
      "platform.rhsm-subscriptions.service-instance-ingress";

  @KafkaBridge private KafkaBridge kafkaBridge;

  private TallyTestHelpers() {}

  public EventPayload createEventPayload(
      String eventSource,
      String eventType,
      String orgId,
      String instanceId,
      String displayName,
      float value,
      String metricId) {
    OffsetDateTime prevHourStart = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime prevHourEnd = prevHourStart.plusHours(1).minusNanos(1);

    // Create EventPayload
    var payload = new EventPayload();

    // Set basic fields
    payload.setEventSource(eventSource);
    payload.setEventType(eventType);
    payload.setOrgId(orgId);
    payload.setInstanceId(instanceId);
    payload.setDisplayName(displayName);

    // Set timestamps
    payload.setTimestamp(
        prevHourStart
            .withOffsetSameInstant(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    payload.setExpiration(
        prevHourEnd
            .withOffsetSameInstant(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

    // Create and set measurement
    var measurement =
        Map.of(
            "value", value,
            "metric_id", metricId);
    payload.setMeasurements(List.of(measurement));

    // Set additional fields
    payload.setSla("Premium");
    payload.setServiceType("rosa Instance");
    payload.setRole("moa-hostedcontrolplane");
    payload.setBillingProvider("aws");
    payload.setBillingAccountId(UUID.randomUUID());

    return payload;
  }

  public void syncTallyByOrgId(String orgId) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(" /v1/internal/rpc/tally/snapshots/{org_id}".replace("{org_id}", orgId)))
            .PUT()
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // Log Reponse
    System.out.println("Status Code: " + response.statusCode());
    System.out.println("Response Body: " + response.body());
  }
}
