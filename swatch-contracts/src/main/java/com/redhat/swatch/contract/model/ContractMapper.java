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
package com.redhat.swatch.contract.model;

import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.Dimension;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface ContractMapper {

  Contract contractEntityToDto(ContractEntity contract);

  @Mapping(target = "lastUpdated", ignore = true)
  ContractEntity dtoToContractEntity(Contract contract);

  @Mapping(target = "subscriptionNumber", source = "contract.redHatSubscriptionNumber")
  ContractEntity partnerContractToContractEntity(PartnerEntitlementContract contract);

  @Mapping(target = "metricId", source = "dimension.dimensionName")
  @Mapping(target = "value", source = "dimension.dimensionValue")
  ContractMetricEntity dimensionToContractMetricEntity(Dimension dimension);

  Set<ContractMetricEntity> dimensionToContractMetricEntity(List<Dimension> dimensions);

  default ContractEntity reconcileUpstreamContract(PartnerEntitlementContract upstreamContract) {
    ContractEntity entity = partnerContractToContractEntity(upstreamContract);
    entity.setMetrics(dimensionToContractMetricEntity(upstreamContract.getCurrentDimensions()));
    if (Objects.nonNull(upstreamContract.getCurrentDimensions())) {
      entity.setEndDate(upstreamContract.getCurrentDimensions().get(0).getExpirationDate());
    return entity;
  }
  
  @AfterMapping
  default void propogateContractUuid(
      @MappingTarget final ContractEntity.ContractEntityBuilder contractEntity,
      final Contract contractDto) {

    if (Objects.requireNonNullElse(contractDto.getMetrics(), new ArrayList<>()).isEmpty()) {
      contractEntity.metrics(new HashSet<>());
    } else {
      contractEntity.metrics(
          contractDto.getMetrics().stream()
              .map(
                  (x -> {
                    var builder = ContractMetricEntity.builder();
                    builder.metricId(x.getMetricId());
                    builder.value(x.getValue());
                    if (Objects.nonNull(contractDto.getUuid())) {
                      builder.contractUuid(UUID.fromString(contractDto.getUuid()));
                    }
                    return builder.build();
                  }))
              .collect(Collectors.toSet()));
    }
  }
}
