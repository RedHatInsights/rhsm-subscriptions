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

import static org.candlepin.subscriptions.tally.TallyWorkerConfiguration.ENABLED_ORGS_TOPIC_PROPERTIES_BEAN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.json.EnabledOrgsRequest;
import org.candlepin.subscriptions.json.EnabledOrgsResponse;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@SpringBootTest
@ActiveProfiles({"kafka-queue", "worker", "test"})
class EnabledOrgsMessageConsumerTest implements ExtendWithEmbeddedKafka {

  private static final String ORG_ID = "123456";

  @Autowired
  @Qualifier(ENABLED_ORGS_TOPIC_PROPERTIES_BEAN)
  TaskQueueProperties enabledOrgsTopicProperties;

  @Autowired KafkaProperties kafkaProperties;
  @MockBean OrgConfigRepository repository;
  @MockBean KafkaTemplate<String, EnabledOrgsResponse> producer;
  @SpyBean EnabledOrgsMessageConsumer consumer;
  KafkaTemplate<String, EnabledOrgsRequest> kafkaTemplate;
  EnabledOrgsRequest request;

  @Transactional
  @BeforeEach
  public void setup() {
    Mockito.reset(consumer);
    Map<String, Object> properties = kafkaProperties.buildProducerProperties(null);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    var factory = new DefaultKafkaProducerFactory<String, EnabledOrgsRequest>(properties);
    kafkaTemplate = new KafkaTemplate<>(factory);

    // mock the repository
    when(repository.findSyncEnabledOrgs()).thenReturn(Stream.of(ORG_ID));
  }

  @Test
  void testRequestIsIgnoredWhenUsingNotAllowedTargetTopic() {
    givenRequestWithTargetTopic("wrong");
    whenSendRequest();
    thenRepositoryWasNotUsed();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "platform.rhsm-subscriptions.subscription-prune-task",
        "platform.rhsm-subscriptions.subscription-sync-task"
      })
  void testRequestWithAllowedTargetTopic(String targetTopic) {
    givenRequestWithTargetTopic(targetTopic);
    whenSendRequest();
    thenRepositoryWasUsed();
    thenResponseIsSentToTargetTopic(targetTopic);
  }

  private void givenRequestWithTargetTopic(String targetTopic) {
    request = new EnabledOrgsRequest().withTargetTopic(targetTopic);
  }

  private void whenSendRequest() {
    kafkaTemplate.send(enabledOrgsTopicProperties.getTopic(), request);
    // wait until message is processed
    Awaitility.await().untilAsserted(() -> verify(consumer).receive(request));
  }

  private void thenRepositoryWasNotUsed() {
    Mockito.verifyNoInteractions(repository);
  }

  private void thenRepositoryWasUsed() {
    verify(repository).findSyncEnabledOrgs();
  }

  private void thenResponseIsSentToTargetTopic(String targetTopic) {
    verify(producer).send(targetTopic, new EnabledOrgsResponse().withOrgId(ORG_ID));
  }
}
