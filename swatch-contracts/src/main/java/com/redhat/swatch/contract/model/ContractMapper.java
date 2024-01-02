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

import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.Dimension;
import com.redhat.swatch.contract.openapi.model.Metric;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContractCloudIdentifiers;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import com.redhat.swatch.contract.repository.SubscriptionProductIdEntity;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(
    componentModel = "cdi",
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    builder = @Builder(disableBuilder = true),
    uses = {ContractOfferingMapper.class})
public interface ContractMapper {

  Contract contractEntityToDto(ContractEntity contract);

  @Mapping(target = "lastUpdated", ignore = true)
  ContractEntity dtoToContractEntity(Contract contract);

  @Mapping(target = "contract", ignore = true)
  @Mapping(target = "contractUuid", ignore = true)
  ContractMetricEntity metricDtoToMetricEntity(Metric metric);

  @Mapping(target = "subscriptionNumber", source = "contract.redHatSubscriptionNumber")
  @Mapping(
      target = "vendorProductCode",
      source = "cloudIdentifiers",
      qualifiedByName = "vendorProductCode")
  @BeanMapping(ignoreByDefault = true)
  ContractEntity partnerContractToContractEntity(PartnerEntitlementContract contract);

  // this method uses the partner field in PartnerEntitlementContractCloudIdentifiers
  //  to determine how product code is mapped for a specific provider
  @Named("vendorProductCode")
  default String extractVendorProductCode(PartnerEntitlementContractCloudIdentifiers code) {
    if (code.getProductCode() != null) {
      return code.getProductCode();
    } else if (code.getPartner().equals("azure_marketplace")) {
      return code.getAzureOfferId();
    }
    return null;
  }

  @Named("billingProviderId")
  default String extractBillingProviderId(PartnerEntitlementContractCloudIdentifiers code) {
    String providerId = null;
    if (Objects.equals("azure_marketplace", code.getPartner())) {
      providerId =
          String.format(
              "%s;%s;%s", code.getAzureResourceId(), code.getPlanId(), code.getAzureOfferId());
    }
    return providerId;
  }

  @Mapping(target = "subscriptionId", ignore = true)
  @Mapping(target = "billingProviderId", ignore = true)
  @Mapping(target = "quantity", constant = "1L")
  @Mapping(target = "offering", source = ".")
  @Mapping(target = "subscriptionProductIds", source = ".")
  @Mapping(target = "subscriptionMeasurements", source = "metrics")
  void mapContractEntityToSubscriptionEntity(
      @MappingTarget SubscriptionEntity subscription, ContractEntity contract);

  @Mapping(target = "subscription", ignore = true)
  @Mapping(target = "productId", source = "productId")
  SubscriptionProductIdEntity contractEntityToSubscriptionProductIdEntity(ContractEntity contract);

  @Mapping(target = "subscription", ignore = true)
  @Mapping(target = "measurementType", constant = "PHYSICAL")
  SubscriptionMeasurementEntity contractMetricEntityToSubscriptionMeasurementEntity(
      ContractMetricEntity contractMetric);

  default BillingProvider billingProviderFromString(String value) {
    return BillingProvider.fromString(value);
  }

  @Mapping(target = "metricId", source = "dimension.dimensionName")
  @Mapping(target = "value", source = "dimension.dimensionValue")
  @Mapping(target = "contract", ignore = true)
  @Mapping(target = "contractUuid", ignore = true)
  ContractMetricEntity dimensionToContractMetricEntity(Dimension dimension);

  @Mapping(target = "orgId", source = "entitlement.rhAccountId")
  @Mapping(target = "billingAccountId", source = "entitlement.partnerIdentities")
  @Mapping(
      target = "sku",
      source = "entitlement.rhEntitlements",
      qualifiedByName = "rhEntitlementSku")
  @BeanMapping(ignoreByDefault = true)
  void mapRhEntitlementsToContractEntity(
      @MappingTarget ContractEntity contractEntity, PartnerEntitlementV1 entitlement);

  // this method is to properly map value from entitlement partnerIdentities
  // as these fields are populated differently based on the marketplace
  default String extractBillingAccountId(PartnerIdentityV1 accountId) {

    if (accountId.getCustomerAwsAccountId() != null) {
      return accountId.getCustomerAwsAccountId();
    } else if (accountId.getAzureTenantId() != null) {
      return accountId.getAzureTenantId();
    }
    return null;
  }

  @Named("rhEntitlementSku")
  default String getRhEntitlementSku(List<RhEntitlementV1> rhEntitlements) {
    if (Objects.nonNull(rhEntitlements)
        && !rhEntitlements.isEmpty()
        && Objects.nonNull(rhEntitlements.get(0))) {
      return rhEntitlements.get(0).getSku();
    } else {
      return null;
    }
  }

  @Named("rhSubscriptionNumber")
  default String getRhSubscriptionNumber(List<RhEntitlementV1> rhEntitlements) {
    if (Objects.nonNull(rhEntitlements)
        && !rhEntitlements.isEmpty()
        && Objects.nonNull(rhEntitlements.get(0))) {
      return rhEntitlements.get(0).getSubscriptionNumber();
    } else {
      return null;
    }
  }

  Set<ContractMetricEntity> dimensionV1ToContractMetricEntity(Set<DimensionV1> dimension);

  @Mapping(target = "metricId", source = "name")
  @Mapping(target = "value", source = "value")
  @Mapping(target = "contract", ignore = true)
  @Mapping(target = "contractUuid", ignore = true)
  ContractMetricEntity dimensionV1ToContractMetricEntity(DimensionV1 dimension);
}
