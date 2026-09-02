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

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.EventTypeEnum;
import org.junit.jupiter.api.Test;

public class ProductDuplicateComponentTest extends BaseProductUmbComponentTest {

  @TestPlanName("product-duplicate-TC001")
  @Test
  void shouldProcessSameSkuFromKafkaAndUmb() {
    // Given: The same parent SKU operational product event for both channels
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing the same event to Kafka and UMB
    whenSameEventIsPublishedToKafkaAndUmb(event);

    // Then: Both consumers process the event and each invokes offering sync
    thenMessageIsConsumed(event, "kafka");
    thenMessageIsConsumed(event, "umb");
    thenBothConsumersInvokedSync(event);
  }

  @TestPlanName("product-duplicate-TC002")
  @Test
  void shouldProcessSameSkuWithDifferentEventTypes() {
    // Given: Create on Kafka and Update on UMB for the same parent SKU
    String productCode = "RH" + RandomUtils.generateRandom();
    OperationalProductEvent createEvent = givenParentSkuEvent(productCode, EventTypeEnum.CREATE);
    OperationalProductEvent updateEvent = givenParentSkuEvent(productCode, EventTypeEnum.UPDATE);

    // When: Publishing Create to Kafka and Update to UMB
    whenCreateGoesToKafkaAndUpdateGoesToUmb(createEvent, updateEvent);

    // Then: Each consumer logs its event type and both invoke offering sync
    thenMessageIsConsumed(createEvent, "kafka");
    thenMessageIsConsumed(updateEvent, "umb");
    thenBothConsumersInvokedSync(updateEvent);
  }

  private void whenSameEventIsPublishedToKafkaAndUmb(OperationalProductEvent event) {
    whenKafkaMessageIsPublished(event);
    whenUmbMessageIsPublished(event);
  }

  private void whenCreateGoesToKafkaAndUpdateGoesToUmb(
      OperationalProductEvent createEvent, OperationalProductEvent updateEvent) {
    whenKafkaMessageIsPublished(createEvent);
    whenUmbMessageIsPublished(updateEvent);
  }

  private void thenBothConsumersInvokedSync(OperationalProductEvent event) {
    AwaitilityUtils.until(
        () ->
            countLogsContaining(
                "Received product message for productSku=" + event.getProductCode()),
        count -> count == 2,
        AwaitilitySettings.defaults()
            .timeoutMessage(
                "Expected both Kafka and UMB to invoke offering sync for productSku=%s",
                event.getProductCode()));
  }
}
