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
import static org.junit.jupiter.api.Assertions.assertNull;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Component tests for Tally Event Ingestion
 *
 * <p>Tests the flow:
 *
 * <ol>
 *   <li>Ingest PAYG events
 *   <li>Run hourly tally (processes events from Kafka)
 *   <li>Verify tally snapshots reflect correct usage after conflict resolution
 * </ol>
 */
public class TallyEventIngestionTest extends BaseTallyComponentTest {
  String productTag = RHEL_FOR_X86_ELS_PAYG.productTag();
  String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
  OffsetDateTime now =
      OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
  Event event1;
  Event event2;
  Double expectedSum;

  @BeforeEach
  void setUp() {
    // Create the valid events
    super.setUp();
    event1 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            RandomUtils.generateRandom(),
            now.toString(),
            UUID.randomUUID().toString(),
            metricId,
            2.0f,
            Event.Sla.STANDARD,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            RandomUtils.generateRandom(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            productTag);

    event2 =
        helpers.createPaygEventWithTimestamp(
            orgId,
            RandomUtils.generateRandom(),
            now.toString(),
            UUID.randomUUID().toString(),
            metricId,
            4.0f,
            Event.Sla.STANDARD,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            RandomUtils.generateRandom(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            productTag);

    expectedSum =
        event1.getMeasurements().get(0).getValue() + event2.getMeasurements().get(0).getValue();
  }

  @Test
  @TestPlanName("tally-event-ingestion-negative-measurements-TC001")
  void testPaygEventIngestionBatchWithEventWithNegativeMeasurement() {
    service.createOptInConfig(orgId);
    givenFeatureFlagIsConfigured(true);

    // Create the invalid event
    Event malformedEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            RandomUtils.generateRandom(),
            now.toString(),
            UUID.randomUUID().toString(),
            metricId,
            -6.0f,
            Event.Sla.STANDARD,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            RandomUtils.generateRandom(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            productTag);

    malformedEvent.setDisplayName(Optional.of("BAD-INSTANCE"));
    // Send the events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, malformedEvent);

    // Ensure event is persisted and hourly tally runs
    awaitHourlyTallySum(orgId, productTag, metricId, now, now.plusHours(1), expectedSum);

    OffsetDateTime beginning = now;
    OffsetDateTime ending = now.plusHours(2);

    var instance =
        getInstanceByDisplayName(
            orgId, productTag, beginning, ending, malformedEvent.getDisplayName().orElse(""));
    assertNull(instance, "Instance should not exist in tally report");
  }

  @Test
  @TestPlanName("tally-event-ingestion-null-instance-id-TC002")
  void testPaygEventIngestionBatchWithEventWithNullInstanceId() {
    service.createOptInConfig(orgId);
    givenFeatureFlagIsConfigured(true);

    // Create the invalid event
    Event malformedEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            null, // Instance ID is null
            now.toString(),
            UUID.randomUUID().toString(),
            metricId,
            6.0f,
            Event.Sla.STANDARD,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            RandomUtils.generateRandom(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            productTag);

    malformedEvent.setDisplayName(Optional.of("BAD-INSTANCE"));
    // Send the events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, malformedEvent);

    // Ensure event is persisted and hourly tally runs
    awaitHourlyTallySum(orgId, productTag, metricId, now, now.plusHours(1), expectedSum);

    OffsetDateTime beginning = now;
    OffsetDateTime ending = now.plusHours(2);

    var instance =
        getInstanceByDisplayName(
            orgId, productTag, beginning, ending, malformedEvent.getDisplayName().orElse(""));
    assertNull(instance, "Instance should not exist in tally report");
  }

  @Test
  @TestPlanName("tally-event-ingestion-invalid-product-tag-TC003")
  void testPaygEventIngestionBatchWithEventWithInvalidProductTag() {
    service.createOptInConfig(orgId);
    givenFeatureFlagIsConfigured(true);

    // Create the invalid event
    Event malformedEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            RandomUtils.generateRandom(),
            now.toString(),
            UUID.randomUUID().toString(),
            metricId,
            10.0f,
            Event.Sla.STANDARD,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            RandomUtils.generateRandom(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            productTag);

    malformedEvent.setDisplayName(Optional.of("BAD-INSTANCE"));
    malformedEvent.setProductTag(Set.of("INVALID-PRODUCT-TAG"));
    // Send the events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, malformedEvent);

    // Ensure event is persisted and hourly tally runs
    awaitHourlyTallySum(orgId, productTag, metricId, now, now.plusHours(1), expectedSum);

    OffsetDateTime beginning = now;
    OffsetDateTime ending = now.plusHours(2);

    var instance =
        getInstanceByDisplayName(
            orgId, productTag, beginning, ending, malformedEvent.getDisplayName().orElse(""));
    assertNull(instance, "Instance should not exist in tally report");
  }
}
