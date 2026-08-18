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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.config.Channels.IT_OFFERING_SYNC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contract.config.FeatureFlags;
import com.redhat.swatch.contract.openapi.model.OperationalProductEvent;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
public class ProductStatusKafkaMessageConsumer {

  @Inject FeatureFlags featureFlags;
  @Inject ObjectMapper mapper;

  @Blocking
  @Incoming(IT_OFFERING_SYNC)
  public void consumeMessage(String message) throws JsonProcessingException {
    log.debug("IT Product Kafka consumer was called");
    if (message == null) {
      return;
    }
    if (!featureFlags.isProductServiceKafkaConsumerEnabled()) {
      log.debug("IT Product Kafka consumer is disabled by feature flag.");
      return;
    }
    consumeProduct(message);
  }

  public void consumeProduct(String message) throws JsonProcessingException {
    OperationalProductEvent productEvent;
    try {
      productEvent = mapper.readValue(message, OperationalProductEvent.class);
    } catch (JsonProcessingException e) {
      log.warn("Unable to read IT Product Kafka message from JSON.", e);
      throw e;
    }

    log.info(
        "IT Product message consumed: source=kafka, productCode={}, eventType={}, "
            + "productCategory={}, childSku={}",
        productEvent.getProductCode(),
        productEvent.getEventType(),
        productEvent.getProductCategory(),
        isChildSku(productEvent.getProductCode()));
  }

  private static boolean isChildSku(String productCode) {
    return productCode != null && productCode.startsWith("SVC");
  }
}
