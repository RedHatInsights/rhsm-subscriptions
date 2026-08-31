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

import static org.junit.jupiter.api.Assertions.assertEquals;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.EventTypeEnum;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.ProductCategoryEnum;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProductUmbComponentTest extends BaseContractComponentTest {

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @BeforeAll
  static void enableProductServiceConsumer() {
    unleash.enableProductServiceConsumerBothConsumers();
  }

  @AfterEach
  void restoreProductServiceConsumerFlag() {
    unleash.enableProductServiceConsumerBothConsumers();
  }

  @TestPlanName("product-umb-TC001")
  @Test
  void shouldProcessValidParentSkuMessage() {
    // Given: A valid parent SKU operational product event
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: Message is consumed and the offering sync service is invoked
    thenMessageIsConsumedWithChildSkuFlag(event, false);
    thenSyncServiceWasInvoked(event);
  }

  @TestPlanName("product-umb-TC002")
  @Test
  void shouldProcessValidChildSkuMessage() {
    // Given: A valid child SKU operational product event (starts with "SVC")
    OperationalProductEvent event = givenChildSkuEvent("SVC" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: Message is consumed with childSku flag as true and sync is invoked
    thenMessageIsConsumedWithChildSkuFlag(event, true);
    thenSyncServiceWasInvoked(event);
  }

  @TestPlanName("product-umb-TC003")
  @Test
  void shouldHandleMalformedJsonMessage() {
    // Given: A malformed JSON message and baseline consume log count
    String malformedJson = "not-valid-json";
    int consumeLogsBefore = countLogsContaining("IT Product message consumed: source=umb");

    // When: Publishing malformed message to UMB
    artemis.forOfferings().sendMalformed(malformedJson);

    // Then: Error is logged, the bad payload is not consumed, and the consumer continues
    service.logs().assertContains("Unable to read UMB product message for JSON");
    assertEquals(
        consumeLogsBefore,
        countLogsContaining("IT Product message consumed: source=umb"),
        "Malformed JSON must not produce a consume log");
    thenUmbConsumerAcceptsSubsequentMessages();
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

  private void whenUmbMessageIsPublished(OperationalProductEvent event) {
    artemis.forOfferings().send(event);
  }

  private void thenUmbConsumerAcceptsSubsequentMessages() {
    OperationalProductEvent probe = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    whenUmbMessageIsPublished(probe);
    thenMessageIsConsumedWithChildSkuFlag(probe, false);
  }

  private void thenMessageIsConsumedWithChildSkuFlag(
      OperationalProductEvent event, boolean isChildSku) {
    service
        .logs()
        .assertContains(
            String.format(
                "IT Product message consumed: source=umb, productCode=%s, eventType=%s, "
                    + "productCategory=%s, childSku=%s",
                event.getProductCode(),
                event.getEventType(),
                event.getProductCategory(),
                isChildSku));
  }

  private void thenSyncServiceWasInvoked(OperationalProductEvent event) {
    service
        .logs()
        .assertContains("Received product message for productSku=" + event.getProductCode());
  }

  private int countLogsContaining(String text) {
    return (int) service.getLogs().stream().filter(line -> line.contains(text)).count();
  }
}
