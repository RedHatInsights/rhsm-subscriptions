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
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Transactional
@QuarkusTest
class BillableUsageRemittanceRepositoryTest {
  private static final String BILLING_PROVIDER_AWS = "AWS";
  private static final String BILLING_PROVIDER_RED_HAT = "RED_HAT";

  @Inject ApplicationClock clock;
  @Inject BillableUsageRemittanceRepository repository;
  @Inject EntityManager entityManager;

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
    repository.persist(toSave);

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
    repository.persist(accountMonthlyList);
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

  private BillableUsageRemittanceEntity remittanceWithLicense(
      String orgId, String licenseId, Double value, OffsetDateTime remittanceDate) {
    var remittance = remittance(orgId, "product1", BILLING_PROVIDER_AWS, value, remittanceDate);
    remittance.setLicenseId(licenseId);
    return remittance;
  }

  @Test
  void testFindByProductId() {
    BillableUsageRemittanceEntity remittance1 =
        remittance("org123", "product1", BILLING_PROVIDER_AWS, 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance("org456", "product2", BILLING_PROVIDER_AWS, 12.0, clock.endOfCurrentQuarter());
    var accountMonthlyList = List.of(remittance1, remittance2);
    repository.persist(accountMonthlyList);

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
    repository.persist(accountMonthlyList);

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
    repository.persist(accountMonthlyList);

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
    repository.persist(accountMonthlyList);
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
    repository.persist(accountMonthlyList);

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
    repository.persist(accountMonthlyList);

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
  void getMonthlySummaryGroupsByLicenseIdWhilePreservingMonthlyTotal() {
    OffsetDateTime remittanceDate = truncateDate(clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity licenseARowOne =
        remittanceWithLicense("org123", "license-a", 3.0, remittanceDate);
    BillableUsageRemittanceEntity licenseARowTwo =
        remittanceWithLicense("org123", "license-a", 2.0, remittanceDate.plusDays(1));
    BillableUsageRemittanceEntity licenseBRow =
        remittanceWithLicense("org123", "license-b", 5.0, remittanceDate.plusDays(2));

    repository.persist(List.of(licenseARowOne, licenseARowTwo, licenseBRow));

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder().orgId("org123").productId("product1").build();

    List<RemittanceSummaryProjection> results = repository.getRemittanceSummaries(filter);

    assertEquals(2, results.size());
    assertEquals(
        10.0,
        results.stream()
            .mapToDouble(RemittanceSummaryProjection::getTotalRemittedPendingValue)
            .sum(),
        0.001);
    assertEquals(
        5.0,
        results.stream()
            .filter(summary -> "license-a".equals(summary.getLicenseId()))
            .mapToDouble(RemittanceSummaryProjection::getTotalRemittedPendingValue)
            .sum(),
        0.001);
    assertEquals(
        5.0,
        results.stream()
            .filter(summary -> "license-b".equals(summary.getLicenseId()))
            .mapToDouble(RemittanceSummaryProjection::getTotalRemittedPendingValue)
            .sum(),
        0.001);
  }

  @Test
  void findByIdInAndStatusNotPending() {
    BillableUsageRemittanceEntity pendingRemittance =
        remittance("org123", "product1", BILLING_PROVIDER_AWS, 12.0, clock.startOfCurrentMonth());
    pendingRemittance.setStatus(RemittanceStatus.PENDING);

    BillableUsageRemittanceEntity succeededRemittance =
        remittance(
            "org123",
            "product1",
            BILLING_PROVIDER_RED_HAT,
            15.0,
            clock.startOfCurrentMonth().plusDays(1));
    succeededRemittance.setStatus(RemittanceStatus.SUCCEEDED);

    BillableUsageRemittanceEntity failedRemittance =
        remittance(
            "org456", "product2", BILLING_PROVIDER_RED_HAT, 10.0, clock.endOfCurrentQuarter());
    failedRemittance.setStatus(RemittanceStatus.FAILED);

    repository.persist(List.of(pendingRemittance, succeededRemittance, failedRemittance));

    List<String> ids =
        List.of(
            pendingRemittance.getUuid().toString(),
            succeededRemittance.getUuid().toString(),
            failedRemittance.getUuid().toString());

    List<BillableUsageRemittanceEntity> results = repository.findByIdInAndStatusNotPending(ids);

    assertEquals(2, results.size());
    assertTrue(results.containsAll(List.of(succeededRemittance, failedRemittance)));
    assertFalse(results.contains(pendingRemittance));
  }

  @Test
  void testRemittanceFilterUsedByBillableUsageService() {

    // billable usage
    String orgId = "123456";
    String productTag = "rhel-for-x86-els-payg";
    String metricId = "vCPUs";
    String sla = "Premium";
    String usage = "Production";
    String billingProvider = "aws";
    String billingAccountId = "ba123456789";
    String remittancePendingDateStr = "2024-06-19T16:52:17.526219+00:00";

    var billableUsageNoExpected =
        BillableUsageRemittanceEntity.builder()
            .orgId(orgId)
            .productId("another_product")
            .metricId(metricId)
            .accumulationPeriod("2024-06")
            .sla(sla)
            .usage(usage)
            .billingProvider(billingProvider)
            .billingAccountId(billingAccountId)
            .remittedPendingValue(1.0)
            .remittancePendingDate(
                OffsetDateTime.parse(
                    remittancePendingDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .tallyId(UUID.randomUUID())
            .status(RemittanceStatus.PENDING)
            .build();

    var billableUsageRemittanceFromCost =
        BillableUsageRemittanceEntity.builder()
            .orgId(orgId)
            .productId(productTag)
            .metricId(metricId)
            .accumulationPeriod("2024-06")
            .sla(sla)
            .usage(usage)
            .billingProvider(billingProvider)
            .billingAccountId(billingAccountId)
            .remittedPendingValue(1.0)
            .remittancePendingDate(
                OffsetDateTime.parse(
                    remittancePendingDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .tallyId(UUID.randomUUID())
            .status(RemittanceStatus.PENDING)
            .build();

    double expectedRemittedPendingValue = 10.0;
    var billableUsageRemittanceFromRhelemeter =
        BillableUsageRemittanceEntity.builder()
            .orgId(orgId)
            .productId(productTag)
            .metricId(metricId)
            .accumulationPeriod("2024-06")
            .sla(sla)
            .usage(usage)
            .billingProvider(billingProvider)
            .billingAccountId(billingAccountId)
            .remittedPendingValue(expectedRemittedPendingValue)
            .remittancePendingDate(
                OffsetDateTime.parse(
                    remittancePendingDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .tallyId(UUID.randomUUID())
            .status(RemittanceStatus.SUCCEEDED)
            .build();
    repository.persist(
        List.of(
            billableUsageRemittanceFromCost,
            billableUsageRemittanceFromRhelemeter,
            billableUsageNoExpected));

    BillableUsage incomingUsage = new BillableUsage();
    incomingUsage
        .withOrgId(orgId)
        .withBillingAccountId(billingAccountId)
        .withBillingProvider(BillableUsage.BillingProvider.AWS)
        .withSnapshotDate(
            OffsetDateTime.parse(
                "2024-06-16T00:00:00.000000+00:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .withMetricId(metricId)
        .withProductId(productTag)
        .withUsage(BillableUsage.Usage.PRODUCTION)
        .withSla(BillableUsage.Sla.PREMIUM);

    // Build the filter the same way that we do in the billable usage in the service.
    var filter = BillableUsageRemittanceFilter.totalRemittedFilter(incomingUsage);

    List<RemittanceSummaryProjection> remittanceSummaries =
        repository.getRemittanceSummaries(filter);
    assertEquals(2, remittanceSummaries.size());

    var expectedSummary =
        RemittanceSummaryProjection.builder()
            .accumulationPeriod(
                getAccumulationPeriod(
                    billableUsageRemittanceFromRhelemeter.getRemittancePendingDate()))
            .billingAccountId(billableUsageRemittanceFromRhelemeter.getBillingAccountId())
            .billingProvider(billableUsageRemittanceFromRhelemeter.getBillingProvider())
            .orgId(billableUsageRemittanceFromRhelemeter.getOrgId())
            .productId(billableUsageRemittanceFromRhelemeter.getProductId())
            .remittancePendingDate(billableUsageRemittanceFromRhelemeter.getRemittancePendingDate())
            .metricId(billableUsageRemittanceFromRhelemeter.getMetricId())
            .totalRemittedPendingValue(expectedRemittedPendingValue)
            .sla(billableUsageRemittanceFromRhelemeter.getSla())
            .usage(billableUsageRemittanceFromRhelemeter.getUsage())
            .status(billableUsageRemittanceFromRhelemeter.getStatus())
            .build();

    assertTrue(remittanceSummaries.contains(expectedSummary));

    var expectedSummary2 =
        RemittanceSummaryProjection.builder()
            .accumulationPeriod(
                getAccumulationPeriod(billableUsageRemittanceFromCost.getRemittancePendingDate()))
            .billingAccountId(billableUsageRemittanceFromCost.getBillingAccountId())
            .billingProvider(billableUsageRemittanceFromCost.getBillingProvider())
            .orgId(billableUsageRemittanceFromCost.getOrgId())
            .productId(billableUsageRemittanceFromCost.getProductId())
            .remittancePendingDate(billableUsageRemittanceFromCost.getRemittancePendingDate())
            .metricId(billableUsageRemittanceFromCost.getMetricId())
            .totalRemittedPendingValue(billableUsageRemittanceFromCost.getRemittedPendingValue())
            .sla(billableUsageRemittanceFromCost.getSla())
            .usage(billableUsageRemittanceFromCost.getUsage())
            .status(billableUsageRemittanceFromCost.getStatus())
            .build();

    assertTrue(remittanceSummaries.contains(expectedSummary2));
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
    repository.persist(accountMonthlyList);

    BillableUsageRemittanceFilter filter1 =
        BillableUsageRemittanceFilter.builder()
            .accumulationPeriod(getAccumulationPeriod(remittance1.getRemittancePendingDate()))
            .build();

    List<RemittanceSummaryProjection> results = repository.getRemittanceSummaries(filter1);
    assertEquals(1, results.size());
    assertEquals(24.0, results.get(0).getTotalRemittedPendingValue());
  }

  @Test
  void testUpdateStatusForStaleRemittances() {
    var now = OffsetDateTime.now(ZoneOffset.UTC);

    var staleInProgress1 = remittance("org1", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    staleInProgress1.setStatus(RemittanceStatus.IN_PROGRESS);
    var staleInProgress2 = remittance("org2", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    staleInProgress2.setStatus(RemittanceStatus.IN_PROGRESS);
    var freshInProgress = remittance("org3", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    freshInProgress.setStatus(RemittanceStatus.IN_PROGRESS);
    var staleSent = remittance("org4", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    staleSent.setStatus(RemittanceStatus.SENT);

    repository.persist(List.of(staleInProgress1, staleInProgress2, freshInProgress, staleSent));
    repository.flush();
    setLastModified(staleInProgress1, now.minusDays(10).toInstant());
    setLastModified(staleInProgress2, now.minusDays(8).toInstant());
    setLastModified(freshInProgress, now.minusDays(2).toInstant());
    setLastModified(staleSent, now.minusDays(10).toInstant());
    entityManager.clear();

    var staleInProgressCount =
        repository.updateStatusForStaleRemittances(
            Duration.ofDays(7),
            RemittanceStatus.IN_PROGRESS,
            RemittanceStatus.FAILED,
            RemittanceErrorCode.SENDING_TO_AGGREGATE_TOPIC);

    assertEquals(2, staleInProgressCount);

    var staleInProgressResults = repository.find("status = ?1", RemittanceStatus.FAILED).list();

    assertTrue(
        staleInProgressResults.stream()
            .anyMatch(result -> result.getUuid().equals(staleInProgress1.getUuid())));
    assertTrue(
        staleInProgressResults.stream()
            .anyMatch(result -> result.getUuid().equals(staleInProgress2.getUuid())));
    assertFalse(
        staleInProgressResults.stream()
            .anyMatch(result -> result.getUuid().equals(freshInProgress.getUuid())));
    assertFalse(
        staleInProgressResults.stream()
            .anyMatch(result -> result.getUuid().equals(staleSent.getUuid())));
  }

  @Test
  void testStaleStatusesWithNullLastModified() {
    var now = OffsetDateTime.now(ZoneOffset.UTC);

    var inProgressNullLastModified =
        remittance("org1", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    inProgressNullLastModified.setStatus(RemittanceStatus.IN_PROGRESS);
    var sentNullLastModified = remittance("org2", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    sentNullLastModified.setStatus(RemittanceStatus.SENT);

    repository.persist(List.of(inProgressNullLastModified, sentNullLastModified));
    repository.flush();
    setLastModified(inProgressNullLastModified, null);
    setLastModified(sentNullLastModified, null);
    entityManager.clear();

    var staleSentResultsCount =
        repository.updateStatusForStaleRemittances(
            Duration.ofDays(7), RemittanceStatus.SENT, RemittanceStatus.UNKNOWN, null);
    var staleInProgressResultsCount =
        repository.updateStatusForStaleRemittances(
            Duration.ofDays(7),
            RemittanceStatus.IN_PROGRESS,
            RemittanceStatus.FAILED,
            RemittanceErrorCode.SENDING_TO_AGGREGATE_TOPIC);

    assertEquals(0, staleInProgressResultsCount);
    assertEquals(0, staleSentResultsCount);
  }

  @Test
  void testFindStaleWithMixedLastModifiedAndStatus() {
    var now = OffsetDateTime.now(ZoneOffset.UTC);

    var staleInProgressNullLastModified =
        remittance("org1", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    staleInProgressNullLastModified.setStatus(RemittanceStatus.IN_PROGRESS);
    var freshInProgressOldRemittanceDate =
        remittance("org2", "product1", BILLING_PROVIDER_AWS, 12.0, now.minusDays(10));
    freshInProgressOldRemittanceDate.setStatus(RemittanceStatus.IN_PROGRESS);
    var staleLastModifiedFreshRemittanceDate =
        remittance("org3", "product1", BILLING_PROVIDER_AWS, 12.0, now);
    staleLastModifiedFreshRemittanceDate.setStatus(RemittanceStatus.IN_PROGRESS);

    repository.persist(
        List.of(
            staleInProgressNullLastModified,
            freshInProgressOldRemittanceDate,
            staleLastModifiedFreshRemittanceDate));
    repository.flush();
    setLastModified(staleInProgressNullLastModified, null);
    setLastModified(freshInProgressOldRemittanceDate, now.minusDays(2).toInstant());
    setLastModified(staleLastModifiedFreshRemittanceDate, now.minusDays(10).toInstant());
    entityManager.clear();

    var staleInProgressCount =
        repository.updateStatusForStaleRemittances(
            Duration.ofDays(7),
            RemittanceStatus.IN_PROGRESS,
            RemittanceStatus.FAILED,
            RemittanceErrorCode.SENDING_TO_AGGREGATE_TOPIC);

    assertEquals(1, staleInProgressCount);

    var staleInProgressResults = repository.find("status = ?1", RemittanceStatus.FAILED).list();
    assertTrue(
        staleInProgressResults.stream()
            .anyMatch(
                result -> result.getUuid().equals(staleLastModifiedFreshRemittanceDate.getUuid())));
    assertFalse(
        staleInProgressResults.stream()
            .anyMatch(
                result -> result.getUuid().equals(staleInProgressNullLastModified.getUuid())));
    assertFalse(
        staleInProgressResults.stream()
            .anyMatch(
                result -> result.getUuid().equals(freshInProgressOldRemittanceDate.getUuid())));
  }

  private void setLastModified(BillableUsageRemittanceEntity entity, Instant lastModified) {
    if (lastModified == null) {
      // Match the Liquibase schema, where last_modified is nullable. Hibernate's generated test
      // schema marks @CurrentTimestamp columns non-null.
      entityManager
          .createNativeQuery(
              "ALTER TABLE billable_usage_remittance ALTER COLUMN last_modified DROP NOT NULL")
          .executeUpdate();
    }
    var query =
        entityManager.createNativeQuery(
            lastModified == null
                ? "UPDATE billable_usage_remittance SET last_modified = NULL WHERE uuid = :uuid"
                : "UPDATE billable_usage_remittance SET last_modified = :lastModified "
                    + "WHERE uuid = :uuid");
    query.setParameter("uuid", entity.getUuid());
    if (lastModified != null) {
      query.setParameter("lastModified", Timestamp.from(lastModified));
    }
    query.executeUpdate();
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
