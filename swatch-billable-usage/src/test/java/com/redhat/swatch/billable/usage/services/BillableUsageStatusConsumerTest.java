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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.billable.usage.configuration.Channels;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceErrorCode;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.billable.usage.BillableUsage.ErrorCode;
import org.candlepin.subscriptions.billable.usage.BillableUsage.Status;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class BillableUsageStatusConsumerTest {

  @InjectSpy BillableUsageStatusConsumer consumer;
  @Inject @Any InMemoryConnector connector;
  @InjectSpy BillableUsageRemittanceRepository remittanceRepository;

  private InMemorySource<BillableUsageAggregate> source;

  private static final String ORG_ID = "org123";

  private static OffsetDateTime BILLED_ON =
      OffsetDateTime.of(2024, 5, 10, 10, 20, 5, 0, ZoneOffset.UTC);

  @BeforeEach
  @Transactional
  void setUp() {
    source = connector.source(Channels.BILLABLE_USAGE_STATUS);
    remittanceRepository.deleteAll();
  }

  @Test
  void testWhenConsumeThenUsageStatusUpdatedSucceeded() {
    var existingRemittances = givenExistingRemittance();
    var message = new BillableUsageAggregate();
    message.setStatus(Status.SUCCEEDED);
    message.setBilledOn(BILLED_ON);
    message.setRemittanceUuids(
        existingRemittances.stream()
            .map(BillableUsageRemittanceEntity::getUuid)
            .map(UUID::toString)
            .collect(Collectors.toList()));
    whenSendResponse(message);
    Awaitility.await().untilAsserted(this::verifyUpdateForSuccess);
  }

  @Test
  void testWhenConsumeThenUsageStatusUpdatedFailed() {
    var existingRemittances = givenExistingRemittance();
    var message = new BillableUsageAggregate();
    message.setStatus(Status.FAILED);
    message.setErrorCode(ErrorCode.INACTIVE);
    message.setRemittanceUuids(
        existingRemittances.stream()
            .map(BillableUsageRemittanceEntity::getUuid)
            .map(UUID::toString)
            .collect(Collectors.toList()));
    whenSendResponse(message);
    Awaitility.await().untilAsserted(this::verifyUpdateForFailure);
  }

  @Transactional
  List<BillableUsageRemittanceEntity> givenExistingRemittance() {
    var remittance = new BillableUsageRemittanceEntity();
    remittance.setOrgId(ORG_ID);
    remittance.setProductId("rosa");
    remittance.setMetricId("Cores");
    remittance.setAccumulationPeriod("mm-AAAA");
    remittance.setSla("_ANY");
    remittance.setUsage("_ANY");
    remittance.setBillingProvider("aws");
    remittance.setBillingAccountId("123");
    remittance.setRemittancePendingDate(OffsetDateTime.now());
    remittance.setRemittedPendingValue(2.0);
    remittanceRepository.persist(remittance);
    var remittance2 = new BillableUsageRemittanceEntity();
    remittance2.setOrgId(ORG_ID);
    remittance2.setProductId("rosa");
    remittance2.setMetricId("Cores");
    remittance2.setAccumulationPeriod("mm-AAAA");
    remittance2.setSla("_ANY");
    remittance2.setUsage("_ANY");
    remittance2.setBillingProvider("aws");
    remittance2.setBillingAccountId("123");
    remittance2.setRemittancePendingDate(OffsetDateTime.now().minusDays(3));
    remittance2.setRemittedPendingValue(4.0);
    remittanceRepository.persist(remittance2);
    return List.of(remittance, remittance2);
  }

  private void whenSendResponse(BillableUsageAggregate response) {
    source.send(response);
    Awaitility.await().untilAsserted(() -> verify(consumer).process(response));
  }

  @Transactional
  void verifyUpdateForSuccess() {
    var results = remittanceRepository.findAll().stream().collect(Collectors.toList());
    results.stream()
        .forEach(
            result -> {
              assertEquals(RemittanceStatus.SUCCEEDED, result.getStatus());
              assertEquals(BILLED_ON, result.getBilledOn());
              assertNull(result.getErrorCode());
            });
  }

  @Transactional
  void verifyUpdateForFailure() {
    var results = remittanceRepository.findAll().stream().collect(Collectors.toList());
    results.stream()
        .forEach(
            result -> {
              assertEquals(RemittanceStatus.FAILED, result.getStatus());
              assertEquals(RemittanceErrorCode.INACTIVE, result.getErrorCode());
              assertNull(result.getBilledOn());
            });
  }
}
