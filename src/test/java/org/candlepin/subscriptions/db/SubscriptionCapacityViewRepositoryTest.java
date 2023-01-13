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
package org.candlepin.subscriptions.db;

import static org.candlepin.subscriptions.db.HypervisorReportCategory.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DirtiesContext
class SubscriptionCapacityViewRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private static final OffsetDateTime LONG_AGO =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private static final OffsetDateTime NOWISH =
      OffsetDateTime.of(2019, 6, 23, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime FAR_FUTURE =
      OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  static final String ACCOUNT_NUMBER = "account";
  static final String PRODUCT_ID = "123";
  static final String SUBSCRIPTION_ID = "123456";
  static final String ORG_ID = "orgId";
  public static final String TEST_SKU = "testsku1";

  @Autowired private SubscriptionCapacityViewRepository repository;

  @Autowired private SubscriptionCapacityRepository subscriptionCapacityRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired private OfferingRepository offeringRepository;

  @Transactional
  @Test
  void shouldFindAllSubsWithMatchingCriteria() {
    SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("12345");
    SubscriptionCapacity anotherPremium =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("12346");
    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                premium.getSku(),
                premium.getSubscriptionId(),
                premium.getBeginDate(),
                premium.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                premium.getSku(),
                anotherPremium.getSubscriptionId(),
                premium.getBeginDate(),
                premium.getEndDate())));
    subscriptionCapacityRepository.saveAll(List.of(premium, anotherPremium));
    offeringRepository.saveAndFlush(
        createOffering(
            premium.getSku(),
            Integer.parseInt(premium.getProductId()),
            null,
            premium.getUsage(),
            "role1"));

    List<SubscriptionCapacityView> all =
        repository.findAllBy(
            premium.getOrgId(),
            null,
            premium.getProductId(),
            premium.getServiceLevel(),
            premium.getUsage(),
            premium.getBeginDate(),
            premium.getEndDate(),
            null);
    assertEquals(2, all.size());
  }

  @Transactional
  @Test
  void shouldFindAllSubsWithMatchingSLA() {
    SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("12345");
    SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    standard.setSubscriptionId("12346");
    standard.setServiceLevel(ServiceLevel.STANDARD);
    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                premium.getSku(),
                premium.getSubscriptionId(),
                premium.getBeginDate(),
                premium.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                premium.getSku(),
                standard.getSubscriptionId(),
                standard.getBeginDate(),
                standard.getEndDate())));
    subscriptionCapacityRepository.saveAll(List.of(premium, standard));
    offeringRepository.saveAndFlush(
        createOffering(
            premium.getSku(),
            Integer.parseInt(premium.getProductId()),
            null,
            premium.getUsage(),
            "role1"));

    List<SubscriptionCapacityView> all =
        repository.findAllBy(null, null, null, ServiceLevel.PREMIUM, null, null, null, null);
    assertEquals(1, all.size());
  }

  static Stream<Arguments> matchingReportRanges() {
    // The subscription lasts from NOWISH to FAR_FUTURE
    return Stream.of(
        // Subscription begins before the range but ends within it
        arguments(NOWISH.plusDays(1), FAR_FUTURE.plusYears(1)),
        // Subscription lies entirely within the range
        arguments(NOWISH.minusYears(1), FAR_FUTURE.plusYears(1)),
        // Subscription begins within the range but ends past it
        arguments(NOWISH.minusYears(1), FAR_FUTURE.minusDays(1)));
  }

  static Stream<Arguments> nonMatchingReportRanges() {
    // The subscription lasts from NOWISH to FAR_FUTURE
    return Stream.of(
        // Subscription begins and ends before the reporting range
        arguments(FAR_FUTURE.plusDays(1), FAR_FUTURE.plusYears(1)),
        // Subscription begins and ends after the reporting range
        arguments(NOWISH.minusYears(1), NOWISH.minusDays(1)));
  }

  /**
   * See the comment at
   * {@link
   * org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository#subscriptionIsActiveBetween(OffsetDateTime,
   * OffsetDateTime)
   */
  @ParameterizedTest
  @MethodSource("matchingReportRanges")
  void shouldFindAllActiveSubsInDateRange(OffsetDateTime reportBegin, OffsetDateTime reportEnd) {
    SubscriptionCapacity premium = createUnpersisted(NOWISH, FAR_FUTURE);
    premium.setSubscriptionId("12345");
    subscriptionRepository.saveAndFlush(
        createSubscription(
            ORG_ID,
            ACCOUNT_NUMBER,
            premium.getSku(),
            premium.getSubscriptionId(),
            premium.getBeginDate(),
            premium.getEndDate()));
    subscriptionCapacityRepository.save(premium);
    offeringRepository.saveAndFlush(
        createOffering(
            premium.getSku(),
            Integer.parseInt(premium.getProductId()),
            null,
            premium.getUsage(),
            "role1"));
    List<SubscriptionCapacityView> all =
        repository.findAllBy(null, null, null, null, null, reportBegin, reportEnd, null);
    assertEquals(1, all.size());
  }

  /**
   * See the comment at
   * {@link
   * org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository#subscriptionIsActiveBetween(OffsetDateTime,
   * OffsetDateTime)
   */
  @ParameterizedTest
  @MethodSource("nonMatchingReportRanges")
  void shouldFindNoActiveSubsInDateRange(OffsetDateTime reportBegin, OffsetDateTime reportEnd) {
    SubscriptionCapacity premium = createUnpersisted(NOWISH, FAR_FUTURE);
    premium.setSubscriptionId("12345");
    subscriptionRepository.saveAndFlush(
        createSubscription(
            ORG_ID,
            ACCOUNT_NUMBER,
            premium.getSku(),
            premium.getSubscriptionId(),
            premium.getBeginDate(),
            premium.getEndDate()));
    subscriptionCapacityRepository.save(premium);
    offeringRepository.saveAndFlush(
        createOffering(
            premium.getSku(),
            Integer.parseInt(premium.getProductId()),
            null,
            premium.getUsage(),
            "role1"));
    List<SubscriptionCapacityView> all =
        repository.findAllBy(null, null, null, null, null, reportBegin, reportEnd, null);
    assertEquals(0, all.size());
  }

  @Transactional
  @Test
  void shouldRequireBothOrgAndProductIds() {
    assertThrows(
        InvalidDataAccessApiUsageException.class,
        () -> repository.findAllBy(null, null, PRODUCT_ID, null, null, null, null, null));
  }

  @Transactional
  @Test
  void shouldFindAllRecordsWithEndDateAfterOrOnThanGivenReportStartDate() {
    SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("12345");
    SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(3), FAR_FUTURE.plusDays(1));
    standard.setSubscriptionId("12346");
    standard.setServiceLevel(ServiceLevel.STANDARD);
    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                premium.getSku(),
                premium.getSubscriptionId(),
                premium.getBeginDate(),
                premium.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                premium.getSku(),
                standard.getSubscriptionId(),
                standard.getBeginDate(),
                standard.getEndDate())));
    subscriptionCapacityRepository.saveAll(List.of(premium, standard));
    offeringRepository.saveAndFlush(
        createOffering(
            premium.getSku(),
            Integer.parseInt(premium.getProductId()),
            null,
            premium.getUsage(),
            "role1"));

    List<SubscriptionCapacityView> all =
        repository.findAllBy(null, null, null, null, null, NOWISH.plusDays(30), null, null);
    assertEquals(2, all.size());
  }

  @Transactional
  @Test
  void shouldFilterCapacityWithUnmatchedSLA() {
    SubscriptionCapacity premium = createUnpersisted(NOWISH, FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("12345");
    SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    standard.setSubscriptionId("12346");
    standard.setServiceLevel(ServiceLevel.STANDARD);
    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                premium.getSku(),
                premium.getSubscriptionId(),
                premium.getBeginDate(),
                premium.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                standard.getSku(),
                standard.getSubscriptionId(),
                standard.getBeginDate(),
                standard.getEndDate())));
    subscriptionCapacityRepository.saveAll(List.of(premium, standard));
    offeringRepository.saveAndFlush(
        createOffering(
            premium.getSku(),
            Integer.parseInt(premium.getProductId()),
            null,
            premium.getUsage(),
            "role1"));

    List<SubscriptionCapacityView> all =
        repository.findAllBy(
            ORG_ID,
            null,
            PRODUCT_ID,
            premium.getServiceLevel(),
            premium.getUsage(),
            NOWISH.minusYears(1),
            FAR_FUTURE.plusMonths(1),
            null);
    assertEquals(1, all.size());
    assertEquals(premium.getServiceLevel(), all.get(0).getServiceLevel());
  }

  @Transactional
  @Test
  void shouldMatchCapacityWithCoresIfSpecified() {
    SubscriptionCapacity sockets = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    sockets.setSubscriptionId("sockets");
    sockets.setSockets(10);
    sockets.setHypervisorSockets(10);
    sockets.setCores(null);
    sockets.setHypervisorCores(null);

    SubscriptionCapacity cores = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    cores.setSubscriptionId("cores");
    cores.setCores(10);
    cores.setHypervisorCores(10);
    cores.setSockets(null);
    cores.setHypervisorSockets(null);

    SubscriptionCapacity socketsAndCores =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    socketsAndCores.setSubscriptionId("socketsAndCores");
    socketsAndCores.setCores(10);
    socketsAndCores.setHypervisorCores(null);
    socketsAndCores.setSockets(10);
    socketsAndCores.setHypervisorSockets(10);

    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                sockets.getSku(),
                sockets.getSubscriptionId(),
                sockets.getBeginDate(),
                sockets.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                cores.getSku(),
                cores.getSubscriptionId(),
                cores.getBeginDate(),
                cores.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                socketsAndCores.getSku(),
                socketsAndCores.getSubscriptionId(),
                socketsAndCores.getBeginDate(),
                socketsAndCores.getEndDate())));
    subscriptionCapacityRepository.saveAll(List.of(sockets, cores, socketsAndCores));
    offeringRepository.saveAndFlush(
        createOffering(
            sockets.getSku(),
            Integer.parseInt(sockets.getProductId()),
            null,
            sockets.getUsage(),
            "role1"));

    List<SubscriptionCapacityView> found =
        repository.findAllBy(null, null, null, null, null, null, null, Uom.CORES);
    assertEquals(2, found.size());
    found.forEach(
        subscriptionCapacityView -> {
          assertNotNull(subscriptionCapacityView.getCores());
          assertNotNull(subscriptionCapacityView.getHypervisorCores());
        });
  }

  @Transactional
  @Test
  void shouldFilterCapacityByCategory() {
    // In prod, RH00006 (VDC SKU) subscriptions have non-null hypervisor sockets and null
    // everything else.
    SubscriptionCapacity hypervisor = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    hypervisor.setSubscriptionId("hypervisor");
    hypervisor.setSockets(null);
    hypervisor.setHypervisorSockets(10);
    hypervisor.setCores(null);
    hypervisor.setHypervisorCores(null);

    // In prod, RH0004 (a non-VDC SKU) subscriptions have non-null sockets and null everything else.
    SubscriptionCapacity noCoresNonHypervisor =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    noCoresNonHypervisor.setSubscriptionId("noCoresNonHypervisor");
    noCoresNonHypervisor.setSockets(10);
    noCoresNonHypervisor.setHypervisorSockets(null);
    noCoresNonHypervisor.setCores(null);
    noCoresNonHypervisor.setHypervisorCores(null);

    // Other non-hypervisor SKUs in prod (SER0419) have cores but no sockets
    SubscriptionCapacity noSocketsNonHypervisor =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    noSocketsNonHypervisor.setSubscriptionId("noSocketsNonHypervisor");
    noSocketsNonHypervisor.setSockets(null);
    noSocketsNonHypervisor.setHypervisorSockets(null);
    noSocketsNonHypervisor.setCores(99);
    noSocketsNonHypervisor.setHypervisorCores(null);

    // As of 13 Jan 2023, there are no SKUS in the subscription_capacity table with non-null
    // hypervisor_cores.

    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                hypervisor.getSku(),
                hypervisor.getSubscriptionId(),
                hypervisor.getBeginDate(),
                hypervisor.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                noCoresNonHypervisor.getSku(),
                noCoresNonHypervisor.getSubscriptionId(),
                noCoresNonHypervisor.getBeginDate(),
                noCoresNonHypervisor.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                noSocketsNonHypervisor.getSku(),
                noSocketsNonHypervisor.getSubscriptionId(),
                noSocketsNonHypervisor.getBeginDate(),
                noSocketsNonHypervisor.getEndDate())));

    subscriptionCapacityRepository.saveAll(
        List.of(hypervisor, noCoresNonHypervisor, noSocketsNonHypervisor));
    offeringRepository.saveAndFlush(
        createOffering(TEST_SKU, Integer.parseInt(PRODUCT_ID), null, Usage.PRODUCTION, "role1"));

    List<SubscriptionCapacityView> results =
        repository.findAllBy(
            ORG_ID, NON_HYPERVISOR, PRODUCT_ID, null, null, NOWISH, FAR_FUTURE.plusDays(4), null);
    assertEquals(2, results.size());
    assertThat(
        results.stream().map(SubscriptionCapacityView::getCores).collect(Collectors.toList()),
        // getCores() turns a null into a zero
        containsInAnyOrder(0, 99));
    assertThat(
        results.stream().map(SubscriptionCapacityView::getSockets).collect(Collectors.toList()),
        // getSockets() turns a null into a zero
        containsInAnyOrder(0, 10));

    results =
        repository.findAllBy(
            ORG_ID, HYPERVISOR, PRODUCT_ID, null, null, NOWISH, FAR_FUTURE.plusDays(4), null);
    assertEquals(1, results.size());
    var record = results.get(0);
    assertAll(
        () -> {
          assertEquals(0, record.getHypervisorCores());
          assertEquals(10, record.getHypervisorSockets());
        });

    results =
        repository.findAllBy(
            ORG_ID, null, PRODUCT_ID, null, null, NOWISH, FAR_FUTURE.plusDays(4), null);
    assertEquals(3, results.size());
    results =
        repository.findAllBy(
            ORG_ID, null, PRODUCT_ID, null, null, NOWISH, FAR_FUTURE.plusDays(4), null);
    assertEquals(3, results.size());
  }

  @Transactional
  @Test
  void shouldMatchCapacityWithSocketsIfSpecified() {
    SubscriptionCapacity sockets = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    sockets.setSubscriptionId("sockets");
    sockets.setSockets(10);
    sockets.setHypervisorSockets(10);
    sockets.setCores(null);
    sockets.setHypervisorCores(null);

    SubscriptionCapacity cores = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    cores.setSubscriptionId("cores");
    cores.setCores(10);
    cores.setHypervisorCores(10);
    cores.setSockets(null);
    cores.setHypervisorSockets(null);

    SubscriptionCapacity socketsAndCores =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    socketsAndCores.setSubscriptionId("socketsAndCores");
    socketsAndCores.setCores(10);
    socketsAndCores.setHypervisorCores(10);
    socketsAndCores.setSockets(10);
    socketsAndCores.setHypervisorSockets(null);

    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                sockets.getSku(),
                sockets.getSubscriptionId(),
                sockets.getBeginDate(),
                sockets.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                cores.getSku(),
                cores.getSubscriptionId(),
                cores.getBeginDate(),
                cores.getEndDate()),
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                socketsAndCores.getSku(),
                socketsAndCores.getSubscriptionId(),
                socketsAndCores.getBeginDate(),
                socketsAndCores.getEndDate())));
    subscriptionCapacityRepository.saveAll(List.of(sockets, cores, socketsAndCores));
    offeringRepository.saveAndFlush(
        createOffering(
            sockets.getSku(),
            Integer.parseInt(sockets.getProductId()),
            null,
            sockets.getUsage(),
            "role1"));
    List<SubscriptionCapacityView> found =
        repository.findAllBy(null, null, null, null, null, null, null, Uom.SOCKETS);
    assertEquals(2, found.size());
    found.forEach(
        subscriptionCapacityView -> {
          assertNotNull(subscriptionCapacityView.getSockets());
          assertNotNull(subscriptionCapacityView.getHypervisorSockets());
        });
  }

  @Transactional
  @Test
  void shouldSetNameFromOfferingDescription() {
    SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    standard.setSubscriptionId("12346");
    standard.setServiceLevel(ServiceLevel.STANDARD);
    subscriptionRepository.saveAllAndFlush(
        List.of(
            createSubscription(
                ORG_ID,
                ACCOUNT_NUMBER,
                standard.getSku(),
                standard.getSubscriptionId(),
                standard.getBeginDate(),
                standard.getEndDate())));
    subscriptionCapacityRepository.save(standard);
    Offering offering =
        createOffering(
            standard.getSku(),
            Integer.parseInt(standard.getProductId()),
            standard.getServiceLevel(),
            standard.getUsage(),
            "role1");
    offeringRepository.saveAndFlush(offering);

    Specification<SubscriptionCapacityView> skuSpec =
        (root, query, builder) ->
            builder.equal(root.get(SubscriptionCapacityView_.sku), standard.getSku());
    List<SubscriptionCapacityView> found = repository.findAll(skuSpec);
    assertEquals(1, found.size());
    assertEquals(offering.getDescription(), found.get(0).getProductName());
  }

  private SubscriptionCapacity createUnpersisted(OffsetDateTime begin, OffsetDateTime end) {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setAccountNumber(ACCOUNT_NUMBER);
    capacity.setProductId(PRODUCT_ID);
    capacity.setSubscriptionId(SUBSCRIPTION_ID);
    capacity.setBeginDate(begin);
    capacity.setEndDate(end);
    capacity.setHasUnlimitedUsage(false);
    capacity.setOrgId(ORG_ID);
    capacity.setSockets(4);
    capacity.setHypervisorSockets(20);
    capacity.setCores(8);
    capacity.setHypervisorCores(40);
    capacity.setServiceLevel(ServiceLevel.PREMIUM);
    capacity.setUsage(Usage.PRODUCTION);
    capacity.setSku(TEST_SKU);
    return capacity;
  }

  private Offering createOffering(
      String sku, int productId, ServiceLevel sla, Usage usage, String role) {
    Offering o = new Offering();
    o.setSku(sku);
    o.setProductIds(Set.of(productId));
    o.setServiceLevel(sla);
    o.setUsage(usage);
    o.setRole(role);
    o.setProductName("Sku: " + sku);
    o.setDescription("Description of: " + sku);
    return o;
  }

  private Subscription createSubscription(
      String orgId, String accountNumber, String sku, String subId) {
    return createSubscription(orgId, accountNumber, sku, subId, NOW, NOW.plusDays(30));
  }

  private Subscription createSubscription(
      String orgId,
      String accountNumber,
      String sku,
      String subId,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {

    Subscription subscription = new Subscription();
    subscription.setBillingProviderId("bananas");
    subscription.setSubscriptionId(subId);
    subscription.setOrgId(orgId);
    subscription.setAccountNumber(accountNumber);
    subscription.setQuantity(4L);
    subscription.setSku(sku);
    subscription.setStartDate(startDate);
    subscription.setEndDate(endDate);
    subscription.setSubscriptionNumber(subId + "1");
    subscription.setBillingProvider(BillingProvider.RED_HAT);

    return subscription;
  }
}
