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
package com.redhat.swatch.hbi.events.ct.tests;

import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.HbiEventHelper;
import com.redhat.swatch.hbi.events.ct.SwatchEventHelper;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreateUpdateEventIngestionTest extends BaseSMHBIComponentTest {

  @BeforeEach
  void setupTest() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterEach
  void teardown() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  /**
   * Verify service accepts HBI Create/Update events for a physical x86 host and produce the
   * expected Swatch Event messages.
   *
   * <p>test_steps: 1. Toggle feature flag to allow service to emit swatch events. 2. Send a
   * created/updated message to the HBI event topic to simulate that a host was created in HBI.
   * expected_results: 1. The swatch-metrics-hbi service will ingest the event and should create an
   * outbox record for a Swatch Event message containing the measurements for the host represented
   * by the HBI event. NOTE: We expect the same result regardless of whether the HBI event was host
   * created OR host updated.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiRhsmHostEvent(String hbiEventType, String swatchEventType) {
    HbiHostCreateUpdateEvent hbiEvent =
        HbiEventHelper.getRhsmHostEvent(
            hbiEventType,
            List.of("69"),
            false,
            OffsetDateTime.now(ZoneOffset.UTC),
            "Self-Support",
            "Development/Test",
            2,
            2);

    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(hbiEvent, List.of("69"), Set.of("RHEL for x86"));

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
  }
}
