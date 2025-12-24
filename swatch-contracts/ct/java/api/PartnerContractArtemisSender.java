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
package api;

import com.redhat.swatch.contract.test.model.Dimension;
import com.redhat.swatch.contract.test.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.test.model.PartnerEntitlementContractCloudIdentifiers;
import domain.BillingProvider;
import domain.Contract;
import java.util.List;

/**
 * Builder for creating and sending PartnerEntitlementContract messages via Artemis. Provides a
 * fluent API for constructing contract messages from test domain objects.
 */
public class PartnerContractArtemisSender {

  private static final String CONTRACTS_CHANNEL =
      "VirtualTopic.services.partner-entitlement-gateway";
  private final ContractsArtemisService artemisService;

  protected PartnerContractArtemisSender(ContractsArtemisService artemisService) {
    this.artemisService = artemisService;
  }

  /**
   * Build a PartnerEntitlementContract object from a Contract domain object.
   *
   * @param contract the contract test data
   * @return PartnerEntitlementContract object
   */
  public PartnerEntitlementContract fromContract(Contract contract) {
    PartnerEntitlementContract message = new PartnerEntitlementContract();

    message.setRedHatSubscriptionNumber(contract.getSubscriptionNumber());
    message.setAction("contract-updated");

    // Build cloud identifiers based on billing provider
    var cloudIdentifiers = new PartnerEntitlementContractCloudIdentifiers();

    if (contract.getBillingProvider() == BillingProvider.AWS) {
      cloudIdentifiers.setAwsCustomerId(contract.getCustomerId());
      cloudIdentifiers.setAwsCustomerAccountId(contract.getBillingAccountId());
      cloudIdentifiers.setProductCode(contract.getProductCode());
    } else if (contract.getBillingProvider() == BillingProvider.AZURE) {
      cloudIdentifiers.setPartner("azure_marketplace");
      cloudIdentifiers.setAzureResourceId(contract.getResourceId());
      cloudIdentifiers.setAzureOfferId(contract.getProductCode());
      cloudIdentifiers.setPlanId(contract.getPlanId());
    }

    message.setCloudIdentifiers(cloudIdentifiers);

    // Build dimensions from contract metrics
    List<Dimension> dimensions =
        contract.getContractMetrics().entrySet().stream()
            .map(
                entry -> {
                  Dimension dimension = new Dimension();
                  dimension.setDimensionName(entry.getKey());
                  dimension.setDimensionValue(String.valueOf(entry.getValue().intValue()));
                  dimension.setExpirationDate(contract.getEndDate());
                  return dimension;
                })
            .toList();

    if (!dimensions.isEmpty()) {
      message.setCurrentDimensions(dimensions);
    }

    return message;
  }

  /**
   * Build and send a PartnerEntitlementContract message from a Contract domain object.
   *
   * @param contract the contract test data
   */
  public void send(Contract contract) {
    PartnerEntitlementContract message = fromContract(contract);
    artemisService.sendAsJson(CONTRACTS_CHANNEL, message);
  }
}
