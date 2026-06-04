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
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base class for building ContractRequest objects from Contract domain objects. Subclasses
 * must implement provider-specific methods for source partner, partner identities, and purchase
 * information.
 */
public abstract class ContractRequestMapper {

  private static final Map<BillingProvider, ContractRequestMapper> MAPPERS =
      Map.of(
          BillingProvider.AWS,
          new AwsContractRequestMapper(),
          BillingProvider.AZURE,
          new AzureContractRequestMapper());

  /** Returns the source partner identifier (e.g., "aws_marketplace", "azure_marketplace"). */
  protected abstract String getSourcePartner();

  /** Returns the partner identities specific to the billing provider. */
  protected abstract PartnerIdentityV1 buildPartnerIdentities(Contract contract);

  /** Returns the purchase information specific to the billing provider. */
  protected abstract PurchaseV1 buildPurchase(Contract contract);

  public static ContractRequest buildContractRequest(Contract contract) {
    Objects.requireNonNull(contract, "contract must not be null");
    var mapper = MAPPERS.get(contract.getBillingProvider());
    if (mapper == null) {
      throw new UnsupportedOperationException(
          contract.getBillingProvider() + " is not supported yet!");
    }

    return mapper.build(contract);
  }

  public ContractRequest build(Contract contract) {
    PartnerEntitlementV1 partnerEntitlement = buildPartnerEntitlement(contract);

    return new ContractRequest()
        .partnerEntitlement(partnerEntitlement)
        .subscriptionId(contract.getSubscriptionId());
  }

  protected PartnerEntitlementV1 buildPartnerEntitlement(Contract contract) {
    return new PartnerEntitlementV1()
        .rhAccountId(contract.getOrgId())
        .sourcePartner(getSourcePartner())
        .entitlementDates(buildEntitlementDates(contract))
        .rhEntitlements(List.of(buildRhEntitlement(contract)))
        .purchase(buildPurchase(contract))
        .partnerIdentities(buildPartnerIdentities(contract));
  }

  protected static DimensionV1 buildDimension(String metricName, double metricValue) {
    return new DimensionV1().name(metricName).value("" + metricValue);
  }

  protected static List<DimensionV1> buildDimensions(Contract contract) {
    return contract.getContractMetrics().entrySet().stream()
        .map(e -> buildDimension(e.getKey(), e.getValue()))
        .toList();
  }

  protected static SaasContractV1 buildSaasContract(Contract contract) {
    return new SaasContractV1().dimensions(buildDimensions(contract));
  }

  protected static PartnerEntitlementV1EntitlementDates buildEntitlementDates(Contract contract) {
    return new PartnerEntitlementV1EntitlementDates()
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate());
  }

  protected static RhEntitlementV1 buildRhEntitlement(Contract contract) {
    return new RhEntitlementV1()
        .subscriptionNumber(contract.getSubscriptionNumber())
        .sku(contract.getOffering().getSku());
  }
}
