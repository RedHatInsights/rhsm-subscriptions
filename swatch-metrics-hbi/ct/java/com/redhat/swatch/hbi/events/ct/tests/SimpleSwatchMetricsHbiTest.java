package com.redhat.swatch.hbi.events.ct.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.model.FlushResponse;
import com.redhat.swatch.hbi.model.FlushResponse.StatusEnum;
import org.junit.jupiter.api.Test;

class SimpleSwatchMetricsHbiTest extends BaseSMHBIComponentTest {

  @Test
  void testServiceIsUpAndRunning() {
    assertTrue(swatchMetricsHbi.isRunning());
  }

  @Test
  void testFlushApi() {
    FlushResponse body = swatchMetricsHbi.flushOutboxSynchronously();
    assertEquals(StatusEnum.SUCCESS, body.getStatus());
    assertFalse(body.getAsync());
  }

  /**
   * NOTE: This is not a valid test but ensures that the swatch-metrics-hbi CT test
   * setup is working and is capable of sending an HBI event message to Kafka to initiate
   * a test. THIS SHOULD BE REMOVED
   */
  @Test
  void testHbiKafkaTopicConnection() {
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, "test");
    kafkaBridge.waitForKafkaMessage(Topics.HBI_EVENT_IN,
        messages -> messages.contains("test"),
        1);
  }

  /**
   * NOTE: This is not a valid test but ensures that the unleash flag can be correctly
   * toggled on and off. THIS SHOULD BE REMOVED
   */
  @Test
  void testUnleashFlagToggling() {
    unleash.enableFlag();
    assertTrue(unleash.isFlagEnabled());
    unleash.disableFlag();
    assertFalse(unleash.isFlagEnabled());
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
  @Test
  void testSimpleHbiEventInSwatchEventOut() throws Exception {

  }

}
