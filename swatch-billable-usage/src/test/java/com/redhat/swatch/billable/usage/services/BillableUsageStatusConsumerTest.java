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

import static com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource.IN_MEMORY_CONNECTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.billable.usage.BillableUsage.ErrorCode;
import org.candlepin.subscriptions.billable.usage.BillableUsage.Status;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class BillableUsageStatusConsumerTest {

  private static final String ORG_ID = "org123";
  private static final OffsetDateTime BILLED_ON =
      OffsetDateTime.of(2024, 5, 10, 10, 20, 5, 0, ZoneOffset.UTC);

  @InjectSpy BillableUsageStatusConsumer consumer;

  @Inject
  @Connector(IN_MEMORY_CONNECTOR)
  InMemoryConnector connector;

  @InjectSpy BillableUsageRemittanceRepository remittanceRepository;

  private InMemorySource<BillableUsageAggregate> source;

  @BeforeEach
  @Transactional
  void setUp() {
    source = connector.source(Channels.BILLABLE_USAGE_STATUS);
    remittanceRepository.deleteAll();
  }

  @Test
  void testWhenConsumeThenUsageStatusUpdatedSucceeded() {
    var existingRemittance = givenExistingRemittance();
    var successMessage =
        createBillableUsageAggregate(Status.SUCCEEDED, null, BILLED_ON, existingRemittance);
    whenSendResponse(successMessage);
    Awaitility.await().untilAsserted(this::verifyUpdateForSuccess);
  }

  @Test
  void testWhenConsumeThenUsageStatusUpdatedFailed() {
    var existingRemittance = givenExistingRemittance();
    var message =
        createBillableUsageAggregate(Status.FAILED, ErrorCode.INACTIVE, null, existingRemittance);
    whenSendResponse(message);
    Awaitility.await().untilAsserted(() -> verifyUpdateForFailure(RemittanceErrorCode.INACTIVE));
  }

  @Test
  void testWhenUsageThatFailedWithSubscriptionNotFoundThenUsageSetToFailed() {
    var existingRemittance = givenExistingRemittance();
    var failedMessage =
        createBillableUsageAggregate(
            Status.FAILED, ErrorCode.SUBSCRIPTION_NOT_FOUND, null, existingRemittance);
    whenSendResponse(failedMessage);
    Awaitility.await()
        .untilAsserted(() -> verifyUpdateForFailure(RemittanceErrorCode.SUBSCRIPTION_NOT_FOUND));
  }

  @Test
  void testWhenUsageThatFailedWithMarketplaceRateLimitThenUsageSetFailed() {
    var existingRemittance = givenExistingRemittance();
    var message =
        createBillableUsageAggregate(
            Status.FAILED, ErrorCode.MARKETPLACE_RATE_LIMIT, null, existingRemittance);
    message.setAggregateKey(new BillableUsageAggregateKey());
    message.getAggregateKey().setBillingProvider("aws");
    whenSendResponse(message);
    Awaitility.await()
        .untilAsserted(() -> verifyUpdateForFailure(RemittanceErrorCode.MARKETPLACE_RATE_LIMIT));
  }

  @Test
  void testWhenConsumeSuccessStatusThenExistingFailedRemittanceNotUpdated() {
    var existingRemittance = givenExistingRemittance();
    var failedMessage =
        createBillableUsageAggregate(Status.FAILED, ErrorCode.INACTIVE, null, existingRemittance);

    whenSendResponse(failedMessage);
    Awaitility.await().untilAsserted(() -> verifyUpdateForFailure(RemittanceErrorCode.INACTIVE));

    var successMessage =
        createBillableUsageAggregate(Status.SUCCEEDED, null, BILLED_ON, existingRemittance);

    whenSendResponse(successMessage);
    Awaitility.await().untilAsserted(() -> verifyUpdateForFailure(RemittanceErrorCode.INACTIVE));
  }

  @Test
  void testWhenConsumeFailedStatusThenExistingSuccessRemittanceNotUpdated() {
    var existingRemittance = givenExistingRemittance();
    var successMessage =
        createBillableUsageAggregate(Status.SUCCEEDED, null, BILLED_ON, existingRemittance);

    whenSendResponse(successMessage);
    Awaitility.await().untilAsserted(this::verifyUpdateForSuccess);

    var failedMessage =
        createBillableUsageAggregate(Status.FAILED, ErrorCode.INACTIVE, null, existingRemittance);

    whenSendResponse(failedMessage);
    Awaitility.await().untilAsserted(this::verifyUpdateForSuccess);
  }

  @Test
  void testWhenConsumeFailedStatusThenExistingGratisRemittanceNotUpdated() {
    var existingGratisRemittance = givenExistingGratisRemittance();
    var failedMessage =
        createBillableUsageAggregate(
            Status.FAILED, ErrorCode.INACTIVE, null, existingGratisRemittance);

    whenSendResponse(failedMessage);
    Awaitility.await().untilAsserted(this::verifyGratisStatus);
  }

  @Test
  void testWhenHandlingStatusThenUpdatedAtIsPopulated() {
    var existingRemittance = givenExistingRemittance();
    OffsetDateTime createdAt = existingRemittance.getUpdatedAt();
    // check the updatedAt was updated when creating the entity
    assertNotNull(createdAt);
    // check the updatedAt was updated when updating an entity
    var successMessage =
        createBillableUsageAggregate(Status.SUCCEEDED, null, BILLED_ON, existingRemittance);
    whenSendResponse(successMessage);
    Awaitility.await().untilAsserted(() -> verifyRemittanceHasUpdatedAtHigherThan(createdAt));
  }

  @Transactional
  BillableUsageRemittanceEntity givenExistingRemittance() {
    var remittance = buildBillableUsageRemittanceEntity();
    remittanceRepository.persist(remittance);
    return remittance;
  }

  @Transactional
  BillableUsageRemittanceEntity givenExistingGratisRemittance() {
    var remittance = buildBillableUsageRemittanceEntity();
    remittance.setStatus(RemittanceStatus.GRATIS);
    remittanceRepository.persist(remittance);
    return remittance;
  }

  private void whenSendResponse(BillableUsageAggregate response) {
    source.send(response);
    Awaitility.await().untilAsserted(() -> verify(consumer).process(response));
  }

  @Transactional
  void verifyUpdateForSuccess() {
    remittanceRepository.findAll().stream()
        .forEach(
            result -> {
              assertEquals(RemittanceStatus.SUCCEEDED, result.getStatus());
              assertEquals(BILLED_ON, result.getBilledOn());
              assertNull(result.getErrorCode());
            });
  }

  @Transactional
  void verifyRemittanceHasUpdatedAtHigherThan(OffsetDateTime createdAt) {
    remittanceRepository.findAll().stream()
        .forEach(
            result ->
                assertTrue(
                    result.getUpdatedAt().isAfter(createdAt),
                    "Updated at '%s' was not updated. Previous value was: '%s'"
                        .formatted(result.getUpdatedAt(), createdAt)));
  }

  @Transactional
  void verifyGratisStatus() {
    remittanceRepository.findAll().stream()
        .forEach(
            result -> {
              assertEquals(RemittanceStatus.GRATIS, result.getStatus());
              assertNull(result.getErrorCode());
            });
  }

  @Transactional
  void verifyUpdateForFailure(RemittanceErrorCode expected) {
    remittanceRepository.findAll().stream()
        .forEach(
            result -> {
              assertEquals(RemittanceStatus.FAILED, result.getStatus());
              assertEquals(expected, result.getErrorCode());
              assertNull(result.getBilledOn());
            });
  }

  @Transactional
  void verifyRemittances(List<String> uuids, Consumer<BillableUsageRemittanceEntity> assertion) {
    uuids.stream()
        .map(uuid -> remittanceRepository.findById(UUID.fromString(uuid)))
        .forEach(assertion);
  }

  private BillableUsageAggregate createBillableUsageAggregate(
      Status status,
      ErrorCode errorCode,
      OffsetDateTime billedOn,
      BillableUsageRemittanceEntity... remittances) {
    var aggregate = new BillableUsageAggregate();
    aggregate.setStatus(status);
    if (errorCode != null) {
      aggregate.setErrorCode(errorCode);
    }
    if (billedOn != null) {
      aggregate.setBilledOn(billedOn);
    }
    aggregate.setRemittanceUuids(
        Stream.of(remittances)
            .map(BillableUsageRemittanceEntity::getUuid)
            .map(UUID::toString)
            .collect(Collectors.toList()));
    BillableUsageAggregateKey key = new BillableUsageAggregateKey();
    key.setBillingProvider("aws");
    aggregate.setAggregateKey(key);
    return aggregate;
  }

  private static BillableUsageRemittanceEntity buildBillableUsageRemittanceEntity() {
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
    return remittance;
  }
}
