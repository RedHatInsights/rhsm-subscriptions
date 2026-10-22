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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.billable.usage.configuration.Channels;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceErrorCode;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.logging.LoggingFileJsonFormatter;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.billable.usage.BillableUsage.ErrorCode;
import org.candlepin.subscriptions.billable.usage.BillableUsage.Status;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class BillableUsageStatusConsumerTest {

  private static final String ORG_ID = "org123";
  private static final String LICENSE_A = "arn:aws:license-manager:1:license:a";
  private static final String LICENSE_B = "arn:aws:license-manager:1:license:b";
  private static final OffsetDateTime BILLED_ON =
      OffsetDateTime.of(2024, 5, 10, 10, 20, 5, 0, ZoneOffset.UTC);
  private static final int MAX_LOG_EVENT_BYTES = 60 * 1024;
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();
  private static Logger capturedLogger;
  private static boolean previousUseParentHandlers;

  @InjectSpy BillableUsageStatusConsumer consumer;

  @Inject
  @Connector(IN_MEMORY_CONNECTOR)
  InMemoryConnector connector;

  @InjectSpy BillableUsageRemittanceRepository remittanceRepository;

  private InMemorySource<BillableUsageAggregate> source;

  @BeforeAll
  static void configureLogging() {
    capturedLogger =
        LogContext.getLogContext().getLogger(BillableUsageStatusConsumer.class.getName());
    previousUseParentHandlers = capturedLogger.getUseParentHandlers();
    capturedLogger.addHandler(LOGGER_CAPTOR);
    capturedLogger.setUseParentHandlers(false);
  }

  @AfterAll
  static void restoreLogging() {
    capturedLogger.removeHandler(LOGGER_CAPTOR);
    capturedLogger.setUseParentHandlers(previousUseParentHandlers);
  }

  @BeforeEach
  @Transactional
  void setUp() {
    LOGGER_CAPTOR.records.clear();
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
  void testWhenUsageThatFailedWithMarketplaceCustomerNotSubscribedThenUsageSetFailed() {
    var existingRemittance = givenExistingRemittance();
    var message =
        createBillableUsageAggregate(
            Status.FAILED, ErrorCode.MARKETPLACE_CUSTOMER_NOT_SUBSCRIBED, null, existingRemittance);
    whenSendResponse(message);
    Awaitility.await()
        .untilAsserted(
            () -> verifyUpdateForFailure(RemittanceErrorCode.MARKETPLACE_CUSTOMER_NOT_SUBSCRIBED));
  }

  @Test
  void testWhenUsageThatFailedWithMarketplaceDuplicateRecordThenUsageSetFailed() {
    var existingRemittance = givenExistingRemittance();
    var message =
        createBillableUsageAggregate(
            Status.FAILED, ErrorCode.MARKETPLACE_DUPLICATE_RECORD, null, existingRemittance);
    whenSendResponse(message);
    Awaitility.await()
        .untilAsserted(
            () -> verifyUpdateForFailure(RemittanceErrorCode.MARKETPLACE_DUPLICATE_RECORD));
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
  void testWhenStatusIsNullThenRemittanceIsNotUpdated() {
    var existingRemittance = givenExistingPendingRemittance();
    var nullStatusMessage = createBillableUsageAggregate(null, null, BILLED_ON, existingRemittance);
    List<String> uuids = createUuids(1001);
    nullStatusMessage.setRemittanceUuids(uuids);

    whenSendResponse(nullStatusMessage);
    Awaitility.await().untilAsserted(this::verifyRemittanceStillPending);
    Awaitility.await()
        .untilAsserted(
            () -> {
              assertEquals(uuids, nullStatusMessage.getRemittanceUuids());
              assertChunkedUuidLogs(uuids, logGroupContaining("with no status"));
            });
  }

  @Test
  void testFinalizedRemittanceUuidRemainsInAggregateAndLogs() {
    var finalizedRemittance = givenFinalizedRemittance();
    var aggregate =
        createBillableUsageAggregate(Status.SUCCEEDED, null, BILLED_ON, finalizedRemittance);
    List<String> uuids = List.copyOf(aggregate.getRemittanceUuids());

    whenSendResponse(aggregate);

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertEquals(uuids, aggregate.getRemittanceUuids());
              assertChunkedUuidLogs(
                  uuids, logGroupContaining("No remittances to update for aggregate"));
            });
  }

  @Test
  void testWhenStatusUpdateReferencesMultipleRemittancesThenAllAreUpdated() {
    var remittance1 = givenExistingRemittance();
    var remittance2 = givenExistingRemittance();
    var remittance3 = givenExistingRemittance();

    var successMessage =
        createBillableUsageAggregate(
            Status.SUCCEEDED, null, BILLED_ON, remittance1, remittance2, remittance3);

    whenSendResponse(successMessage);
    Awaitility.await().untilAsserted(this::verifyUpdateForSuccess);
  }

  @Test
  void testWhenStatusHasLicenseIdThenRemittancesUpdatedToThatLicense() {
    var remittanceA = givenExistingRemittanceWithLicense(LICENSE_A);
    var remittanceB = givenExistingRemittanceWithLicense(LICENSE_B);
    var remittanceNull = givenExistingRemittanceWithLicense(null);

    var successMessage =
        createBillableUsageAggregate(
            Status.SUCCEEDED, null, BILLED_ON, LICENSE_B, remittanceA, remittanceB, remittanceNull);

    whenSendResponse(successMessage);
    Awaitility.await()
        .untilAsserted(
            () ->
                verifyRemittancesHaveLicense(
                    List.of(
                        remittanceA.getUuid().toString(),
                        remittanceB.getUuid().toString(),
                        remittanceNull.getUuid().toString()),
                    LICENSE_B));
  }

  @Test
  void testWhenStatusHasNullLicenseIdThenRemittanceLicenseCleared() {
    var remittanceA = givenExistingRemittanceWithLicense(LICENSE_A);
    var remittanceNull = givenExistingRemittanceWithLicense(null);

    var successMessage =
        createBillableUsageAggregate(
            Status.SUCCEEDED, null, BILLED_ON, (String) null, remittanceA, remittanceNull);

    whenSendResponse(successMessage);
    Awaitility.await()
        .untilAsserted(
            () ->
                verifyRemittancesHaveLicense(
                    List.of(remittanceA.getUuid().toString(), remittanceNull.getUuid().toString()),
                    null));
  }

  @Transactional
  BillableUsageRemittanceEntity givenExistingRemittance() {
    return givenExistingRemittanceWithLicense(null);
  }

  @Transactional
  BillableUsageRemittanceEntity givenExistingRemittanceWithLicense(String licenseId) {
    var remittance = buildBillableUsageRemittanceEntity();
    remittance.setLicenseId(licenseId);
    remittanceRepository.persist(remittance);
    return remittance;
  }

  @Transactional
  BillableUsageRemittanceEntity givenExistingPendingRemittance() {
    var remittance = buildBillableUsageRemittanceEntity();
    // Status must be explicitly PENDING so verifyRemittanceStillPending can assert it was not
    // changed.
    remittance.setStatus(RemittanceStatus.PENDING);
    remittanceRepository.persist(remittance);
    return remittance;
  }

  @Transactional
  BillableUsageRemittanceEntity givenFinalizedRemittance() {
    var remittance = buildBillableUsageRemittanceEntity();
    remittance.setStatus(RemittanceStatus.SUCCEEDED);
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
  void verifyRemittanceStillPending() {
    remittanceRepository.findAll().stream()
        .forEach(
            result -> {
              assertEquals(RemittanceStatus.PENDING, result.getStatus());
              assertNull(result.getErrorCode());
              assertNull(result.getBilledOn());
            });
  }

  @Transactional
  void verifyRemittances(List<String> uuids, Consumer<BillableUsageRemittanceEntity> assertion) {
    uuids.stream()
        .map(uuid -> remittanceRepository.findById(UUID.fromString(uuid)))
        .forEach(assertion);
  }

  @Transactional
  void verifyRemittancesHaveLicense(List<String> uuids, String expectedLicenseId) {
    verifyUpdateForSuccess();
    verifyRemittances(
        uuids,
        result -> {
          if (expectedLicenseId == null) {
            assertNull(result.getLicenseId());
          } else {
            assertEquals(expectedLicenseId, result.getLicenseId());
          }
        });
  }

  private BillableUsageAggregate createBillableUsageAggregate(
      Status status,
      ErrorCode errorCode,
      OffsetDateTime billedOn,
      BillableUsageRemittanceEntity... remittances) {
    return createBillableUsageAggregate(status, errorCode, billedOn, null, remittances);
  }

  private BillableUsageAggregate createBillableUsageAggregate(
      Status status,
      ErrorCode errorCode,
      OffsetDateTime billedOn,
      String licenseId,
      BillableUsageRemittanceEntity... remittances) {
    var aggregate = new BillableUsageAggregate();
    aggregate.setStatus(status);
    if (errorCode != null) {
      aggregate.setErrorCode(errorCode);
    }
    if (billedOn != null) {
      aggregate.setBilledOn(billedOn);
    }
    aggregate.setLicenseId(licenseId);
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

  private List<String> logGroupContaining(String text) {
    List<String> messages = new ArrayList<>();
    boolean inGroup = false;
    for (var record : LOGGER_CAPTOR.records) {
      String message = record.getMessage();
      if (!message.startsWith("Billable usage aggregate continuation:")) {
        inGroup = message.contains(text);
      }
      if (inGroup) {
        messages.add(message);
      }
    }
    return messages;
  }

  private static void assertChunkedUuidLogs(List<String> uuids, List<String> messages) {
    int expectedChunkCount = (uuids.size() + 499) / 500;
    assertEquals(expectedChunkCount, messages.size());

    for (int index = 0; index < messages.size(); index++) {
      String message = messages.get(index);
      if (index == 0) {
        assertFalse(message.startsWith("Billable usage aggregate continuation:"));
        assertFalse(message.contains("(chunk "));
      } else {
        assertTrue(message.startsWith("Billable usage aggregate continuation:"));
        assertTrue(message.contains("(chunk " + (index + 1) + "/" + expectedChunkCount + ")"));
      }
      var record =
          LOGGER_CAPTOR.records.stream()
              .filter(log -> message.equals(log.getMessage()))
              .findFirst()
              .orElseThrow();
      assertTrue(
          new LoggingFileJsonFormatter()
                  .format(ExtLogRecord.wrap(record))
                  .getBytes(StandardCharsets.UTF_8)
                  .length
              <= MAX_LOG_EVENT_BYTES);
    }

    for (String uuid : uuids) {
      assertEquals(1L, messages.stream().filter(message -> message.contains(uuid)).count());
    }
  }

  private static List<String> createUuids(int count) {
    return IntStream.range(0, count).mapToObj(index -> UUID.randomUUID().toString()).toList();
  }

  private static class LoggerCaptor extends Handler {
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
      records.clear();
    }
  }
}
