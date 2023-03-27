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
import com.redhat.swatch.contract.exception.CreateContractException;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.service.ContractService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ContractUMBMessageConsumer implements Runnable {
  @Inject ContractService service;
  @Inject ConnectionFactory connectionFactory;

  private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

  void onStart(@Observes StartupEvent ev) {
    scheduler.submit(this);
  }

  void onStop(@Observes ShutdownEvent ev) {
    scheduler.shutdown();
  }

  @Override
  public void run() {
    log.trace("Running consumer");
    try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
      log.trace("Creating JMS Consumer");
      JMSConsumer consumer = context.createConsumer(context.createQueue("umb-contract"));
      log.trace("Entering loop");
      while (true) { // NOSONAR
        log.trace("Receiving");
        Message message = consumer.receive();
        StatusResponse response = consumeContract(message.getBody(String.class));
        log.trace(response.toString());
        // Acknowledge the incoming message
        message.acknowledge();
      }
    } catch (JMSException | JsonProcessingException e) {
      throw new CreateContractException(e.getMessage());
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
