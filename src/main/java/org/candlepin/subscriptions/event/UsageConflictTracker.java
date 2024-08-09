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
package org.candlepin.subscriptions.event;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.candlepin.subscriptions.json.Event;

/**
 * Tracks the latest event conflicts based on {@link UsageConflictKey}. Used by the {@link
 * EventConflictResolver} to track the latest conflicting Event based on {@link UsageConflictKey}. A
 * set of keys are created based on a tracked event's measurements and tags, and are checked against
 * the latest event mapped by usage conflict key.
 */
public class UsageConflictTracker {

  private final Map<UsageConflictKey, Event> keyToLatestEvent;

  public UsageConflictTracker(List<Event> events) {
    this.keyToLatestEvent = new HashMap<>();
    events.forEach(this::track);
  }

  public Event getLatest(UsageConflictKey key) {
    return keyToLatestEvent.get(key);
  }

  public boolean contains(UsageConflictKey key) {
    return keyToLatestEvent.containsKey(key);
  }

  public void track(Event event) {
    UsageConflictKey key = getConflictKeyForEvent(event);
    if (!keyToLatestEvent.containsKey(key)) {
      keyToLatestEvent.put(key, event);
    } else {
      // If record date is null, we prefer that event. This can happen if a non-persisted
      // event is tracked (i.e. an incoming event).
      Optional<OffsetDateTime> eventRecordDate = Optional.ofNullable(event.getRecordDate());
      Optional<OffsetDateTime> latestEventRecordDate =
          Optional.ofNullable(keyToLatestEvent.get(key).getRecordDate());
      if (eventRecordDate.isEmpty()
          || (latestEventRecordDate.isPresent()
              && eventRecordDate.get().isAfter(latestEventRecordDate.get()))) {
        keyToLatestEvent.put(key, event);
      }
    }
  }

  public UsageConflictKey getConflictKeyForEvent(Event event) {
    if (event.getMeasurements().size() > 1) {
      throw new IllegalStateException("An ingested event should only have a single measurement.");
    }

    if (event.getProductTag().size() > 1) {
      throw new IllegalStateException("An ingested event should only have a single product tag.");
    }
    return new UsageConflictKey(
        event.getProductTag().stream().findFirst().get(),
        UsageConflictKey.getMetricId(event.getMeasurements().get(0)));
  }
}
