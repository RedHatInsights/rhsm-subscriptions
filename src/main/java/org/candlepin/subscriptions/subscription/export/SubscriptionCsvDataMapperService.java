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
package org.candlepin.subscriptions.subscription.export;

import static org.candlepin.subscriptions.resource.SubscriptionTableController.addTotalCapacity;
import static org.candlepin.subscriptions.resource.SubscriptionTableController.initializeSkuCapacity;

import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.SubscriptionsExportCsvItem;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacity;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionCsvDataMapperService implements DataMapperService<Subscription> {

  private static final Uom NO_UOM = null;

  @Override
  public List<Object> mapDataItem(Subscription dataItem, ExportServiceRequest request) {
    if (dataItem.getSubscriptionMeasurements().isEmpty()) {
      return List.of();
    }

    return dataItem.getSubscriptionMeasurements().entrySet().stream()
        .map(
            m -> {
              var item = new SubscriptionsExportCsvItem();
              item.setSubscriptionId(dataItem.getSubscriptionId());
              item.setSubscriptionNumber(dataItem.getSubscriptionNumber());
              item.setBegin(dataItem.getStartDate());
              item.setEnd(dataItem.getEndDate());
              item.setQuantity((double) dataItem.getQuantity());
              item.setMetricId(m.getKey().getMetricId());

              SkuCapacity skuCapacity = initializeSkuCapacity(dataItem, NO_UOM, item.getMetricId());
              addTotalCapacity(dataItem, m.getKey(), m.getValue(), skuCapacity);

              item.setCapacity(skuCapacity.getCapacity());
              item.setHypervisorCapacity(skuCapacity.getHypervisorCapacity());

              // map offering
              var offering = dataItem.getOffering();
              item.setSku(offering.getSku());
              Optional.ofNullable(offering.getUsage())
                  .map(Usage::getValue)
                  .ifPresent(item::setUsage);
              Optional.ofNullable(offering.getServiceLevel())
                  .map(ServiceLevel::getValue)
                  .ifPresent(item::setServiceLevel);
              item.setProductName(offering.getProductName());
              return (Object) item;
            })
        .toList();
  }

  @Override
  public Class<Subscription> getDataClass() {
    return Subscription.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return SubscriptionsExportCsvItem.class;
  }
}
