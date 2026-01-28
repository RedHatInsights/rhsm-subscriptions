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
package tests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.ReportCategory;
import com.redhat.swatch.contract.test.model.ServiceLevelType;
import com.redhat.swatch.contract.test.model.UsageType;
import domain.BillingProvider;
import domain.Offering;
import domain.Product;
import domain.ServiceLevel;
import domain.Subscription;
import domain.Usage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

/**
 * Component tests for capacity report filtering functionality.
 *
 * These tests verify that capacity reports can be filtered by various criteria: * Service Level
 * (SLA) * Usage * Billing Account ID * Report Category (Physical/Hypervisor) * Combined filters
 */
public class CapacityReportFilteringComponentTest extends BaseContractComponentTest {

  private static final double PREMIUM_CORES_CAPACITY = 8.0;
  private static final double STANDARD_CORES_CAPACITY = 4.0;
  private static final double PRODUCTION_CORES_CAPACITY = 6.0;
  private static final double DEV_TEST_CORES_CAPACITY = 3.0;
  private static final double PHYSICAL_SOCKETS_CAPACITY = 4.0;
  private static final double HYPERVISOR_SOCKETS_CAPACITY = 2.0;

  @TestPlanName("capacity-report-filtering-TC001")
  @Test
  void shouldFilterCapacityByServiceLevel() {
    // Given: Create subscriptions with different SLAs (Premium, Standard)
    final String premiumSku = RandomUtils.generateRandom();
    final String standardSku = RandomUtils.generateRandom();

    givenSubscriptionWithServiceLevel(premiumSku, PREMIUM_CORES_CAPACITY, ServiceLevel.PREMIUM);
    givenSubscriptionWithServiceLevel(standardSku, STANDARD_CORES_CAPACITY, ServiceLevel.STANDARD);

    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);

    // When: Get capacity with sla=Premium
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricIdWithFilters(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            null,
            ServiceLevelType.PREMIUM,
            null,
            null);

    // Then: Only Premium subscription capacity is included
    double actualCapacity = getMaxCapacityFromReport(capacityReport);
    assertEquals(
        PREMIUM_CORES_CAPACITY,
        actualCapacity,
        "Only Premium subscription capacity should be included");
  }

  @TestPlanName("capacity-report-filtering-TC002")
  @Test
  void shouldFilterCapacityByUsage() {
    // Given: Create subscriptions with Production and Development/Test usage
    final String productionSku = RandomUtils.generateRandom();
    final String devTestSku = RandomUtils.generateRandom();

    givenSubscriptionWithUsage(productionSku, PRODUCTION_CORES_CAPACITY, Usage.PRODUCTION);
    givenSubscriptionWithUsage(devTestSku, DEV_TEST_CORES_CAPACITY, Usage.DEVELOPMENT_TEST);

    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);

    // When: Get capacity with usage=Production
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricIdWithFilters(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            null,
            null,
            UsageType.PRODUCTION,
            null);

    // Then: Only Production subscription capacity is included
    double actualCapacity = getMaxCapacityFromReport(capacityReport);
    assertEquals(
        PRODUCTION_CORES_CAPACITY,
        actualCapacity,
        "Only Production subscription capacity should be included");
  }

  @TestPlanName("capacity-report-filtering-TC003")
  @Test
  void shouldFilterCapacityByBillingAccount() {
    // Given: Create subscriptions for different billing accounts
    final String sku1 = RandomUtils.generateRandom();
    final String sku2 = RandomUtils.generateRandom();
    final String billingAccountId1 = "aws-account-" + RandomUtils.generateRandom();
    final String billingAccountId2 = "aws-account-" + RandomUtils.generateRandom();

    final double account1Capacity = 10.0;
    final double account2Capacity = 5.0;

    givenSubscriptionWithBillingAccount(sku1, account1Capacity, billingAccountId1);
    givenSubscriptionWithBillingAccount(sku2, account2Capacity, billingAccountId2);

    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);

    // When: Get capacity with billing_account_id filter
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricIdWithFilters(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            null,
            null,
            null,
            billingAccountId1);

    // Then: Only specified billing account capacity is returned
    double actualCapacity = getMaxCapacityFromReport(capacityReport);
    assertEquals(
        account1Capacity, actualCapacity, "Only billing account 1 capacity should be returned");
  }

  @TestPlanName("capacity-report-filtering-TC004")
  @Test
  void shouldFilterCapacityByReportCategoryPhysical() {
    // Given: Create a subscription with PHYSICAL Cores and HYPERVISOR Cores
    final String sku = RandomUtils.generateRandom();

    givenPhysicalAndHypervisorSubscription(
        sku, PHYSICAL_SOCKETS_CAPACITY, HYPERVISOR_SOCKETS_CAPACITY);

    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);

    // When: Get capacity with category=PHYSICAL (maps to NON_HYPERVISOR in API)
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricIdWithFilters(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            ReportCategory.PHYSICAL,
            null,
            null,
            null);

    // Then: Only PHYSICAL measurements are included in the capacity
    double actualCapacity = getMaxCapacityFromReport(capacityReport);
    assertEquals(
        PHYSICAL_SOCKETS_CAPACITY, actualCapacity, "Only PHYSICAL measurements should be included");
  }

  @TestPlanName("capacity-report-filtering-TC005")
  @Test
  void shouldFilterCapacityByReportCategoryHypervisor() {
    // Given: Create a subscription with HYPERVISOR measurement type
    // Note: The offering sync logic doesn't support both physical and hypervisor
    // capacity in the same offering, so we create a separate hypervisor subscription
    final String hypervisorSku = RandomUtils.generateRandom();

    givenHypervisorSubscription(hypervisorSku, HYPERVISOR_SOCKETS_CAPACITY);

    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);

    // When: Get capacity with category=HYPERVISOR
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricIdWithFilters(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            ReportCategory.HYPERVISOR,
            null,
            null,
            null);

    // Then: Only HYPERVISOR measurements are included
    double actualCapacity = getMaxCapacityFromReport(capacityReport);
    assertEquals(
        HYPERVISOR_SOCKETS_CAPACITY,
        actualCapacity,
        "Only HYPERVISOR measurements should be included");
  }

  private void givenHypervisorSubscription(String sku, double hypervisorSockets) {
    // Create hypervisor offering
    Offering hypervisorOffering =
        Offering.buildRhelHypervisorOffering(sku, null, hypervisorSockets);

    wiremock.forProductAPI().stubOfferingData(hypervisorOffering);
    assertThat(
        "Sync hypervisor offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription with hypervisor sockets
    // Reconciliation will create HYPERVISOR SOCKETS from offering.hypervisorSockets
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.RHEL)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(hypervisorOffering)
            .subscriptionMeasurements(Map.of(SOCKETS, hypervisorSockets))
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().plusDays(1))
            .quantity(1)
            .build();

    assertThat(
        "Creating hypervisor subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  @TestPlanName("capacity-report-filtering-TC006")
  @Test
  void shouldFilterCapacityWithCombinedFilters() {
    // Given: Create a diverse subscription set
    final String matchingSku = RandomUtils.generateRandom();
    final String nonMatchingSku1 = RandomUtils.generateRandom();
    final String nonMatchingSku2 = RandomUtils.generateRandom();
    final String nonMatchingSku3 = RandomUtils.generateRandom();

    final double matchingCapacity = 16.0;

    // Create subscription matching ALL criteria: Premium, Production, Physical
    givenSubscriptionWithAllCriteria(
        matchingSku, matchingCapacity, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    // Create subscriptions that don't match all criteria
    // Standard SLA (doesn't match Premium filter)
    givenSubscriptionWithAllCriteria(nonMatchingSku1, 8.0, ServiceLevel.STANDARD, Usage.PRODUCTION);

    // Development usage (doesn't match Production filter)
    givenSubscriptionWithAllCriteria(
        nonMatchingSku2, 4.0, ServiceLevel.PREMIUM, Usage.DEVELOPMENT_TEST);

    // Different SLA and usage
    givenSubscriptionWithAllCriteria(
        nonMatchingSku3, 2.0, ServiceLevel.STANDARD, Usage.DEVELOPMENT_TEST);

    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);

    // When: Get capacity with multiple filters (sla=Premium, usage=Production, category=PHYSICAL)
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricIdWithFilters(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            ReportCategory.PHYSICAL,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            null);

    // Then: Only subscriptions matching ALL criteria are included
    double actualCapacity = getMaxCapacityFromReport(capacityReport);
    assertEquals(
        matchingCapacity,
        actualCapacity,
        "Only subscriptions matching ALL criteria should be included");
  }

  // ==================== Helper Methods ====================

  private void givenSubscriptionWithServiceLevel(
      String sku, double coresCapacity, ServiceLevel serviceLevel) {
    // Create offering with specified service level using builder
    Offering offering =
        Offering.builder()
            .sku(sku)
            .description("Test offering for SLA filtering")
            .metered(Offering.METERED_NO)
            .cores((int) coresCapacity)
            .serviceLevel(serviceLevel)
            .usage(Usage.PRODUCTION)
            .engProducts(List.of(Offering.PRODUCT_ID_OPENSHIFT))
            .build();

    wiremock.forProductAPI().stubOfferingData(offering);
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.OPENSHIFT)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(offering)
            .subscriptionMeasurements(Map.of(CORES, coresCapacity))
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().plusDays(1))
            .build();

    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  private void givenSubscriptionWithUsage(String sku, double coresCapacity, Usage usage) {
    // Create offering with specified usage using builder
    Offering offering =
        Offering.builder()
            .sku(sku)
            .description("Test offering for usage filtering")
            .metered(Offering.METERED_NO)
            .cores((int) coresCapacity)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(usage)
            .engProducts(List.of(Offering.PRODUCT_ID_OPENSHIFT))
            .build();

    wiremock.forProductAPI().stubOfferingData(offering);
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.OPENSHIFT)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(offering)
            .subscriptionMeasurements(Map.of(CORES, coresCapacity))
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().plusDays(1))
            .build();

    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  private void givenSubscriptionWithBillingAccount(
      String sku, double coresCapacity, String billingAccountId) {
    // Create offering
    Offering offering = Offering.buildOpenShiftOffering(sku, coresCapacity, null);

    wiremock.forProductAPI().stubOfferingData(offering);
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription with specified billing account
    // Note: billingProvider must be set to AWS so that extractBillingAccountId can find it
    // in the externalReferences map under the "awsMarketplace" key
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.OPENSHIFT)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(offering)
            .subscriptionMeasurements(Map.of(CORES, coresCapacity))
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().plusDays(1))
            .billingProvider(BillingProvider.AWS)
            .billingAccountId(billingAccountId)
            .build();

    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  private void givenPhysicalAndHypervisorSubscription(
      String sku, double physicalSockets, double hypervisorSockets) {
    // Create offering with physical sockets
    Offering offering =
        Offering.builder()
            .sku(sku)
            .description("Test offering with physical sockets capacity")
            .metered(Offering.METERED_NO)
            .sockets((int) physicalSockets)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .engProducts(List.of(Offering.PRODUCT_ID_RHEL_SERVER, Offering.PRODUCT_ID_RHEL_X86))
            .role("Red Hat Enterprise Linux Server")
            .build();

    wiremock.forProductAPI().stubOfferingData(offering);
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription with physical sockets measurement
    // The reconciliation will create PHYSICAL SOCKETS from offering.sockets
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.RHEL)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(offering)
            .subscriptionMeasurements(Map.of(SOCKETS, physicalSockets))
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().plusDays(1))
            .quantity(1)
            .build();

    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  private void givenSubscriptionWithAllCriteria(
      String sku, double socketsCapacity, ServiceLevel serviceLevel, Usage usage) {
    // Create offering with all criteria using builder
    Offering offering =
        Offering.builder()
            .sku(sku)
            .description("Test offering with all criteria")
            .metered(Offering.METERED_NO)
            .sockets((int) socketsCapacity)
            .serviceLevel(serviceLevel)
            .usage(usage)
            .engProducts(List.of(Offering.PRODUCT_ID_RHEL_SERVER, Offering.PRODUCT_ID_RHEL_X86))
            .role("Red Hat Enterprise Linux Server")
            .build();

    wiremock.forProductAPI().stubOfferingData(offering);
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription with sockets measurement
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.RHEL)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(offering)
            .subscriptionMeasurements(Map.of(SOCKETS, socketsCapacity))
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().plusDays(1))
            .quantity(1)
            .build();

    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  private double getMaxCapacityFromReport(CapacityReportByMetricId report) {
    if (report.getData() == null) {
      return 0.0;
    }
    return report.getData().stream()
        .filter(snapshot -> Boolean.TRUE.equals(snapshot.getHasData()))
        .mapToDouble(snapshot -> snapshot.getValue().doubleValue())
        .max()
        .orElse(0.0);
  }
}
