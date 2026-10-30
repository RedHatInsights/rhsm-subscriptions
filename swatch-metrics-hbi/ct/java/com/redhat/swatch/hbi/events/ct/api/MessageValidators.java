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
package com.redhat.swatch.hbi.events.ct.api;

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper;
import org.candlepin.subscriptions.json.Event;

public class MessageValidators {

  /**
   * Minimal validator that matches SWatch events by key fields only (orgId, instanceId, eventType).
   * Use this to retrieve events, then assert on specific fields for better error messages.
   */
  public static DefaultMessageValidator<Event> swatchEventMatches(
      String orgId, String instanceId, String eventType) {
    return new DefaultMessageValidator<>(
        event ->
            orgId.equals(event.getOrgId())
                && instanceId.equals(event.getInstanceId())
                && eventType.equals(event.getEventType()),
        Event.class);
  }

  public static DefaultMessageValidator<Event> swatchEventEquals(Event swatchEvent) {
    return new DefaultMessageValidator<>(
        event -> SwatchEventHelper.eventsMatch(swatchEvent, event), Event.class);
  }
}
