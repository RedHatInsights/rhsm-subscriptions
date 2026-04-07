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
package com.redhat.swatch.contract.resource;

import static com.redhat.swatch.contract.config.Channels.CONTRACTS_FROM_GATEWAY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.utils.MessageUtils;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
public class PartnerEntitlementKafkaMessageConsumer {

  @Inject ObjectMapper mapper;

  @Blocking
  @Incoming(CONTRACTS_FROM_GATEWAY)
  public void consumeMessage(Object dtoContract) {
    log.debug("IT Partner Kafka consumer was called");
    if (dtoContract == null) {
      return;
    }

    String dtoContractString = MessageUtils.toString(dtoContract);
    if (dtoContractString == null) {
      log.error(
          "Unsupported message type: {}. Expected byte[] or String",
          dtoContract.getClass().getName());
      return;
    }

    consumeContract(dtoContractString);
  }

  public void consumeContract(String dtoContract) {
    log.info(dtoContract);

    try {
      PartnerEntitlementContract contract =
          mapper.readValue(dtoContract, PartnerEntitlementContract.class);

      String awsCustomerAccountId = null;
      String productCode = null;
      String azureResourceId = null;

      if (contract.getCloudIdentifiers() != null) {
        awsCustomerAccountId = contract.getCloudIdentifiers().getAwsCustomerAccountId();
        productCode = contract.getCloudIdentifiers().getProductCode();
        azureResourceId = contract.getCloudIdentifiers().getAzureResourceId();
      }

      log.info(
          "IT Partner message consumed: source=kafka, action={}, "
              + "awsCustomerAccountId={}, productCode={}, azureResourceId={}, "
              + "redHatSubscriptionNumber={}",
          contract.getAction(),
          awsCustomerAccountId,
          productCode,
          azureResourceId,
          contract.getRedHatSubscriptionNumber());

    } catch (Exception e) {
      log.warn("Unable to read IT Partner Kafka message from JSON.", e);
    }
  }
}
