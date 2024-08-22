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

import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.contract.openapi.model.ContractRequest;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import java.util.Objects;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PartnerEntitlementsRequest {
  private String redHatSubscriptionNumber;
  private String productCode;
  private String awsCustomerId;
  private String awsCustomerAccountId;
  private String azureResourceId;

  public static PartnerEntitlementsRequest from(PartnerEntitlementContract contract) {
    PartnerEntitlementsRequest request = new PartnerEntitlementsRequest();
    request.redHatSubscriptionNumber = contract.getRedHatSubscriptionNumber();
    if (contract.getCloudIdentifiers() != null) {
      request.awsCustomerId = contract.getCloudIdentifiers().getAwsCustomerId();
      request.awsCustomerAccountId = contract.getCloudIdentifiers().getAwsCustomerAccountId();
      request.azureResourceId = contract.getCloudIdentifiers().getAzureResourceId();
      request.productCode = contract.getCloudIdentifiers().getProductCode();
    }

    return request;
  }

  public static PartnerEntitlementsRequest from(ContractRequest contractRequest) {
    PartnerEntitlementsRequest request = new PartnerEntitlementsRequest();
    var entitlement = contractRequest.getPartnerEntitlement();
    if (entitlement.getRhEntitlements() != null) {
      request.redHatSubscriptionNumber =
          entitlement.getRhEntitlements().stream()
              .map(RhEntitlementV1::getSubscriptionNumber)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
    }

    if (entitlement.getPartnerIdentities() != null) {
      request.awsCustomerId = entitlement.getPartnerIdentities().getAwsCustomerId();
      request.awsCustomerAccountId = entitlement.getPartnerIdentities().getCustomerAwsAccountId();
    }

    if (entitlement.getPurchase() != null) {
      request.azureResourceId = entitlement.getPurchase().getAzureResourceId();
      request.productCode = entitlement.getPurchase().getVendorProductCode();
    }

    return request;
  }

  public static PartnerEntitlementsRequest from(SubscriptionEntity subscription) {
    PartnerEntitlementsRequest request = new PartnerEntitlementsRequest();
    request.redHatSubscriptionNumber = subscription.getSubscriptionNumber();
    if (subscription.getBillingAccountId() != null) {
      var billingProviderIds = subscription.getBillingProviderId().split(";");
      if (subscription.getBillingProvider().equals(BillingProvider.AWS)) {
        request.awsCustomerId = subscription.getBillingAccountId();
        request.awsCustomerAccountId = billingProviderIds[1];
        request.productCode = billingProviderIds[0];
      }
      if (subscription.getBillingProvider().equals(BillingProvider.AZURE)) {
        request.azureResourceId = billingProviderIds[0];
        request.productCode = billingProviderIds[2];
      }
    }
    return request;
  }
}
