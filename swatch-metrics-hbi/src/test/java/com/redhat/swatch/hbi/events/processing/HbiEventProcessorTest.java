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
package com.redhat.swatch.hbi.events.processing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.normalization.model.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestData;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HbiEventProcessorTest {

  @Inject ObjectMapper objectMapper;
  @Inject ApplicationConfiguration config;
  @Inject HbiEventProcessor processor;
  @Inject HbiEventTestHelper hbiEventHelper;

  @Test
  void processReturnsEmptyListOfEventsWhenHbiEventIsSkipped() {
    var hbiHostEvent =
        hbiEventHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    // Force the 'host_type' system profile fact to 'edge' so that it will be skipped.
    hbiHostEvent.getHost().getSystemProfile().put(SystemProfileFacts.HOST_TYPE_FACT, "edge");
    assertTrue(processor.process(hbiHostEvent).isEmpty());
  }

  @Test
  @Transactional
  void testCreateUpdateEventIsSupported() {
    HbiHostCreateUpdateEvent event =
        hbiEventHelper.createTemplatedGuestCreatedEvent(
            "org123", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());
    assertNotNull(processor.process(event));
  }

  @Test
  @Transactional
  void testDeleteEventIsSupported() {
    HbiHostDeleteEvent event =
        hbiEventHelper.createHostDeleteEvent("org123", UUID.randomUUID(), OffsetDateTime.now());
    assertNotNull(processor.process(event));
  }

  @Test
  void testProcessThrowsExceptionWhenHbiEventIsNotSupported() {
    assertThrows(
        UnsupportedHbiEventException.class, () -> processor.process(new NotSupportedHbiEvent()));
  }

  private static class NotSupportedHbiEvent extends HbiEvent {}
}
