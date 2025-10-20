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
package utils;

import com.redhat.swatch.contract.test.model.ContractRequest;
import com.redhat.swatch.contract.test.model.DimensionV1;
import com.redhat.swatch.contract.test.model.PartnerEntitlementV1;
import com.redhat.swatch.contract.test.model.PartnerEntitlementV1EntitlementDates;
import com.redhat.swatch.contract.test.model.PartnerIdentityV1;
import com.redhat.swatch.contract.test.model.PurchaseV1;
import com.redhat.swatch.contract.test.model.RhEntitlementV1;
import com.redhat.swatch.contract.test.model.SaasContractV1;
import domain.BillingProvider;
import domain.Contract;
import java.util.List;
import java.util.Objects;

public final class ContractRequestMapper {

  private static final String SOURCE_PARTNER = "aws_marketplace";

  private ContractRequestMapper() {}

  public static ContractRequest buildContractRequest(Contract contract) {
    Objects.requireNonNull(contract, "contract must not be null");
    if (contract.getBillingProvider() != BillingProvider.AWS) {
      throw new UnsupportedOperationException(
          contract.getBillingProvider() + " is not supported yet!");
    }

    PartnerEntitlementV1 partnerEntitlement = buildPartnerEntitlement(contract);

    return new ContractRequest()
        .partnerEntitlement(partnerEntitlement)
        .subscriptionId(contract.getSubscriptionId());
  }

  private static PartnerEntitlementV1 buildPartnerEntitlement(Contract contract) {
    return new PartnerEntitlementV1()
        .rhAccountId(contract.getOrgId())
        .sourcePartner(SOURCE_PARTNER)
        .entitlementDates(buildEntitlementDates(contract))
        .rhEntitlements(List.of(buildRhEntitlement(contract)))
        .purchase(buildPurchase(contract))
        .partnerIdentities(buildPartnerIdentities(contract));
  }

  private static DimensionV1 buildDimension(String metricName, double metricValue) {
    return new DimensionV1().name(metricName).value("" + metricValue);
  }

  private static PurchaseV1 buildPurchase(Contract contract) {
    SaasContractV1 saasContract =
        new SaasContractV1()
            .dimensions(
                contract.getContractCapacity().entrySet().stream()
                    .map(e -> buildDimension(e.getKey(), e.getValue()))
                    .toList());

    return new PurchaseV1()
        .vendorProductCode(contract.getProductCode())
        .contracts(List.of(saasContract));
  }

  private static PartnerIdentityV1 buildPartnerIdentities(Contract contract) {
    return new PartnerIdentityV1()
        .awsCustomerId(contract.getCustomerId())
        .sellerAccountId(contract.getSellerAccountId())
        .customerAwsAccountId(contract.getBillingAccountId());
  }

  private static PartnerEntitlementV1EntitlementDates buildEntitlementDates(Contract contract) {
    return new PartnerEntitlementV1EntitlementDates()
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate());
  }

  private static RhEntitlementV1 buildRhEntitlement(Contract contract) {
    return new RhEntitlementV1()
        .subscriptionNumber(contract.getSubscriptionNumber())
        .sku(contract.getOffering().getSku());
  }
}
