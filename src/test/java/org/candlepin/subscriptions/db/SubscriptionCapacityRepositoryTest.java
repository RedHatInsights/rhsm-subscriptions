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

import static org.candlepin.subscriptions.utilization.api.model.MetricId.CORES;
import static org.candlepin.subscriptions.utilization.api.model.MetricId.SOCKETS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@ActiveProfiles("test")
class SubscriptionCapacityRepositoryTest {
  private static final OffsetDateTime LONG_AGO =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private static final OffsetDateTime NOWISH =
      OffsetDateTime.of(2019, 6, 23, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime FAR_FUTURE =
      OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private SubscriptionCapacityRepository repository;

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
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testShouldFindGivenSubscriptionStartingBeforeRangeAndEndingAfterRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(1), FAR_FUTURE.plusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testShouldFindGivenSubscriptionStartingDuringRangeAndEndingDuringRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testShouldFindGivenSubscriptionStartingDuringRangeAndEndingAfterRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testShouldNotFindGivenSubscriptionBeforeWindow() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(7), NOWISH.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testShouldNotFindGivenSubscriptionAfterWindow() {
    SubscriptionCapacity c = createUnpersisted(FAR_FUTURE.plusDays(1), FAR_FUTURE.plusDays(7));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testAllowsNullAccountNumber() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    c.setAccountNumber(null);
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
  }

  @Test
  void testShouldFilterOutSlaIfDifferent() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", ServiceLevel.STANDARD, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testShouldFilterOutUsageIfDifferent() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", ServiceLevel._ANY, Usage.DEVELOPMENT_TEST, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testShouldMatchSlaIfSame() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", ServiceLevel.PREMIUM, Usage._ANY, NOWISH, FAR_FUTURE);

    assertEquals(1, found.size());
  }

  @Test
  void testShouldMatchUsageIfSame() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", ServiceLevel._ANY, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
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
        repository.findAllBy("orgId", "product", null, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
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
        repository.findAllBy(
            "orgId", "product", ServiceLevel.EMPTY, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
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
        repository.findAllBy(
            "orgId", "product", ServiceLevel._ANY, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
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
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, null, NOWISH, FAR_FUTURE);
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
        repository.findAllBy(
            "orgId", "product", ServiceLevel._ANY, Usage.EMPTY, NOWISH, FAR_FUTURE);
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
        repository.findAllBy("orgId", "product", ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testFindByMetricIdShouldFindGivenSubscriptionStartingBeforeRangeAndEndingDuringRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(1), FAR_FUTURE.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testFindByMetricIdShouldFindGivenSubscriptionStartingBeforeRangeAndEndingAfterRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(1), FAR_FUTURE.plusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.minusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testFindByMetricIdShouldFindGivenSubscriptionStartingDuringRangeAndEndingDuringRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.minusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testFindByMetricIdShouldFindGivenSubscriptionStartingDuringRangeAndEndingAfterRange() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    SubscriptionCapacity capacity = found.get(0);
    assertEquals("account", capacity.getAccountNumber());
    assertEquals("product", capacity.getProductId());
    assertEquals("subscription", capacity.getSubscriptionId());
    assertEquals(4, capacity.getSockets().intValue());
    assertEquals(20, capacity.getHypervisorSockets().intValue());
    assertEquals(8, capacity.getCores().intValue());
    assertEquals(40, capacity.getHypervisorCores().intValue());
    assertEquals("orgId", capacity.getOrgId());
    assertEquals(NOWISH.plusDays(1), capacity.getBeginDate());
    assertEquals(FAR_FUTURE.plusDays(1), capacity.getEndDate());
    assertFalse(capacity.getHasUnlimitedUsage());
  }

  @Test
  void testFindByMetricIdShouldNotFindGivenSubscriptionBeforeWindow() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.minusDays(7), NOWISH.minusDays(1));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testFindByMetricIdShouldNotFindGivenSubscriptionAfterWindow() {
    SubscriptionCapacity c = createUnpersisted(FAR_FUTURE.plusDays(1), FAR_FUTURE.plusDays(7));

    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testFindByMetricIdAllowsNullAccountNumber() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    c.setAccountNumber(null);
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
  }

  @Test
  void testFindByMetricIdShouldFilterOutSlaIfDifferent() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel.STANDARD, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testFindByMetricIdShouldFilterOutUsageIfDifferent() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            CORES,
            null,
            ServiceLevel._ANY,
            Usage.DEVELOPMENT_TEST,
            NOWISH,
            FAR_FUTURE);
    assertEquals(0, found.size());
  }

  @Test
  void testFindByMetricIdShouldMatchSlaIfSame() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel.PREMIUM, Usage._ANY, NOWISH, FAR_FUTURE);

    assertEquals(1, found.size());
  }

  @Test
  void testFindByMetricIdShouldMatchUsageIfSame() {
    SubscriptionCapacity c = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    repository.save(c);
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            CORES,
            null,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
    assertEquals(1, found.size());
  }

  @Test
  void testFindByMetricIdCanQueryBySlaNull() {
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
        repository.findAllBy(
            "orgId", "product", CORES, null, null, Usage.PRODUCTION, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testFindByMetricIdCanQueryBySlaUnspecified() {
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
        repository.findAllBy(
            "orgId",
            "product",
            CORES,
            null,
            ServiceLevel.EMPTY,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
    assertEquals(1, found.size());
    assertEquals(ServiceLevel.EMPTY, found.get(0).getServiceLevel());
  }

  @Test
  void testFindByMetricIdCanQueryBySlaAny() {
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
        repository.findAllBy(
            "orgId",
            "product",
            CORES,
            null,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testFindByMetricIdCanQueryByUsageNull() {
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
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, null, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testFindByMetricIdCanQueryByUsageUnspecified() {
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
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage.EMPTY, NOWISH, FAR_FUTURE);
    assertEquals(1, found.size());
    assertEquals(Usage.EMPTY, found.get(0).getUsage());
  }

  @Test
  void testFindByMetricIdCanQueryByUsageAny() {
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
        repository.findAllBy(
            "orgId", "product", CORES, null, ServiceLevel._ANY, Usage._ANY, NOWISH, FAR_FUTURE);
    assertEquals(3, found.size());
  }

  @Test
  void testFindByMetricIdOnlyRetrieveCoresCapacity() {
    SubscriptionCapacity cores = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity sockets = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    cores.setHypervisorSockets(0);
    cores.setSockets(0);
    cores.setSubscriptionId("cores");
    sockets.setCores(0);
    sockets.setHypervisorCores(0);
    sockets.setSubscriptionId("sockets");
    repository.saveAll(Arrays.asList(cores, sockets));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            CORES,
            null,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
    assertEquals(1, found.size());
    assertEquals(8, found.get(0).getCores());
    assertEquals(40, found.get(0).getHypervisorCores());
  }

  @Test
  void testFindByMetricIdOnlyRetrieveHypervisorCoresCapacity() {
    SubscriptionCapacity hypervisorCores =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity cores = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    hypervisorCores.setHypervisorSockets(0);
    hypervisorCores.setSockets(0);
    hypervisorCores.setCores(0);
    hypervisorCores.setSubscriptionId("hypervisorCores");
    cores.setSockets(0);
    cores.setHypervisorSockets(0);
    cores.setHypervisorCores(0);
    cores.setSubscriptionId("cores");
    repository.saveAll(Arrays.asList(hypervisorCores, cores));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            CORES,
            HypervisorReportCategory.HYPERVISOR,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);

    assertEquals(1, found.size());
    assertEquals(40, found.get(0).getHypervisorCores());
  }

  @Test
  void testFindByMetricIdFiltersByCategoryCorrectly() {
    // In prod, RH00006 (VDC SKU) subscriptions have non-null hypervisor sockets and null
    // everything else.
    SubscriptionCapacity hypervisor = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    hypervisor.setSku("RH00006");
    hypervisor.setSockets(null);
    hypervisor.setHypervisorSockets(99);
    hypervisor.setCores(null);
    hypervisor.setHypervisorCores(null);
    hypervisor.setSubscriptionId("hypervisor");

    // In prod, RH0004 (a non-VDC SKU) subscriptions have non-null sockets and null everything else.
    SubscriptionCapacity nonHypervisor =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    nonHypervisor.setSku("RH00004");
    nonHypervisor.setSockets(10);
    nonHypervisor.setHypervisorSockets(null);
    nonHypervisor.setCores(null);
    nonHypervisor.setHypervisorCores(null);
    nonHypervisor.setSubscriptionId("nonHypervisor");
    repository.saveAll(Arrays.asList(hypervisor, nonHypervisor));
    repository.flush();

    List<SubscriptionCapacity> results =
        repository.findAllBy(
            "orgId",
            "product",
            SOCKETS,
            HypervisorReportCategory.NON_HYPERVISOR,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);

    assertEquals(1, results.size());
    var record = results.get(0);
    assertAll(
        () -> {
          assertNull(record.getHypervisorCores());
          assertNull(record.getHypervisorSockets());
          assertNull(record.getCores());
          assertEquals(10, record.getSockets());
        });

    results =
        repository.findAllBy(
            "orgId",
            "product",
            SOCKETS,
            HypervisorReportCategory.HYPERVISOR,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);

    assertEquals(1, results.size());
    var hypervisorRecord = results.get(0);
    assertAll(
        () -> {
          assertNull(hypervisorRecord.getHypervisorCores());
          assertEquals(99, hypervisorRecord.getHypervisorSockets());
          assertNull(hypervisorRecord.getCores());
          assertNull(hypervisorRecord.getSockets());
        });
  }

  @Test
  void testFindByMetricIdOnlyRetrieveStandardCoresCapacity() {
    SubscriptionCapacity hypervisorCores =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity cores = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    hypervisorCores.setHypervisorSockets(0);
    hypervisorCores.setSockets(0);
    hypervisorCores.setCores(0);
    hypervisorCores.setSubscriptionId("hypervisorCores");
    cores.setSockets(0);
    cores.setHypervisorSockets(0);
    cores.setHypervisorCores(0);
    cores.setSubscriptionId("cores");
    repository.saveAll(Arrays.asList(hypervisorCores, cores));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            CORES,
            HypervisorReportCategory.NON_HYPERVISOR,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);

    assertEquals(1, found.size());
    assertEquals(8, found.get(0).getCores());
  }

  @Test
  void testFindByMetricIdOnlyRetrieveSocketsCapacity() {
    SubscriptionCapacity cores = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity sockets = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    cores.setHypervisorSockets(0);
    cores.setSockets(0);
    cores.setSubscriptionId("cores");
    sockets.setCores(0);
    sockets.setHypervisorCores(0);
    sockets.setSubscriptionId("sockets");
    repository.saveAll(Arrays.asList(cores, sockets));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            SOCKETS,
            null,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);
    assertEquals(1, found.size());
    assertEquals(4, found.get(0).getSockets());
    assertEquals(20, found.get(0).getHypervisorSockets());
  }

  @Test
  void testFindByMetricIdOnlyRetrieveHypervisorSocketsCapacity() {
    SubscriptionCapacity hypervisorSockets =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity sockets = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    hypervisorSockets.setHypervisorCores(0);
    hypervisorSockets.setSockets(0);
    hypervisorSockets.setCores(0);
    hypervisorSockets.setSubscriptionId("hypervisorSockets");
    sockets.setCores(0);
    sockets.setHypervisorSockets(0);
    sockets.setHypervisorCores(0);
    sockets.setSubscriptionId("sockets");
    repository.saveAll(Arrays.asList(hypervisorSockets, sockets));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            SOCKETS,
            HypervisorReportCategory.HYPERVISOR,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);

    assertEquals(1, found.size());
    assertEquals(20, found.get(0).getHypervisorSockets());
  }

  @Test
  void testFindByMetricIdOnlyRetrieveStandardSocketsCapacity() {
    SubscriptionCapacity hypervisorSockets =
        createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    SubscriptionCapacity sockets = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    hypervisorSockets.setHypervisorCores(0);
    hypervisorSockets.setSockets(0);
    hypervisorSockets.setCores(0);
    hypervisorSockets.setSubscriptionId("hypervisorSockets");
    sockets.setCores(0);
    sockets.setHypervisorSockets(0);
    sockets.setHypervisorCores(0);
    sockets.setSubscriptionId("sockets");
    repository.saveAll(Arrays.asList(hypervisorSockets, sockets));
    repository.flush();

    List<SubscriptionCapacity> found =
        repository.findAllBy(
            "orgId",
            "product",
            SOCKETS,
            HypervisorReportCategory.NON_HYPERVISOR,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            NOWISH,
            FAR_FUTURE);

    assertEquals(1, found.size());
    assertEquals(4, found.get(0).getSockets());
  }

  private SubscriptionCapacity createUnpersisted(OffsetDateTime begin, OffsetDateTime end) {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setAccountNumber("account");
    capacity.setProductId("product");
    capacity.setSubscriptionId("subscription");
    capacity.setBeginDate(begin);
    capacity.setEndDate(end);
    capacity.setHasUnlimitedUsage(false);
    capacity.setOrgId("orgId");
    capacity.setSockets(4);
    capacity.setHypervisorSockets(20);
    capacity.setCores(8);
    capacity.setHypervisorCores(40);
    capacity.setServiceLevel(ServiceLevel.PREMIUM);
    capacity.setUsage(Usage.PRODUCTION);
    return capacity;
  }
}
