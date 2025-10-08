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
package org.candlepin.subscriptions.contracts;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contracts.spring.api.model.CapacityReportByMetricId;
import com.redhat.swatch.contracts.spring.api.resources.CapacityApi;
import com.redhat.swatch.contracts.spring.client.ApiException;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.utilization.api.v1.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v1.model.UsageType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContractsCapacityController {

  private final CapacityApi capacityApi;

  @SuppressWarnings("java:S1068")
  private final ContractsClientProperties contractsClientProperties;

  public ContractsCapacityController(
      CapacityApi capacityApi, ContractsClientProperties contractsClientProperties) {
    this.capacityApi = capacityApi;
    this.contractsClientProperties = contractsClientProperties;
  }

  @Retryable(
      retryFor = ExternalServiceException.class,
      maxAttemptsExpression = "#{@contractsClientProperties.getMaxAttempts()}",
      backoff =
          @Backoff(
              delayExpression = "#{@contractsClientProperties.getBackOffInitialInterval()}",
              maxDelayExpression = "#{@contractsClientProperties.getBackOffMaxInterval()}",
              multiplierExpression = "#{@contractsClientProperties.getBackOffMultiplier()}"))
  @SuppressWarnings("java:S107")
  public CapacityReportByMetricId getCapacityReportByMetricId(
      ProductId productId,
      MetricId metricId,
      GranularityType granularity,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      Integer offset,
      Integer limit,
      String billingAccountId,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usage) {
    try {
      var capacityApiProductId = productId.toString();
      var capacityApiMetricId = metricId.toString();
      var capacityApiGranularity =
          com.redhat.swatch.contracts.spring.api.model.GranularityType.fromValue(
              granularity.toString());

      // ReportCategory, SLA, and Usage may all be null
      var capacityCategory =
          category == null
              ? null
              : com.redhat.swatch.contracts.spring.api.model.ReportCategory.fromValue(
                  category.toString());
      var capacitySla =
          sla == null
              ? null
              : com.redhat.swatch.contracts.spring.api.model.ServiceLevelType.fromValue(
                  sla.toString());
      var capacityUsage =
          usage == null
              ? null
              : com.redhat.swatch.contracts.spring.api.model.UsageType.fromValue(usage.toString());

      return capacityApi.getCapacityReportByMetricId(
          capacityApiProductId,
          capacityApiMetricId,
          capacityApiGranularity,
          beginning,
          ending,
          offset,
          limit,
          billingAccountId,
          capacityCategory,
          capacitySla,
          capacityUsage);
    } catch (ApiException | ProcessingException ex) {
      throw new ExternalServiceException(
          ErrorCode.CONTRACTS_SERVICE_ERROR,
          String.format(
              "Could fetch capacity report '%s/%s' for %s", productId, metricId, billingAccountId),
          ex);
    }
  }
}
