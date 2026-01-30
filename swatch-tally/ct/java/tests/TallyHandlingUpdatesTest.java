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

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;
import utils.TallyTestHelpers;

public class TallyHandlingUpdatesTest extends BaseTallyComponentTest {

  private static final TallyTestHelpers helpers = new TallyTestHelpers();
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String TEST_PRODUCT_TAG = "rhel-for-x86-els-payg";
  private static final String TEST_PRODUCT_ID = "204";
  private static final String TEST_METRIC_ID = "vCPUs";

  @Test
  public void testTallyCorrectlyHandlesPositiveMetricValueUpdates() throws Exception {
    String orgId = RandomUtils.generateRandom();
    helpers.createOptInConfig(orgId, service);

    // Use a fixed hour bucket so both events collide (same instance_id + same hour).
    OffsetDateTime start =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);
    String instanceId = UUID.randomUUID().toString();

    float initialValue = 10.0f;
    float updatedValue = 25.0f;

    // Initial event
    createEvent(orgId, instanceId, start, initialValue);
    helpers.syncTallyHourly(orgId, service);
    double before = awaitHourlyTallySum(orgId, start, start.plusHours(1), initialValue);

    // Update event: same instanceId + same timestamp hour, different positive value
    createEvent(orgId, instanceId, start, updatedValue);
    helpers.syncTallyHourly(orgId, service);
    double after = awaitHourlyTallySum(orgId, start, start.plusHours(1), updatedValue);
    assertEquals(
        updatedValue, after, 0.0001, "Updated tally should reflect the updated measurement");
  }

  @Test
  public void testTallyCorrectlyHandlesNegativeMetricValueNoUpdates() throws Exception {
    String orgId = RandomUtils.generateRandom();
    helpers.createOptInConfig(orgId, service);

    // Use a fixed hour bucket so both events collide (same instance_id + same hour).
    OffsetDateTime start =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);
    String instanceId = UUID.randomUUID().toString();

    float initialValue = 10.0f;
    float updatedValue = -25.0f;

    // Initial event
    createEvent(orgId, instanceId, start, initialValue);
    helpers.syncTallyHourly(orgId, service);
    double before = awaitHourlyTallySum(orgId, start, start.plusHours(1), initialValue);

    // Update event: same instanceId + same timestamp hour, different negative value
    createEvent(orgId, instanceId, start, updatedValue);
    helpers.syncTallyHourly(orgId, service);
    double after = awaitHourlyTallySum(orgId, start, start.plusHours(1), before);
    assertEquals(
        before, after, 0.0001, "Tally should not reflect the updated negative measurement");
  }

  private void createEvent(String orgId, String instanceId, OffsetDateTime timestamp, float value) {
    kafkaBridge.produceKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS,
        helpers.createEventWithTimestamp(
            orgId,
            instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            value,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG));
  }

  private double getHourlyTallySum(String orgId, OffsetDateTime beginning, OffsetDateTime ending)
      throws Exception {
    Map<String, ?> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", beginning.toString(),
            "ending", ending.toString());

    Response resp =
        helpers.getTallyReport(service, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);
    JsonNode json = objectMapper.readTree(resp.asString());
    JsonNode data = json.path("data");

    double sum = 0.0;
    if (data.isArray()) {
      for (JsonNode row : data) {
        JsonNode valueNode = row.get("value");
        if (valueNode != null && valueNode.isNumber()) {
          sum += valueNode.asDouble();
        }
      }
    }
    return sum;
  }

  private double awaitHourlyTallySum(
      String orgId, OffsetDateTime beginning, OffsetDateTime ending, double expected)
      throws Exception {
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(30))
            .withService(service)
            .timeoutMessage(
                "Timed out waiting for hourly tally to reach expected value %.4f", expected);

    // Poll until the tally report reflects the expected sum for the range.
    AwaitilityUtils.untilAsserted(
        () -> {
          helpers.syncTallyHourly(orgId, service);
          assertEquals(expected, getHourlyTallySum(orgId, beginning, ending), 0.0001);
        },
        settings);

    return getHourlyTallySum(orgId, beginning, ending);
  }
}
