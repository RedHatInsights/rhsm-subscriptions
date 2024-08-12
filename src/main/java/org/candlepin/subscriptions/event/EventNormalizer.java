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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.springframework.stereotype.Component;

/**
 * Defines the logic for flattening an Event into multiple events based on a (tag,measurement)
 * tuple. This ensures that any event we ingest only contains a single tag and metric, even if the
 * source sends multiple.
 *
 * <pre>
 *   Incoming event:
 *      Event(['tag1', 'tag2'], {cores: 2, instance-hours: 4}
 *   Normalized events:
 *      Event(['tag1'], {cores: 2})
 *      Event(['tag1'], {instance-hours: 4})
 *      Event(['tag2'], {cores: 2})
 *      Event(['tag2'], {instance-hours: 4})
 * </pre>
 */
@Component
public class EventNormalizer {

  private static final String ANSIBLE_INFRASTRUCTURE_HOUR = "Ansible Infrastructure Hour";

  private final ResolvedEventMapper resolvedEventMapper;

  public EventNormalizer(ResolvedEventMapper resolvedEventMapper) {
    this.resolvedEventMapper = resolvedEventMapper;
  }

  public List<Event> flattenEventUsage(Event event) {
    Set<String> tags = event.getProductTag();
    Set<Measurement> measurements = new HashSet<>(event.getMeasurements());
    return Sets.cartesianProduct(tags, measurements).stream()
        .map(tuple -> create(event, (String) tuple.get(0), (Measurement) tuple.get(1)))
        .toList();
  }

  public Event normalizeEvent(Event event) {
    // NOTE we will probably remove the below serviceType normalization
    // after https://issues.redhat.com/browse/SWATCH-2533
    // placeholder card to remove it in https://issues.redhat.com/browse/SWATCH-2794
    if (Objects.nonNull(event.getServiceType())
        && event.getServiceType().equals(ANSIBLE_INFRASTRUCTURE_HOUR)) {
      event.setServiceType("Ansible Managed Node");
    }
    // normalize UOM to metric_id
    event
        .getMeasurements()
        .forEach(
            measurement -> {
              if (measurement.getUom() != null) {
                measurement.setMetricId(measurement.getUom());
                measurement.setUom(null);
              }
            });
    return event;
  }

  private Event create(Event from, String tag, Measurement measurement) {
    Event target = new Event();
    resolvedEventMapper.copy(target, from);
    target.setProductTag(Set.of(tag));
    target.setMeasurements(List.of(measurement));
    return target;
  }
}
