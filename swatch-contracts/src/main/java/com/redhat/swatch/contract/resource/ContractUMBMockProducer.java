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

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/** This class is only to test the ContractUMBMessageConsumer via ActiveMQ JMS */
@ApplicationScoped
@Slf4j
public class ContractUMBMockProducer {

  @ConfigProperty(name = "SWATCH_CONTRACT_PRODUCER_ENABLED")
  boolean contractProducerEnabled;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  @Inject
  @Channel("contractstest")
  Emitter<String> contractEmitter;

  String contract =
      """
                    {
                      "action" : "contract-updated",
                      "redHatSubscriptionNumber" : "12400374",
                      "currentDimensions" : [ {
                        "dimensionName" : "test_dim_1",
                        "dimensionValue" : "5",
                        "expirationDate" : "2023-02-15T00:00:00Z"
                      }, {
                        "dimensionName" : "test_dim_2",
                        "dimensionValue" : "10",
                        "expirationDate" : "2023-02-15T00:00:00Z"
                      } ],
                      "cloudIdentifiers" : {
                        "awsCustomerId" : "HSwCpt6sqkC",
                        "awsCustomerAccountId" : "568056954830",
                        "productCode" : "6n58d3s3qpvk22dgew2gal7w3"
                      }
                    }
                            """;

  void onStart(@Observes StartupEvent ev) { // NOSONAR
    if (contractProducerEnabled) {
      scheduler.scheduleWithFixedDelay(
          () -> {
            sendContract(contract);
            log.info("done sending");
          },
          0L,
          10L,
          TimeUnit.SECONDS);
    }
  }

  void onStop(@Observes ShutdownEvent ev) { // NOSONAR
    scheduler.shutdown();
  }

  public void sendContract(String contract) {
    contractEmitter.send(contract);
    log.info("Done sending Contract");
  }
}
