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
package com.redhat.swatch.hbi.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationComponentsProducerUnitTest {

  private final ApplicationComponentsProducer producer = new ApplicationComponentsProducer();

  @Test
  @DisplayName("Produces ApplicationClock manually")
  void producesClock() {
    ApplicationClock clock = producer.applicationClock();
    assertNotNull(clock);
    assertNotNull(clock.now());
  }

  @Test
  @DisplayName("Produces JsonFactory manually with custom mapper")
  void producesJsonFactory() {
    ObjectMapper mapper = new ObjectMapper();
    JsonFactory factory = producer.jsonFactory(mapper);
    assertNotNull(factory);
    assertSame(mapper, factory.getCodec());
  }
}
