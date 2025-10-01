package com.redhat.swatch.hbi.events.ct.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.services.FeatureFlags;
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

  @Test
  void testUnleashFlagToggling() {
    unleash.enableFlag(FeatureFlags.EMIT_EVENTS);
    assertTrue(unleash.isFlagEnabled(FeatureFlags.EMIT_EVENTS));
    unleash.disableFlag(FeatureFlags.EMIT_EVENTS);
    assertFalse(unleash.isFlagEnabled(FeatureFlags.EMIT_EVENTS));
  }

}
