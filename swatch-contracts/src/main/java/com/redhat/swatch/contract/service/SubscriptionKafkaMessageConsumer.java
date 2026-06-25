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

import static com.redhat.swatch.contract.config.Channels.IT_SUBSCRIPTION_SYNC;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redhat.swatch.contract.config.FeatureFlags;
import com.redhat.swatch.contract.product.umb.CanonicalMessage;
import com.redhat.swatch.contract.product.umb.UmbSubscription;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
public class SubscriptionKafkaMessageConsumer {

  @Inject FeatureFlags featureFlags;

  private final XmlMapper xmlMapper = CanonicalMessage.createMapper();

  @Blocking
  @Incoming(IT_SUBSCRIPTION_SYNC)
  public void consumeFromKafka(String subscriptionMessageXml) {
    log.debug("IT Subscription Kafka consumer was called");
    if (subscriptionMessageXml == null) {
      return;
    }
    if (!featureFlags.isItSubscriptionServiceKafkaConsumerEnabled()) {
      log.debug("IT Subscription Kafka consumer is disabled by feature flag.");
      return;
    }
    consumeSubscription(subscriptionMessageXml);
  }

  public void consumeSubscription(String subscriptionMessageXml) {
    try {
      CanonicalMessage subscriptionMessage =
          xmlMapper.readValue(subscriptionMessageXml, CanonicalMessage.class);
      UmbSubscription subscription = subscriptionMessage.getPayload().getSync().getSubscription();
      log.info(
          "IT Subscription message consumed: source=kafka, "
              + "subscriptionNumber={}, webCustomerId={}, sku={}, quantity={}, "
              + "effectiveStartDate={}, effectiveEndDate={}, terminated={}",
          subscription.getSubscriptionNumber(),
          subscription.getWebCustomerId(),
          subscription.findSku().orElse(null),
          subscription.getQuantity(),
          subscription.getEffectiveStartDate(),
          subscription.getEffectiveEndDate(),
          subscription.findTerminatedStatus().isPresent());
    } catch (Exception e) {
      log.warn("Unable to process IT Subscription Kafka message.", e);
    }
  }
}
