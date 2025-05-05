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
package com.redhat.swatch.contract.product.umb;

import static com.redhat.swatch.contract.config.Channels.OFFERING_SYNC_TASK_SERVICE_UMB;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.openapi.model.OperationalProductEvent;
import com.redhat.swatch.contract.product.UpstreamProductData;
import com.redhat.swatch.contract.service.OfferingSyncService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.MDC;

@ApplicationScoped
@Slf4j
public class ProductStatusUMBMessageConsumer {

  @Inject ObjectMapper mapper;
  @Inject OfferingSyncService service;

  @ConfigProperty(name = "UMB_ENABLED")
  boolean umbEnabled;

  @Blocking
  @Incoming(OFFERING_SYNC_TASK_SERVICE_UMB)
  public void consumeMessage(LinkedHashMap message) {

    log.info(
        "Received message from UMB offering sync service.  product {} - {}",
        message,
        message.getClass().getName());
    if (umbEnabled) {
      try {
        MDC.put(UpstreamProductData.REQUEST_SOURCE, Channels.OFFERING_SYNC_TASK_SERVICE_UMB);
        consumeProduct(message);
      } finally {
        MDC.remove(UpstreamProductData.REQUEST_SOURCE);
      }
    } else {
      log.debug("UMB processing is not enabled");
    }
  }

  public SyncResult consumeProduct(LinkedHashMap message) {
    try {

      OperationalProductEvent productEvent =
          mapper.convertValue(message, OperationalProductEvent.class);

      return service.syncUmbProductFromEvent(productEvent);
    } catch (Exception e) {
      log.warn("Unable to read UMB message from JSON.", e);
      return SyncResult.FAILED;
    }
  }
}
