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

import com.redhat.swatch.openapi.model.Contract;
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

  private static Contract convertToDtos(ContractsEntity x) {

    // TODO use fancy projection? https://quarkus.io/guides/hibernate-orm-panache#query-projection

    // try MapStruct https://www.youtube.com/watch?v=r_lrpv9msc8&list=PL6oD2syjfW7ADAkICQr-SQcEqsenVPfqg&index=32

    var dto = new Contract();

    dto.setUuid(x.getUuid().toString());
    dto.setBillingProvider(x.getBillingProvider());
    dto.setEndDate(x.getEndDate());
    dto.setOrgId(x.getOrgId());
    dto.setBillingAccountId(x.getBillingAccountId());
    dto.setStartDate(x.getStartDate());
    dto.setSubscriptionNumber(x.getSubscriptionNumber());
    dto.setProductId(x.getProductId());

    var metric = new Metric();
    metric.setMetricId(x.getMetricId());
    metric.setValue(BigDecimal.valueOf(x.getValue()));

    dto.setMetrics(List.of(metric));

    return dto;
  }

  Contract saveContract(Contract contract) {

    var entity = new ContractsEntity();
    var now = OffsetDateTime.now();

    var uuid = Objects.requireNonNullElse(contract.getUuid(), UUID.randomUUID().toString());
    entity.setUuid(UUID.fromString(uuid));

    entity.setStartDate(now);
    entity.setLastUpdated(now);

    // TODO not aren't part of the api schema
    entity.setSku("BANANAS");

    // TODO
    var metricDto = contract.getMetrics().get(0);

    entity.setMetricId(metricDto.getMetricId());
    entity.setValue(metricDto.getValue().doubleValue());

    entity.setProductId(contract.getProductId());
    entity.setSubscriptionNumber(contract.getSubscriptionNumber());
    entity.setOrgId(contract.getOrgId());
    entity.setBillingAccountId(contract.getBillingAccountId());
    entity.setBillingProvider(contract.getBillingProvider());

    repository.persist(entity);

    return contract;
  }

  public List<Contract> getContracts(Map<String, Object> parameters) {

    return repository.getContracts(parameters).stream().map(x -> convertToDtos(x)).toList();
  }
}
