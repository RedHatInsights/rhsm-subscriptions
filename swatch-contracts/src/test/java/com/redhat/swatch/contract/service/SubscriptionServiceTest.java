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

import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.test.LoggerCaptor;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionServiceTest {

  private static final String ORG_ID = "org123";
  private static final String SUBSCRIPTION_ID = "456";
  private static final String SUBSCRIPTION_NUMBER = "890";
  private static final String SKU = "RH000001";

  @Inject SubscriptionService subscriptionService;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject OfferingRepository offeringRepository;
  @Inject ApplicationClock clock;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  @BeforeAll
  static void configureLogging() {
    LoggerCaptor.registerHandler(SubscriptionService.class);
  }

  @BeforeEach
  @Transactional
  void setUp() {
    LoggerCaptor.clearRecords();
    subscriptionRepository.deleteAll();
    offeringRepository.deleteAll();
    offeringRepository.persistAndFlush(createOffering());

    startDate = clock.now().truncatedTo(ChronoUnit.SECONDS);
    endDate = startDate.plusYears(1);
  }

  @Test
  @TestTransaction
  void saveCreatesNewSubscriptionFromDetachedEntity() {
    var subscription = newSubscription("initial-acct", 4L);

    subscriptionService.save(subscription);
    subscriptionService.flushAndClearPersistenceContext();

    var persisted = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    assertEquals(1, persisted.size());
    assertEquals(ORG_ID, persisted.getFirst().getOrgId());
    assertEquals("initial-acct", persisted.getFirst().getBillingAccountId());
    assertLogContains("Subscription created/updated org_id=org123 subscription_id=456");
  }

  @Test
  @TestTransaction
  void saveUpdatesManagedSubscription() {
    subscriptionRepository.persistAndFlush(newSubscription("initial-acct", 4L));
    LoggerCaptor.clearRecords();

    var managed = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    managed.setQuantity(16L);
    managed.setBillingAccountId("managed-update");

    subscriptionService.save(managed);
    subscriptionService.flushAndClearPersistenceContext();

    var reloaded = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    assertEquals(16L, reloaded.getQuantity());
    assertEquals("managed-update", reloaded.getBillingAccountId());
    assertLogContains("Subscription created/updated org_id=org123 subscription_id=456");
  }

  @Test
  @TestTransaction
  void saveMergesDetachedEntityUpdates() {
    subscriptionRepository.persistAndFlush(newSubscription("initial-acct", 4L));
    subscriptionService.flushAndClearPersistenceContext();

    EntityManager entityManager = subscriptionRepository.getEntityManager();
    var managed = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    entityManager.detach(managed);
    managed.setBillingAccountId("detached-update");
    managed.setQuantity(32L);
    LoggerCaptor.clearRecords();

    subscriptionService.save(managed);
    subscriptionService.flushAndClearPersistenceContext();

    var reloaded = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    assertEquals("detached-update", reloaded.getBillingAccountId());
    assertEquals(32L, reloaded.getQuantity());
    assertLogContains("Subscription created/updated org_id=org123 subscription_id=456");
  }

  @Test
  @TestTransaction
  void terminatePersistsEndDateForManagedSubscription() {
    subscriptionRepository.persistAndFlush(newSubscription("initial-acct", 4L));
    LoggerCaptor.clearRecords();

    var managed = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    var terminationDate = startDate.plusMonths(6);
    managed.setEndDate(terminationDate);
    managed.setBillingAccountId(null);
    managed.setBillingProvider(null);

    subscriptionService.terminate(managed);
    subscriptionService.flushAndClearPersistenceContext();

    var reloaded = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    assertEquals(terminationDate, reloaded.getEndDate());
    assertEquals(null, reloaded.getBillingAccountId());
    assertLogContains("Subscription terminated org_id=org123 subscription_id=456");
  }

  @Test
  @TestTransaction
  void terminateMergesDetachedEntityWithUpdatedEndDate() {
    subscriptionRepository.persistAndFlush(newSubscription("initial-acct", 4L));
    subscriptionService.flushAndClearPersistenceContext();

    EntityManager entityManager = subscriptionRepository.getEntityManager();
    var managed = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    entityManager.detach(managed);
    var terminationDate = startDate.plusMonths(3);
    managed.setEndDate(terminationDate);
    LoggerCaptor.clearRecords();

    subscriptionService.terminate(managed);
    subscriptionService.flushAndClearPersistenceContext();

    var reloaded = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    assertEquals(terminationDate, reloaded.getEndDate());
    assertLogContains("Subscription terminated org_id=org123 subscription_id=456");
  }

  @Test
  @TestTransaction
  void deleteRemovesSubscriptionAndLogsReason() {
    subscriptionRepository.persistAndFlush(newSubscription("initial-acct", 4L));
    var managed = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    LoggerCaptor.clearRecords();

    subscriptionService.delete(managed, SubscriptionDeleteReason.PRODUCT_DENYLIST);
    subscriptionService.flushAndClearPersistenceContext();

    assertTrue(subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).isEmpty());
    assertLogContains(
        "Subscription deleted org_id=org123 subscription_id=456", "delete_reason=PRODUCT_DENYLIST");
  }

  @Test
  @TestTransaction
  void flushAndClearPersistenceContextEvictsManagedEntities() {
    subscriptionRepository.persistAndFlush(newSubscription("initial-acct", 4L));

    EntityManager entityManager = subscriptionRepository.getEntityManager();
    var managed = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).getFirst();
    assertTrue(entityManager.contains(managed));

    subscriptionService.flushAndClearPersistenceContext();

    assertFalse(entityManager.contains(managed));
    assertEquals(
        4L,
        subscriptionRepository
            .findBySubscriptionNumber(SUBSCRIPTION_NUMBER)
            .getFirst()
            .getQuantity());
  }

  @Test
  @TestTransaction
  void findByContractReturnsPersistedSubscription() {
    var subscription = newSubscription("contract-acct", 8L);
    subscriptionRepository.persistAndFlush(subscription);

    var contract = new ContractEntity();
    contract.setSubscriptionNumber(SUBSCRIPTION_NUMBER);
    contract.setStartDate(startDate);

    var result = subscriptionService.findByContract(contract);

    assertEquals(1, result.size());
    assertEquals(SUBSCRIPTION_ID, result.getFirst().getSubscriptionId());
    assertEquals("contract-acct", result.getFirst().getBillingAccountId());
  }

  @Test
  @TestTransaction
  void streamByOrgIdReturnsSubscriptionsForOrg() {
    subscriptionRepository.persistAndFlush(newSubscription("acct-1", 4L));
    subscriptionRepository.persistAndFlush(
        newSubscriptionForOrg("other-org", "sub-2", "num-2", 2L));

    var result = subscriptionService.streamByOrgId(ORG_ID).toList();

    assertEquals(1, result.size());
    assertEquals(SUBSCRIPTION_ID, result.getFirst().getSubscriptionId());
  }

  private OfferingEntity createOffering() {
    return OfferingEntity.builder()
        .sku(SKU)
        .productIds(Set.of(69))
        .productTags(Set.of("rhel"))
        .build();
  }

  private SubscriptionEntity newSubscription(String billingAccountId, long quantity) {
    return SubscriptionEntity.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .subscriptionNumber(SUBSCRIPTION_NUMBER)
        .orgId(ORG_ID)
        .offering(offeringRepository.findById(SKU))
        .quantity(quantity)
        .startDate(startDate)
        .endDate(endDate)
        .billingProvider(BillingProvider.RED_HAT)
        .billingAccountId(billingAccountId)
        .build();
  }

  private SubscriptionEntity newSubscriptionForOrg(
      String orgId, String subscriptionId, String subscriptionNumber, long quantity) {
    return SubscriptionEntity.builder()
        .subscriptionId(subscriptionId)
        .subscriptionNumber(subscriptionNumber)
        .orgId(orgId)
        .offering(offeringRepository.findById(SKU))
        .quantity(quantity)
        .startDate(startDate)
        .endDate(endDate)
        .billingProvider(BillingProvider.RED_HAT)
        .billingAccountId("acct")
        .build();
  }

  private static void assertLogContains(String... fragments) {
    for (String fragment : fragments) {
      LoggerCaptor.thenInfoLogWithMessage(fragment);
    }
  }
}
