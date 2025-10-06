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
package com.redhat.swatch.component.tests.api;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.redhat.swatch.component.tests.api.dto.KafkaMessage;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import io.restassured.config.EncoderConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

public class KafkaBridgeService extends RestService {

  private static final String CONTENT_TYPE = "application/vnd.kafka.json.v2+json";

  // Consumers by topic
  private final Set<Consumer> consumers = new HashSet<>();

  public KafkaBridgeService subscribeToTopic(String topic) {
    consumers.add(
        new Consumer(UUID.randomUUID().toString(), "component-tests-" + topic, topic)
    );
    return this;
  }

  @Override
  public void start() {
    super.start();
    consumers.forEach(this::createConsumerForTopic);
  }

  @Override
  public void stop() {
    consumers.forEach(this::deleteConsumer);
    super.stop();
  }

  public void produceKafkaMessage(String topic, Object value) {
    produceKafkaMessage(topic, null, value);
  }

  public void produceKafkaMessage(String topic, String key, Object value) {
    var data = Map.of("records", List.of(buildMessage(key, value)));

    given()
        .config(
            RestAssuredConfig.config()
                .objectMapperConfig(
                    ObjectMapperConfig.objectMapperConfig()
                        .jackson2ObjectMapperFactory((cls, charset) -> JsonUtils.getObjectMapper()))
                .encoderConfig(
                    EncoderConfig.encoderConfig()
                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
        .contentType("application/vnd.kafka.json.v2+json")
        .body(data)
        .when()
        .post("/topics/" + topic)
        .then()
        .statusCode(200);
  }

  public <V> void waitForKafkaMessage(
      String topic, MessageValidator<V> validator, int expectedCount) {
    Consumer consumer = consumers.stream().filter(c -> c.getTopic().equals(topic))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No consumer for topic " + topic));

    AwaitilityUtils.untilIsTrue(
        () -> {
          Response response =
              given()
                  .accept(CONTENT_TYPE)
                  .when()
                  .get("/consumers/" + consumer.getGroupId() + "/instances/" + consumer.getInstanceId() + "/records");

          if (response.getStatusCode() != 200) {
            throw new RuntimeException("Could not get messages from Kafka: " + response.getStatusLine());
          }
          String responseBody = response.getBody().asString();
          // Parse the response to count the actual messages
          try {
            List<KafkaMessage<V>> messages = getMessages(responseBody, validator.getType());
            // Check if we have exactly the expected number of messages
            if (messages.size() < expectedCount) {
              return false;
            }
            // If expected count is 0, we don't need to validate message content
            if (expectedCount == 0) {
              return true;
            }
            // Validate each message until a match is found.
            int validCount = 0;
            for (var message : messages) {
              if (validator.test(message.getValue())) {
                validCount++;
              }
            }
            return validCount == expectedCount;
          } catch (Exception e) {
            throw new RuntimeException("Failed to parse Kafka response: " + e.getMessage());
          }
        },
        AwaitilitySettings.defaults().doNotIgnoreExceptions().withService(this));
  }

  private void deleteConsumer(Consumer consumer) {
    Log.debug(this, "Deleting consumer: %s", consumer.getInstanceId());
    given()
        .when()
        .delete("/consumers/" + consumer.getGroupId() + "/instances/" + consumer.getInstanceId())
        .then()
        .statusCode(204);
  }

  private void createConsumerForTopic(Consumer consumer) {
    Log.info(this, "Creating consumer for topic '%s': %s", consumer.getTopic(), consumer.getInstanceId());
    createConsumerGroup(consumer);
    subscribeToTopic(consumer);
    miniDrain(consumer);
  }

  private void createConsumerGroup(Consumer consumer) {
    given()
        .contentType(CONTENT_TYPE)
        .body(
            Map.of(
                "name", consumer.getInstanceId(),
                "format", "json",
                "auto.offset.reset", "latest"))
        .when()
        .post("/consumers/" + consumer.getGroupId())
        .then()
        .statusCode(200);
  }

  private void subscribeToTopic(Consumer consumer) {
    given()
        .contentType(CONTENT_TYPE)
        .body(Map.of("topics", List.of(consumer.getTopic())))
        .when()
        .post("/consumers/" + consumer.getGroupId() + "/instances/" + consumer.getInstanceId() + "/subscription")
        .then()
        .statusCode(204);
  }

  private Map<String, Object> buildMessage(String key, Object value) {
    Map<String, Object> message = new HashMap<>();
    message.put("value", value);
    if (key != null) {
      message.put("key", key);
    }
    return message;
  }

  private <T> List<KafkaMessage<T>> getMessages(String data, Class<T> clazz) {
    TypeFactory typeFactory = JsonUtils.getObjectMapper().getTypeFactory();
    JavaType messageType = typeFactory.constructParametricType(KafkaMessage.class, clazz);
    JavaType dataType = typeFactory.constructParametricType(List.class, messageType);
    try {
      return JsonUtils.getObjectMapper().readValue(data, dataType);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Kafka response: " + e.getMessage());
    }
  }

  private void miniDrain(Consumer consumer) {
    Log.info(this, "Draining consumer: %s", consumer.getInstanceId());
    for (int i = 0; i < 5; i++) {
      given()
          .accept(CONTENT_TYPE)
          .when()
          .get("/consumers/" + consumer.getGroupId() + "/instances/" + consumer.getInstanceId() + "/records")
          .then()
          .statusCode(200);
    }

  }

  @Getter
  private class Consumer {
    private final String instanceId;
    private final String groupId;
    private final String topic;

    public Consumer(String instanceId, String groupId, String topic) {
      this.instanceId = instanceId;
      this.groupId = groupId;
      this.topic = topic;
    }
  }
}
