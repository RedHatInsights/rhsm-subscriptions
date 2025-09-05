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
package com.redhat.swatch.hbi.events.test.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.hbi.events.repository.HbiEventOutbox;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;

@ApplicationScoped
public class HbiEventOutboxTestHelper {

  public HbiEventOutbox createHbiEventOutbox(String orgId) {
    HbiEventOutbox entity = new HbiEventOutbox();
    entity.setOrgId(orgId);
    Event event = new Event();
    event.setOrgId(orgId);
    event.setEventSource("HBI_HOST");
    event.setEventType("test");
    event.setServiceType("RHEL System");
    event.setInstanceId(UUID.randomUUID().toString());
    event.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
    entity.setSwatchEventJson(event);
    return entity;
  }

  public void assertHbiEventOutboxEquals(HbiEventOutbox expected, HbiEventOutbox actual) {
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getOrgId(), actual.getOrgId());
    assertEquals(expected.getCreatedOn(), actual.getCreatedOn());
    assertEquals(expected.getSwatchEventJson(), actual.getSwatchEventJson());
  }
}
