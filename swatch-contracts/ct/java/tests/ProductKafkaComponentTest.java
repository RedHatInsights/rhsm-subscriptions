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
import com.redhat.swatch.contract.test.model.OperationalProductEvent.EventTypeEnum;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.ProductCategoryEnum;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProductKafkaComponentTest extends BaseContractComponentTest {

  @BeforeAll
  static void enableProductServiceConsumer() {
    unleash.enableProductServiceConsumerBothConsumers();
  }

  @AfterEach
  void restoreProductServiceConsumerFlag() {
    unleash.enableProductServiceConsumerBothConsumers();
  }

  @TestPlanName("product-kafka-TC001")
  @Test
  void shouldProcessValidParentSkuMessage() {
    // Given: A valid parent SKU operational product event
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Message is consumed successfully
    thenMessageIsConsumedWithChildSkuFlag(event, false, "kafka");
  }

  @TestPlanName("product-kafka-TC002")
  @Test
  void shouldProcessValidChildSkuMessage() {
    // Given: A valid child SKU operational product event (starts with "SVC")
    OperationalProductEvent event = givenChildSkuEvent("SVC" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Message is consumed with childSku flag as true
    thenMessageIsConsumedWithChildSkuFlag(event, true, "kafka");
  }

  @TestPlanName("product-kafka-TC003")
  @Test
  void shouldHandleMalformedJsonMessage() {
    // Given: A malformed JSON message and baseline consume log count
    String malformedJson = "not-valid-json";
    int consumeLogsBefore = countLogsContaining("IT Product message consumed: source=kafka");

    // When: Publishing malformed message to Kafka
    kafkaBridge.produceKafkaMessage(IT_PRODUCT_SYNC, malformedJson);

    // Then: Warning is logged, the bad payload is not consumed, and the consumer continues
    service.logs().assertContains("Unable to read IT Product Kafka message from JSON");
    assertEquals(
        consumeLogsBefore,
        countLogsContaining("IT Product message consumed: source=kafka"),
        "Malformed JSON must not produce a consume log");
    thenKafkaConsumerAcceptsSubsequentMessages();
  }

  @TestPlanName("product-kafka-TC004")
  @Test
  void shouldIgnoreNullMessage() {
    // Given: Baseline consume and parse-error log counts
    int consumeLogsBefore = countLogsContaining("IT Product message consumed: source=kafka");
    int parseErrorLogsBefore =
        countLogsContaining("Unable to read IT Product Kafka message from JSON");

    // When: Publishing a null message to Kafka
    kafkaBridge.produceKafkaMessage(IT_PRODUCT_SYNC, null);

    // Then: Null is ignored; the consumer continues processing
    thenKafkaConsumerAcceptsSubsequentMessages();
    assertEquals(
        consumeLogsBefore + 1,
        countLogsContaining("IT Product message consumed: source=kafka"),
        "Null message must not produce a consume log");
    assertEquals(
        parseErrorLogsBefore,
        countLogsContaining("Unable to read IT Product Kafka message from JSON"),
        "Null message must not be treated as malformed JSON");
  }

  @TestPlanName("product-kafka-TC005")
  @Test
  void shouldIgnoreMessageWhenFeatureFlagDisabled() {
    // Given: Feature flag is disabled and a valid parent SKU event
    unleash.disableProductServiceConsumer();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Consumer is disabled and the message is not processed
    thenKafkaConsumerReportsDisabled();
    thenMessageIsNotConsumed(event, "kafka");
  }

  @TestPlanName("product-kafka-TC006")
  @Test
  void shouldIgnoreMessageWhenKafkaDisabledViaVariant() {
    // Given: Flag enabled with Kafka disabled and UMB enabled via variant
    unleash.enableProductServiceConsumerUmbOnly();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Kafka is independently disabled
    thenKafkaConsumerReportsDisabled();
    thenMessageIsNotConsumed(event, "kafka");
  }

  @TestPlanName("product-kafka-TC007")
  @Test
  void shouldProcessWhenKafkaEnabledAndUmbDisabled() {
    // Given: Flag enabled with Kafka enabled and UMB disabled via variant
    unleash.enableProductServiceConsumerKafkaOnly();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Kafka consumes independently
    thenMessageIsConsumedWithChildSkuFlag(event, false, "kafka");
  }

  @TestPlanName("product-kafka-TC008")
  @Test
  void shouldProcessMessageWhenBothConsumersEnabled() {
    // Given: Flag enabled with both Kafka and UMB consumers enabled via variant
    unleash.enableProductServiceConsumerBothConsumers();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Kafka consumes
    thenMessageIsConsumedWithChildSkuFlag(event, false, "kafka");
  }

  private OperationalProductEvent givenParentSkuEvent(String productCode) {
    return givenProductEvent(productCode, ProductCategoryEnum.PARENT_SKU);
  }

  private OperationalProductEvent givenChildSkuEvent(String productCode) {
    return givenProductEvent(productCode, ProductCategoryEnum.CHILD_SKU);
  }

  private OperationalProductEvent givenProductEvent(
      String productCode, ProductCategoryEnum category) {
    OperationalProductEvent event = new OperationalProductEvent();
    event.setProductCode(productCode);
    event.setProductCategory(category);
    event.setEventType(EventTypeEnum.UPDATE);
    event.setOccurredOn(OffsetDateTime.now().toString());
    return event;
  }

  private void whenKafkaMessageIsPublished(OperationalProductEvent event) {
    kafkaBridge.produceKafkaMessage(IT_PRODUCT_SYNC, event);
  }

  private void thenKafkaConsumerAcceptsSubsequentMessages() {
    OperationalProductEvent probe = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    whenKafkaMessageIsPublished(probe);
    thenMessageIsConsumedWithChildSkuFlag(probe, false, "kafka");
  }

  private void thenKafkaConsumerReportsDisabled() {
    service.logs().assertContains("IT Product Kafka consumer is disabled by feature flag.");
  }

  private void thenMessageIsNotConsumed(OperationalProductEvent event, String source) {
    service
        .logs()
        .assertDoesNotContain("source=" + source + ", productCode=" + event.getProductCode());
  }

  private void thenMessageIsConsumedWithChildSkuFlag(
      OperationalProductEvent event, boolean isChildSku, String source) {
    service
        .logs()
        .assertContains(
            String.format(
                "IT Product message consumed: source=%s, productCode=%s, eventType=%s, "
                    + "productCategory=%s, childSku=%s",
                source,
                event.getProductCode(),
                event.getEventType(),
                event.getProductCategory(),
                isChildSku));
  }

  private int countLogsContaining(String text) {
    return (int) service.getLogs().stream().filter(line -> line.contains(text)).count();
  }
}
