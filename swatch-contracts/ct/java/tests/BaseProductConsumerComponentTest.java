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

import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.EventTypeEnum;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.ProductCategoryEnum;
import java.time.OffsetDateTime;

public abstract class BaseProductConsumerComponentTest extends BaseContractComponentTest {

  protected OperationalProductEvent givenParentSkuEvent(String productCode) {
    return givenProductEvent(productCode, ProductCategoryEnum.PARENT_SKU);
  }

  protected OperationalProductEvent givenChildSkuEvent(String productCode) {
    return givenProductEvent(productCode, ProductCategoryEnum.CHILD_SKU);
  }

  protected OperationalProductEvent givenProductEvent(
      String productCode, ProductCategoryEnum category) {
    OperationalProductEvent event = new OperationalProductEvent();
    event.setProductCode(productCode);
    event.setProductCategory(category);
    event.setEventType(EventTypeEnum.UPDATE);
    event.setOccurredOn(OffsetDateTime.now().toString());
    return event;
  }

  protected void thenMessageIsNotConsumed(OperationalProductEvent event, String source) {
    service
        .logs()
        .assertDoesNotContain("source=" + source + ", productCode=" + event.getProductCode());
  }

  protected void thenMessageIsConsumedWithChildSkuFlag(
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

  protected int countLogsContaining(String text) {
    return (int) service.getLogs().stream().filter(line -> line.contains(text)).count();
  }
}
