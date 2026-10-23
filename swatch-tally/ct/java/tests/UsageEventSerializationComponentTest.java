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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.swatch.component.tests.api.ComponentTest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.UsageEvent;
import org.junit.jupiter.api.Test;

@Slf4j
@ComponentTest(name = "swatch-tally")
class UsageEventSerializationComponentTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void shouldSerializeUsageEvent() throws Exception {
    UsageEvent event = new UsageEvent();
    event.setCorrelationId(UUID.randomUUID().toString());
    event.setOrgId("12345");
    event.setServiceType("RHEL System");
    event.setInstanceId("instance-123");
    event.setTimestamp(OffsetDateTime.now());

    Measurement measurement = new Measurement();
    measurement.setMetricId("Cores");
    measurement.setValue(4.0);
    event.setMeasurements(Arrays.asList(measurement));

    String json = objectMapper.writeValueAsString(event);
    log.info("Serialized UsageEvent: {}", json);

    assertNotNull(json);
    assertTrue(json.contains("correlation_id"));
    assertTrue(json.contains("org_id"));
    assertTrue(json.contains("service_type"));
    assertTrue(json.contains("instance_id"));
    assertTrue(json.contains("timestamp"));
    assertTrue(json.contains("measurements"));
  }

  @Test
  void shouldDeserializeUsageEvent() throws Exception {
    String json =
        """
        {
          "correlation_id": "550e8400-e29b-41d4-a716-446655440000",
          "org_id": "12345",
          "service_type": "RHEL System",
          "instance_id": "instance-123",
          "timestamp": "2024-08-29T12:00:00Z",
          "measurements": [
            {
              "metric_id": "Cores",
              "value": 4.0
            }
          ]
        }
        """;

    UsageEvent event = objectMapper.readValue(json, UsageEvent.class);

    assertNotNull(event);
    assertEquals("550e8400-e29b-41d4-a716-446655440000", event.getCorrelationId());
    assertEquals("12345", event.getOrgId());
    assertEquals("RHEL System", event.getServiceType());
    assertEquals("instance-123", event.getInstanceId());
    assertNotNull(event.getTimestamp());
    assertNotNull(event.getMeasurements());
    assertEquals(1, event.getMeasurements().size());

    Measurement measurement = event.getMeasurements().get(0);
    assertEquals("Cores", measurement.getMetricId());
    assertEquals(4.0, measurement.getValue());

    log.info("Successfully deserialized UsageEvent: {}", event);
  }

  @Test
  void shouldHandleUsageEventWithoutMeasurements() throws Exception {
    String json =
        """
        {
          "correlation_id": "550e8400-e29b-41d4-a716-446655440001",
          "org_id": "12345",
          "service_type": "RHEL System",
          "instance_id": "instance-123",
          "timestamp": "2024-08-29T12:00:00Z"
        }
        """;

    UsageEvent event = objectMapper.readValue(json, UsageEvent.class);

    assertNotNull(event);
    assertEquals("550e8400-e29b-41d4-a716-446655440001", event.getCorrelationId());
    assertEquals("12345", event.getOrgId());
    assertEquals("RHEL System", event.getServiceType());
    assertEquals("instance-123", event.getInstanceId());
    assertNotNull(event.getTimestamp());

    log.info("Successfully deserialized UsageEvent without measurements: {}", event);
  }

  @Test
  void shouldHandleMultipleMeasurements() throws Exception {
    String json =
        """
        {
          "correlation_id": "550e8400-e29b-41d4-a716-446655440002",
          "org_id": "12345",
          "service_type": "OpenShift Cluster",
          "instance_id": "cluster-456",
          "timestamp": "2024-08-29T12:00:00Z",
          "measurements": [
            {
              "metric_id": "Cores",
              "value": 16.0
            },
            {
              "metric_id": "Memory",
              "value": 32.0
            }
          ]
        }
        """;

    UsageEvent event = objectMapper.readValue(json, UsageEvent.class);

    assertNotNull(event);
    assertEquals("550e8400-e29b-41d4-a716-446655440002", event.getCorrelationId());
    assertNotNull(event.getMeasurements());
    assertEquals(2, event.getMeasurements().size());

    Measurement cores = event.getMeasurements().get(0);
    assertEquals("Cores", cores.getMetricId());
    assertEquals(16.0, cores.getValue());

    Measurement memory = event.getMeasurements().get(1);
    assertEquals("Memory", memory.getMetricId());
    assertEquals(32.0, memory.getValue());

    log.info("Successfully deserialized UsageEvent with multiple measurements: {}", event);
  }
}
