package com.redhat.swatch.hbi.events.ct.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.HbiEventHelper;
import com.redhat.swatch.hbi.events.ct.SwatchEventHelper;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.Host;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.services.FeatureFlags;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
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
        OffsetDateTime.now(ZoneOffset.UTC),
        "Self-Support",
        "Development/Test",
        2,
        2);

    Event swatchEvent = SwatchEventHelper.createExpectedEvent(
        hbiEvent,
        List.of("69"),
        Set.of("RHEL for x86"));

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);
    kafkaBridge.waitForKafkaMessage(Topics.HBI_EVENT_IN,
        MessageValidators.hbiEventEquals(hbiEvent),
        1);
    
    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
        
  }

}