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
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import org.mapstruct.Builder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(
    componentModel = "cdi",
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    builder = @Builder(disableBuilder = true))
public interface SubscriptionContractDtoMapper {

  @Mapping(target = "uuid", ignore = true)
  @Mapping(target = "metrics", ignore = true)
  @Mapping(target = "sku", source = "offering.sku")
  @Mapping(target = "productTags", source = "offering.productTags")
  @Mapping(
      target = "billingProvider",
      source = "billingProvider",
      qualifiedByName = "billingProviderValue")
  Contract subscriptionEntityToDto(SubscriptionEntity subscription);

  @Named("billingProviderValue")
  default String billingProviderValue(BillingProvider billingProvider) {
    return billingProvider == null ? null : billingProvider.getValue();
  }
}
