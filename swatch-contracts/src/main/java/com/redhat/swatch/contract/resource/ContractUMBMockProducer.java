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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** This class is only to test the ContractUMBMessageConsumer via ActiveMQ JMS */
@ApplicationScoped
@Slf4j
public class ContractUMBMockProducer {

  @Inject ConnectionFactory connectionFactory;

  @ConfigProperty(name = "SWATCH_JMS_PRODUCER_ENABLED")
  boolean jmsProducerEnabled;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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
                        "awsCustomerId" : "896801664647",
                        "productCode" : "1e1234el8qbwnqimt2wogc5n"
                      }
                    }
                            """;

  void onStart(@Observes StartupEvent ev) {
    if (jmsProducerEnabled) {
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

  void onStop(@Observes ShutdownEvent ev) {
    scheduler.shutdown();
  }

  public void sendContract(String contract) {
    try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
      context.createProducer().send(context.createQueue("umb-contract"), contract);
    }
    log.info("Done sending Contract");
  }
}
