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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KafkaBridgeService extends RestService {

  private static final String CONTENT_TYPE = "application/vnd.kafka.json.v2+json";
  private static final String CONSUMER_GROUP = "component-tests";

  // Consumers by topic
  private final Map<String, String> consumers = new HashMap<>();

  // Background scheduler to keep consumers alive
  private ScheduledExecutorService keepAliveScheduler;

  // In-memory cache of all messages received by topic
  private final Map<String, CopyOnWriteArrayList<Object>> messageCache = new ConcurrentHashMap<>();

  public KafkaBridgeService subscribeToTopic(String topic) {
    String consumerId = UUID.randomUUID().toString();
    // If service is already running, create consumer for topic
    if (isRunning()) {
      createConsumerForTopic(topic, consumerId);
    }

    // Register it to keep track of consumers for this topic
    consumers.put(topic, consumerId);
    // Initialize message cache for this topic
    messageCache.put(topic, new CopyOnWriteArrayList<>());
    return this;
  }

  @Override
  public void start() {
    super.start();
    for (var consumer : consumers.entrySet()) {
      createConsumerForTopic(consumer.getKey(), consumer.getValue());
    }

    // Start background keep-alive process
    startConsumerKeepAlive();

    Log.debug(this, "KafkaBridge service fully initialized");
  }

  @Override
  public void stop() {
    // Stop background keep-alive process
    stopConsumerKeepAlive();

    for (String consumer : consumers.values()) {
      deleteConsumer(consumer);
    }

    super.stop();
  }

  public void produceKafkaMessage(String topic, Object value) {
    produceKafkaMessage(topic, null, value);
  }

  public void produceKafkaMessage(String topic, Object key, Object value) {
    Log.debug(this, "Sending kafka message to topic '%s': %s", topic, value);
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

  /**
   * Waits for a single Kafka message that matches the validator and returns it.
   *
   * @param topic The Kafka topic to wait for messages from
   * @param validator The message validator containing the filter and type information
   * @return The first message that matches the validator
   * @throws IllegalArgumentException if no consumer exists for the topic
   */
  public <K, V> V waitForKafkaMessage(String topic, MessageValidator<K, V> validator) {
    List<V> messages = waitForKafkaMessage(topic, validator, 1);
    return messages.isEmpty() ? null : messages.get(0);
  }

  /**
   * Waits for multiple Kafka messages that match the validator and returns them.
   *
   * @param topic The Kafka topic to wait for messages from
   * @param validator The message validator containing the filter and type information
   * @param expectedCount The number of messages to wait for
   * @return A list of messages that match the validator
   * @throws IllegalArgumentException if no consumer exists for the topic
   */
  public <K, V> List<V> waitForKafkaMessage(
      String topic, MessageValidator<K, V> validator, int expectedCount) {
    return waitForKafkaMessage(topic, validator, expectedCount, AwaitilitySettings.defaults());
  }

  /**
   * Waits for multiple Kafka messages that match the validator and returns them with custom
   * awaitility settings.
   *
   * @param topic The Kafka topic to wait for messages from
   * @param validator The message validator containing the filter and type information
   * @param expectedCount The number of messages to wait for
   * @param settings Custom awaitility settings for timeout configuration
   * @return A list of messages that match the validator
   * @throws IllegalArgumentException if no consumer exists for the topic
   */
  public <K, V> List<V> waitForKafkaMessage(
      String topic,
      MessageValidator<K, V> validator,
      int expectedCount,
      AwaitilitySettings settings) {
    String consumer = consumers.get(topic);
    if (consumer == null) {
      throw new IllegalArgumentException("No consumer for topic " + topic);
    }

    Log.debug(
        this, "Waiting for %d messages in topic %s using cached messages", expectedCount, topic);

    List<V> matchedMessages = new ArrayList<>();

    AwaitilityUtils.untilIsTrue(
        () -> {
          try {
            matchedMessages.clear(); // Clear previous attempts

            // Get cached messages for this topic
            CopyOnWriteArrayList<Object> cachedMessages = messageCache.get(topic);
            if (cachedMessages == null || cachedMessages.isEmpty()) {
              Log.debug(this, "No cached messages found for topic %s", topic);
              // If expecting 0 messages and cache is empty, that's success
              return expectedCount == 0;
            }

            Log.debug(this, "Found %d cached messages in topic %s", cachedMessages.size(), topic);

            // Convert cached messages to typed messages and validate
            for (Object rawMessage : cachedMessages) {
              try {
                // Parse the raw message as KafkaMessage<V>
                String messageJson = JsonUtils.getObjectMapper().writeValueAsString(rawMessage);
                TypeFactory typeFactory = JsonUtils.getObjectMapper().getTypeFactory();
                JavaType messageType =
                    typeFactory.constructParametricType(
                        KafkaMessage.class, validator.getKeyType(), validator.getValueType());
                KafkaMessage<K, V> typedMessage =
                    JsonUtils.getObjectMapper().readValue(messageJson, messageType);
                K key = typedMessage.getKey();
                V message = typedMessage.getValue();

                if (validator.test(key, message)) {
                  matchedMessages.add(message);
                  Log.debug(this, "Valid message found: %s", message);
                }
              } catch (Exception e) {
                Log.debug(this, "Failed to parse cached message: %s", e.getMessage());
                // Continue processing other messages - invalid messages are ignored
              }
            }

            Log.debug(
                this,
                "Found %d valid messages out of %d total cached",
                matchedMessages.size(),
                cachedMessages.size());
            return matchedMessages.size() >= expectedCount;

          } catch (Exception e) {
            Log.debug(this, "Error checking cached messages: %s", e.getMessage());
            return false;
          }
        },
        settings.withService(this));

    return new ArrayList<>(matchedMessages);
  }

  private void deleteConsumer(String consumerInstance) {
    Log.debug(this, "Deleting consumer: %s", consumerInstance);
    given().when().delete("/consumers/" + CONSUMER_GROUP + "/instances/" + consumerInstance);
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
                "name",
                consumerInstance,
                "format",
                "json",
                // Prevents race condition by ensuring consumer sees messages produced before
                // consumer reconciliation
                "auto.offset.reset",
                "earliest",
                "enable.auto.commit",
                true,
                "consumer.request.timeout.ms",
                3000))
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

  /**
   * Starts a background process that polls consumers every 2 seconds to keep them alive. This
   * prevents Kafka Bridge from automatically deleting inactive consumers. Also caches all received
   * messages in memory for later retrieval.
   */
  private void startConsumerKeepAlive() {
    if (keepAliveScheduler != null) {
      stopConsumerKeepAlive();
    }

    keepAliveScheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "kafka-consumer-keepalive");
              t.setDaemon(true);
              return t;
            });

    Log.debug(
        this,
        "Starting consumer keep-alive and message caching process for %d consumers",
        consumers.size());

    keepAliveScheduler.scheduleAtFixedRate(
        () -> {
          try {
            for (Map.Entry<String, String> entry : consumers.entrySet()) {
              String topic = entry.getKey();
              String consumerInstance = entry.getValue();

              try {
                // Poll for messages to keep consumer alive AND cache messages
                Response response =
                    given()
                        .accept(CONTENT_TYPE)
                        .queryParam("timeout", 1000) // Slightly longer timeout to get messages
                        .when()
                        .get(
                            "/consumers/"
                                + CONSUMER_GROUP
                                + "/instances/"
                                + consumerInstance
                                + "/records");

                if (response.getStatusCode() == 200) {
                  String responseBody = response.getBody().asString();

                  // Parse and cache any messages received
                  try {
                    List<Map<Object, Object>> rawMessages = parseRawMessages(responseBody);
                    if (!rawMessages.isEmpty()) {
                      CopyOnWriteArrayList<Object> topicCache = messageCache.get(topic);
                      if (topicCache != null) {
                        topicCache.addAll(rawMessages);
                        Log.debug(
                            this,
                            "Cached %d new messages for topic %s (total: %d)",
                            rawMessages.size(),
                            topic,
                            topicCache.size());
                      }
                    }
                  } catch (Exception e) {
                    Log.debug(this, "Failed to parse messages for caching: %s", e.getMessage());
                  }
                } else {
                  Log.warn(
                      "Kafka Bridge: Keep-alive poll for consumer %s (topic: %s) failed with response %s",
                      consumerInstance, topic, response.getStatusCode());
                }

              } catch (Exception e) {
                Log.debug(
                    this,
                    "Keep-alive poll failed for consumer %s: %s",
                    consumerInstance,
                    e.getMessage());
              }
            }
          } catch (Exception e) {
            Log.debug(this, "Keep-alive process error: %s", e.getMessage());
          }
        },
        2,
        500,
        TimeUnit.MILLISECONDS);
  }

  /** Stops the background keep-alive process. */
  private void stopConsumerKeepAlive() {
    if (keepAliveScheduler != null) {
      Log.debug(this, "Stopping consumer keep-alive process");
      keepAliveScheduler.shutdown();
      try {
        if (!keepAliveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          keepAliveScheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        keepAliveScheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
      keepAliveScheduler = null;
    }
  }

  /** Parses raw message response from Kafka Bridge into a list of message objects. */
  private List<Map<Object, Object>> parseRawMessages(String responseBody) {
    try {
      if (responseBody == null
          || responseBody.trim().isEmpty()
          || "[]".equals(responseBody.trim())) {
        return List.of();
      }

      // Parse as list of generic objects
      TypeFactory typeFactory = JsonUtils.getObjectMapper().getTypeFactory();
      JavaType listType = typeFactory.constructCollectionType(List.class, Map.class);
      return JsonUtils.getObjectMapper().readValue(responseBody, listType);

    } catch (Exception e) {
      Log.debug(this, "Failed to parse raw messages: %s", e.getMessage());
      return List.of();
    }
  }

  private Map<String, Object> buildMessage(Object key, Object value) {
    Map<String, Object> message = new HashMap<>();
    message.put("value", value);
    if (key != null) {
      message.put("key", key);
    }

    return message;
  }
}
