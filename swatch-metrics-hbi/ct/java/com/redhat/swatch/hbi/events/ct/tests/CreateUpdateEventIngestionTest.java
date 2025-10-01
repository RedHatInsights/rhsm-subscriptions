package com.redhat.swatch.hbi.events.ct.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.HbiEventHelper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.services.FeatureFlags;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

class CreateUpdateEventIngestionTest extends BaseSMHBIComponentTest {

  @BeforeEach
  void setupTest() {
    unleash.enableFlag(FeatureFlags.EMIT_EVENTS);
  }
  
  @AfterEach
  void teardown() {
    unleash.disableFlag(FeatureFlags.EMIT_EVENTS);
  }

  /**
   * Verify service accepts HBI Create/Update events for a physical x86 host
   * and produce the expected Swatch Event messages.
   *
   * test_steps:
   *     1. Toggle feature flag to allow service to emit swatch events.
   *     2. Send a created/updated message to the HBI event topic to simulate that a host was created in HBI.
   * expected_results:
   *     1. The swatch-metrics-hbi service will ingest the event and should create an outbox record for a Swatch Event
   *        message containing the measurements for the host represented by the HBI event. NOTE: We expect the same
   *        result regardless of whether the HBI event was host created OR host updated.
   */
  @ParameterizedTest
  @CsvSource({
    "created, INSTANCE_CREATED",
    "updated, INSTANCE_UPDATED"
  })
  void testHbiRhsmHostEvent(String hbiEventType, String swatchEventType) {
    HbiHostCreateUpdateEvent hbiEvent = HbiEventHelper.getRhsmHostEvent(
        hbiEventType,
        List.of("69"),
        false,
        "2024-10-18T16:42:27.185484784Z",
        "Self-Support",
        "Development/Test",
        2,
        2);
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEventType, hbiEvent);
    kafkaBridge.waitForKafkaMessage(Topics.HBI_EVENT_IN,
        messages -> messages.contains(hbiEventType),
        1);
    /*
    swatchMetricsHbi.flushOutboxSynchronously();

    kafkaBridge.waitForKafkaMessage(Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        messages -> messages.contains(swatchEventType),
        1);
    */
  }

}