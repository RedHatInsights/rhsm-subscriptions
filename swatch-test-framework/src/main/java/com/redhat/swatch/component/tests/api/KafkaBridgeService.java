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
  private static final String CONSUMER_GROUP = "component-tests-" + UUID.randomUUID().toString();

  // Consumers by topic
  private final Map<String, String> consumers = new HashMap<>();

  // Background scheduler to keep consumers alive
  private ScheduledExecutorService keepAliveScheduler;

  // In-memory cache of all messages received by topic
  private final Map<String, CopyOnWriteArrayList<Object>> messageCache = new ConcurrentHashMap<>();

  public KafkaBridgeService subscribeToTopic(String topic) {
    consumers.put(topic, UUID.randomUUID().toString());
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
    String consumer = consumers.get(topic);
    if (consumer == null) {
      throw new IllegalArgumentException("No consumer for topic " + topic);
    }

    Log.debug(this, "Waiting for %d messages in topic %s", expectedCount, topic);

    AwaitilityUtils.untilIsTrue(
        () -> {
          try {
            // First check cached messages
            CopyOnWriteArrayList<Object> cachedMessages = messageCache.get(topic);
            List<KafkaMessage<V>> allTypedMessages = new ArrayList<>();

            if (cachedMessages != null && !cachedMessages.isEmpty()) {
              Log.debug(this, "Found %d cached messages in topic %s", cachedMessages.size(), topic);

              // Convert cached messages to typed messages
              for (Object rawMessage : cachedMessages) {
                try {
                  String messageJson = JsonUtils.getObjectMapper().writeValueAsString(rawMessage);
                  TypeFactory typeFactory = JsonUtils.getObjectMapper().getTypeFactory();
                  JavaType messageType =
                      typeFactory.constructParametricType(KafkaMessage.class, validator.getType());
                  KafkaMessage<V> typedMessage =
                      JsonUtils.getObjectMapper().readValue(messageJson, messageType);
                  allTypedMessages.add(typedMessage);
                } catch (Exception e) {
                  Log.debug(this, "Failed to parse cached message: %s", e.getMessage());
                }
              }
            }

            // If we don't have enough cached messages, poll directly for more
            if (allTypedMessages.size() < expectedCount) {
              Log.debug(
                  this,
                  "Not enough cached messages (%d < %d), polling directly",
                  allTypedMessages.size(),
                  expectedCount);

              try {
                Response response =
                    given()
                        .accept(CONTENT_TYPE)
                        .queryParam("timeout", 2000) // 2 second timeout for direct poll
                        .when()
                        .get(
                            "/consumers/" + CONSUMER_GROUP + "/instances/" + consumer + "/records");

                if (response.getStatusCode() == 200) {
                  String responseBody = response.getBody().asString();
                  List<Map<String, Object>> rawMessages = parseRawMessages(responseBody);

                  if (!rawMessages.isEmpty()) {
                    Log.debug(this, "Found %d new messages from direct poll", rawMessages.size());

                    // Convert new messages to typed messages
                    for (Object rawMessage : rawMessages) {
                      try {
                        String messageJson =
                            JsonUtils.getObjectMapper().writeValueAsString(rawMessage);
                        TypeFactory typeFactory = JsonUtils.getObjectMapper().getTypeFactory();
                        JavaType messageType =
                            typeFactory.constructParametricType(
                                KafkaMessage.class, validator.getType());
                        KafkaMessage<V> typedMessage =
                            JsonUtils.getObjectMapper().readValue(messageJson, messageType);
                        allTypedMessages.add(typedMessage);
                      } catch (Exception e) {
                        Log.debug(this, "Failed to parse direct poll message: %s", e.getMessage());
                      }
                    }
                  }
                }
              } catch (Exception e) {
                Log.debug(this, "Direct poll failed: %s", e.getMessage());
              }
            }

            Log.debug(this, "Total messages available: %d", allTypedMessages.size());

            // Validate messages
            int validCount = 0;
            for (var message : allTypedMessages) {
              if (validator.test(message.getValue())) {
                validCount++;
                Log.debug(this, "Valid message found: %s", message.getValue());
              }
            }

            Log.debug(
                this,
                "Found %d valid messages out of %d total",
                validCount,
                allTypedMessages.size());

            // Return true if we have enough valid messages
            boolean hasEnoughMessages = validCount >= expectedCount;
            if (hasEnoughMessages) {
              Log.info(
                  this, "Found sufficient valid messages (%d >= %d)", validCount, expectedCount);
            }

            return hasEnoughMessages;

          } catch (Exception e) {
            Log.debug(this, "Error checking messages: %s", e.getMessage());
            return false;
          }
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
                "name",
                consumerInstance,
                "format",
                "json",
                "auto.offset.reset",
                "earliest",
                "enable.auto.commit",
                true,
                "consumer.request.timeout.ms",
                30000))
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
                    List<Map<String, Object>> rawMessages = parseRawMessages(responseBody);
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
                }

                Log.trace(
                    this, "Keep-alive poll for consumer %s (topic: %s)", consumerInstance, topic);

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
        2,
        TimeUnit.SECONDS);
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
  private List<Map<String, Object>> parseRawMessages(String responseBody) {
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

  private Map<String, Object> buildMessage(String key, Object value) {
    Map<String, Object> message = new HashMap<>();
    message.put("value", value);
    if (key != null) {
      message.put("key", key);
    }
    return message;
  }
}
