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
package tests;

import static com.redhat.swatch.component.tests.utils.Topics.IT_PRODUCT_SYNC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import org.junit.jupiter.api.Test;

public class ProductKafkaComponentTest extends BaseProductConsumerComponentTest {

  @TestPlanName("product-kafka-TC001")
  @Test
  void shouldProcessValidParentSkuMessage() {
    // Given: A valid parent SKU operational product event
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Message is consumed and the offering sync service is invoked
    thenMessageIsConsumedAndSynced(event, "kafka");
  }

  @TestPlanName("product-kafka-TC002")
  @Test
  void shouldProcessValidChildSkuMessage() {
    // Given: A valid child SKU operational product event (starts with "SVC")
    OperationalProductEvent event = givenChildSkuEvent("SVC" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Message is consumed with childSku flag as true and sync is invoked
    thenMessageIsConsumedAndSynced(event, "kafka");
  }

  @TestPlanName("product-kafka-TC003")
  @Test
  void shouldHandleMalformedJsonMessage() {
    // Given: A malformed JSON message and baseline consume log count
    String malformedJson = "not-valid-json";
    int consumeLogsBefore = countLogsContaining("IT Product message consumed: source=kafka");

    // When: Publishing malformed message to Kafka
    whenMalformedKafkaMessageIsPublished(malformedJson);

    // Then: Warning is logged and the bad payload is not consumed
    service.logs().assertContains("Unable to read IT Product Kafka message from JSON");
    assertEquals(
        consumeLogsBefore,
        countLogsContaining("IT Product message consumed: source=kafka"),
        "Malformed JSON must not produce a consume log");

    // And: a subsequent valid message is still consumed
    OperationalProductEvent probe = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    whenKafkaMessageIsPublished(probe);
    thenMessageIsConsumedAndSynced(probe, "kafka");
  }

  @TestPlanName("product-kafka-TC004")
  @Test
  void shouldIgnoreNullMessage() {
    // Given: Baseline consume and parse-error log counts
    int consumeLogsBefore = countLogsContaining("IT Product message consumed: source=kafka");
    int parseErrorLogsBefore =
        countLogsContaining("Unable to read IT Product Kafka message from JSON");

    // When: Publishing a null message to Kafka
    whenNullKafkaMessageIsPublished();

    // Then: Null is ignored and a subsequent valid message is still consumed
    OperationalProductEvent probe = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    whenKafkaMessageIsPublished(probe);
    thenMessageIsConsumedAndSynced(probe, "kafka");
    assertEquals(
        consumeLogsBefore + 1,
        countLogsContaining("IT Product message consumed: source=kafka"),
        "Null message must not produce a consume log");
    assertEquals(
        parseErrorLogsBefore,
        countLogsContaining("Unable to read IT Product Kafka message from JSON"),
        "Null message must not be treated as malformed JSON");
  }

  private void whenMalformedKafkaMessageIsPublished(String malformedJson) {
    kafkaBridge.produceKafkaMessage(IT_PRODUCT_SYNC, malformedJson);
  }

  private void whenNullKafkaMessageIsPublished() {
    kafkaBridge.produceKafkaMessage(IT_PRODUCT_SYNC, null);
  }
}
