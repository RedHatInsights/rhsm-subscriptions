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

import com.google.common.collect.Sets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;

public class UsageConflictTracker {

  private Map<UsageConflictKey, Event> keyToLatestEvent;

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
    Set<String> tags = event.getProductTag();
    Set<Measurement> measurements = new HashSet<>(event.getMeasurements());

    Set<List<Object>> tuples = Sets.cartesianProduct(tags, measurements);
    tuples.forEach(
        tuple -> {
          String tag = (String) tuple.get(0);
          Measurement measurement = (Measurement) tuple.get(1);
          UsageConflictKey key = new UsageConflictKey(tag, measurement.getMetricId());
          if (!keyToLatestEvent.containsKey(key)) {
            keyToLatestEvent.put(key, event);
          } else {
            // If record date is null, we prefer that events. This can happen if a non-persisted
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
        });
  }
}
