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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

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
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

  private static final String ORG_ID = "org5528";
  private static final String SUBSCRIPTION_ID = "sub5528";
  private static final String SUBSCRIPTION_NUMBER = "num5528";
  private static final String SKU = "RH05528";
  private static final int INITIAL_QUANTITY = 4;
  private static final int UPDATED_QUANTITY = 10;

  @Inject OfferingRepository offeringRepository;
  @InjectMock ProductDenylist denylist;
  @InjectMock CapacityReconciliationService capacityReconciliationService;
  @Inject ApplicationClock clock;
  @Inject SubscriptionSyncService subscriptionSyncService;
  @Inject SubscriptionRepository subscriptionRepository;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  @BeforeAll
  static void configureLogging() {
    LoggerCaptor.registerHandler(SubscriptionSyncService.class);
  }

  @Transactional
  @BeforeEach
  void setUp() {
    startDate = clock.now().truncatedTo(ChronoUnit.SECONDS);
    endDate = startDate.plusYears(1);

    LoggerCaptor.clearRecords();
    reset(denylist, capacityReconciliationService);

    subscriptionRepository.deleteAll();
    offeringRepository.deleteAll();
    var offering = buildOffering();
    offeringRepository.persistAndFlush(offering);
    subscriptionRepository.persistAndFlush(buildInitialSubscription(offering));

    when(denylist.productIdMatches(any())).thenReturn(false);
    doNothing().when(capacityReconciliationService).reconcileCapacityForSubscription(any());
  }

  @Test
  void concurrentQuantityChangeShouldLeaveSingleActiveSubscriptionRow() throws Exception {
    runConcurrentSaveSubscription(quantityChangePayload(UPDATED_QUANTITY));

    var rows = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    assertEquals(2, rows.size(), "Expected one ended segment and one active segment");
    var activeRows = activeRows(rows);

    assertEquals(1, activeRows.size(), "Expected exactly one active subscription row");
    assertEquals(
        0, rowsWithNullEndDate(rows).size(), "Duplicate active rows with end_date IS NULL");
    assertEquals(UPDATED_QUANTITY, activeRows.getFirst().getQuantity());
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
    return OfferingEntity.builder()
        .sku(SKU)
        .productIds(Set.of(69))
        .productTags(Set.of("rhel"))
        .build();
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
}
