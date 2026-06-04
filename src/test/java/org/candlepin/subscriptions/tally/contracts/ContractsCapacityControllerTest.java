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
package org.candlepin.subscriptions.tally.contracts;

import static org.mockito.Mockito.verify;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contracts.spring.api.resources.CapacityApi;
import com.redhat.swatch.contracts.spring.client.ApiException;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.contracts.ContractsCapacityController;
import org.candlepin.subscriptions.utilization.api.v1.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v1.model.UsageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractsCapacityControllerTest {

  @Mock CapacityApi contractsApi;
  @InjectMocks ContractsCapacityController controller;

  @Test
  void testGetCapacityReportsByMetricId() throws ApiException {
    ProductId product = ProductId.fromString("rosa");
    MetricId metric = MetricId.fromString("cores");
    OffsetDateTime now = OffsetDateTime.now();
    controller.getCapacityReportByMetricId(
        product,
        metric,
        GranularityType.DAILY,
        now,
        now.plusDays(1),
        10,
        10,
        "billingAccountId",
        ReportCategory.PHYSICAL,
        ServiceLevelType.STANDARD,
        UsageType.PRODUCTION);
    verify(contractsApi)
        .getCapacityReportByMetricId(
            product.toString(),
            metric.toString(),
            com.redhat.swatch.contracts.spring.api.model.GranularityType.DAILY,
            now,
            now.plusDays(1),
            10,
            10,
            "billingAccountId",
            com.redhat.swatch.contracts.spring.api.model.ReportCategory.PHYSICAL,
            com.redhat.swatch.contracts.spring.api.model.ServiceLevelType.STANDARD,
            com.redhat.swatch.contracts.spring.api.model.UsageType.PRODUCTION);
  }
}
