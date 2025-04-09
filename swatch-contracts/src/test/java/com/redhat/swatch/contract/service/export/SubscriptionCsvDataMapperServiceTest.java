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
package com.redhat.swatch.contract.service.export;

import static com.redhat.swatch.contract.repository.ReportCategory.HYPERVISOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.model.SubscriptionsExportCsvItem;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewMetric;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SubscriptionCsvDataMapperServiceTest {

  @Inject SubscriptionCsvDataMapperService csvDataMapperService;

  @Test
  public void testMapDataItem() {
    SubscriptionCapacityView subscriptionCapacityView = new SubscriptionCapacityView();

    String subscriptionId = "sub1";
    String subscriptionNumber = "sub-num";
    String sku = "sku-value";
    String billingAccountId = "billing-account-123";
    String productName = "Sample Product";
    BillingProvider aws = BillingProvider.AWS;
    String metricId = "CORES";
    double capacity = 40;

    subscriptionCapacityView.setSubscriptionId(subscriptionId);
    subscriptionCapacityView.setSubscriptionNumber(subscriptionNumber);
    subscriptionCapacityView.setStartDate(OffsetDateTime.now().minusDays(1));
    subscriptionCapacityView.setEndDate(OffsetDateTime.now().plusDays(1));
    subscriptionCapacityView.setQuantity(10);
    subscriptionCapacityView.setSku(sku);
    subscriptionCapacityView.setBillingAccountId(billingAccountId);
    subscriptionCapacityView.setBillingProvider(aws);
    subscriptionCapacityView.setProductName(productName);
    subscriptionCapacityView.setUsage(Usage.PRODUCTION);
    subscriptionCapacityView.setServiceLevel(ServiceLevel.PREMIUM);

    SubscriptionCapacityViewMetric metric = new SubscriptionCapacityViewMetric();
    metric.setMeasurementType(String.valueOf(HYPERVISOR));
    metric.setMetricId(MetricId.fromString(metricId).toUpperCaseFormatted());
    metric.setCapacity(capacity);
    var measurements = new HashSet<SubscriptionCapacityViewMetric>();
    measurements.add(metric);
    subscriptionCapacityView.setMetrics(measurements);

    List<Object> result = csvDataMapperService.mapDataItem(subscriptionCapacityView, null);

    assertEquals(1, result.size());
    SubscriptionsExportCsvItem csvItem = (SubscriptionsExportCsvItem) result.get(0);
    assertEquals(subscriptionId, csvItem.getSubscriptionId());
    assertEquals(subscriptionNumber, csvItem.getSubscriptionNumber());
    assertEquals(sku, csvItem.getSku());
    assertEquals(productName, csvItem.getProductName());
    assertEquals(10.0, csvItem.getQuantity());
    assertEquals(billingAccountId, csvItem.getBillingAccountId());
    assertEquals(aws.getValue(), csvItem.getBillingProvider());
    assertEquals(metricId, csvItem.getMetricId());
    assertEquals(Usage.PRODUCTION.getValue(), csvItem.getUsage());
    assertEquals(ServiceLevel.PREMIUM.getValue(), csvItem.getServiceLevel());
    assertEquals(capacity, csvItem.getCapacity());
  }
}
