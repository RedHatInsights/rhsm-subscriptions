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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import org.candlepin.subscriptions.json.Event;

public class MessageValidators {

  public static DefaultMessageValidator<HbiEvent> hbiEventEquals(
      HbiHostCreateUpdateEvent hbiEvent) {
    return new DefaultMessageValidator<>(
        event -> {
          try {
            var mapper = JsonUtils.getObjectMapper();
            var expected = mapper.valueToTree(hbiEvent);
            var actual = mapper.valueToTree(event);
            return expected.equals(actual);
          } catch (Exception e) {
            return false;
          }
        },
        HbiEvent.class);
  }

  public static DefaultMessageValidator<Event> swatchEventEquals(Event swatchEvent) {
    return new DefaultMessageValidator<>(
        event -> {
          try {
            ObjectMapper mapper = JsonUtils.getObjectMapper();
            ObjectNode expected = mapper.valueToTree(swatchEvent).deepCopy();
            ObjectNode actual = mapper.valueToTree(event).deepCopy();

            normalizeSwatchEventNode(expected);
            normalizeSwatchEventNode(actual);

            return expected.equals(actual);
          } catch (Exception e) {
            return false;
          }
        },
        Event.class);
  }

  private static void normalizeSwatchEventNode(ObjectNode node) {
    // Drop volatile fields
    node.remove("timestamp");
    node.remove("expiration");
    node.remove("last_seen");
    node.remove("lastSeen");

    // Remove null-valued fields
    List<String> toRemove = new ArrayList<>();
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String f = it.next();
      JsonNode v = node.get(f);
      if (v == null || v.isNull()) {
        toRemove.add(f);
      }
    }
    node.remove(toRemove);

    // Sort arrays where order is not semantically relevant
    sortTextArray(node, "product_ids");
    sortTextArray(node, "productIds");
    sortTextArray(node, "product_tag");
    sortTextArray(node, "productTag");
    sortMeasurements(node, "measurements");
  }

  private static void sortTextArray(ObjectNode node, String fieldName) {
    JsonNode n = node.get(fieldName);
    if (n != null && n.isArray()) {
      List<JsonNode> items = new ArrayList<>();
      n.forEach(items::add);
      items.sort(Comparator.comparing(JsonNode::asText));
      ArrayNode newArr = node.arrayNode();
      for (JsonNode it : items) {
        newArr.add(it);
      }
      node.set(fieldName, newArr);
    }
  }

  private static void sortMeasurements(ObjectNode node, String fieldName) {
    JsonNode n = node.get(fieldName);
    if (n != null && n.isArray()) {
      List<JsonNode> items = new ArrayList<>();
      n.forEach(items::add);
      Function<JsonNode, String> key =
          m -> {
            if (m.has("metric_id")) {
              return m.get("metric_id").asText();
            }
            if (m.has("metricId")) {
              return m.get("metricId").asText();
            }
            return m.toString();
          };
      items.sort(Comparator.comparing(key));
      ArrayNode newArr = node.arrayNode();
      for (JsonNode it : items) {
        newArr.add(it);
      }
      node.set(fieldName, newArr);
    }
  }
}
