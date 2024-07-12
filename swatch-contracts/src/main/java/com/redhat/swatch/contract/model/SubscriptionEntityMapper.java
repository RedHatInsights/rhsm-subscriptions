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

import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import org.mapstruct.Builder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(
    componentModel = "cdi",
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    builder = @Builder(disableBuilder = true))
public interface SubscriptionEntityMapper {

  @Mapping(target = "subscriptionId", ignore = true)
  @Mapping(target = "quantity", constant = "1L")
  @Mapping(target = "subscriptionMeasurements", source = "metrics")
  @Mapping(
      target = "billingProvider",
      source = "billingProvider",
      qualifiedByName = "extractBillingProvider")
  // For an update, hibernate complains if startDate is changed, because it is part of the entity
  // key
  @Mapping(target = "startDate", ignore = true)
  void mapSubscriptionEntityFromContractEntity(
      @MappingTarget SubscriptionEntity subscription, ContractEntity contract);

  @Mapping(target = "subscription", ignore = true)
  @Mapping(target = "measurementType", constant = "PHYSICAL")
  SubscriptionMeasurementEntity contractMetricEntityToSubscriptionMeasurementEntity(
      ContractMetricEntity contractMetric);

  @Named("extractBillingProvider")
  default BillingProvider extractBillingProvider(String value) {
    return BillingProvider.fromString(value);
  }

  void updateSubscription(
      @MappingTarget SubscriptionEntity existingSubscription, SubscriptionEntity entity);
}
