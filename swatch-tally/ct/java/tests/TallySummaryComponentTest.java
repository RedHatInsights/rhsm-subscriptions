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
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;

import api.MessageValidators;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;
import utils.TallyTestHelpers;

public class TallySummaryComponentTest extends BaseTallyComponentTest {

  TallyTestHelpers helpers = new TallyTestHelpers();
  private static final String TEST_PRODUCT_ID = "rhel-for-x86-els-payg";
  private static final String TEST_METRIC_ID = "VCPUS";

  @Test
  public void testTallyNightlySummaryEmitsGranularityDaily() {
    final String testOrgId = helpers.generateRandomOrgId(); // Use random org ID
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();
    // Step 1: Create hosts in HBI database (simulated by creating events)
    // This step is handled by the event creation below

    // Step 2: Create events within the last 24 hours for nightly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(8).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(7).toString(), testEventId, 1.0f);

    Event event3 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(6).toString(), testEventId, 1.0f);

    Event event4 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(5).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    // Step 3: Run nightly tally using internal RPC endpoint (async)
    try {
      helpers.asyncTallyNightly(testOrgId, service);
    } catch (Exception e) {
      throw new RuntimeException("Failed to sync tally", e);
    }

    // Wait for tally messages to be produced
    kafkaBridge.waitForKafkaMessage(
        TALLY,
        MessageValidators.tallySummaryMatches(testOrgId, TEST_PRODUCT_ID, TEST_METRIC_ID),
        3); // Expected count of messages
  }

  @Test
  public void testTallyHourlySummaryEmitsGranularityHourlyDaily() {
    final String testOrgId = helpers.generateRandomOrgId(); // Use random org ID
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Step 1: Create hosts in HBI database (simulated by creating events)

    // Step 2: Create events within the last 2 hours for hourly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusMinutes(45).toString(), testEventId, 1.0f);

    Event event3 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusMinutes(30).toString(), testEventId, 1.0f);

    Event event4 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusMinutes(15).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    // Step 3: Run hourly tally
    try {
      helpers.syncTallyHourly(testOrgId, service);
    } catch (Exception e) {
      throw new RuntimeException("Failed to sync tally", e);
    }

    // Wait for tally messages to be produced
    kafkaBridge.waitForKafkaMessage(
        TALLY,
        MessageValidators.tallySummaryMatches(testOrgId, TEST_PRODUCT_ID, TEST_METRIC_ID),
        3); // Expected count of messages
  }
}
