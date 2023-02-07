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

import com.redhat.swatch.openapi.model.BillingProvider;
import com.redhat.swatch.openapi.model.Contract;
import com.redhat.swatch.openapi.model.Metric;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContractService {

  @Inject ContractRepository repository;

  Contract saveContract(Contract contract) {

    var entity = new ContractsEntity();
    var now = OffsetDateTime.now();

    var uuid = Objects.requireNonNull(contract.getUuid(), UUID.randomUUID().toString());
    entity.setUuid(UUID.fromString(uuid));

    entity.setStartDate(now);
    entity.setLastUpdated(now);

    // TODO not aren't part of the api schema
    entity.setSku("BANANAS");

    // TODO
    var metricDto = contract.getMetrics().get(0);

    entity.setMetricId(metricDto.getMetricId());
    entity.setValue(metricDto.getValue().doubleValue());

    repository.persist(entity);

    return contract;
  }

  @SneakyThrows
  public List<Contract> getAllContracts() {

    // need to add filtering here

    List<ContractsEntity> contracts = repository.listAll();

    var dto = new Contract();

    if (!contracts.isEmpty()) {

      // Since everything will be the same except for the metric id & value
      var x = contracts.get(0);

      dto.setUuid(x.getUuid().toString());
      dto.setBillingProvider(BillingProvider.fromValue(x.getBillingProvider()));
      dto.setEndDate(x.getEndDate());
      dto.setOrgId(x.getOrgId());
      dto.setBillingAccountId(x.getBillingAccountId());
      dto.setStartDate(x.getStartDate());
      dto.setSubscriptionNumber(x.getSubscriptionNumber());
      dto.setProductId(x.getProductId());

      List<Metric> metrics =
          contracts.stream()
              .map(
                  y -> {
                    var metric = new Metric();
                    metric.setMetricId(y.getMetricId());
                    metric.setValue(BigDecimal.valueOf(y.getValue()));
                    return metric;
                  })
              .collect(Collectors.toList());

      dto.setMetrics(metrics);
    }

    return null;
  }
}
