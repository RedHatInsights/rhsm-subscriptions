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
package org.candlepin.subscriptions.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.umb.CanonicalMessage;
import org.candlepin.subscriptions.umb.UmbProperties;
import org.candlepin.subscriptions.umb.UmbSubscription;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("capacity-ingress")
public class SubscriptionWorker extends SeekableKafkaConsumer {

  SubscriptionSyncController subscriptionSyncController;

  private final UmbProperties umbProperties;
  private final XmlMapper xmlMapper;

  protected SubscriptionWorker(
      @Qualifier("syncSubscriptionTasks") TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      SubscriptionSyncController subscriptionSyncController,
      UmbProperties umbProperties) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.subscriptionSyncController = subscriptionSyncController;
    this.umbProperties = umbProperties;
    this.xmlMapper = CanonicalMessage.createMapper();
    if (!umbProperties.isProcessingEnabled()) {
      log.warn("Message processing disabled. Messages will be acked and ignored.");
    }
  }

  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "subscriptionSyncListenerContainerFactory")
  public void receive(SyncSubscriptionsTask syncSubscriptionsTask, Acknowledgment acknowledgment) {
    // NOTE(khowell): this sync can take a while when there are significant changes, but swatch is
    // okay with eventual consistency on this operation, so we acknowledge early
    acknowledgment.acknowledge();
    log.info(
        "Subscription Worker is syncing subs with values: {} ", syncSubscriptionsTask.toString());
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService(
        syncSubscriptionsTask.getOrgId(), false);
  }

  @JmsListener(destination = "#{@umbProperties.subscriptionTopic}")
  public void receive(String subscriptionMessageXml) throws JsonProcessingException {
    log.debug("Received message from UMB {}", subscriptionMessageXml);
    if (!umbProperties.isProcessingEnabled()) {
      return;
    }
    CanonicalMessage subscriptionMessage =
        xmlMapper.readValue(subscriptionMessageXml, CanonicalMessage.class);
    UmbSubscription subscription = subscriptionMessage.getPayload().getSync().getSubscription();
    log.info(
        "Received UMB message for subscriptionNumber={} webCustomerId={}",
        subscription.getSubscriptionNumber(),
        subscription.getWebCustomerId());
    subscriptionSyncController.saveUmbSubscription(subscription);
  }
}
