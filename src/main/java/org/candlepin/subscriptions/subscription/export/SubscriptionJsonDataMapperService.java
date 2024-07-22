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

import static org.candlepin.subscriptions.subscription.export.SubscriptionDataExporterService.groupMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.SubscriptionsExportJsonItem;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SubscriptionJsonDataMapperService
    implements DataMapperService<SubscriptionCapacityView> {

  private final ApiModelMapperV1 mapper;

  @Override
  public List<Object> mapDataItem(SubscriptionCapacityView dataItem, ExportServiceRequest request) {
    var item = new SubscriptionsExportJsonItem();
    item.setOrgId(dataItem.getOrgId());
    item.setMeasurements(new ArrayList<>());

    // map offering
    item.setSku(dataItem.getSku());
    Optional.ofNullable(dataItem.getUsage()).map(Usage::getValue).ifPresent(item::setUsage);
    Optional.ofNullable(dataItem.getServiceLevel())
        .map(ServiceLevel::getValue)
        .ifPresent(item::setServiceLevel);
    item.setProductName(dataItem.getProductName());
    item.setSubscriptionNumber(dataItem.getSubscriptionNumber());
    item.setQuantity((double) dataItem.getQuantity());

    // aggregate metrics
    item.setMeasurements(groupMetrics(mapper, dataItem, request));

    return List.of(item);
  }

  @Override
  public Class<SubscriptionCapacityView> getDataClass() {
    return SubscriptionCapacityView.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return SubscriptionsExportJsonItem.class;
  }
}
