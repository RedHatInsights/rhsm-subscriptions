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

import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@ActiveProfiles("test")
class SubscriptionCapacityRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final OffsetDateTime LONG_AGO =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private static final OffsetDateTime NOWISH =
      OffsetDateTime.of(2019, 6, 23, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime FAR_FUTURE =
      OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  static final String ACCOUNT_NUMBER = "account";
  static final String PRODUCT_ID = "123";
  static final String SUBSCRIPTION_ID = "subscription";
  static final String OWNER_ID = "ownerId";

  @Autowired private SubscriptionCapacityRepository repository;

  @Autowired private SubscriptionCapacityRepository subscriptionCapacityRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired OfferingRepository offeringRepository;

  @Test
  void testSave() {
    SubscriptionCapacity capacity = createUnpersisted(LONG_AGO, FAR_FUTURE);
    assertNotNull(repository.saveAndFlush(capacity));
  }

  @Test
  void testShouldFindGivenSubscriptionStartingBeforeRangeAndEndingDuringRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(1), FAR_FUTURE.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getPhysicalSockets().intValue());
    assertEquals(20, capacity.getVirtualSockets().intValue());
    assertEquals(8, capacity.getPhysicalCores().intValue());
    assertEquals(40, capacity.getVirtualCores().intValue());
    assertEquals("ownerId", capacity.getOwnerId());
    assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedGuestSockets());
  }

  @Test
  public void testShouldFindGivenSubscriptionStartingBeforeRangeAndEndingAfterRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(1), FAR_FUTURE.plusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getPhysicalSockets().intValue());
    assertEquals(20, capacity.getVirtualSockets().intValue());
    assertEquals(8, capacity.getPhysicalCores().intValue());
    assertEquals(40, capacity.getVirtualCores().intValue());
    assertEquals("ownerId", capacity.getOwnerId());
    assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedGuestSockets());
  }

  @Test
  void testShouldFindGivenSubscriptionStartingDuringRangeAndEndingDuringRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getPhysicalSockets().intValue());
    assertEquals(20, capacity.getVirtualSockets().intValue());
    assertEquals(8, capacity.getPhysicalCores().intValue());
    assertEquals(40, capacity.getVirtualCores().intValue());
    assertEquals("ownerId", capacity.getOwnerId());
    assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedGuestSockets());
  }

  @Test
  void testShouldFindGivenSubscriptionStartingDuringRangeAndEndingAfterRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getPhysicalSockets().intValue());
    assertEquals(20, capacity.getVirtualSockets().intValue());
    assertEquals(8, capacity.getPhysicalCores().intValue());
    assertEquals(40, capacity.getVirtualCores().intValue());
    assertEquals("ownerId", capacity.getOwnerId());
    assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedGuestSockets());
  }

  @Test
  void testShouldNotFindGivenSubscriptionBeforeWindow() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(7), NOWISH.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testShouldNotFindGivenSubscriptionAfterWindow() {
    SubscriptionCapacity c = createUnpersisted(FAR_FUTURE.plusDays(1), FAR_FUTURE.plusDays(7));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testAllowsNullAccountNumber() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    c.setAccountNumber(null);
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
  }

  @Test
  void testShouldFilterOutSlaIfDifferent() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel.STANDARD, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testShouldFilterOutUsageIfDifferent() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage.DEVELOPMENT_TEST, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testShouldMatchSlaIfSame() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel.PREMIUM, Usage._ANY, NOWISH, FAR_FUTURE);

    assertEquals(1, found.size());
  }

  @Test
  void testShouldMatchUsageIfSame() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
  }

  @Test
  void testCanQueryBySlaNull() {
    SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("premium");
    premium.setServiceLevel(ServiceLevel.PREMIUM);
    standard.setSubscriptionId("standard");
    standard.setServiceLevel(ServiceLevel.STANDARD);
    unset.setSubscriptionId("unset");
    unset.setServiceLevel(ServiceLevel.EMPTY);
    repository.saveAll(Arrays.asList(premium, standard, unset));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", null, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testCanQueryBySlaUnspecified() {
    SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("premium");
    premium.setServiceLevel(ServiceLevel.PREMIUM);
    standard.setSubscriptionId("standard");
    standard.setServiceLevel(ServiceLevel.STANDARD);
    unset.setSubscriptionId("unset");
    unset.setServiceLevel(ServiceLevel.EMPTY);
    repository.saveAll(Arrays.asList(premium, standard, unset));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel.EMPTY, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    assertEquals(ServiceLevel.EMPTY, found.get(0).getServiceLevel());
  }

  @Test
  void testCanQueryBySlaAny() {
    SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity standard = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("premium");
    premium.setServiceLevel(ServiceLevel.PREMIUM);
    standard.setSubscriptionId("standard");
    standard.setServiceLevel(ServiceLevel.STANDARD);
    unset.setSubscriptionId("unset");
    unset.setServiceLevel(ServiceLevel.EMPTY);
    repository.saveAll(Arrays.asList(premium, standard, unset));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testCanQueryByUsageNull() {
    SubscriptionCapacity production = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity dr = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    production.setSubscriptionId("production");
    production.setUsage(Usage.PRODUCTION);
    dr.setSubscriptionId("dr");
    dr.setUsage(Usage.DISASTER_RECOVERY);
    unset.setSubscriptionId("unset");
    unset.setUsage(Usage.EMPTY);
    repository.saveAll(Arrays.asList(production, dr, unset));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, null, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testCanQueryByUsageUnspecified() {
    SubscriptionCapacity production = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity dr = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    production.setSubscriptionId("production");
    production.setUsage(Usage.PRODUCTION);
    dr.setSubscriptionId("dr");
    dr.setUsage(Usage.DISASTER_RECOVERY);
    unset.setSubscriptionId("unset");
    unset.setUsage(Usage.EMPTY);
    repository.saveAll(Arrays.asList(production, dr, unset));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage.EMPTY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    assertEquals(Usage.EMPTY, found.get(0).getUsage());
  }

  @Test
  void testCanQueryByUsageAny() {
    SubscriptionCapacity production = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity dr = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity unset = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    production.setSubscriptionId("production");
    production.setUsage(Usage.PRODUCTION);
    dr.setSubscriptionId("dr");
    dr.setUsage(Usage.DISASTER_RECOVERY);
    unset.setSubscriptionId("unset");
    unset.setUsage(Usage.EMPTY);
    repository.saveAll(Arrays.asList(production, dr, unset));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findByOwnerAndProductId(
            "ownerId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  private SubscriptionCapacity createUnpersisted(OffsetDateTime begin, OffsetDateTime end) {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setAccountNumber("account");
    capacity.setProductId("product");
    capacity.setSubscriptionId("subscription");
    capacity.setBeginDate(begin);
    capacity.setEndDate(end);
    capacity.setHasUnlimitedGuestSockets(false);
    capacity.setOwnerId("ownerId");
    capacity.setPhysicalSockets(4);
    capacity.setVirtualSockets(20);
    capacity.setPhysicalCores(8);
    capacity.setVirtualCores(40);
    capacity.setServiceLevel(ServiceLevel.PREMIUM);
    capacity.setUsage(Usage.PRODUCTION);
    return capacity;
  }

  private Subscription createSubscription(
      String orgId,
      String accountNumber,
      String sku,
      String subId,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {

    Subscription subscription = new Subscription();
    subscription.setMarketplaceSubscriptionId("bananas");
    subscription.setSubscriptionId(subId);
    subscription.setOwnerId(orgId);
    subscription.setAccountNumber(accountNumber);
    subscription.setQuantity(4L);
    subscription.setSku(sku);
    subscription.setStartDate(startDate);
    subscription.setEndDate(endDate);
    subscription.setSubscriptionNumber(subId + "1");

    return subscription;
  }
}
