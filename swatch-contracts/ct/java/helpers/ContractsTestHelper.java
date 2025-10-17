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
package helpers;

import com.redhat.swatch.contract.test.model.ContractRequest;
import com.redhat.swatch.contract.test.model.DimensionV1;
import com.redhat.swatch.contract.test.model.PartnerEntitlementV1;
import com.redhat.swatch.contract.test.model.PartnerEntitlementV1EntitlementDates;
import com.redhat.swatch.contract.test.model.PartnerIdentityV1;
import com.redhat.swatch.contract.test.model.PurchaseV1;
import com.redhat.swatch.contract.test.model.RhEntitlementV1;
import com.redhat.swatch.contract.test.model.SaasContractV1;
import java.util.List;
import java.util.Objects;
import model.ContractTestData;

/** Helper class for contract related operations in component tests. */
public final class ContractsTestHelper {

  private ContractsTestHelper() {}

  public static ContractRequest buildContractRequest(ContractTestData contractData) {
    Objects.requireNonNull(contractData, "contractData must not be null");

    PartnerEntitlementV1 partnerEntitlement = buildPartnerEntitlement(contractData);

    return new ContractRequest()
        .partnerEntitlement(partnerEntitlement)
        .subscriptionId(contractData.subscriptionId());
  }

  private static PartnerEntitlementV1 buildPartnerEntitlement(ContractTestData contractData) {
    return new PartnerEntitlementV1()
        .rhAccountId(contractData.orgId())
        .sourcePartner(contractData.sourcePartner())
        .entitlementDates(buildEntitlementDates(contractData))
        .rhEntitlements(List.of(buildRhEntitlement(contractData)))
        .purchase(buildPurchase(contractData))
        .partnerIdentities(buildPartnerIdentities(contractData));
  }

  private static DimensionV1 buildDimension(ContractTestData contractData) {
    return new DimensionV1().name(contractData.metricName()).value(contractData.metricValue());
  }

  private static PurchaseV1 buildPurchase(ContractTestData contractData) {
    SaasContractV1 saasContract =
        new SaasContractV1().dimensions(List.of(buildDimension(contractData)));

    return new PurchaseV1()
        .vendorProductCode(contractData.productCode())
        .contracts(List.of(saasContract));
  }

  private static PartnerIdentityV1 buildPartnerIdentities(ContractTestData contractData) {
    return new PartnerIdentityV1()
        .awsCustomerId(contractData.awsCustomerId())
        .sellerAccountId(contractData.sellerAccountId())
        .customerAwsAccountId(contractData.awsAccountId());
  }

  private static PartnerEntitlementV1EntitlementDates buildEntitlementDates(
      ContractTestData contractData) {
    return new PartnerEntitlementV1EntitlementDates()
        .startDate(contractData.startDate())
        .endDate(contractData.endDate());
  }

  private static RhEntitlementV1 buildRhEntitlement(ContractTestData contractData) {
    return new RhEntitlementV1()
        .subscriptionNumber(contractData.subscriptionNumber())
        .sku(contractData.sku());
  }
}
