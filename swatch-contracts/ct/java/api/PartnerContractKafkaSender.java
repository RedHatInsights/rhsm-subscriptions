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

import static com.redhat.swatch.component.tests.utils.Topics.PARTNER_ENTITLEMENT_GATEWAY;

import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.contract.test.model.Dimension;
import com.redhat.swatch.contract.test.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.test.model.PartnerEntitlementContractCloudIdentifiers;
import domain.BillingProvider;
import domain.Contract;
import java.util.List;

/**
 * Builder for creating and sending PartnerEntitlementContract messages via the IT Partner Gateway
 * Kafka topic. Mirrors the structure of PartnerContractArtemisSender for UMB.
 */
public class PartnerContractKafkaSender {

  private final KafkaBridgeService kafkaBridge;

  public PartnerContractKafkaSender(KafkaBridgeService kafkaBridge) {
    this.kafkaBridge = kafkaBridge;
  }

  public PartnerEntitlementContract buildMessage(Contract contract) {
    PartnerEntitlementContract message = new PartnerEntitlementContract();

    if (contract.getBillingProvider() == BillingProvider.AWS) {
      message.setRedHatSubscriptionNumber(contract.getSubscriptionNumber());
    }
    message.setAction("contract-updated");

    var cloudIdentifiers = new PartnerEntitlementContractCloudIdentifiers();
    if (contract.getBillingProvider() == BillingProvider.AWS) {
      cloudIdentifiers.setAwsCustomerId(contract.getCustomerId());
      cloudIdentifiers.setAwsCustomerAccountId(contract.getBillingAccountId());
      cloudIdentifiers.setProductCode(contract.getProductCode());
    } else if (contract.getBillingProvider() == BillingProvider.AZURE) {
      cloudIdentifiers.setPartner("azure_marketplace");
      cloudIdentifiers.setAzureResourceId(contract.getResourceId());
      cloudIdentifiers.setAzureTenantId(contract.getClientId());
      cloudIdentifiers.setAzureOfferId(contract.getProductCode());
      cloudIdentifiers.setPlanId(contract.getPlanId());
    }
    message.setCloudIdentifiers(cloudIdentifiers);

    List<Dimension> dimensions =
        contract.getContractMetrics().entrySet().stream()
            .map(
                entry -> {
                  Dimension dim = new Dimension();
                  dim.setDimensionName(entry.getKey());
                  dim.setDimensionValue(String.valueOf(entry.getValue().intValue()));
                  dim.setExpirationDate(contract.getEndDate());
                  return dim;
                })
            .toList();

    if (!dimensions.isEmpty()) {
      message.setCurrentDimensions(dimensions);
    }

    return message;
  }

  public void send(Contract contract) {
    kafkaBridge.produceKafkaMessage(PARTNER_ENTITLEMENT_GATEWAY, buildMessage(contract));
  }

  public void sendRaw(Object rawMessage) {
    kafkaBridge.produceKafkaMessage(PARTNER_ENTITLEMENT_GATEWAY, rawMessage);
  }
}
