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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.model.SubscriptionProduct;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.openapi.model.SubscriptionOutboxPayload;
import com.redhat.swatch.contract.openapi.model.SubscriptionOutboxProduct;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.test.LoggerCaptor;
import com.redhat.swatch.contract.test.resources.PostgresTestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PostgresTestProfile.class)
class SubscriptionSyncServiceIntegrationTest {

  private static final String ORG_ID = "5528";
  private static final String SUBSCRIPTION_ID = "5528";
  private static final String SUBSCRIPTION_NUMBER = "num5528";
  private static final String SKU = "RH05528";
  private static final int INITIAL_QUANTITY = 4;
  private static final int UPDATED_QUANTITY = 10;

  // Prod duplicate scenario: org 7906886 / subscription 21178979 (RS00041)
  private static final String FUTURE_SEGMENT_ORG_ID = "7906886";
  private static final String FUTURE_SEGMENT_SUBSCRIPTION_ID = "21178814";
  private static final String FUTURE_SEGMENT_SUBSCRIPTION_NUMBER = "21178979";
  private static final String FUTURE_SEGMENT_SKU = "RS00041";
  private static final long FUTURE_SEGMENT_UPSTREAM_QUANTITY = 1L;
  private static final long FUTURE_SEGMENT_FUTURE_QUANTITY = 10L;

  // Prod duplicate scenario: drifted start_date (MCT4022)
  private static final String DRIFTED_ORG_ID = "6913405";
  private static final String DRIFTED_SUBSCRIPTION_ID = "9449201";
  private static final String DRIFTED_SUBSCRIPTION_NUMBER = "9449356";
  private static final String DRIFTED_SKU = "MCT4022";

  @Inject OfferingRepository offeringRepository;
  @InjectMock ProductDenylist denylist;
  @InjectMock CapacityReconciliationService capacityReconciliationService;
  @InjectMock SubscriptionSearchService subscriptionSearchService;
  @Inject ApplicationClock clock;
  @Inject SubscriptionSyncService subscriptionSyncService;
  @Inject SubscriptionService subscriptionService;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject ObjectMapper objectMapper;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  @BeforeAll
  static void configureLogging() {
    LoggerCaptor.registerHandler(SubscriptionSyncService.class);
  }

  @BeforeEach
  void setUp() {
    LoggerCaptor.clearRecords();
    reset(denylist, capacityReconciliationService, subscriptionSearchService);

    inTransaction(
        () -> {
          subscriptionRepository.deleteAll();
          offeringRepository.deleteAll();
        });

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(denylist.productIdMatches(anyString())).thenReturn(false);
    doNothing().when(capacityReconciliationService).reconcileCapacityForSubscription(any());

    startDate = clock.now().truncatedTo(ChronoUnit.SECONDS);
    endDate = startDate.plusYears(1);
  }

  @Test
  void concurrentReconcileAndSaveSubscriptionShouldLeaveSingleActiveSubscriptionRow() {
    seedConcurrentQuantityChangeFixture();

    when(subscriptionSearchService.getSubscriptionsByOrgId(ORG_ID))
        .thenReturn(List.of(upstreamSubscription(UPDATED_QUANTITY)));

    runConcurrentReconcileAndSaveSubscription(quantityChangePayload(UPDATED_QUANTITY));

    assertSingleActiveSegmentAfterQuantityChange();
  }

  @Test
  void concurrentQuantityChangeShouldLeaveSingleActiveSubscriptionRow() throws Exception {
    seedConcurrentQuantityChangeFixture();

    runConcurrentSaveSubscription(quantityChangePayload(UPDATED_QUANTITY));

    assertSingleActiveSegmentAfterQuantityChange();
  }

  /**
   * Reconcile matches the latest {@code start_date} segment (including a future-dated row), detects
   * a quantity change vs IT Search, and must not leave multiple active rows for one subscription
   * number.
   */
  @Test
  void reconcileQuantityChangeOnFutureSegmentShouldLeaveSingleActiveSubscriptionRow() {
    OffsetDateTime subscriptionEndDate = clock.now().plusYears(1).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime priorSegmentStart = clock.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime futureSegmentStart = clock.now().plusDays(7).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime upstreamStart = clock.now().truncatedTo(ChronoUnit.SECONDS);

    inTransaction(
        () -> {
          offeringRepository.persistAndFlush(
              offering(FUTURE_SEGMENT_SKU, Set.of("rhel"), false, "Red Hat Satellite"));
          subscriptionRepository.persistAndFlush(
              segment(
                  FUTURE_SEGMENT_SUBSCRIPTION_ID,
                  FUTURE_SEGMENT_SUBSCRIPTION_NUMBER,
                  FUTURE_SEGMENT_ORG_ID,
                  FUTURE_SEGMENT_SKU,
                  priorSegmentStart,
                  subscriptionEndDate,
                  FUTURE_SEGMENT_UPSTREAM_QUANTITY));
          subscriptionRepository.persistAndFlush(
              segment(
                  FUTURE_SEGMENT_SUBSCRIPTION_ID,
                  FUTURE_SEGMENT_SUBSCRIPTION_NUMBER,
                  FUTURE_SEGMENT_ORG_ID,
                  FUTURE_SEGMENT_SKU,
                  futureSegmentStart,
                  subscriptionEndDate,
                  FUTURE_SEGMENT_FUTURE_QUANTITY));
          flushAndClear();
        });

    when(subscriptionSearchService.getSubscriptionsByOrgId(FUTURE_SEGMENT_ORG_ID))
        .thenReturn(
            List.of(
                searchApiDto(
                    FUTURE_SEGMENT_SUBSCRIPTION_ID,
                    FUTURE_SEGMENT_SUBSCRIPTION_NUMBER,
                    FUTURE_SEGMENT_ORG_ID,
                    FUTURE_SEGMENT_SKU,
                    upstreamStart,
                    subscriptionEndDate,
                    FUTURE_SEGMENT_UPSTREAM_QUANTITY)));

    inTransaction(
        () ->
            subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService(
                FUTURE_SEGMENT_ORG_ID, false));
    flushAndClear();

    assertSingleActiveSubscriptionRow(FUTURE_SEGMENT_SUBSCRIPTION_NUMBER);
  }

  @Test
  void mergeWithDriftedStartDateShouldNotCreateDuplicateRow() {
    OffsetDateTime driftedEndDate = clock.now().plusYears(3).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    inTransaction(
        () -> {
          offeringRepository.persistAndFlush(
              offering(DRIFTED_SKU, Set.of("rhel"), false, "Red Hat Multi-Cluster Engine"));
          subscriptionRepository.persistAndFlush(
              persistedSubscription(
                  DRIFTED_SUBSCRIPTION_ID,
                  DRIFTED_SUBSCRIPTION_NUMBER,
                  DRIFTED_ORG_ID,
                  DRIFTED_SKU,
                  dayOneStart,
                  driftedEndDate));
          flushAndClear();
        });

    inTransaction(
        () ->
            subscriptionService.save(
                persistedSubscription(
                    DRIFTED_SUBSCRIPTION_ID,
                    DRIFTED_SUBSCRIPTION_NUMBER,
                    DRIFTED_ORG_ID,
                    DRIFTED_SKU,
                    dayTwoStart,
                    driftedEndDate)));
    flushAndClear();

    assertSingleSubscriptionRow(DRIFTED_SUBSCRIPTION_NUMBER);
  }

  @Test
  void syncSubscriptionWithDriftedStartDateShouldNotCreateDuplicateRow() {
    OffsetDateTime driftedEndDate = clock.now().plusYears(3).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    inTransaction(
        () -> {
          offeringRepository.persistAndFlush(
              offering(DRIFTED_SKU, Set.of("rhel"), false, "Red Hat Multi-Cluster Engine"));
          subscriptionRepository.persistAndFlush(
              persistedSubscription(
                  DRIFTED_SUBSCRIPTION_ID,
                  DRIFTED_SUBSCRIPTION_NUMBER,
                  DRIFTED_ORG_ID,
                  DRIFTED_SKU,
                  dayOneStart,
                  driftedEndDate));
          flushAndClear();
        });

    when(subscriptionSearchService.getSubscriptionBySubscriptionNumber(DRIFTED_SUBSCRIPTION_NUMBER))
        .thenReturn(
            searchApiDto(
                DRIFTED_SUBSCRIPTION_ID,
                DRIFTED_SUBSCRIPTION_NUMBER,
                DRIFTED_ORG_ID,
                DRIFTED_SKU,
                dayTwoStart,
                driftedEndDate,
                10L));

    inTransaction(
        () ->
            subscriptionSyncService.syncSubscription(
                DRIFTED_SKU, incomingKafkaEntity(dayTwoStart, driftedEndDate), Optional.empty()));
    flushAndClear();

    assertSingleSubscriptionRow(DRIFTED_SUBSCRIPTION_NUMBER);
  }

  @Test
  void saveSubscriptionsWithDriftedStartDateShouldNotCreateDuplicateRow()
      throws JsonProcessingException {
    OffsetDateTime driftedEndDate = clock.now().plusYears(3).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    inTransaction(
        () -> {
          offeringRepository.persistAndFlush(
              offering(DRIFTED_SKU, Set.of("rhel"), false, "Red Hat Multi-Cluster Engine"));
          subscriptionRepository.persistAndFlush(
              persistedSubscription(
                  DRIFTED_SUBSCRIPTION_ID,
                  DRIFTED_SUBSCRIPTION_NUMBER,
                  DRIFTED_ORG_ID,
                  DRIFTED_SKU,
                  dayOneStart,
                  driftedEndDate));
          flushAndClear();
        });

    String subscriptionsJson =
        objectMapper.writeValueAsString(
            new Subscription[] {
              searchApiDto(
                  DRIFTED_SUBSCRIPTION_ID,
                  DRIFTED_SUBSCRIPTION_NUMBER,
                  DRIFTED_ORG_ID,
                  DRIFTED_SKU,
                  dayTwoStart,
                  driftedEndDate,
                  10L)
            });
    inTransaction(() -> subscriptionSyncService.saveSubscriptions(subscriptionsJson, false));
    flushAndClear();

    assertSingleSubscriptionRow(DRIFTED_SUBSCRIPTION_NUMBER);
  }

  @Test
  void kafkaSyncWithDriftedStartDateShouldKeepSingleRow() {
    OffsetDateTime driftedEndDate = clock.now().plusYears(3).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    inTransaction(
        () -> {
          offeringRepository.persistAndFlush(
              offering(DRIFTED_SKU, Set.of("rhel"), false, "Red Hat Multi-Cluster Engine"));
          subscriptionRepository.persistAndFlush(
              persistedSubscription(
                  DRIFTED_SUBSCRIPTION_ID,
                  DRIFTED_SUBSCRIPTION_NUMBER,
                  DRIFTED_ORG_ID,
                  DRIFTED_SKU,
                  dayOneStart,
                  driftedEndDate));
          flushAndClear();
        });

    when(subscriptionSearchService.getSubscriptionBySubscriptionNumber(DRIFTED_SUBSCRIPTION_NUMBER))
        .thenReturn(
            searchApiDto(
                DRIFTED_SUBSCRIPTION_ID,
                DRIFTED_SUBSCRIPTION_NUMBER,
                DRIFTED_ORG_ID,
                DRIFTED_SKU,
                dayTwoStart,
                driftedEndDate,
                10L));

    inTransaction(
        () -> subscriptionSyncService.saveSubscription(kafkaPayload(dayTwoStart, driftedEndDate)));
    flushAndClear();

    assertSingleSubscriptionRow(DRIFTED_SUBSCRIPTION_NUMBER);
    assertSameInstant(
        dayOneStart,
        subscriptionRepository
            .findBySubscriptionNumber(DRIFTED_SUBSCRIPTION_NUMBER)
            .getFirst()
            .getStartDate());
  }

  @Test
  void reconcileWithDriftingStartDateShouldKeepSingleRow() {
    OffsetDateTime driftedEndDate = clock.now().plusYears(3).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);

    inTransaction(
        () -> {
          offeringRepository.persistAndFlush(
              offering(DRIFTED_SKU, Set.of("rhel"), false, "Red Hat Multi-Cluster Engine"));
          subscriptionRepository.persistAndFlush(
              persistedSubscription(
                  DRIFTED_SUBSCRIPTION_ID,
                  DRIFTED_SUBSCRIPTION_NUMBER,
                  DRIFTED_ORG_ID,
                  DRIFTED_SKU,
                  dayOneStart,
                  driftedEndDate));
          flushAndClear();
        });

    for (int daysAgo = 4; daysAgo >= 2; daysAgo--) {
      OffsetDateTime upstreamStart = clock.now().minusDays(daysAgo).truncatedTo(ChronoUnit.SECONDS);
      when(subscriptionSearchService.getSubscriptionsByOrgId(DRIFTED_ORG_ID))
          .thenReturn(
              List.of(
                  searchApiDto(
                      DRIFTED_SUBSCRIPTION_ID,
                      DRIFTED_SUBSCRIPTION_NUMBER,
                      DRIFTED_ORG_ID,
                      DRIFTED_SKU,
                      upstreamStart,
                      driftedEndDate,
                      10L)));
      inTransaction(
          () ->
              subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService(
                  DRIFTED_ORG_ID, false));
      flushAndClear();
    }

    assertSingleSubscriptionRow(DRIFTED_SUBSCRIPTION_NUMBER);
    assertSameInstant(
        dayOneStart,
        subscriptionRepository
            .findBySubscriptionNumber(DRIFTED_SUBSCRIPTION_NUMBER)
            .getFirst()
            .getStartDate());
  }

  private void seedConcurrentQuantityChangeFixture() {
    inTransaction(
        () -> {
          var offering = buildOffering();
          offeringRepository.persistAndFlush(offering);
          subscriptionRepository.persistAndFlush(buildInitialSubscription(offering));
        });
  }

  private void inTransaction(Runnable action) {
    QuarkusTransaction.requiringNew().run(action);
  }

  private void flushAndClear() {
    inTransaction(() -> subscriptionService.flushAndClearPersistenceContext());
  }

  private void assertSingleActiveSegmentAfterQuantityChange() {
    var rows = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    assertEquals(2, rows.size(), "Expected one ended segment and one active segment");
    var active = activeRows(rows);

    assertEquals(1, active.size(), "Expected exactly one active subscription row");
    assertEquals(
        0, rowsWithNullEndDate(rows).size(), "Duplicate active rows with end_date IS NULL");
    assertEquals(UPDATED_QUANTITY, active.getFirst().getQuantity());
  }

  private void assertSingleSubscriptionRow(String subscriptionNumber) {
    assertEquals(
        1,
        subscriptionRepository.findBySubscriptionNumber(subscriptionNumber).size(),
        "Expected exactly one subscription row for subscriptionNumber=" + subscriptionNumber);
  }

  private void assertSingleActiveSubscriptionRow(String subscriptionNumber) {
    var rows = subscriptionRepository.findBySubscriptionNumber(subscriptionNumber);
    assertEquals(
        1,
        activeRows(rows).size(),
        "Expected exactly one active subscription row for subscriptionNumber="
            + subscriptionNumber);
  }

  private void runConcurrentReconcileAndSaveSubscription(SubscriptionOutboxPayload payload) {
    int threads = 2;
    var startLatch = new CountDownLatch(1);
    var doneLatch = new CountDownLatch(threads);
    var failure = new AtomicReference<Throwable>();

    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              QuarkusTransaction.requiringNew()
                  .run(
                      () ->
                          subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService(
                              ORG_ID, false));
            } catch (Throwable t) {
              failure.compareAndSet(null, t);
            } finally {
              doneLatch.countDown();
            }
          });
      executor.submit(
          () -> {
            try {
              startLatch.await();
              QuarkusTransaction.requiringNew()
                  .run(() -> subscriptionSyncService.saveSubscription(payload));
            } catch (Throwable t) {
              failure.compareAndSet(null, t);
            } finally {
              doneLatch.countDown();
            }
          });

      startLatch.countDown();
      try {
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Concurrent reconcile did not finish");
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    if (failure.get() != null) {
      throw new AssertionError("Concurrent reconcile failed", failure.get());
    }
  }

  private Subscription upstreamSubscription(int quantity) {
    return searchApiDto(
        SUBSCRIPTION_ID, SUBSCRIPTION_NUMBER, ORG_ID, SKU, startDate, endDate, quantity);
  }

  private void runConcurrentSaveSubscription(SubscriptionOutboxPayload payload)
      throws InterruptedException {
    int threads = 3;
    var startLatch = new CountDownLatch(1);
    var doneLatch = new CountDownLatch(threads);
    var failure = new AtomicReference<Throwable>();

    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                QuarkusTransaction.requiringNew()
                    .run(() -> subscriptionSyncService.saveSubscription(payload));
              } catch (Throwable t) {
                failure.compareAndSet(null, t);
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Concurrent save did not finish in time");
    }

    if (failure.get() != null) {
      throw new AssertionError("Concurrent save failed", failure.get());
    }
  }

  private SubscriptionOutboxPayload quantityChangePayload(int quantity) {
    return new SubscriptionOutboxPayload()
        .subscriptionNumber(SUBSCRIPTION_NUMBER)
        .customerId(ORG_ID)
        .quantity(quantity)
        .effectiveStartDate(startDate.toInstant().toEpochMilli())
        .effectiveEndDate(endDate.toInstant().toEpochMilli())
        .product(new SubscriptionOutboxProduct().sku(SKU));
  }

  private SubscriptionEntity buildInitialSubscription(OfferingEntity offering) {
    return SubscriptionEntity.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .subscriptionNumber(SUBSCRIPTION_NUMBER)
        .orgId(ORG_ID)
        .offering(offering)
        .quantity(INITIAL_QUANTITY)
        .startDate(startDate)
        .endDate(endDate)
        .billingProvider(BillingProvider.RED_HAT)
        .build();
  }

  private static OfferingEntity buildOffering() {
    return offering(SKU, Set.of("rhel"), false, null);
  }

  private static OfferingEntity offering(
      String sku, Set<String> productTags, boolean metered, String productName) {
    var builder =
        OfferingEntity.builder()
            .sku(sku)
            .productIds(Set.of(69))
            .productTags(productTags)
            .metered(metered);
    if (productName != null) {
      builder.productName(productName);
    }
    return builder.build();
  }

  private SubscriptionEntity segment(
      String subscriptionId,
      String subscriptionNumber,
      String orgId,
      String sku,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      long quantity) {
    return SubscriptionEntity.builder()
        .subscriptionId(subscriptionId)
        .subscriptionNumber(subscriptionNumber)
        .orgId(orgId)
        .offering(offeringRepository.findById(sku))
        .quantity(quantity)
        .startDate(startDate)
        .endDate(endDate)
        .build();
  }

  private SubscriptionEntity persistedSubscription(
      String subscriptionId,
      String subscriptionNumber,
      String orgId,
      String sku,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {
    return SubscriptionEntity.builder()
        .subscriptionId(subscriptionId)
        .subscriptionNumber(subscriptionNumber)
        .orgId(orgId)
        .offering(offeringRepository.findById(sku))
        .quantity(10L)
        .startDate(startDate)
        .endDate(endDate)
        .billingProvider(BillingProvider.RED_HAT)
        .build();
  }

  private SubscriptionEntity incomingKafkaEntity(OffsetDateTime startDate, OffsetDateTime endDate) {
    return SubscriptionEntity.builder()
        .subscriptionNumber(DRIFTED_SUBSCRIPTION_NUMBER)
        .orgId(DRIFTED_ORG_ID)
        .quantity(10L)
        .startDate(startDate)
        .endDate(endDate)
        .build();
  }

  private SubscriptionOutboxPayload kafkaPayload(OffsetDateTime startDate, OffsetDateTime endDate) {
    return new SubscriptionOutboxPayload()
        .subscriptionNumber(DRIFTED_SUBSCRIPTION_NUMBER)
        .customerId(DRIFTED_ORG_ID)
        .quantity(10)
        .effectiveStartDate(startDate.toInstant().toEpochMilli())
        .effectiveEndDate(endDate.toInstant().toEpochMilli())
        .product(new SubscriptionOutboxProduct().sku(DRIFTED_SKU));
  }

  private Subscription searchApiDto(
      String subscriptionId,
      String subscriptionNumber,
      String orgId,
      String sku,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      long quantity) {
    var product = new SubscriptionProduct().parentSubscriptionProductId(null).sku(sku);
    var dto = new Subscription();
    dto.setId(Integer.valueOf(subscriptionId));
    dto.setSubscriptionNumber(subscriptionNumber);
    dto.setWebCustomerId(Integer.valueOf(orgId));
    dto.setQuantity(Math.toIntExact(quantity));
    dto.setEffectiveStartDate(startDate.toInstant().toEpochMilli());
    dto.setEffectiveEndDate(endDate.toInstant().toEpochMilli());
    dto.setSubscriptionProducts(Collections.singletonList(product));
    return dto;
  }

  private static List<SubscriptionEntity> activeRows(List<SubscriptionEntity> rows) {
    var now = OffsetDateTime.now();
    return rows.stream()
        .filter(row -> row.getEndDate() == null || row.getEndDate().isAfter(now))
        .toList();
  }

  private static List<SubscriptionEntity> rowsWithNullEndDate(List<SubscriptionEntity> rows) {
    return rows.stream().filter(row -> row.getEndDate() == null).toList();
  }

  private static void assertSameInstant(OffsetDateTime expected, OffsetDateTime actual) {
    assertEquals(expected.toInstant(), actual.toInstant());
  }
}
