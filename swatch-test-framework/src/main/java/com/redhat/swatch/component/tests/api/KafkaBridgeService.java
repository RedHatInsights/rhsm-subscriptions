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

import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import io.restassured.config.EncoderConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class KafkaBridgeService extends RestService {

  private static final String CONTENT_TYPE = "application/vnd.kafka.json.v2+json";
  private static final String CONSUMER_GROUP = "component-tests";

  // Consumers by topic
  private final Map<String, String> consumers = new HashMap<>();

  public KafkaBridgeService subscribeToTopic(String topic) {
    consumers.put(topic, UUID.randomUUID().toString());
    return this;
  }

  @Override
  public void start() {
    super.start();
    for (var consumer : consumers.entrySet()) {
      createConsumerForTopic(consumer.getKey(), consumer.getValue());
    }
  }

  @Override
  public void stop() {
    for (String consumer : consumers.values()) {
      deleteConsumer(consumer);
    }

    super.stop();
  }

  public void produceKafkaMessage(String topic, Object value) {
    produceKafkaMessage(topic, null, value);
  }

  public void produceKafkaMessage(String topic, String key, Object value) {
    var data = Map.of("records", List.of(Map.of("key", key, "value", value)));

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

  public void waitForKafkaMessage(
      String topic, Predicate<String> messageValidator, int expectedCount) {
    String consumer = consumers.get(topic);
    if (consumer == null) {
      throw new IllegalArgumentException("No consumer for topic " + topic);
    }
    AwaitilityUtils.untilIsTrue(
        () -> {
          Response response =
              given()
                  .accept(CONTENT_TYPE)
                  .when()
                  .get("/consumers/" + CONSUMER_GROUP + "/instances/" + consumer + "/records");

          if (response.getStatusCode() == 200) {
            String responseBody = response.getBody().asString();
            // Parse the response to count the actual messages
            try {
              List<Map<String, Object>> records =
                  JsonUtils.getObjectMapper().readValue(responseBody, List.class);
              // Check if we have exactly the expected number of messages
              if (records.size() >= expectedCount) {
                // If expected count is 0, we don't need to validate message content
                if (expectedCount == 0) {
                  return true;
                }
                // Otherwise, validate the message content using the provided validator
                return messageValidator.test(responseBody);
              }
              return false;
            } catch (Exception e) {
              Log.debug(this, "Failed to parse Kafka response: %s", e.getMessage());
              return false;
            }
          }

          return false;
        },
        AwaitilitySettings.defaults().withService(this));
  }

  private void deleteConsumer(String consumerInstance) {
    Log.debug(this, "Deleting consumer: %s", consumerInstance);
    given()
        .when()
        .delete("/consumers/" + CONSUMER_GROUP + "/instances/" + consumerInstance)
        .then()
        .statusCode(204);
  }

  private void createConsumerForTopic(String topic, String consumerInstance) {
    Log.debug(this, "Creating consumer for topic '%s': %s", topic, consumerInstance);
    createConsumerGroup(consumerInstance);
    subscribeToTopic(consumerInstance, topic);
  }

  private void createConsumerGroup(String consumerInstance) {
    given()
        .contentType(CONTENT_TYPE)
        .body(
            Map.of(
                "name", consumerInstance,
                "format", "json",
                "auto.offset.reset", "latest"))
        .when()
        .post("/consumers/" + CONSUMER_GROUP)
        .then()
        .statusCode(200);
  }

  private void subscribeToTopic(String consumerInstance, String topic) {
    given()
        .contentType(CONTENT_TYPE)
        .body(Map.of("topics", List.of(topic)))
        .when()
        .post("/consumers/" + CONSUMER_GROUP + "/instances/" + consumerInstance + "/subscription")
        .then()
        .statusCode(204);
  }
}
