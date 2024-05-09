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
package com.redhat.swatch.billable.usage.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.registry.Sla;
import com.redhat.swatch.configuration.registry.Usage;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Transactional
@QuarkusTest
class BillableUsageRemittanceRepositoryTest {

  private static final String BILLING_PROVIDER_AWS = "AWS";
  private static final String BILLING_PROVIDER_RED_HAT = "RED_HAT";

  @Inject ApplicationClock clock;
  @Inject BillableUsageRemittanceRepository repository;

  @Transactional
  @BeforeEach()
  public void setUp() {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    repository.deleteAll();
  }

  @Test
  void saveAndFetch() {
    BillableUsageRemittanceEntity remittance =
        remittance("org123", "product1", BILLING_PROVIDER_AWS, 12.0, clock.startOfCurrentMonth());
    repository.persist(remittance);
    Optional<BillableUsageRemittanceEntity> fetched =
        repository.findOne(billableUsageRemittanceFilterFromEntity(remittance));
    assertTrue(fetched.isPresent());
    assertEquals(remittance, fetched.get());
  }

  @Test
  void deleteByOrgId() {
    BillableUsageRemittanceEntity remittance1 =
        remittance("org123", "product1", BILLING_PROVIDER_AWS, 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance("org555", "product1", BILLING_PROVIDER_AWS, 12.0, clock.startOfCurrentMonth());

    List<BillableUsageRemittanceEntity> toSave = List.of(remittance1, remittance2);
    repository.saveAll(toSave);

    repository.deleteByOrgId("org123");
    repository.flush();
    assertTrue(repository.findOne(billableUsageRemittanceFilterFromEntity(remittance1)).isEmpty());
    Optional<BillableUsageRemittanceEntity> found =
        repository.findOne(billableUsageRemittanceFilterFromEntity(remittance2));
    assertTrue(found.isPresent());
    assertEquals(remittance2, found.get());
  }

  @Test
  void findByAccount() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_AWS,
            12.0,
            truncateDate(clock.startOfCurrentMonth()));
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_AWS,
            12.0,
            truncateDate(clock.endOfCurrentQuarter()));
    var accountMonthlyList = List.of(remittance1, remittance2);
    repository.saveAll(accountMonthlyList);
    List<BillableUsageRemittanceEntity> found =
        repository.find(BillableUsageRemittanceFilter.builder().productId("product1").build());
    assertFalse(found.isEmpty());
    assertEquals(accountMonthlyList.size(), found.size());
    accountMonthlyList.forEach(expected -> assertTrue(found.contains(expected)));
  }

  private BillableUsageRemittanceEntity remittance(
      String orgId,
      String productId,
      String billingProvider,
      Double value,
      OffsetDateTime remittanceDate) {
    return BillableUsageRemittanceEntity.builder()
        .usage(Usage.PRODUCTION.getValue())
        .orgId(orgId)
        .billingProvider(billingProvider)
        .billingAccountId(orgId + "_ba")
        .productId(productId)
        .sla(Sla.PREMIUM.getValue())
        .metricId(MetricIdUtils.getCores().toString())
        .accumulationPeriod(AccumulationPeriodFormatter.toMonthId(remittanceDate))
        .remittancePendingDate(remittanceDate)
        .remittedPendingValue(value)
        .build();
  }

  @Test
  void testFindByProductId() {
    BillableUsageRemittanceEntity remittance1 =
        remittance("org123", "product1", BILLING_PROVIDER_AWS, 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance("org456", "product2", BILLING_PROVIDER_AWS, 12.0, clock.endOfCurrentQuarter());
    var accountMonthlyList = List.of(remittance1, remittance2);
    repository.saveAll(accountMonthlyList);

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder().productId("product2").orgId("org456").build();
    Optional<BillableUsageRemittanceEntity> usage = repository.findOne(filter);
    assertTrue(usage.isPresent());
    assertEquals("product2", usage.get().getProductId());
  }

  @Test
  void findByBillingProvider() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_AWS,
            12.0,
            truncateDate(clock.startOfCurrentMonth()));
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            12.0,
            truncateDate(clock.endOfCurrentQuarter()));
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org456",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            12.0,
            truncateDate(clock.endOfCurrentQuarter()));
    var accountMonthlyList = List.of(remittance1, remittance2, remittance3);
    repository.saveAll(accountMonthlyList);

    BillableUsageRemittanceFilter filter1 =
        BillableUsageRemittanceFilter.builder().billingProvider(BILLING_PROVIDER_RED_HAT).build();

    List<BillableUsageRemittanceEntity> byBillingProviderResults = repository.find(filter1);
    assertEquals(2, byBillingProviderResults.size());
    assertTrue(byBillingProviderResults.containsAll(List.of(remittance2, remittance3)));

    BillableUsageRemittanceFilter filter2 =
        BillableUsageRemittanceFilter.builder()
            .orgId(remittance3.getOrgId())
            .billingProvider(BILLING_PROVIDER_RED_HAT)
            .build();
    List<BillableUsageRemittanceEntity> byBillingProviderAndOrgId = repository.find(filter2);
    assertEquals(1, byBillingProviderAndOrgId.size());
    assertEquals(remittance3, byBillingProviderAndOrgId.get(0));
  }

  @Test
  void findByBillingAccountId() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_AWS,
            12.0,
            truncateDate(clock.startOfCurrentMonth()));
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            12.0,
            truncateDate(clock.endOfCurrentQuarter()));
    remittance2.setOrgId("special_org");

    var accountMonthlyList = List.of(remittance1, remittance2);
    repository.saveAll(accountMonthlyList);

    BillableUsageRemittanceFilter filter1 =
        BillableUsageRemittanceFilter.builder()
            // Will be the same generated value for remittance1 and remittance2
            .billingAccountId(remittance1.getBillingAccountId())
            .build();

    List<BillableUsageRemittanceEntity> byBillingAccountIdResults = repository.find(filter1);
    assertEquals(2, byBillingAccountIdResults.size());
    assertTrue(byBillingAccountIdResults.containsAll(List.of(remittance1, remittance2)));

    BillableUsageRemittanceFilter filter2 =
        BillableUsageRemittanceFilter.builder()
            .orgId(remittance2.getOrgId())
            .billingAccountId(remittance2.getBillingAccountId())
            .build();
    List<BillableUsageRemittanceEntity> byBillingProviderAndOrgId = repository.find(filter2);
    assertEquals(1, byBillingProviderAndOrgId.size());
    assertEquals(remittance2, byBillingProviderAndOrgId.get(0));
  }

  @Test
  void findByRange() {
    OffsetDateTime ending = OffsetDateTime.of(LocalDateTime.of(2023, 12, 1, 12, 0), ZoneOffset.UTC);
    OffsetDateTime beginning = ending.minusDays(4);

    BillableUsageRemittanceEntity remittance1 =
        remittance("org123", "product1", BILLING_PROVIDER_AWS, 12.0, ending);
    BillableUsageRemittanceEntity remittance2 =
        remittance("org123", "product1", BILLING_PROVIDER_RED_HAT, 12.0, beginning);
    BillableUsageRemittanceEntity remittance3 =
        remittance("org234", "product1", BILLING_PROVIDER_RED_HAT, 12.0, ending.minusDays(8));

    var accountMonthlyList = List.of(remittance1, remittance2, remittance3);
    repository.saveAll(accountMonthlyList);
    repository.flush();

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder().beginning(beginning).ending(ending).build();

    List<BillableUsageRemittanceEntity> byDateRange = repository.find(filter);
    assertEquals(2, byDateRange.size());
    assertTrue(byDateRange.containsAll(List.of(remittance1, remittance2)));
  }

  @Test
  void findByByRangeAndOrgId() {
    OffsetDateTime ending = OffsetDateTime.of(LocalDateTime.of(2023, 12, 1, 12, 0), ZoneOffset.UTC);
    OffsetDateTime beginning = ending.minusDays(8);

    BillableUsageRemittanceEntity remittance1 =
        remittance("org123", "product1", BILLING_PROVIDER_AWS, 12.0, ending);
    BillableUsageRemittanceEntity remittance2 =
        remittance("org123", "product1", BILLING_PROVIDER_RED_HAT, 12.0, ending.minusDays(4));
    BillableUsageRemittanceEntity remittance3 =
        remittance("org234", "product1", BILLING_PROVIDER_RED_HAT, 12.0, beginning);
    // Outside range with matching orgId.
    BillableUsageRemittanceEntity remittance4 =
        remittance("org234", "product1", BILLING_PROVIDER_AWS, 12.0, beginning.minusDays(1));

    var accountMonthlyList = List.of(remittance1, remittance2, remittance3, remittance4);
    repository.saveAll(accountMonthlyList);

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(remittance3.getOrgId())
            .beginning(beginning)
            .ending(ending)
            .build();

    List<BillableUsageRemittanceEntity> byDateRangeAndOrgId = repository.find(filter);
    assertEquals(1, byDateRangeAndOrgId.size());
    assertEquals(remittance3, byDateRangeAndOrgId.get(0));
  }

  @Test
  void getMonthlySummary() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            12.0,
            truncateDate(clock.startOfCurrentMonth()));
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            12.0,
            truncateDate(clock.startOfCurrentMonth().plusDays(1)));
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org456",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            15.0,
            truncateDate(clock.endOfCurrentMonth()));

    var accountMonthlyList = List.of(remittance1, remittance2, remittance3);
    repository.saveAll(accountMonthlyList);

    BillableUsageRemittanceFilter filter1 =
        BillableUsageRemittanceFilter.builder().productId(remittance1.getProductId()).build();

    var expectedSummary1 =
        RemittanceSummaryProjection.builder()
            .accumulationPeriod(getAccumulationPeriod(remittance1.getRemittancePendingDate()))
            .billingAccountId(remittance1.getBillingAccountId())
            .billingProvider(remittance1.getBillingProvider())
            .orgId("org123")
            .productId(remittance1.getProductId())
            .remittancePendingDate(remittance2.getRemittancePendingDate())
            .metricId(remittance1.getMetricId())
            .totalRemittedPendingValue(24.0)
            .sla(remittance1.getSla())
            .usage(remittance1.getUsage())
            .build();

    var expectedSummary2 =
        RemittanceSummaryProjection.builder()
            .accumulationPeriod(getAccumulationPeriod(remittance3.getRemittancePendingDate()))
            .billingAccountId(remittance3.getBillingAccountId())
            .billingProvider(remittance3.getBillingProvider())
            .orgId("org456")
            .productId(remittance3.getProductId())
            .remittancePendingDate(remittance3.getRemittancePendingDate())
            .metricId(remittance3.getMetricId())
            .totalRemittedPendingValue(15.0)
            .sla(remittance3.getSla())
            .usage(remittance3.getUsage())
            .build();

    List<RemittanceSummaryProjection> results = repository.getRemittanceSummaries(filter1);
    assertEquals(2, results.size());
    assertTrue(results.containsAll(List.of(expectedSummary1, expectedSummary2)));
  }

  @Test
  void getMonthlySummaryForSpecificMonth() {
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            12.0,
            truncateDate(clock.startOfCurrentMonth()));
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            12.0,
            truncateDate(clock.startOfCurrentMonth().plusDays(1)));
    // One remittance is out of date accumulation_period and should not be counted
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            15.0,
            truncateDate(clock.endOfCurrentMonth().plusDays(1)));

    var accountMonthlyList = List.of(remittance1, remittance2, remittance3);
    repository.saveAll(accountMonthlyList);

    BillableUsageRemittanceFilter filter1 =
        BillableUsageRemittanceFilter.builder()
            .accumulationPeriod(getAccumulationPeriod(remittance1.getRemittancePendingDate()))
            .build();

    List<RemittanceSummaryProjection> results = repository.getRemittanceSummaries(filter1);
    assertEquals(1, results.size());
    assertEquals(24.0, results.get(0).getTotalRemittedPendingValue());
  }

  // In memory DB does not save same length of decimals so truncate to make sure they equal
  OffsetDateTime truncateDate(OffsetDateTime date) {
    return date.truncatedTo(ChronoUnit.MILLIS);
  }

  public static String getAccumulationPeriod(OffsetDateTime reference) {
    return AccumulationPeriodFormatter.toMonthId(reference);
  }

  private static BillableUsageRemittanceFilter billableUsageRemittanceFilterFromEntity(
      BillableUsageRemittanceEntity entity) {
    return BillableUsageRemittanceFilter.builder()
        .orgId(entity.getOrgId())
        .billingAccountId(entity.getBillingAccountId())
        .billingProvider(entity.getBillingProvider())
        .accumulationPeriod(entity.getAccumulationPeriod())
        .metricId(entity.getMetricId())
        .productId(entity.getProductId())
        .sla(entity.getSla())
        .usage(entity.getUsage())
        .build();
  }
}
