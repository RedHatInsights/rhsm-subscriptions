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

import static com.redhat.swatch.contract.config.Channels.OFFERING_SYNC_TASK_TOPIC;
import static com.redhat.swatch.contract.config.Channels.OFFERING_SYNC_TASK_UMB;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.swatch.contract.model.OfferingSyncTask;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
public class OfferingSyncTaskConsumer {

  private final OfferingSyncService service;
  private final boolean umbEnabled;

  public OfferingSyncTaskConsumer(
      OfferingSyncService service, @ConfigProperty(name = "UMB_ENABLED") boolean umbEnabled) {
    this.service = service;
    this.umbEnabled = umbEnabled;
  }

  @Blocking
  @Incoming(OFFERING_SYNC_TASK_TOPIC)
  public void consumeFromTopic(OfferingSyncTask task) {
    String sku = task.getSku();
    log.info("Sync for offeringSku={} triggered by OfferingSyncTask", sku);

    service.syncOffering(sku);
  }

  @Blocking
  @Incoming(OFFERING_SYNC_TASK_UMB)
  public void consumeFromUmb(String productMessageXml) throws JsonProcessingException {
    log.debug("Received message from UMB offering product{}", productMessageXml);
    if (!umbEnabled) {
      log.debug("UMB processing is not enabled");
      return;
    }
    service.syncUmbProductFromXml(productMessageXml);
  }
}
