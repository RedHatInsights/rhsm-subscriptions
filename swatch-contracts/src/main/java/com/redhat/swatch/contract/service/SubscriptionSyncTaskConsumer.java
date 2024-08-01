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

import static com.redhat.swatch.contract.config.Channels.SUBSCRIPTION_SYNC_TASK_TOPIC;
import static com.redhat.swatch.contract.config.Channels.SUBSCRIPTION_SYNC_TASK_UMB;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redhat.swatch.contract.model.EnabledOrgsResponse;
import com.redhat.swatch.contract.product.umb.CanonicalMessage;
import com.redhat.swatch.contract.product.umb.UmbSubscription;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class SubscriptionSyncTaskConsumer {

  private final SubscriptionSyncService service;
  private final boolean umbEnabled;
  private final XmlMapper xmlMapper;

  public SubscriptionSyncTaskConsumer(
      SubscriptionSyncService service, @ConfigProperty(name = "UMB_ENABLED") boolean umbEnabled) {
    this.service = service;
    this.umbEnabled = umbEnabled;
    this.xmlMapper = CanonicalMessage.createMapper();
  }

  @Blocking
  @Incoming(SUBSCRIPTION_SYNC_TASK_TOPIC)
  public void consumeFromTopic(EnabledOrgsResponse message) {
    log.info("Received task for subscription sync with org ID: {}", message.getOrgId());
    service.reconcileSubscriptionsWithSubscriptionService(message.getOrgId(), false);
  }

  @Blocking
  @Incoming(SUBSCRIPTION_SYNC_TASK_UMB)
  public void consumeFromUmb(String subscriptionMessageXml) throws JsonProcessingException {
    log.debug("Received message from UMB {}", subscriptionMessageXml);
    if (!umbEnabled) {
      return;
    }
    CanonicalMessage subscriptionMessage =
        xmlMapper.readValue(subscriptionMessageXml, CanonicalMessage.class);
    UmbSubscription subscription = subscriptionMessage.getPayload().getSync().getSubscription();
    log.info(
        "Received UMB message for subscriptionNumber={} webCustomerId={} startDate={} endDate={}",
        subscription.getSubscriptionNumber(),
        subscription.getWebCustomerId(),
        subscription.getEffectiveStartDate(),
        subscription.getEffectiveEndDate());
    service.saveUmbSubscription(subscription);
  }
}
