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
package com.redhat.swatch.billable.usage.services;

import static com.redhat.swatch.billable.usage.configuration.Channels.BILLABLE_USAGE_DLT;
import static com.redhat.swatch.billable.usage.data.BillableUsageRemittanceFilter.fromUsage;
import static com.redhat.swatch.billable.usage.services.BillableUsageDeadLetterConsumer.RETRY_AFTER_HEADER;
import static java.util.Optional.of;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class BillableUsageDeadLetterConsumerTest {
  @InjectMock BillableUsageRemittanceRepository billableUsageRemittanceRepository;
  @InjectSpy BillableUsageDeadLetterConsumer consumer;
  @Inject @Any InMemoryConnector connector;

  private InMemorySource<Message<BillableUsage>> source;
  private final BillableUsage usage = givenUsage();

  @BeforeEach
  public void setup() {
    source = connector.source(BILLABLE_USAGE_DLT);
  }

  @Test
  void testConsumeDeadLetterWhenRetryAfterIsSet() {
    BillableUsageRemittanceEntity entity = givenExistingRemittanceEntityForUsage();
    whenSendMessageWithRetryAfter(OffsetDateTime.now().minusMinutes(5));
    thenEntityShouldBeUpdated(entity);
  }

  @Test
  void testConsumeDeadLetterWhenRetryAfterIsAbsent() {
    BillableUsageRemittanceEntity entity = givenExistingRemittanceEntityForUsage();
    whenSendMessageWithoutRetryAfter();
    thenEntityShouldNotBeUpdated(entity);
  }

  private BillableUsageRemittanceEntity givenExistingRemittanceEntityForUsage() {
    BillableUsageRemittanceEntity entity = new BillableUsageRemittanceEntity();
    when(billableUsageRemittanceRepository.findOne(fromUsage(usage))).thenReturn(of(entity));
    return entity;
  }

  private BillableUsage givenUsage() {
    BillableUsage usage = new BillableUsage();
    usage.setBillingProvider(BillableUsage.BillingProvider.AWS);
    usage.setSnapshotDate(OffsetDateTime.now());
    usage.setMetricId(MetricIdUtils.getCores().toString());
    usage.setSla(BillableUsage.Sla.STANDARD);
    usage.setUsage(BillableUsage.Usage.PRODUCTION);
    return usage;
  }

  private void whenSendMessageWithoutRetryAfter() {
    source.send(Message.of(usage));
    await().untilAsserted(() -> verify(consumer, times(1)).consume(any(), any()));
  }

  private void whenSendMessageWithRetryAfter(OffsetDateTime retryAfter) {
    source.send(
        Message.of(usage)
            .addMetadata(
                OutgoingKafkaRecordMetadata.<String>builder()
                    .withHeaders(
                        new RecordHeaders()
                            .add(RETRY_AFTER_HEADER, retryAfter.toString().getBytes()))
                    .build()));
  }

  private void thenEntityShouldBeUpdated(BillableUsageRemittanceEntity entity) {
    await().untilAsserted(() -> verify(billableUsageRemittanceRepository).persist(entity));
  }

  private void thenEntityShouldNotBeUpdated(BillableUsageRemittanceEntity entity) {
    verify(billableUsageRemittanceRepository, times(0)).persist(entity);
  }
}
