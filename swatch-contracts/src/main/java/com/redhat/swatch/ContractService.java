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
package com.redhat.swatch;

import com.redhat.swatch.openapi.model.Metric;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContractService {

  @Inject ContractRepository repository;

  private static com.redhat.swatch.openapi.model.Contract convertToDtos(Contract x) {

    // TODO use fancy projection? https://quarkus.io/guides/hibernate-orm-panache#query-projection

    // try MapStruct
    // https://www.youtube.com/watch?v=r_lrpv9msc8&list=PL6oD2syjfW7ADAkICQr-SQcEqsenVPfqg&index=32

    var dto = new com.redhat.swatch.openapi.model.Contract();

    dto.setUuid(x.getUuid().toString());
    dto.setBillingProvider(x.getBillingProvider());
    dto.setEndDate(x.getEndDate());
    dto.setOrgId(x.getOrgId());
    dto.setBillingAccountId(x.getBillingAccountId());
    dto.setStartDate(x.getStartDate());
    dto.setSubscriptionNumber(x.getSubscriptionNumber());
    dto.setProductId(x.getProductId());
    dto.setSku(x.getSku());

    for (ContractMetric y : x.getMetrics()) {

      var metric = new Metric();
      metric.setMetricId(y.getMetricId());
      metric.setValue(BigDecimal.valueOf(y.getValue().doubleValue()));

      dto.addMetricsItem(metric);
    }

    return dto;
  }

  com.redhat.swatch.openapi.model.Contract saveContract(
      com.redhat.swatch.openapi.model.Contract contract) {

    var entity = new Contract();
    var now = OffsetDateTime.now();

    var uuid = Objects.requireNonNullElse(contract.getUuid(), UUID.randomUUID().toString());
    entity.setUuid(UUID.fromString(uuid));

    entity.setStartDate(now);
    entity.setLastUpdated(now);
    entity.setSku(contract.getSku());

    //TODO fix unique constraint....right now you can spam a POST request successfully

    var metricDto = contract.getMetrics().get(0);
    var metric = new ContractMetric();
    metric.setContractUuid(UUID.fromString(uuid));
    metric.setMetricId(metricDto.getMetricId());
    metric.setValue(metricDto.getValue().doubleValue());

    entity.addMetric(metric);

    entity.setProductId(contract.getProductId());
    entity.setSubscriptionNumber(contract.getSubscriptionNumber());
    entity.setOrgId(contract.getOrgId());
    entity.setBillingAccountId(contract.getBillingAccountId());
    entity.setBillingProvider(contract.getBillingProvider());

    repository.persist(entity);

    return contract;
  }

  public List<com.redhat.swatch.openapi.model.Contract> getContracts(
      Map<String, Object> parameters) {

    return repository.getContracts(parameters).stream().map(x -> convertToDtos(x)).toList();
  }
}
