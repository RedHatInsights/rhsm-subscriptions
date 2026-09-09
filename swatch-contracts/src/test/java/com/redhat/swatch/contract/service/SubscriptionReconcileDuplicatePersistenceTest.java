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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.model.SubscriptionProduct;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reproduces prod duplicate creation for org 7906886 / subscription 21178979 (RS00041):
 *
 * <p>nightly {@code subscription-sync} reconcile matches the latest {@code start_date} segment
 * (including a future-dated row), detects a quantity change vs IT Search, terminates that segment,
 * and inserts a new row with {@code start_date = now()} — leaving prior segments as siblings.
 *
 * <p>Production impact: two concurrently active rows for the same subscription number, both with
 * {@code end_date} still in the future (only the terminated segment has {@code end_date ≈ now()}).
 */
@QuarkusTest
class SubscriptionReconcileDuplicatePersistenceTest {

  private static final String ORG_ID = "7906886";
  private static final String SUBSCRIPTION_ID = "21178814";
  private static final String SUBSCRIPTION_NUMBER = "21178979";
  private static final String SKU = "RS00041";

  private static final long UPSTREAM_QUANTITY = 1L;
  private static final long FUTURE_SEGMENT_QUANTITY = 10L;

  @Inject SubscriptionSyncService subscriptionSyncService;
  @Inject SubscriptionService subscriptionService;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject OfferingRepository offeringRepository;
  @Inject ApplicationClock clock;

  @InjectMock SubscriptionSearchService subscriptionSearchService;
  @InjectMock ProductDenylist productDenylist;

  private OffsetDateTime subscriptionEndDate;

  @BeforeEach
  @Transactional
  void setUp() {
    subscriptionRepository.deleteAll();
    offeringRepository.deleteAll();
    offeringRepository.persistAndFlush(createOffering());
    when(productDenylist.productIdMatches(anyString())).thenReturn(false);
    subscriptionEndDate = clock.now().plusYears(1).truncatedTo(ChronoUnit.SECONDS);
  }

  @Test
  @TestTransaction
  void shouldInsertDuplicateWhenReconcileQuantityChangesOnFutureSegment() {
    OffsetDateTime priorSegmentStart = clock.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime futureSegmentStart = clock.now().plusDays(7).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime upstreamStart = clock.now().truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime reconcileStartedAt = clock.now();

    subscriptionRepository.persistAndFlush(segment(priorSegmentStart, UPSTREAM_QUANTITY));
    subscriptionRepository.persistAndFlush(segment(futureSegmentStart, FUTURE_SEGMENT_QUANTITY));
    subscriptionService.flushAndClearPersistenceContext();

    when(subscriptionSearchService.getSubscriptionsByOrgId(ORG_ID))
        .thenReturn(List.of(searchApiDto(upstreamStart, UPSTREAM_QUANTITY)));

    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService(ORG_ID, false);
    subscriptionService.flushAndClearPersistenceContext();

    List<SubscriptionEntity> rows =
        subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    assertEquals(3, rows.size(), "prior segment + terminated future segment + new now() segment");

    SubscriptionEntity priorSegment = findSegment(rows, priorSegmentStart);
    SubscriptionEntity futureSegment = findSegment(rows, futureSegmentStart);
    SubscriptionEntity newSegment =
        rows.stream()
            .filter(
                row ->
                    row.getQuantity() == UPSTREAM_QUANTITY
                        && !row.getStartDate().equals(priorSegmentStart)
                        && !row.getStartDate().equals(futureSegmentStart))
            .findFirst()
            .orElseThrow();

    thenSegmentIsTerminated(futureSegment);
    thenSegmentIsActiveWithFutureEndDate(priorSegment);
    thenSegmentIsActiveWithFutureEndDate(newSegment);

    // Production bug: two live rows for one subscription number (Kafka/UMB sync skips when size >
    // 1).
    List<SubscriptionEntity> activeRowsWithFutureEndDate =
        rows.stream().filter(this::isActiveWithFutureEndDate).toList();
    assertEquals(
        2,
        activeRowsWithFutureEndDate.size(),
        "Reconcile leaves two active rows with end_date in the future");
    assertEquals(
        Set.of(priorSegmentStart, newSegment.getStartDate()),
        activeRowsWithFutureEndDate.stream()
            .map(SubscriptionEntity::getStartDate)
            .collect(Collectors.toSet()),
        "Active rows should be the untouched prior segment and the newly inserted segment");

    assertTrue(
        !newSegment.getStartDate().isBefore(reconcileStartedAt.minusSeconds(5)),
        "Quantity-change path should start the new segment at now()");
    assertTrue(
        !newSegment.getStartDate().isAfter(clock.now().plusSeconds(5)),
        "New segment start_date should be reconcile processing time, not Search API start");
    assertFalse(
        priorSegmentStart.equals(newSegment.getStartDate()),
        "Duplicate active rows must differ by start_date (compound PK)");
  }

  private SubscriptionEntity findSegment(List<SubscriptionEntity> rows, OffsetDateTime startDate) {
    return rows.stream()
        .filter(row -> row.getStartDate().equals(startDate))
        .findFirst()
        .orElseThrow();
  }

  private void thenSegmentIsTerminated(SubscriptionEntity segment) {
    assertTrue(
        segment.getEndDate().isBefore(subscriptionEndDate),
        "Terminated segment end_date should no longer be the original subscription end");
    assertTrue(
        !segment.getEndDate().isAfter(clock.now().plusSeconds(5)),
        "Terminated segment end_date should be near reconcile time, not in the future");
  }

  private void thenSegmentIsActiveWithFutureEndDate(SubscriptionEntity segment) {
    assertEquals(
        subscriptionEndDate,
        segment.getEndDate(),
        "Active segment should keep the original subscription end_date in the future");
    assertTrue(
        segment.getEndDate().isAfter(clock.now()),
        "Active segment end_date must still be in the future");
    assertTrue(isActiveWithFutureEndDate(segment), "Segment should count as active");
  }

  private boolean isActiveWithFutureEndDate(SubscriptionEntity segment) {
    OffsetDateTime endDate = segment.getEndDate();
    return endDate == null || endDate.isAfter(clock.now());
  }

  private OfferingEntity createOffering() {
    return OfferingEntity.builder()
        .sku(SKU)
        .productIds(Set.of(69))
        .productTags(Set.of("rhel"))
        .metered(false)
        .productName("Red Hat Satellite")
        .build();
  }

  private SubscriptionEntity segment(OffsetDateTime startDate, long quantity) {
    return SubscriptionEntity.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .subscriptionNumber(SUBSCRIPTION_NUMBER)
        .orgId(ORG_ID)
        .offering(offeringRepository.findById(SKU))
        .quantity(quantity)
        .startDate(startDate)
        .endDate(subscriptionEndDate)
        .build();
  }

  private Subscription searchApiDto(OffsetDateTime startDate, long quantity) {
    var product = new SubscriptionProduct().parentSubscriptionProductId(null).sku(SKU);
    var dto = new Subscription();
    dto.setId(Integer.valueOf(SUBSCRIPTION_ID));
    dto.setSubscriptionNumber(SUBSCRIPTION_NUMBER);
    dto.setWebCustomerId(Integer.valueOf(ORG_ID));
    dto.setQuantity(Math.toIntExact(quantity));
    dto.setEffectiveStartDate(startDate.toInstant().toEpochMilli());
    dto.setEffectiveEndDate(subscriptionEndDate.toInstant().toEpochMilli());
    dto.setSubscriptionProducts(Collections.singletonList(product));
    return dto;
  }
}
