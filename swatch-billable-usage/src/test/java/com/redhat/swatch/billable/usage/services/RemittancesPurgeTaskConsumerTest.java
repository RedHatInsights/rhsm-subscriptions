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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.configuration.ApplicationConfiguration;
import com.redhat.swatch.billable.usage.configuration.Channels;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.billable.usage.model.EnabledOrgsResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class RemittancesPurgeTaskConsumerTest {
  @InjectMock ApplicationConfiguration configuration;
  @InjectSpy BillableUsageRemittanceRepository remittanceRepository;
  @InjectSpy RemittancesPurgeTaskConsumer consumer;
  @Inject @Any InMemoryConnector connector;

  private InMemorySource<EnabledOrgsResponse> source;

  @BeforeEach
  void setUp() {
    source = connector.source(Channels.REMITTANCES_PURGE_TASK);
  }

  @Test
  void testWhenConsumeWithoutPolicyThenNothingHappens() {
    when(configuration.getRemittanceRetentionPolicyDuration()).thenReturn(null);
    whenSendResponse(new EnabledOrgsResponse());
    verifyNoInteractions(remittanceRepository);
  }

  @Test
  void testWhenConsumeWithPolicyThenPurgeHappens() {
    String expectedOrgId = "org123";
    when(configuration.getRemittanceRetentionPolicyDuration()).thenReturn(Duration.ofMinutes(5));
    whenSendResponse(new EnabledOrgsResponse().withOrgId(expectedOrgId));
    verify(remittanceRepository)
        .deleteAllByOrgIdAndRemittancePendingDateBefore(eq(expectedOrgId), any());
  }

  private void whenSendResponse(EnabledOrgsResponse response) {
    source.send(response);
    Awaitility.await().untilAsserted(() -> verify(consumer).consume(response));
  }
}
