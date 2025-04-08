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

import static com.redhat.swatch.contract.model.ContractSourcePartnerEnum.isAwsMarketplace;
import static com.redhat.swatch.contract.model.ContractSourcePartnerEnum.isAzureMarketplace;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(
    componentModel = "jakarta-cdi",
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
  @Mapping(target = "startDate", expression = "java(extractStartDate(entitlement, contract))")
  @Mapping(target = "endDate", expression = "java(extractEndDate(entitlement, contract))")
  @Mapping(target = "metrics", source = "contract.dimensions", qualifiedByName = "metrics")
  @BeanMapping(ignoreByDefault = true)
  ContractEntity mapEntitlementToContractEntity(
      PartnerEntitlementV1 entitlement, SaasContractV1 contract);

  @Mapping(target = "metricId", source = "name")
  @Mapping(target = "value", source = "value")
  @BeanMapping(ignoreByDefault = true)
  @Named("metrics")
  ContractMetricEntity mapDimensionToContractMetricEntity(DimensionV1 dimension);

  @Mapping(target = "uuid", ignore = true)
  @Mapping(target = "startDate", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateContract(@MappingTarget ContractEntity existingContract, ContractEntity entity);

  @Named("subscriptionNumber")
  default String extractSubscriptionNumber(List<RhEntitlementV1> rhEntitlements) {
    return extractValueFromRhEntitlements(rhEntitlements, RhEntitlementV1::getSubscriptionNumber);
  }

  // this method is to properly map value from entitlement partnerIdentities
  // as these fields are populated differently based on the marketplace
  @Named("billingAccountId")
  default String extractBillingAccountId(PartnerIdentityV1 accountId) {
    if (accountId.getCustomerAwsAccountId() != null) {
      return accountId.getCustomerAwsAccountId();
    } else if (accountId.getAzureSubscriptionId() != null) {
      return accountId.getAzureSubscriptionId();
    }
    return null;
  }

  /** Extract billingProviderId from Partner Gateway API response */
  @Named("billingProviderId")
  default String extractBillingProviderId(PartnerEntitlementV1 entitlement) {
    var partner = entitlement.getSourcePartner();
    if (isAwsMarketplace(partner)) {
      return String.format(
          "%s;%s;%s",
          entitlement.getPurchase().getVendorProductCode(),
          entitlement.getPartnerIdentities().getAwsCustomerId(),
          entitlement.getPartnerIdentities().getSellerAccountId());
    } else if (isAzureMarketplace(partner)) {
      var azurePlanId =
          entitlement.getPurchase().getContracts().stream()
              .map(SaasContractV1::getPlanId)
              .findFirst()
              .orElse("");
      var azureOfferId = entitlement.getPurchase().getVendorProductCode();
      var azureCustomerId = entitlement.getPartnerIdentities().getAzureCustomerId();
      var azureResourceId = entitlement.getPurchase().getAzureResourceId();
      // for contracts created before ITPART-1180, the client ID is not provided,
      // so we need to default it to empty.
      var azureClientId =
          Optional.ofNullable(entitlement.getPartnerIdentities().getClientId()).orElse("");
      return String.format(
          "%s;%s;%s;%s;%s",
          azureResourceId, azurePlanId, azureOfferId, azureCustomerId, azureClientId);
    }
    return null;
  }

  @Named("billingProvider")
  default String extractBillingProvider(String sourcePartner) {
    return ContractSourcePartnerEnum.getByCode(sourcePartner);
  }

  default String extractValueFromRhEntitlements(
      List<RhEntitlementV1> rhEntitlements, Function<RhEntitlementV1, String> extractor) {
    if (rhEntitlements == null) {
      return null;
    }

    return rhEntitlements.stream().map(extractor).filter(Objects::nonNull).findFirst().orElse(null);
  }

  @Named("startDate")
  default OffsetDateTime extractStartDate(
      PartnerEntitlementV1 entitlement, SaasContractV1 contract) {
    if (contract != null && contract.getStartDate() != null) {
      return truncateToMicroPrecision(contract.getStartDate());
    }
    if (entitlement.getEntitlementDates() != null) {
      return truncateToMicroPrecision(entitlement.getEntitlementDates().getStartDate());
    }
    return null;
  }

  @Named("endDate")
  default OffsetDateTime extractEndDate(PartnerEntitlementV1 entitlement, SaasContractV1 contract) {
    // If the start_date is populated then take end_date from the contract
    if (contract != null && contract.getStartDate() != null) {
      return truncateToMicroPrecision(contract.getEndDate());
    }
    if (entitlement.getEntitlementDates() != null) {
      return truncateToMicroPrecision(entitlement.getEntitlementDates().getEndDate());
    }
    return null;
  }

  /**
   * Database stores timestamps using micro precision, so we need to truncate the date given, so we
   * can match existing records.
   */
  static OffsetDateTime truncateToMicroPrecision(OffsetDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }

    return dateTime.truncatedTo(ChronoUnit.MICROS);
  }
}
