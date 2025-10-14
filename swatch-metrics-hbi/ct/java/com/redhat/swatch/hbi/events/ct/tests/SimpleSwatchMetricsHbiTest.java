package com.redhat.swatch.hbi.events.ct.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void testUnleashFlagToggling() {
    unleash.enableFlag(EMIT_EVENTS);
    assertTrue(unleash.isFlagEnabled(EMIT_EVENTS));
    unleash.disableFlag(EMIT_EVENTS);
    assertFalse(unleash.isFlagEnabled(EMIT_EVENTS));
  }

}
