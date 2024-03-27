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
package org.candlepin.subscriptions.tally.tasks;

import static org.candlepin.subscriptions.tally.TallyWorkerConfiguration.ENABLED_ORGS_KAFKA_LISTENER_CONTAINER_FACTORY_BEAN;
import static org.candlepin.subscriptions.tally.TallyWorkerConfiguration.ENABLED_ORGS_TOPIC_PROPERTIES_BEAN;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.json.EnabledOrgsRequest;
import org.candlepin.subscriptions.json.EnabledOrgsResponse;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class EnabledOrgsMessageConsumer extends SeekableKafkaConsumer {

  private final Set<String> targetTopicsAllowed;
  private final OrgConfigRepository repository;
  private final KafkaTemplate<String, EnabledOrgsResponse> producer;

  @Autowired
  public EnabledOrgsMessageConsumer(
      @Qualifier(ENABLED_ORGS_TOPIC_PROPERTIES_BEAN) TaskQueueProperties enabledOrgsTopicProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      @Value("${rhsm-subscriptions.enabled-orgs.target-topics-allowed}")
          Set<String> targetTopicsAllowed,
      OrgConfigRepository repository,
      KafkaTemplate<String, EnabledOrgsResponse> producer) {
    super(enabledOrgsTopicProperties, kafkaConsumerRegistry);
    this.targetTopicsAllowed = targetTopicsAllowed;
    this.repository = repository;
    this.producer = producer;
  }

  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = ENABLED_ORGS_KAFKA_LISTENER_CONTAINER_FACTORY_BEAN)
  @Transactional(noRollbackFor = RuntimeException.class)
  public void receive(@Payload EnabledOrgsRequest request) {
    if (!targetTopicsAllowed.contains(request.getTargetTopic())) {
      log.warn(
          "Ignoring enabled organizations request for target topic '{}' because it's not allowed. List of allowed target topics are: '{}'",
          request.getTargetTopic(),
          targetTopicsAllowed);
      return;
    }

    log.info("Sending all enabled organizations to topic '{}'", request.getTargetTopic());
    AtomicInteger requests = new AtomicInteger(0);
    try (var organizations = repository.findSyncEnabledOrgs()) {
      organizations.forEach(
          orgId -> {
            producer.send(request.getTargetTopic(), new EnabledOrgsResponse().withOrgId(orgId));
            requests.incrementAndGet();
          });
    }

    log.info(
        "Sent {} messages with the organization ID to topic '{}'",
        requests.get(),
        request.getTargetTopic());
  }
}
