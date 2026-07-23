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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contract.config.FeatureFlags;
import com.redhat.swatch.contract.model.PartnerEntitlementsRequest;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.service.ContractService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
public class ContractsPartnerEntitlementMessageConsumer {

  @Inject FeatureFlags featureFlags;
  @Inject ObjectMapper mapper;
  @Inject ContractService service;

  @Blocking
  @Incoming(CONTRACTS_FROM_GATEWAY)
  public void consumeMessage(String dtoContract) throws JsonProcessingException {
    log.debug("IT Partner Kafka consumer was called");
    if (dtoContract == null) {
      return;
    }
    if (!featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled()) {
      log.debug("IT Partner Kafka consumer for contracts is disabled by feature flag.");
      return;
    }
    consumeContract(dtoContract);
  }

  public void consumeContract(String dtoContract) throws JsonProcessingException {
    log.info(dtoContract);

    PartnerEntitlementContract contract;
    try {
      contract = mapper.readValue(dtoContract, PartnerEntitlementContract.class);
    } catch (JsonProcessingException e) {
      log.warn("Unable to read IT Partner Kafka message from JSON.", e);
      throw e;
    }

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

    var response = service.createPartnerContract(PartnerEntitlementsRequest.from(contract));
    log.debug("kafka response: {}", response);
  }
}
