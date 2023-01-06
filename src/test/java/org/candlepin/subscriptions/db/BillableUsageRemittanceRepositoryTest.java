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

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.json.BillableUsage.BillingProvider;
import org.candlepin.subscriptions.json.BillableUsage.Sla;
import org.candlepin.subscriptions.json.BillableUsage.Uom;
import org.candlepin.subscriptions.json.BillableUsage.Usage;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureTestDatabase
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class BillableUsageRemittanceRepositoryTest {

  @Autowired private BillableUsageRemittanceRepository repository;
  private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

  @Test
  void saveAndFetch() {
    BillableUsageRemittanceEntity remittance =
        remittance(
            "org123", "product1", BillingProvider.AWS.value(), 12.0, clock.startOfCurrentMonth());
    repository.saveAndFlush(remittance);
    Optional<BillableUsageRemittanceEntity> fetched = repository.findById(remittance.getKey());
    assertTrue(fetched.isPresent());
    assertEquals(remittance, fetched.get());
  }

  @Test
  void deleteByOrgId() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123", "product1", BillingProvider.AWS.value(), 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org555", "product1", BillingProvider.AWS.value(), 12.0, clock.startOfCurrentMonth());

    List<BillableUsageRemittanceEntity> toSave = List.of(remittance1, remittance2);
    repository.saveAllAndFlush(toSave);

    repository.deleteByKeyOrgId("org123");
    repository.flush();
    assertTrue(repository.findById(remittance1.getKey()).isEmpty());
    Optional<BillableUsageRemittanceEntity> found = repository.findById(remittance2.getKey());
    assertTrue(found.isPresent());
    assertEquals(remittance2, found.get());
  }

  @Test
  void findByAccount() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123", "product1", BillingProvider.AWS.value(), 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123", "product1", BillingProvider.AWS.value(), 12.0, clock.endOfCurrentQuarter());
    var accountMonthlyList = List.of(remittance1, remittance2);
    repository.saveAllAndFlush(accountMonthlyList);
    List<BillableUsageRemittanceEntity> found =
        repository.filterBy(
            BillableUsageRemittanceFilter.builder()
                .account(remittance1.getAccountNumber())
                .productId("product1")
                .build());
    assertFalse(found.isEmpty());
    assertEquals(accountMonthlyList, found);
  }

  private BillableUsageRemittanceEntity remittance(
      String orgId,
      String productId,
      String billingProvider,
      Double value,
      OffsetDateTime remittanceDate) {
    BillableUsageRemittanceEntityPK key =
        BillableUsageRemittanceEntityPK.builder()
            .usage(Usage.PRODUCTION.value())
            .orgId(orgId)
            .billingProvider(billingProvider)
            .billingAccountId(orgId + "_ba")
            .productId(productId)
            .sla(Sla.PREMIUM.value())
            .metricId(Uom.CORES.value())
            .accumulationPeriod(InstanceMonthlyTotalKey.formatMonthId(remittanceDate))
            .build();
    return BillableUsageRemittanceEntity.builder()
        .key(key)
        .billingFactor(1.0)
        .remittanceDate(remittanceDate)
        .remittedValue(value)
        .build();
  }

  @Test
  void testFindByProductId() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123", "product1", BillingProvider.AWS.value(), 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org456", "product2", BillingProvider.AWS.value(), 12.0, clock.endOfCurrentQuarter());
    var accountMonthlyList = List.of(remittance1, remittance2);
    repository.saveAllAndFlush(accountMonthlyList);

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder().productId("product2").orgId("org456").build();
    List<BillableUsageRemittanceEntity> usage = repository.filterBy(filter);
    assertEquals(1, usage.size());
    assertEquals("product2", usage.get(0).getKey().getProductId());
  }

  @Test
  void findByBillingProvider() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123", "product1", BillingProvider.AWS.value(), 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BillingProvider.RED_HAT.value(),
            12.0,
            clock.endOfCurrentQuarter());
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org456",
            "product1",
            BillingProvider.RED_HAT.value(),
            12.0,
            clock.endOfCurrentQuarter());
    var accountMonthlyList = List.of(remittance1, remittance2, remittance3);
    repository.saveAllAndFlush(accountMonthlyList);

    BillableUsageRemittanceFilter filter1 =
        BillableUsageRemittanceFilter.builder()
            .billingProvider(BillingProvider.RED_HAT.value())
            .build();

    List<BillableUsageRemittanceEntity> byBillingProviderResults = repository.filterBy(filter1);
    assertEquals(2, byBillingProviderResults.size());
    assertTrue(byBillingProviderResults.containsAll(List.of(remittance2, remittance3)));

    BillableUsageRemittanceFilter filter2 =
        BillableUsageRemittanceFilter.builder()
            .orgId(remittance3.getKey().getOrgId())
            .billingProvider(BillingProvider.RED_HAT.value())
            .build();
    List<BillableUsageRemittanceEntity> byBillingProviderAndOrgId = repository.filterBy(filter2);
    assertEquals(1, byBillingProviderAndOrgId.size());
    assertEquals(remittance3, byBillingProviderAndOrgId.get(0));
  }

  @Test
  void findByBillingAccountId() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123", "product1", BillingProvider.AWS.value(), 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BillingProvider.RED_HAT.value(),
            12.0,
            clock.endOfCurrentQuarter());
    remittance2.getKey().setOrgId("special_org");

    var accountMonthlyList = List.of(remittance1, remittance2);
    repository.saveAllAndFlush(accountMonthlyList);

    BillableUsageRemittanceFilter filter1 =
        BillableUsageRemittanceFilter.builder()
            // Will be the same generated value for remittance1 and remittance2
            .billingAccountId(remittance1.getKey().getBillingAccountId())
            .build();

    List<BillableUsageRemittanceEntity> byBillingAccountIdResults = repository.filterBy(filter1);
    assertEquals(2, byBillingAccountIdResults.size());
    assertTrue(byBillingAccountIdResults.containsAll(List.of(remittance1, remittance2)));

    BillableUsageRemittanceFilter filter2 =
        BillableUsageRemittanceFilter.builder()
            .orgId(remittance2.getKey().getOrgId())
            .billingAccountId(remittance2.getKey().getBillingAccountId())
            .build();
    List<BillableUsageRemittanceEntity> byBillingProviderAndOrgId = repository.filterBy(filter2);
    assertEquals(1, byBillingProviderAndOrgId.size());
    assertEquals(remittance2, byBillingProviderAndOrgId.get(0));
  }

  @Test
  void findByRange() {
    OffsetDateTime ending = clock.now();
    OffsetDateTime beginning = clock.now().minusDays(4);

    BillableUsageRemittanceEntity remittance1 =
        remittance("org123", "product1", BillingProvider.AWS.value(), 12.0, ending);
    BillableUsageRemittanceEntity remittance2 =
        remittance("org123", "product1", BillingProvider.RED_HAT.value(), 12.0, beginning);
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org234", "product1", BillingProvider.RED_HAT.value(), 12.0, clock.now().minusDays(8));

    var accountMonthlyList = List.of(remittance1, remittance2, remittance3);
    repository.saveAllAndFlush(accountMonthlyList);

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder().beginning(beginning).ending(ending).build();

    List<BillableUsageRemittanceEntity> byDateRange = repository.filterBy(filter);
    assertEquals(2, byDateRange.size());
    assertTrue(byDateRange.containsAll(List.of(remittance1, remittance2)));
  }

  @Test
  void findByByRangeAndOrgId() {
    OffsetDateTime ending = clock.now();
    OffsetDateTime beginning = clock.now().minusDays(8);

    BillableUsageRemittanceEntity remittance1 =
        remittance("org123", "product1", BillingProvider.AWS.value(), 12.0, ending);
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123", "product1", BillingProvider.RED_HAT.value(), 12.0, clock.now().minusDays(4));
    BillableUsageRemittanceEntity remittance3 =
        remittance("org234", "product1", BillingProvider.RED_HAT.value(), 12.0, beginning);
    // Outside range with matching orgId.
    BillableUsageRemittanceEntity remittance4 =
        remittance("org234", "product1", BillingProvider.AWS.value(), 12.0, beginning.minusDays(1));

    var accountMonthlyList = List.of(remittance1, remittance2, remittance3, remittance4);
    repository.saveAllAndFlush(accountMonthlyList);

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(remittance3.getKey().getOrgId())
            .beginning(beginning)
            .ending(ending)
            .build();

    List<BillableUsageRemittanceEntity> byDateRangeAndOrgId = repository.filterBy(filter);
    assertEquals(1, byDateRangeAndOrgId.size());
    assertEquals(remittance3, byDateRangeAndOrgId.get(0));
  }
}
