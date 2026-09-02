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

import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.EventTypeEnum;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.ProductCategoryEnum;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseProductConsumerComponentTest extends BaseContractComponentTest {

  @BeforeAll
  static void enableProductServiceConsumer() {
    unleash.enableProductServiceConsumerBothConsumers();
  }

  private static boolean isChildSku(String productCode) {
    return productCode != null && productCode.startsWith("SVC");
  }

  @AfterEach
  void restoreProductServiceConsumerFlag() {
    unleash.enableProductServiceConsumerBothConsumers();
  }

  protected OperationalProductEvent givenParentSkuEvent(String productCode) {
    return givenParentSkuEvent(productCode, EventTypeEnum.UPDATE);
  }

  protected OperationalProductEvent givenParentSkuEvent(
      String productCode, EventTypeEnum eventType) {
    return givenProductEvent(productCode, ProductCategoryEnum.PARENT_SKU, eventType);
  }

  protected OperationalProductEvent givenChildSkuEvent(String productCode) {
    return givenProductEvent(productCode, ProductCategoryEnum.CHILD_SKU);
  }

  protected OperationalProductEvent givenProductEvent(
      String productCode, ProductCategoryEnum category) {
    return givenProductEvent(productCode, category, EventTypeEnum.UPDATE);
  }

  protected OperationalProductEvent givenProductEvent(
      String productCode, ProductCategoryEnum category, EventTypeEnum eventType) {
    OperationalProductEvent event = new OperationalProductEvent();
    event.setProductCode(productCode);
    event.setProductCategory(category);
    event.setEventType(eventType);
    event.setOccurredOn(OffsetDateTime.now().toString());
    return event;
  }

  protected void whenKafkaMessageIsPublished(OperationalProductEvent event) {
    kafkaBridge.produceKafkaMessage(IT_PRODUCT_SYNC, event);
  }

  protected void thenMessageIsNotConsumed(OperationalProductEvent event, String source) {
    service
        .logs()
        .assertDoesNotContain("source=" + source + ", productCode=" + event.getProductCode());
  }

  protected void thenMessageIsConsumed(OperationalProductEvent event, String source) {
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
                isChildSku(event.getProductCode())));
  }

  protected void thenMessageIsConsumedAndSynced(OperationalProductEvent event, String source) {
    thenMessageIsConsumed(event, source);
    thenSyncServiceWasInvoked(event);
  }

  protected void thenSyncServiceWasInvoked(OperationalProductEvent event) {
    service
        .logs()
        .assertContains("Received product message for productSku=" + event.getProductCode());
  }

  protected void thenSyncServiceWasNotInvoked(OperationalProductEvent event) {
    service
        .logs()
        .assertDoesNotContain("Received product message for productSku=" + event.getProductCode());
  }

  protected int countLogsContaining(String text) {
    return (int) service.getLogs().stream().filter(line -> line.contains(text)).count();
  }
}
