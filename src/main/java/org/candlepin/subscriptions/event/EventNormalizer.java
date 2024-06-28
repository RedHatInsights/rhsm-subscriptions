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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Normalizes an incoming event into potentially multiple {@link
 * org.candlepin.subscriptions.json.Event}s that represent a single unit of usage. Tags and
 * measurement collections are flattened and events created from each combination.
 */
@Slf4j
@Component
public class EventNormalizer {

  private final EventMapper eventMapper;

  @Autowired
  public EventNormalizer(EventMapper eventMapper) {
    this.eventMapper = eventMapper;
  }

  public List<Event> normalize(Event toNormalize) {
    Set<String> tags = toNormalize.getProductTag();
    Set<Measurement> measurements = new HashSet<>(toNormalize.getMeasurements());

    Set<List<Object>> tuples = Sets.cartesianProduct(tags, measurements);
    if (tuples.size() <= 1) {
      return List.of(toNormalize);
    }

    List<Event> normalized = new ArrayList<>();
    tuples.forEach(
        tuple -> {
          String tag = (String) tuple.get(0);
          Measurement measurement = (Measurement) tuple.get(1);
          Event normalizedEvent = new Event();
          eventMapper.toUnpersistedWithoutMeasurements(normalizedEvent, toNormalize);
          normalizedEvent.setProductTag(Set.of(tag));
          normalizedEvent.setMeasurements(List.of(measurement));
          normalized.add(normalizedEvent);
        });
    return normalized;
  }
}
