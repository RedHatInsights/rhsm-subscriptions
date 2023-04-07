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
import com.redhat.swatch.contract.openapi.model.Metric;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "cdi",
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    builder = @Builder(disableBuilder = true))
public interface ContractMapper {

  Contract contractEntityToDto(ContractEntity contract);

  @Mapping(target = "lastUpdated", ignore = true)
  ContractEntity dtoToContractEntity(Contract contract);

  @Mapping(target = "contract", ignore = true)
  @Mapping(target = "contractUuid", ignore = true)
  ContractMetricEntity metricDtoToMetricEntity(Metric metric);

  @Mapping(target = "subscriptionNumber", source = "contract.redHatSubscriptionNumber")
  @Mapping(target = "metrics", source = "currentDimensions")
  @Mapping(target = "vendorProductCode", source = "cloudIdentifiers.productCode")
  @BeanMapping(ignoreByDefault = true)
  ContractEntity partnerContractToContractEntity(PartnerEntitlementContract contract);

  @Mapping(target = "metricId", source = "dimension.dimensionName")
  @Mapping(target = "value", source = "dimension.dimensionValue")
  @Mapping(target = "contract", ignore = true)
  @Mapping(target = "contractUuid", ignore = true)
  ContractMetricEntity dimensionToContractMetricEntity(Dimension dimension);
}
