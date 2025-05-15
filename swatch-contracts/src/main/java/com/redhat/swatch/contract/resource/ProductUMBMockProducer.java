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

/** This class is only to test the productUMBMessageConsumer via ActiveMQ JMS */
@ApplicationScoped
@Slf4j
public class ProductUMBMockProducer {

  @ConfigProperty(name = "SWATCH_PRODUCT_PRODUCER_ENABLED")
  boolean productProducerEnabled;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  @Inject
  @Channel("offering-sync-service-umb-test")
  Emitter<String> productEmitter;

  String product =
      """
    {
      "occurredOn": "2023-05-29T16:00:54.279Z",
      "productCode": "RH00005",
      "productCategory": "Parent SKU",
      "eventType": "Create"
    }
    """;

  void onStart(@Observes StartupEvent ev) { // NOSONAR
    if (productProducerEnabled) {
      scheduler.scheduleWithFixedDelay(
          () -> {
            sendproduct(product);
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

  public void sendproduct(String product) {
    productEmitter.send(product);
    log.info("Done sending product");
  }
}
