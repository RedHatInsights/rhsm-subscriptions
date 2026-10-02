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
package com.redhat.swatch.component.tests.api.hbi;

import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;

/** Entry point for publishing HBI events in component tests without database insertion. */
public class HbiEventManager {
  private final KafkaBridgeService kafkaBridge;

  public HbiEventManager(KafkaBridgeService kafkaBridge) {
    this.kafkaBridge = kafkaBridge;
  }

  /**
   * Start building a host for event publishing. The returned builder has no database connector;
   * call {@link HostBuilder#build()} rather than {@code insert()}.
   */
  public HostBuilder createHost(String orgId) {
    return new HostBuilder(null, new Host(orgId));
  }

  /**
   * Create an event builder from a host snapshot previously obtained via {@link
   * HostBuilder#build()}. The builder captures a stable inventory ID so that create, update, and
   * delete events built from the same instance share the same UUID.
   */
  public HbiEventBuilder event(Host host) {
    return new HbiEventBuilder(host);
  }

  /**
   * Publish the event to {@code platform.inventory.events} and return it so callers can pass it
   * directly to SwatchEventHelper for expectation construction.
   */
  public HbiEvent publish(HbiEvent event) {
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, event);
    return event;
  }
}
