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
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1.SourcePartnerEnum;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mapstruct.AfterMapping;
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
public interface ContractEntityMapper {

  @Mapping(
      target = "subscriptionNumber",
      source = "entitlement.rhEntitlements",
      qualifiedByName = "subscriptionNumber")
  @Mapping(target = "orgId", source = "entitlement.rhAccountId")
  @Mapping(target = "offering", source = "entitlement.rhEntitlements", qualifiedByName = "offering")
  @Mapping(target = "startDate", source = "entitlementDates.startDate")
  @Mapping(target = "endDate", source = "entitlementDates.endDate")
  @Mapping(target = "vendorProductCode", source = "entitlement.purchase.vendorProductCode")
  @Mapping(
      target = "billingAccountId",
      source = "entitlement.partnerIdentities",
      qualifiedByName = "billingAccountId")
  @Mapping(
      target = "billingProviderId",
      source = "entitlement",
      qualifiedByName = "billingProviderId")
  @Mapping(
      target = "billingProvider",
      source = "entitlement.sourcePartner",
      qualifiedByName = "billingProvider")
  @BeanMapping(ignoreByDefault = true)
  ContractEntity mapEntitlementToContractEntity(PartnerEntitlementV1 entitlement);

  @Mapping(target = "metricId", source = "name")
  @Mapping(target = "value", source = "value")
  @BeanMapping(ignoreByDefault = true)
  ContractMetricEntity mapDimensionToContractMetricEntity(DimensionV1 dimension);

  @Mapping(target = "uuid", ignore = true)
  @Mapping(target = "startDate", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateContract(@MappingTarget ContractEntity existingContract, ContractEntity entity);

  @Named("subscriptionNumber")
  default String extractSubscriptionNumber(List<RhEntitlementV1> rhEntitlements) {
    return extractValueFromRhEntitlements(rhEntitlements, RhEntitlementV1::getSubscriptionNumber);
  }

  @AfterMapping
  default void populateMetricsFromEntitlement(
      PartnerEntitlementV1 entitlement, @MappingTarget ContractEntity entity) {
    entity.setMetrics(
        entitlement.getPurchase().getContracts().stream()
            .filter(contract -> Objects.nonNull(contract.getDimensions()))
            .flatMap(contract -> contract.getDimensions().stream())
            .map(this::mapDimensionToContractMetricEntity)
            .collect(Collectors.toSet()));
  }

  // this method is to properly map value from entitlement partnerIdentities
  // as these fields are populated differently based on the marketplace
  @Named("billingAccountId")
  default String extractBillingAccountId(PartnerIdentityV1 accountId) {
    if (accountId.getCustomerAwsAccountId() != null) {
      return accountId.getCustomerAwsAccountId();
    } else if (accountId.getAzureTenantId() != null && accountId.getAzureSubscriptionId() != null) {
      return String.format(
          "%s;%s", accountId.getAzureTenantId(), accountId.getAzureSubscriptionId());
    } else if (accountId.getAzureTenantId() != null) {
      return accountId.getAzureTenantId();
    }
    return null;
  }

  /** Extract billingProviderId from Partner Gateway API response */
  @Named("billingProviderId")
  default String extractBillingProviderId(PartnerEntitlementV1 entitlement) {
    var partner = entitlement.getSourcePartner();
    if (partner == SourcePartnerEnum.AWS_MARKETPLACE) {
      return String.format(
          "%s;%s;%s",
          entitlement.getPurchase().getVendorProductCode(),
          entitlement.getPartnerIdentities().getAwsCustomerId(),
          entitlement.getPartnerIdentities().getSellerAccountId());
    } else if (partner == SourcePartnerEnum.AZURE_MARKETPLACE) {
      var azurePlanId =
          entitlement.getPurchase().getContracts().stream()
              .map(SaasContractV1::getPlanId)
              .findFirst()
              .orElse("");
      var azureOfferId = entitlement.getPurchase().getVendorProductCode();
      var azureCustomerId = entitlement.getPartnerIdentities().getAzureCustomerId();
      var azureResourceId = entitlement.getPurchase().getAzureResourceId();
      return String.format(
          "%s;%s;%s;%s", azureResourceId, azurePlanId, azureOfferId, azureCustomerId);
    }
    return null;
  }

  @Named("billingProvider")
  default String extractBillingProvider(SourcePartnerEnum sourcePartner) {
    return ContractSourcePartnerEnum.getByCode(sourcePartner.value());
  }

  default String extractValueFromRhEntitlements(
      List<RhEntitlementV1> rhEntitlements, Function<RhEntitlementV1, String> extractor) {
    if (rhEntitlements == null) {
      return null;
    }

    return rhEntitlements.stream().map(extractor).filter(Objects::nonNull).findFirst().orElse(null);
  }
}
