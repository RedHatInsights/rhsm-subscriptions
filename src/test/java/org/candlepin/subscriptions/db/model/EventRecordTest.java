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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EventRecordTest {

  @Autowired ObjectMapper objectMapper;

  @Test
  void testJsonOptionalVsNull() throws JsonProcessingException {
    String testData =
        "{\"event_id\":\"99f6b275-6031-4967-84b6-147bd0191474\",\"display_name\":null,\"conversion\":false}";
    EventRecord eventRecord = objectMapper.readValue(testData, EventRecord.class);
    Event event = eventRecord.getEvent();
    assertNotNull(event.getDisplayName());
    assertFalse(event.getDisplayName().isPresent());
    assertNull(event.getInventoryId());
    assertEquals(testData, objectMapper.writeValueAsString(eventRecord.getEvent()));
  }
}
