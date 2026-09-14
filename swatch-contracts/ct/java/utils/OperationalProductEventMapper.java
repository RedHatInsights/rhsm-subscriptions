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
package utils;

import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.EventTypeEnum;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.ProductCategoryEnum;
import domain.Offering;
import java.time.OffsetDateTime;

/** Maps test {@link Offering} fixtures to Kafka {@link OperationalProductEvent} payloads. */
public final class OperationalProductEventMapper {
  private OperationalProductEventMapper() {}

  public static OperationalProductEvent mapFrom(Offering offering) {
    return mapFrom(offering, EventTypeEnum.UPDATE);
  }

  public static OperationalProductEvent mapFrom(Offering offering, EventTypeEnum eventType) {
    if (offering == null) {
      return null;
    }

    OperationalProductEvent event = new OperationalProductEvent();
    event.setProductCode(offering.getSku());
    event.setProductCategory(resolveProductCategory(offering.getSku()));
    event.setEventType(eventType);
    event.setOccurredOn(OffsetDateTime.now().toString());
    return event;
  }

  private static ProductCategoryEnum resolveProductCategory(String productCode) {
    return isChildSku(productCode) ? ProductCategoryEnum.CHILD_SKU : ProductCategoryEnum.PARENT_SKU;
  }

  private static boolean isChildSku(String productCode) {
    return productCode != null && productCode.startsWith("SVC");
  }
}
