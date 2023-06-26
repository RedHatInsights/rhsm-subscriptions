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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Subscription.SubscriptionCompoundId;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.db.model.SubscriptionProductId;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.model.MetricId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@ActiveProfiles("test")
class SubscriptionMeasurementRepositoryTest {
  private static final OffsetDateTime START =
      OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime END =
      OffsetDateTime.of(2022, 2, 2, 0, 0, 0, 0, ZoneOffset.UTC);
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private OfferingRepository offeringRepository;
  @Autowired private SubscriptionMeasurementRepository subscriptionMeasurementRepository;
  private SubscriptionProductId subscriptionProductId;
  private Subscription subscription;
  private SubscriptionMeasurement physicalCores;

  @BeforeEach
  void setUp() {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    var offering =
        Offering.builder()
            .sku("premiumproduction")
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .build();

    subscription =
        Subscription.builder()
            .accountNumber("account123")
            .subscriptionId("subscription123")
            .subscriptionNumber("subscriptionNumber123")
            .offering(offering)
            .orgId("org123")
            .billingProvider(BillingProvider.RED_HAT)
            .quantity(10)
            .startDate(START)
            .endDate(END)
            .build();

    subscriptionProductId = SubscriptionProductId.builder().productId("RHEL").build();

    subscription.addSubscriptionProductId(subscriptionProductId);
    physicalCores = createMeasurement("PHYSICAL", MetricId.CORES, 42.0);
    subscription.addSubscriptionMeasurement(physicalCores);
    subscription.setOffering(offering);

    offeringRepository.saveAndFlush(offering);
    subscriptionRepository.saveAndFlush(subscription);
  }

  @Test
  void testSimpleFetch() {
    var subscriptionId = new SubscriptionCompoundId();
    subscriptionId.setSubscriptionId("subscription123");
    subscriptionId.setStartDate(subscription.getStartDate());
    var key = new SubscriptionMeasurementKey();
    key.setSubscription(subscriptionId);
    key.setMetricId(MetricId.CORES.toString());
    key.setMeasurementType("PHYSICAL");
    var result = subscriptionMeasurementRepository.findById(key).orElseThrow();
    assertEquals(physicalCores, result);
  }

  @Test
  void testFiltersByOrgId() {
    // Same subscription ID; different start date since the Subscription PK is a composite of the
    // ID and start date.
    var wrongOrgId =
        Subscription.builder()
            .accountNumber("account123")
            .subscriptionId("subscription123")
            .subscriptionNumber("subscriptionNumber123")
            .orgId("other")
            .billingProvider(BillingProvider.RED_HAT)
            .quantity(10)
            .startDate(START.plusMonths(1))
            .endDate(START.plusYears(1))
            .build();

    var unexpectedMeasurement = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);
    wrongOrgId.addSubscriptionProductId(SubscriptionProductId.builder().productId("RHEL").build());
    wrongOrgId.addSubscriptionMeasurement(unexpectedMeasurement);
    subscriptionRepository.saveAndFlush(wrongOrgId);
    var result =
        subscriptionMeasurementRepository.findAllBy(
            "org123", "RHEL", null, null, null, null, START.minusYears(2), START.plusYears(2));

    assertThat(result, contains(physicalCores));
    assertThat(result, not(contains(unexpectedMeasurement)));
  }

  @Test
  void testFiltersOutSubsBeforeRange() {
    var specification =
        SubscriptionMeasurementRepository.subscriptionIsActiveBetween(
            END.plusDays(1), END.plusDays(5));
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFiltersOutSubsAfterRange() {
    var specification =
        SubscriptionMeasurementRepository.subscriptionIsActiveBetween(
            START.minusDays(5), START.minusDays(1));
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFiltersOutSubStartsAfterRange() {
    var specification =
        SubscriptionMeasurementRepository.subscriptionIsActiveBetween(
            START.minusDays(5), START.minusDays(1));
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFindsSubStartingBeforeRangeAndEndingDuringRange() { // *
    var specification =
        SubscriptionMeasurementRepository.subscriptionIsActiveBetween(
            START.plusDays(5), END.plusDays(5));
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(physicalCores));
  }

  @Test
  void testFindsSubStartingBeforeRangeAndEndingAfterRange() { // *
    var specification =
        SubscriptionMeasurementRepository.subscriptionIsActiveBetween(
            START.plusDays(5), END.minusDays(5));
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(physicalCores));
  }

  @Test
  void testFindsSubStartingDuringRangeAndEndingDuringRange() { // *
    var specification =
        SubscriptionMeasurementRepository.subscriptionIsActiveBetween(
            START.minusDays(5), END.plusDays(5));
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(physicalCores));
  }

  @Test
  void testFindsSubStartingDuringRangeAndEndingAfterRange() { // *
    var specification =
        SubscriptionMeasurementRepository.subscriptionIsActiveBetween(
            START.minusDays(5), END.minusDays(5));
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(physicalCores));
  }

  @Test
  void testFiltersBySla() {
    var specification = SubscriptionMeasurementRepository.slaEquals(ServiceLevel.PREMIUM);
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(physicalCores));

    specification = SubscriptionMeasurementRepository.slaEquals(ServiceLevel.SELF_SUPPORT);
    result = subscriptionMeasurementRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFiltersByUsage() {
    var specification = SubscriptionMeasurementRepository.usageEquals(Usage.PRODUCTION);
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(physicalCores));

    specification = SubscriptionMeasurementRepository.usageEquals(Usage.DEVELOPMENT_TEST);
    result = subscriptionMeasurementRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testMetricsCriteriaForPhysical() {
    var hypervisorCores = createMeasurement("HYPERVISOR", MetricId.CORES, 42.0);
    subscription.addSubscriptionMeasurement(hypervisorCores);
    subscriptionRepository.saveAndFlush(subscription);
    var specification =
        SubscriptionMeasurementRepository.metricsCriteria(
            HypervisorReportCategory.NON_HYPERVISOR, MetricId.CORES.toString());
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, not(contains(hypervisorCores)));
    assertThat(result, contains(physicalCores));
  }

  @Test
  void testMetricsCriteriaForHypervisor() {
    var hypervisorCores = createMeasurement("HYPERVISOR", MetricId.CORES, 42.0);
    subscription.addSubscriptionMeasurement(hypervisorCores);
    subscriptionRepository.saveAndFlush(subscription);
    var specification =
        SubscriptionMeasurementRepository.metricsCriteria(
            HypervisorReportCategory.HYPERVISOR, MetricId.CORES.toString());
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(hypervisorCores));
    assertThat(result, not(contains(physicalCores)));
  }

  @Test
  void testMetricsCriteriaFiltersByCategory() {
    var hypervisorCores = createMeasurement("HYPERVISOR", MetricId.CORES, 3.0);
    subscription.addSubscriptionMeasurement(hypervisorCores);
    subscriptionRepository.saveAndFlush(subscription);
    var specification =
        SubscriptionMeasurementRepository.metricsCriteria(
            HypervisorReportCategory.NON_HYPERVISOR, MetricId.CORES.toString());
    var result = subscriptionMeasurementRepository.findAll(specification);
    assertThat(result, contains(physicalCores));
    assertThat(result, not(contains(hypervisorCores)));
  }

  SubscriptionMeasurement createMeasurement(String type, MetricId metric, double value) {
    return SubscriptionMeasurement.builder()
        .measurementType(type)
        .metricId(metric.toString())
        .value(value)
        .build();
  }
}
