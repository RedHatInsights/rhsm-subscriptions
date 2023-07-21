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
package org.candlepin.subscriptions.conduit.inventory.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.candlepin.subscriptions.conduit.inventory.ConduitFacts;
import org.candlepin.subscriptions.conduit.inventory.InventoryService;
import org.candlepin.subscriptions.conduit.inventory.InventoryServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.support.RetryTemplate;

/**
 * An InventoryService implementation that includes a Kafka producer that is capable of sending
 * messages to the inventory service's Kafka instance. A message is sent as soon as a host update is
 * scheduled.
 */
public class KafkaEnabledInventoryService extends InventoryService {

  private static final Logger log = LoggerFactory.getLogger(KafkaEnabledInventoryService.class);

  private final KafkaTemplate<String, CreateUpdateHostMessage> producer;
  private final String hostIngressTopic;
  private final Counter sentMessageCounter;
  private final Counter failedMessageCounter;
  private final Counter messageSizeCounter;
  private final RetryTemplate retryTemplate;

  @SuppressWarnings("java:S3740")
  public KafkaEnabledInventoryService(
      InventoryServiceProperties serviceProperties,
      KafkaTemplate<String, CreateUpdateHostMessage> producer,
      MeterRegistry meterRegistry,
      RetryTemplate retryTemplate) {
    // Flush updates as soon as they get scheduled.
    super(serviceProperties, 1);
    this.producer = producer;
    this.hostIngressTopic = serviceProperties.getKafkaHostIngressTopic();
    this.sentMessageCounter = meterRegistry.counter("rhsm-conduit.send.inventory-message");
    this.failedMessageCounter = meterRegistry.counter("rhsm.conduit.send.inventory-message.failed");
    this.messageSizeCounter = meterRegistry.counter("rhsm-conduit.inventory-message.size.bytes");
    this.retryTemplate = retryTemplate;
  }

  @Override
  public void scheduleHostUpdate(ConduitFacts facts) {
    this.sendHostUpdate(Collections.singletonList(facts));
  }

  @Override
  public void flushHostUpdates() {
    /* intentionally left blank */
  }

  @Override
  protected void sendHostUpdate(List<ConduitFacts> facts) {
    if (facts.isEmpty()) {
      log.info("No facts to report!");
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    for (ConduitFacts factSet : facts) {
      // Attempt to send the host create/update message. If the send fails for any reason,
      // log the error and move on to the next one.
      try {
        log.debug(
            "Sending host inventory message: {}:{}:{}",
            factSet.getAccountNumber(),
            factSet.getOrgId(),
            factSet.getSubscriptionManagerId());
        // After the retry limit is reached, the exception will bubble up to the catch clause and
        // the
        // loop will move on.
        retryTemplate.execute(
            context -> {
              sendToKafka(now, factSet);
              return null;
            });
      } catch (Exception e) {
        recordFailure(e);
      }
    }
  }

  private void sendToKafka(OffsetDateTime now, ConduitFacts factSet) {
    CreateUpdateHostMessage message = new CreateUpdateHostMessage(createHost(factSet, now));
    message.setMetadata("request_id", UUID.randomUUID().toString());
    producer.send(hostIngressTopic, message).handle(this::handleResult);
  }

  private SendResult<String, CreateUpdateHostMessage> handleResult(
      SendResult<String, CreateUpdateHostMessage> result, Throwable throwable) {
    if (throwable != null) {
      recordFailure(throwable);
    } else {
      recordSuccess(result);
    }
    return result;
  }

  private void recordFailure(Throwable throwable) {
    log.error("Unable to send host create/update message.", throwable);
    failedMessageCounter.increment();
  }

  @SuppressWarnings("java:S3740")
  private void recordSuccess(SendResult<String, CreateUpdateHostMessage> result) {
    sentMessageCounter.increment();
    RecordMetadata metadata = result.getRecordMetadata();
    double messageSize = (double) metadata.serializedKeySize() + metadata.serializedValueSize();
    messageSizeCounter.increment(messageSize);
  }
}
