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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.swatch.contract.config.UmbConfiguration;
import com.redhat.swatch.contract.exception.CreateContractException;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.service.ContractService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
public class ContractUMBMessageConsumer {

  @Inject ContractService service;
  @Inject UmbConfiguration umbConfiguration;

  @ConfigProperty(name = "CONTRACT_UMB_QUEUE")
  String queueName;

  @ConfigProperty(name = "UMB_ENABLED")
  boolean umbEnabled;


  @Incoming("contracts")
  public void consumeMessage(String dtoContract){
    log.info("Consumer was called");
    if(umbEnabled) {
      try {
        consumeContract(dtoContract);
      } catch (JsonProcessingException e) {
        throw new CreateContractException(e.getMessage());
      }
    }
  }


  public StatusResponse consumeContract(String dtoContract) throws JsonProcessingException {
    // process UMB contract.
    log.info(dtoContract);

    try (Jsonb jsonbMapper = JsonbBuilder.create()) {
      PartnerEntitlementContract contract =
          jsonbMapper.fromJson(dtoContract, PartnerEntitlementContract.class);

      return service.createPartnerContract(contract);
    } catch (Exception e) {
      log.warn("Unable to read UMB message from JSON.", e);
      StatusResponse response = new StatusResponse();
      response.setMessage("Unable to read UMB message from JSON");
      return response;
    }
  }
}
