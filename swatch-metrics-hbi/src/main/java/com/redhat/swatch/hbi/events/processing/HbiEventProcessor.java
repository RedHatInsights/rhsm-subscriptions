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

import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.processing.handlers.CreateUpdateHostHandler;
import com.redhat.swatch.hbi.events.processing.handlers.DeleteHostHandler;
import com.redhat.swatch.hbi.events.processing.handlers.HbiEventHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;

@Slf4j
@ApplicationScoped
public class HbiEventProcessor {

  private Map<Class<? extends HbiEvent>, HbiEventHandler<? extends HbiEvent>> handlers;

  public HbiEventProcessor(
      CreateUpdateHostHandler createUpdateHostHandler, DeleteHostHandler deleteHostHandler) {
    // Register any message handlers here.
    initHandlers(createUpdateHostHandler, deleteHostHandler);
  }

  @Transactional
  public <E extends HbiEvent> List<Event> process(E hbiEvent) {
    if (!supports(hbiEvent)) {
      throw new IllegalArgumentException("Unsupported HBI event: " + hbiEvent.getClass());
    }

    log.info("Processing host event from HBI - {}", hbiEvent);
    HbiEventHandler<E> handler = getHandler(hbiEvent.getClass());
    if (handler.skipEvent(hbiEvent)) {
      log.debug("Filtering HBI event: {}", hbiEvent);
      return List.of();
    }
    return handler.handleEvent(hbiEvent);
  }

  public boolean supports(HbiEvent hbiEvent) {
    return this.handlers.containsKey(hbiEvent.getClass());
  }

  private void initHandlers(HbiEventHandler<?>... eventHandlers) {
    this.handlers = new HashMap<>();
    for (HbiEventHandler<?> handler : eventHandlers) {
      this.handlers.put(handler.getHbiEventClass(), handler);
    }
  }

  @SuppressWarnings("unchecked")
  private <E extends HbiEvent> HbiEventHandler<E> getHandler(Class<?> hbiEventClass) {
    return (HbiEventHandler<E>) this.handlers.get(hbiEventClass);
  }
}
