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
package api;

import com.redhat.swatch.component.tests.api.MessageValidator;
import org.candlepin.subscriptions.json.Event;

public final class MessageValidators {

  private MessageValidators() {}

  public static MessageValidator<String, Event> isEventForInstance(
      String instanceId, String metricId) {
    return new MessageValidator<>(
        (key, event) ->
            event != null
                && event.getInstanceId().equals(instanceId)
                && event.getMeasurements().stream().anyMatch(m -> m.getMetricId().equals(metricId)),
        String.class,
        Event.class);
  }

  public static MessageValidator<String, Event> isEventForOrgId(String orgId) {
    return new MessageValidator<>(
        (key, event) -> event != null && event.getOrgId().equals(orgId), String.class, Event.class);
  }
}
