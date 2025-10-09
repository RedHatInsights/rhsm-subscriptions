package com.redhat.swatch.hbi.events.ct.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.swatch.component.tests.api.MessageValidator;
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
  public static MessageValidator<HbiEvent> eventMatches(String type) {
    return new MessageValidator<>(event -> type.equals(event.getType()), HbiEvent.class);
  }

  public static MessageValidator<HbiEvent> eventMatches(String type, String requestId) {
    return new MessageValidator<>(event ->
        type.equals(event.getType()) && requestId.equals(event.getMetadata().getRequestId()),
        HbiEvent.class);
  }

  public static MessageValidator<HbiEvent> hbiEventEquals(HbiHostCreateUpdateEvent hbiEvent) {
    return new MessageValidator<>(
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

  public static MessageValidator<Event> swatchEventEquals(Event swatchEvent) {
    return new MessageValidator<>(
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
            if (m.has("metric_id")) return m.get("metric_id").asText();
            if (m.has("metricId")) return m.get("metricId").asText();
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
