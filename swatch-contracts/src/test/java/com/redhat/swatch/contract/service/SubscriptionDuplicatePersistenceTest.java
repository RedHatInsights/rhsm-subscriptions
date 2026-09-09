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
import static org.mockito.ArgumentMatchers.anyString;
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
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Persistence-level reproduction of production duplicate subscription rows.
 *
 * <p>Mocked {@link SubscriptionSyncServiceTest} cannot catch this: {@link
 * SubscriptionService#persistAndMerge} uses JPA {@code merge()} on detached entities, and a
 * compound primary key of {@code (subscription_id, start_date)} causes an INSERT when {@code
 * start_date} differs from the row already stored.
 */
@QuarkusTest
class SubscriptionDuplicatePersistenceTest {

  private static final String ORG_ID = "6913405";
  private static final String SUBSCRIPTION_ID = "9449201";
  private static final String SUBSCRIPTION_NUMBER = "9449356";
  private static final String SKU = "MCT4022";

  @Inject SubscriptionSyncService subscriptionSyncService;
  @Inject SubscriptionService subscriptionService;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject OfferingRepository offeringRepository;
  @Inject ApplicationClock clock;
  @Inject ObjectMapper objectMapper;

  @InjectMock SubscriptionSearchService subscriptionSearchService;
  @InjectMock ProductDenylist productDenylist;

  private OffsetDateTime endDate;

  @BeforeEach
  @Transactional
  void setUp() {
    subscriptionRepository.deleteAll();
    offeringRepository.deleteAll();
    offeringRepository.persistAndFlush(createOffering());
    when(productDenylist.productIdMatches(anyString())).thenReturn(false);
    endDate = clock.now().plusYears(3).truncatedTo(ChronoUnit.SECONDS);
  }

  @Test
  @TestTransaction
  void shouldInsertDuplicateWhenMergeUsesDriftedStartDate() {
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    subscriptionRepository.persistAndFlush(persistedSubscription(dayOneStart));
    subscriptionService.flushAndClearPersistenceContext();

    subscriptionService.save(persistedSubscription(dayTwoStart));
    subscriptionService.flushAndClearPersistenceContext();

    thenSubscriptionRowCountIs(2);
    thenBothDuplicatesHaveSameEndDateInFuture();
  }

  @Test
  @TestTransaction
  void shouldInsertDuplicateWhenSyncSubscriptionInsertsWithDriftedStartDate()
      throws JsonProcessingException {
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    subscriptionRepository.persistAndFlush(persistedSubscription(dayOneStart));
    subscriptionService.flushAndClearPersistenceContext();

    when(subscriptionSearchService.getSubscriptionBySubscriptionNumber(SUBSCRIPTION_NUMBER))
        .thenReturn(searchApiSubscription(dayTwoStart));

    subscriptionSyncService.syncSubscription(
        SKU, incomingKafkaEntity(dayTwoStart), Optional.empty());
    subscriptionService.flushAndClearPersistenceContext();

    thenSubscriptionRowCountIs(2);
    thenBothDuplicatesHaveSameEndDateInFuture();
  }

  @Test
  @TestTransaction
  void shouldInsertDuplicateWhenSaveSubscriptionsUsesDriftedStartDate()
      throws JsonProcessingException {
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    subscriptionRepository.persistAndFlush(persistedSubscription(dayOneStart));
    subscriptionService.flushAndClearPersistenceContext();

    subscriptionSyncService.saveSubscriptions(
        objectMapper.writeValueAsString(new Subscription[] {searchApiSubscription(dayTwoStart)}),
        false);
    subscriptionService.flushAndClearPersistenceContext();

    thenSubscriptionRowCountIs(2);
    thenBothDuplicatesHaveSameEndDateInFuture();
  }

  @Test
  @TestTransaction
  void shouldKeepSingleRowWhenKafkaSyncFindsExistingSubscription() {
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime dayTwoStart = clock.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS);

    subscriptionRepository.persistAndFlush(persistedSubscription(dayOneStart));
    subscriptionService.flushAndClearPersistenceContext();

    when(subscriptionSearchService.getSubscriptionBySubscriptionNumber(SUBSCRIPTION_NUMBER))
        .thenReturn(searchApiSubscription(dayTwoStart));

    subscriptionSyncService.saveSubscription(kafkaPayload(dayTwoStart));
    subscriptionService.flushAndClearPersistenceContext();

    thenSubscriptionRowCountIs(1);
    assertEquals(
        dayOneStart,
        subscriptionRepository
            .findBySubscriptionNumber(SUBSCRIPTION_NUMBER)
            .getFirst()
            .getStartDate());
  }

  @Test
  @TestTransaction
  void shouldKeepSingleRowWhenReconcileRunsWithDriftingStartDate() {
    OffsetDateTime dayOneStart = clock.now().minusDays(5).truncatedTo(ChronoUnit.SECONDS);

    subscriptionRepository.persistAndFlush(persistedSubscription(dayOneStart));
    subscriptionService.flushAndClearPersistenceContext();

    for (int daysAgo = 4; daysAgo >= 2; daysAgo--) {
      OffsetDateTime upstreamStart = clock.now().minusDays(daysAgo).truncatedTo(ChronoUnit.SECONDS);
      when(subscriptionSearchService.getSubscriptionsByOrgId(ORG_ID))
          .thenReturn(List.of(searchApiSubscription(upstreamStart)));
      subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService(ORG_ID, false);
      subscriptionService.flushAndClearPersistenceContext();
    }

    thenSubscriptionRowCountIs(1);
    assertEquals(
        dayOneStart,
        subscriptionRepository
            .findBySubscriptionNumber(SUBSCRIPTION_NUMBER)
            .getFirst()
            .getStartDate());
  }

  private void thenSubscriptionRowCountIs(int expectedCount) {
    assertEquals(
        expectedCount, subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).size());
  }

  private void thenBothDuplicatesHaveSameEndDateInFuture() {
    List<SubscriptionEntity> duplicates =
        subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);

    assertEquals(2, duplicates.size(), "Expected exactly 2 duplicate rows");

    SubscriptionEntity first = duplicates.get(0);
    SubscriptionEntity second = duplicates.get(1);

    // Verify both have the same end_date
    assertEquals(
        first.getEndDate(),
        second.getEndDate(),
        "Both duplicate subscriptions should have identical end_date (only start_date differs)");

    // Verify end_date is in the future (matching production pattern)
    OffsetDateTime now = clock.now();
    assertEquals(
        endDate,
        first.getEndDate(),
        "End date should be the expected future date (3 years from test start)");

    if (first.getEndDate() != null) {
      assertEquals(
          true,
          first.getEndDate().isAfter(now),
          "End date should be in the future, not expired. endDate="
              + first.getEndDate()
              + ", now="
              + now);
    }

    // Verify all other fields are identical except start_date
    assertEquals(
        first.getSubscriptionId(),
        second.getSubscriptionId(),
        "Both should have same subscription_id");
    assertEquals(
        first.getSubscriptionNumber(),
        second.getSubscriptionNumber(),
        "Both should have same subscription_number");
    assertEquals(first.getOrgId(), second.getOrgId(), "Both should have same org_id");
    assertEquals(first.getQuantity(), second.getQuantity(), "Both should have same quantity");
    assertEquals(
        first.getOffering().getSku(), second.getOffering().getSku(), "Both should have same SKU");

    // NOTE: billing_provider may differ between duplicates depending on data source
    // (original may have RED_HAT, incoming message may have null)
    // This is part of the data inconsistency problem but not the root cause

    // Verify start_dates ARE different (this is what makes them duplicates)
    if (first.getStartDate().equals(second.getStartDate())) {
      throw new AssertionError(
          "BUG: Duplicates have SAME start_date! This shouldn't happen. "
              + "start_date1="
              + first.getStartDate()
              + ", start_date2="
              + second.getStartDate());
    }
  }

  private OfferingEntity createOffering() {
    return OfferingEntity.builder()
        .sku(SKU)
        .productIds(Set.of(69))
        .productTags(Set.of("rhel"))
        .metered(false)
        .productName("Red Hat Multi-Cluster Engine")
        .build();
  }

  private SubscriptionEntity persistedSubscription(OffsetDateTime startDate) {
    return SubscriptionEntity.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .subscriptionNumber(SUBSCRIPTION_NUMBER)
        .orgId(ORG_ID)
        .offering(offeringRepository.findById(SKU))
        .quantity(10L)
        .startDate(startDate)
        .endDate(endDate)
        .billingProvider(BillingProvider.RED_HAT)
        .build();
  }

  private SubscriptionEntity incomingKafkaEntity(OffsetDateTime startDate) {
    return SubscriptionEntity.builder()
        .subscriptionNumber(SUBSCRIPTION_NUMBER)
        .orgId(ORG_ID)
        .quantity(10L)
        .startDate(startDate)
        .endDate(endDate)
        .build();
  }

  private SubscriptionOutboxPayload kafkaPayload(OffsetDateTime startDate) {
    return new SubscriptionOutboxPayload()
        .subscriptionNumber(SUBSCRIPTION_NUMBER)
        .customerId(ORG_ID)
        .quantity(10)
        .effectiveStartDate(startDate.toInstant().toEpochMilli())
        .effectiveEndDate(endDate.toInstant().toEpochMilli())
        .product(new SubscriptionOutboxProduct().sku(SKU));
  }

  private Subscription searchApiSubscription(OffsetDateTime startDate) {
    var product = new SubscriptionProduct().parentSubscriptionProductId(null).sku(SKU);
    var dto = new Subscription();
    dto.setId(Integer.valueOf(SUBSCRIPTION_ID));
    dto.setSubscriptionNumber(SUBSCRIPTION_NUMBER);
    dto.setWebCustomerId(Integer.valueOf(ORG_ID));
    dto.setQuantity(10);
    dto.setEffectiveStartDate(startDate.toInstant().toEpochMilli());
    dto.setEffectiveEndDate(endDate.toInstant().toEpochMilli());
    dto.setSubscriptionProducts(Collections.singletonList(product));
    return dto;
  }
}
