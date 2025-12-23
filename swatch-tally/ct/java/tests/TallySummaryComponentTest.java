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

import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import utils.TallyTestHelpers;

public class TallySummaryComponentTest extends BaseTallyComponentTest {

  private static final TallyTestHelpers helpers = new TallyTestHelpers();
  private static final String TEST_PRODUCT_ID = "rhel-for-x86-els-payg";
  private static final String TEST_METRIC_ID = "VCPUS";
  private static final String TEST_PRODUCT_ID_NO_PAYG = "RHEL for x86";
  private static final String TEST_METRIC_ID_NO_PAYG = "Sockets";

  // @Test
  @Ignore("This test should run after https://issues.redhat.com/browse/SWATCH-2922")
  public void testTallyNightlySummaryEmitsGranularityHourly() {
    final String testOrgId = RandomUtils.generateRandom(); // Use random org ID
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Create events within the last 24-48 hours for nightly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(24).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(48).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);

    // Run hourly tally and wait for messages with polling
    helpers.pollForTallySyncAndMessages(
        testOrgId, TEST_PRODUCT_ID, "CORES", Granularity.HOURLY, 1, service, kafkaBridge);
  }

  // @Test
  @Ignore("This test should run after https://issues.redhat.com/browse/SWATCH-2922")
  public void testTallyNightlySummaryEmitsGranularityDaily() {
    final String testOrgId = RandomUtils.generateRandom(); // Use random org ID
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Create events within the last 24-48 hours for nightly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(2).toString(), testEventId, 1.0f);

    Event event3 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(3).toString(), testEventId, 1.0f);

    Event event4 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(4).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    // Run hourly tally and wait for messages with polling
    helpers.pollForTallySyncAndMessages(
        testOrgId, TEST_PRODUCT_ID, TEST_METRIC_ID, Granularity.HOURLY, 4, service, kafkaBridge);
  }

  @Test
  public void testTallyHourlySummaryEmitsGranularityDailyNoPayg() {
    final String testOrgId = RandomUtils.generateRandom(); // Use random org ID
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Create events within the last day for hourly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            testOrgId,
            testInstanceId,
            now.minusHours(1).toString(),
            testEventId,
            TEST_METRIC_ID_NO_PAYG,
            1.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            "69",
            TEST_PRODUCT_ID_NO_PAYG);

    Event event2 =
        helpers.createEventWithTimestamp(
            testOrgId,
            testInstanceId,
            now.minusHours(2).toString(),
            testEventId,
            TEST_METRIC_ID_NO_PAYG,
            1.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            "69",
            TEST_PRODUCT_ID_NO_PAYG);

    Event event3 =
        helpers.createEventWithTimestamp(
            testOrgId,
            testInstanceId,
            now.minusHours(3).toString(),
            testEventId,
            TEST_METRIC_ID_NO_PAYG,
            1.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            "69",
            TEST_PRODUCT_ID_NO_PAYG);

    Event event4 =
        helpers.createEventWithTimestamp(
            testOrgId,
            testInstanceId,
            now.minusHours(4).toString(),
            testEventId,
            TEST_METRIC_ID_NO_PAYG,
            1.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            "69",
            TEST_PRODUCT_ID_NO_PAYG);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    List<TallySnapshot> tallySnapshots =
        helpers.pollForTallySyncAndMessages(
            testOrgId,
            TEST_PRODUCT_ID_NO_PAYG,
            TEST_METRIC_ID_NO_PAYG,
            Granularity.DAILY,
            1,
            service,
            kafkaBridge);
    // Confirms no total hardware measurements are present
    assert (tallySnapshots
            .get(0)
            .getMeasurements()
            .entrySet()
            .removeIf(
                entry -> HardwareMeasurementType.TOTAL.equals(entry.getKey().getMeasurementType()))
            .size()
        == tallySnapshots.get(0).getMeasurements().size());
  }

  @Test
  public void testTallyHourlySummaryNotEmitsGranularityDailyPayg() {
    final String testOrgId = RandomUtils.generateRandom(); // Use random org ID
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Create events within the last day for hourly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(2).toString(), testEventId, 1.0f);

    Event event3 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(3).toString(), testEventId, 1.0f);

    Event event4 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(4).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    helpers.pollForTallySyncAndMessages(
        testOrgId, TEST_PRODUCT_ID, TEST_METRIC_ID, Granularity.DAILY, 0, service, kafkaBridge);
  }

  @Test
  public void testTallyHourlySummaryEmitsGranularityHourly() {
    final String testOrgId = RandomUtils.generateRandom(); // Use random org ID
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Create events within the last day for hourly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(2).toString(), testEventId, 1.0f);

    Event event3 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(3).toString(), testEventId, 1.0f);

    Event event4 =
        helpers.createEventWithTimestamp(
            testOrgId, testInstanceId, now.minusHours(4).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    helpers.pollForTallySyncAndMessages(
        testOrgId, TEST_PRODUCT_ID, TEST_METRIC_ID, Granularity.HOURLY, 4, service, kafkaBridge);
  }
}
