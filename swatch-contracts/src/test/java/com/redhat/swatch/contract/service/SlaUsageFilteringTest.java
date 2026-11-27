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
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.config.FeatureFlags;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.openapi.model.BillingProviderType;
import com.redhat.swatch.contract.openapi.model.ServiceLevelType;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.openapi.model.SkuCapacitySubscription;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.resource.api.v1.SubscriptionTableControllerV1;
import com.redhat.swatch.contract.resource.api.v2.SubscriptionTableControllerV2;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SlaUsageFilteringTest {

  private static final ProductId PRODUCT = ProductId.fromString("RHEL for x86");
  private static final String ORG_ID = "sla-usage-filtering";

  @Inject OfferingRepository offeringRepository;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject SubscriptionTableControllerV1 controllerV1;
  @Inject SubscriptionTableControllerV2 controller;
  @Inject SubscriptionCapacityService capacityService;
  @Inject ApplicationClock clock;
  @InjectMock FeatureFlags featureFlags;

  @Test
  @TestTransaction
  void v1QueriesResolveMissingValuesWhenEnabled() {
    persistSubscriptions();
    when(featureFlags.isResolvedSlaUsageFilteringEnabled()).thenReturn(true);

    var report =
        controllerV1.capacityReportBySkuV1(
            ORG_ID,
            PRODUCT,
            null,
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            null,
            null,
            null,
            null,
            "Sockets",
            null,
            null);

    assertEquals(
        Set.of("missing-both", "missing-sla", "missing-usage", "explicit-defaults"),
        report.getData().stream()
            .flatMap(item -> item.getSubscriptions().stream())
            .map(SkuCapacitySubscription::getNumber)
            .collect(Collectors.toSet()));
    report
        .getData()
        .forEach(item -> {
          assertEquals(ServiceLevelType.PREMIUM, item.getServiceLevel());
          assertEquals(UsageType.PRODUCTION, item.getUsage());
          assertEquals(1, item.getQuantity());
        });
  }

  @Test
  @TestTransaction
  void v1OnDemandFallbackResolvesDefaultsAndPreservesBillingFilters() {
    when(featureFlags.isResolvedSlaUsageFilteringEnabled()).thenReturn(true);
    persistOnDemandSubscription(
        "on-demand-missing", null, null, BillingProvider.AWS, "account-a", "linked");
    persistOnDemandSubscription(
        "on-demand-explicit",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "account-a",
        "linked");
    persistOnDemandSubscription(
        "on-demand-standard",
        ServiceLevel.STANDARD,
        null,
        BillingProvider.AWS,
        "account-a",
        "linked");
    persistOnDemandSubscription(
        "on-demand-development",
        null,
        Usage.DEVELOPMENT_TEST,
        BillingProvider.AWS,
        "account-a",
        "linked");
    persistOnDemandSubscription(
        "on-demand-other-provider", null, null, BillingProvider.AZURE, "account-a", "linked");
    persistOnDemandSubscription(
        "on-demand-other-account", null, null, BillingProvider.AWS, "account-b", "linked");
    persistOnDemandSubscription(
        "on-demand-unlinked", null, null, BillingProvider.AWS, "account-a", null);
    subscriptionRepository.flush();

    var report =
        controllerV1.capacityReportBySkuV1(
            ORG_ID,
            ProductId.fromString("rosa"),
            null,
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            BillingProviderType.AWS,
            "account-a",
            null,
            null,
            "Cores",
            null,
            null);

    assertEquals(
        Set.of("on-demand-missing", "on-demand-explicit"),
        report.getData().stream()
            .flatMap(item -> item.getSubscriptions().stream())
            .map(SkuCapacitySubscription::getNumber)
            .collect(Collectors.toSet()));
    report
        .getData()
        .forEach(item -> {
          assertEquals(ServiceLevelType.PREMIUM, item.getServiceLevel());
          assertEquals(UsageType.PRODUCTION, item.getUsage());
          assertEquals(BillingProviderType.AWS, item.getBillingProvider());
        });
  }

  @Test
  @TestTransaction
  void v2QueriesResolveMissingValuesWhenEnabled() {
    persistSubscriptions();
    when(featureFlags.isResolvedSlaUsageFilteringEnabled()).thenReturn(true);

    var report =
        assertReport(
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            "missing-both",
            "missing-sla",
            "missing-usage",
            "explicit-defaults");
    report
        .getData()
        .forEach(item -> {
          assertEquals(ServiceLevelType.PREMIUM, item.getServiceLevel());
          assertEquals(UsageType.PRODUCTION, item.getUsage());
          assertEquals(List.of(2.0), item.getMeasurements());
        });
    assertReport(ServiceLevel.STANDARD, Usage.PRODUCTION, "standard", "standard-missing-usage");
    assertReport(
        ServiceLevel.PREMIUM, Usage.DEVELOPMENT_TEST, "development", "development-missing-sla");
    assertReport(ServiceLevel.SELF_SUPPORT, Usage.DISASTER_RECOVERY, "self-support");
    assertReport(ServiceLevel.EMPTY, Usage._ANY, "unmapped-both");
    assertReport(ServiceLevel._ANY, Usage.EMPTY, "unmapped-both");
  }

  @Test
  @TestTransaction
  void v2QueriesKeepLegacyValuesWhenDisabled() {
    persistSubscriptions();
    when(featureFlags.isResolvedSlaUsageFilteringEnabled()).thenReturn(false);

    assertReport(ServiceLevel.PREMIUM, Usage.PRODUCTION, "explicit-defaults");
    assertReport(ServiceLevel.STANDARD, Usage.PRODUCTION, "standard");
    assertReport(ServiceLevel.PREMIUM, Usage.DEVELOPMENT_TEST, "development");

    var report = report(null, null);
    var missing =
        report.getData().stream()
            .filter(item -> item.getSku().equals("defaults-missing-both"))
            .findFirst()
            .orElseThrow();
    assertEquals(ServiceLevelType.EMPTY, missing.getServiceLevel());
    assertEquals(UsageType.EMPTY, missing.getUsage());
  }

  @Test
  @TestTransaction
  void v1ResponsesKeepEmptyForMissingValuesWhenDisabled() {
    persistSubscriptions();
    when(featureFlags.isResolvedSlaUsageFilteringEnabled()).thenReturn(false);

    var report =
        controllerV1.capacityReportBySkuV1(
            ORG_ID, PRODUCT, null, null, null, null, null, null, null, null, null, "Sockets", null,
            null);
    var missing =
        report.getData().stream()
            .filter(item -> item.getSku().equals("defaults-missing-both"))
            .findFirst()
            .orElseThrow();
    assertEquals(ServiceLevelType.EMPTY, missing.getServiceLevel());
    assertEquals(UsageType.EMPTY, missing.getUsage());
  }

  @Test
  @TestTransaction
  void utilizationQueriesResolveMissingValuesWhenEnabled() {
    persistSubscriptions();
    when(featureFlags.isResolvedSlaUsageFilteringEnabled()).thenReturn(true);

    assertCapacities(
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        "missing-both",
        "missing-sla",
        "missing-usage",
        "explicit-defaults",
        "unmapped-both",
        "wildcard");
    assertCapacities(
        ServiceLevel.STANDARD,
        Usage.PRODUCTION,
        "standard",
        "standard-missing-usage",
        "unmapped-both",
        "wildcard");
    assertCapacities(
        ServiceLevel.PREMIUM,
        Usage.DEVELOPMENT_TEST,
        "development",
        "development-missing-sla",
        "unmapped-both",
        "wildcard");
    assertCapacities(
        ServiceLevel.SELF_SUPPORT,
        Usage.DISASTER_RECOVERY,
        "self-support",
        "unmapped-both",
        "wildcard");
    assertCapacities(
        ServiceLevel._ANY,
        Usage._ANY,
        "missing-both",
        "unmapped-both",
        "missing-sla",
        "missing-usage",
        "explicit-defaults",
        "standard",
        "standard-missing-usage",
        "development",
        "development-missing-sla",
        "self-support",
        "wildcard");
  }

  @Test
  @TestTransaction
  void utilizationQueriesKeepLegacyWildcardsWhenDisabled() {
    persistSubscriptions();
    when(featureFlags.isResolvedSlaUsageFilteringEnabled()).thenReturn(false);

    assertCapacities(
        ServiceLevel.STANDARD,
        Usage.PRODUCTION,
        "missing-both",
        "unmapped-both",
        "missing-sla",
        "standard",
        "standard-missing-usage",
        "wildcard");
    assertCapacities(
        ServiceLevel.PREMIUM,
        Usage.DEVELOPMENT_TEST,
        "missing-both",
        "unmapped-both",
        "missing-usage",
        "development",
        "development-missing-sla",
        "wildcard");
  }

  private void persistSubscriptions() {
    persistSubscription("missing-both", null, null);
    persistSubscription("unmapped-both", ServiceLevel.EMPTY, Usage.EMPTY);
    persistSubscription("missing-sla", null, Usage.PRODUCTION);
    persistSubscription("missing-usage", ServiceLevel.PREMIUM, null);
    persistSubscription("explicit-defaults", ServiceLevel.PREMIUM, Usage.PRODUCTION);
    persistSubscription("standard", ServiceLevel.STANDARD, Usage.PRODUCTION);
    persistSubscription("standard-missing-usage", ServiceLevel.STANDARD, null);
    persistSubscription("development", ServiceLevel.PREMIUM, Usage.DEVELOPMENT_TEST);
    persistSubscription("development-missing-sla", null, Usage.DEVELOPMENT_TEST);
    persistSubscription("self-support", ServiceLevel.SELF_SUPPORT, Usage.DISASTER_RECOVERY);
    persistSubscription("wildcard", ServiceLevel._ANY, Usage._ANY);
    subscriptionRepository.flush();
  }

  private SubscriptionEntity persistSubscription(
      String number, ServiceLevel serviceLevel, Usage usage) {
    var offering =
        OfferingEntity.builder()
            .sku("defaults-" + number)
            .description(number)
            .productTags(Set.of(PRODUCT.getValue()))
            .serviceLevel(serviceLevel)
            .usage(usage)
            .hasUnlimitedUsage(false)
            .build();
    offeringRepository.persist(offering);
    var now = clock.now().truncatedTo(ChronoUnit.SECONDS);
    var subscription =
        SubscriptionEntity.builder()
            .subscriptionNumber(number)
            .offering(offering)
            .orgId(ORG_ID)
            .quantity(1)
            .startDate(now.minusDays(1))
            .endDate(now.plusDays(30))
            .billingProvider(BillingProvider.RED_HAT)
            .build();
    subscription.addSubscriptionMeasurement("Sockets", "PHYSICAL", 2.0);
    subscriptionRepository.persist(subscription);
    return subscription;
  }

  private void persistOnDemandSubscription(
      String number,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      String billingProviderId) {
    var subscription = persistSubscription(number, serviceLevel, usage);
    subscription.getOffering().setProductTags(Set.of("rosa"));
    subscription.getSubscriptionMeasurements().clear();
    subscription.setBillingProvider(billingProvider);
    subscription.setBillingAccountId(billingAccountId);
    subscription.setBillingProviderId(billingProviderId);
  }

  private SkuCapacityReportV2 assertReport(
      ServiceLevel serviceLevel, Usage usage, String... expectedNumbers) {
    var report = report(serviceLevel, usage);
    var actualNumbers =
        report.getData().stream()
            .flatMap(item -> item.getSubscriptions().stream())
            .map(SkuCapacitySubscription::getNumber)
            .collect(Collectors.toSet());
    assertEquals(Set.of(expectedNumbers), actualNumbers);
    return report;
  }

  private SkuCapacityReportV2 report(ServiceLevel serviceLevel, Usage usage) {
    return controller.capacityReportBySkuV2(
        ORG_ID,
        PRODUCT,
        null,
        null,
        null,
        serviceLevel == null ? null : ServiceLevelType.fromValue(serviceLevel.getValue()),
        usage == null ? null : UsageType.fromValue(usage.getValue()),
        null,
        null,
        null,
        null,
        null);
  }

  private void assertCapacities(ServiceLevel serviceLevel, Usage usage, String... expectedNumbers) {
    var snapshot = new TallySnapshot();
    snapshot.setProductId(PRODUCT.getValue());
    snapshot.setSla(TallySnapshot.Sla.fromValue(serviceLevel.getValue()));
    snapshot.setUsage(TallySnapshot.Usage.fromValue(usage.getValue()));
    var summary = new TallySummary();
    summary.setOrgId(ORG_ID);
    summary.setTallySnapshots(List.of(snapshot));

    var capacities = capacityService.getCapacityForTallySummary(summary);
    var actualNumbers =
        capacities.getOrDefault(snapshot, List.of()).stream()
            .map(SubscriptionCapacityView::getSubscriptionNumber)
            .collect(Collectors.toSet());
    assertEquals(Set.of(expectedNumbers), actualNumbers);
  }
}
