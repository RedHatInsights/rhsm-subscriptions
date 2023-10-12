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

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
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

  private static final String CORES = "Cores";
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private OfferingRepository offeringRepository;
  private Subscription subscription;
  private SubscriptionMeasurementKey physicalCores =
      createMeasurementKey("PHYSICAL", MetricId.fromString(CORES));

  private Subscription createTestSubscription(String subscriptionId) {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    var offering =
        Offering.builder()
            .sku("premiumproduction")
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .build();

    var subscription =
        Subscription.builder()
            .subscriptionId(subscriptionId)
            .subscriptionNumber("subscriptionNumber123")
            .offering(offering)
            .orgId("org123")
            .billingProvider(BillingProvider.RED_HAT)
            .quantity(10)
            .startDate(START)
            .endDate(END)
            .build();

    subscription.setSubscriptionProductIds(new HashSet<>(List.of("RHEL")));
    subscription.getSubscriptionMeasurements().put(physicalCores, 42.0);
    subscription.setOffering(offering);

    offeringRepository.saveAndFlush(offering);
    subscriptionRepository.saveAndFlush(subscription);

    return subscription;
  }

  @BeforeEach
  void setUp() {
    subscription = createTestSubscription("subscription123");
  }

  @Test
  void testFiltersByOrgId() {
    // Same subscription ID; different start date since the Subscription PK is a composite of the
    // ID and start date.
    var wrongOrgId =
        Subscription.builder()
            .subscriptionId("subscription123")
            .subscriptionNumber("subscriptionNumber123")
            .orgId("other")
            .billingProvider(BillingProvider.RED_HAT)
            .quantity(10)
            .startDate(START.plusMonths(1))
            .endDate(START.plusYears(1))
            .build();

    var productIds = new HashSet<>(List.of("RHEL"));
    wrongOrgId.setSubscriptionProductIds(productIds);

    wrongOrgId.getSubscriptionMeasurements().put(physicalCores, 8.0);

    subscriptionRepository.saveAndFlush(wrongOrgId);

    var criteria =
        DbReportCriteria.builder()
            .orgId("org123")
            .productId("RHEL")
            .beginning(START.minusYears(2))
            .ending(START.plusYears(2))
            .build();

    var result =
        subscriptionRepository.findByCriteria(criteria, Sort.unsorted()).stream().findFirst();

    result.ifPresentOrElse(
        x -> {
          assertEquals(42.0, x.getSubscriptionMeasurements().get(physicalCores), 42.0);
          assertEquals(1, x.getSubscriptionMeasurements().size());
        },
        () -> fail("No matching subscription"));
  }

  @Test
  void testFiltersOutSubsBeforeRange() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder().beginning(END.plusDays(1)).ending(END.plusDays(5)).build());
    var result = subscriptionRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFiltersOutSubsAfterRange() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .beginning(START.minusDays(5))
                .ending(START.minusDays(1))
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFiltersOutSubStartsAfterRange() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .beginning(START.minusDays(5))
                .ending(START.minusDays(1))
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFindsSubStartingBeforeRangeAndEndingDuringRange() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .beginning(START.plusDays(5))
                .ending(END.plusDays(5))
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, contains(subscription));
  }

  @Test
  void testFindsSubStartingBeforeRangeAndEndingAfterRange() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .beginning(START.plusDays(5))
                .ending(END.minusDays(5))
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, contains(subscription));
  }

  @Test
  void testFindsSubStartingDuringRangeAndEndingDuringRange() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .beginning(START.minusDays(5))
                .ending(END.plusDays(5))
                .build());
    var result = subscriptionRepository.findAll(specification);

    assertThat(result, contains(subscription));
  }

  @Test
  void testFindsSubStartingDuringRangeAndEndingAfterRange() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .beginning(START.minusDays(5))
                .ending(END.minusDays(5))
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, contains(subscription));
  }

  @Test
  void testFiltersBySla() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder().serviceLevel(ServiceLevel.PREMIUM).build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, contains(subscription));

    specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder().serviceLevel(ServiceLevel.SELF_SUPPORT).build());
    result = subscriptionRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFiltersByUsage() {
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder().usage(Usage.PRODUCTION).build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, contains(subscription));

    specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder().usage(Usage.DEVELOPMENT_TEST).build());
    result = subscriptionRepository.findAll(specification);
    assertTrue(result.isEmpty());
  }

  @Test
  void testMetricsCriteriaForPhysical() {
    var hypervisorSub = createTestSubscription("hyp");
    hypervisorSub.getSubscriptionMeasurements().clear();
    var hypervisorCores = createMeasurementKey("HYPERVISOR", MetricId.fromString(CORES));
    hypervisorSub.getSubscriptionMeasurements().put(hypervisorCores, 3.0);
    subscriptionRepository.saveAndFlush(hypervisorSub);
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .hypervisorReportCategory(HypervisorReportCategory.NON_HYPERVISOR)
                .metricId(CORES)
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, not(contains(hypervisorSub)));
    assertThat(result, contains(subscription));
  }

  @Test
  void testMetricsCriteriaForHypervisor() {
    var hypervisorSub = createTestSubscription("hyp");
    hypervisorSub.getSubscriptionMeasurements().clear();
    var hypervisorCores = createMeasurementKey("HYPERVISOR", MetricId.fromString(CORES));
    hypervisorSub.getSubscriptionMeasurements().put(hypervisorCores, 3.0);
    subscriptionRepository.saveAndFlush(hypervisorSub);
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .hypervisorReportCategory(HypervisorReportCategory.HYPERVISOR)
                .metricId(CORES)
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, contains(hypervisorSub));
    assertThat(result, not(contains(subscription)));
  }

  @Test
  void testMetricsCriteriaFiltersByCategory() {
    var hypervisorSub = createTestSubscription("hyp");
    hypervisorSub.getSubscriptionMeasurements().clear();
    var hypervisorCores = createMeasurementKey("HYPERVISOR", MetricId.fromString(CORES));
    hypervisorSub.getSubscriptionMeasurements().put(hypervisorCores, 3.0);
    subscriptionRepository.saveAndFlush(hypervisorSub);
    var specification =
        SubscriptionRepository.buildSearchSpecification(
            DbReportCriteria.builder()
                .hypervisorReportCategory(HypervisorReportCategory.NON_HYPERVISOR)
                .metricId(CORES)
                .build());
    var result = subscriptionRepository.findAll(specification);
    assertThat(result, contains(subscription));
    assertThat(result, not(contains(hypervisorSub)));
  }

  SubscriptionMeasurementKey createMeasurementKey(String type, MetricId metric) {
    var key = new SubscriptionMeasurementKey();
    key.setMeasurementType(type);
    key.setMetricId(metric.toString());
    return key;
  }
}
