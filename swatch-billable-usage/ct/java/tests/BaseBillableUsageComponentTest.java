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

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static domain.RemittanceStatus.COVERED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static utils.BillableUsageAggregateHelper.buildBillableUsageAggregate;
import static utils.BillableUsageAggregateHelper.mergeHourlyAggregates;

import api.BillableUsageSwatchService;
import api.BillableUsageUnleashService;
import api.ContractsWiremockService;
import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.Product;
import domain.RemittanceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import utils.TallySummaryBuilder;

@ComponentTest(name = "swatch-billable-usage")
public class BaseBillableUsageComponentTest {
  static final MetricId CORES = MetricIdUtils.getCores();
  static final MetricId INSTANCE_HOURS = MetricIdUtils.getInstanceHours();
  static final MetricId MANAGED_NODES = MetricIdUtils.getManagedNodes();
  static final MetricId VCPUS = MetricIdUtils.getVCpus();
  static final MetricId VCPUS_SELF_MANAGED = MetricIdUtils.getVCpusSelfManaged();

  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService()
          .subscribeToTopic(BILLABLE_USAGE)
          .subscribeToTopic(BILLABLE_USAGE_HOURLY_AGGREGATE);

  @Wiremock static ContractsWiremockService contractsWiremock = new ContractsWiremockService();

  @Quarkus(service = "swatch-billable-usage")
  static BillableUsageSwatchService service = new BillableUsageSwatchService();

  @Unleash
  static BillableUsageUnleashService unleash =
      new BillableUsageUnleashService().withSwatchService(service);

  protected String orgId;
  protected String billingAccountId;
  private final Set<String> orgIds = new HashSet<>();

  @BeforeEach
  void setUp() {
    orgId = givenOrgId();
    billingAccountId = RandomUtils.generateRandom();
    // Drain suppressed windows left by prior tests on shared Kafka before producing new usages.
    service.flushBillableUsageAggregationTopic();
    unleash.disableReadOnly();
  }

  @AfterEach
  void tearDown() {
    for (String orgId : orgIds) {
      service.deleteRemittancesAssociatedWithOrg(orgId);
    }
  }

  protected String givenOrgId() {
    return givenOrgId(RandomUtils.generateRandom());
  }

  protected String givenOrgId(String orgId) {
    orgIds.add(orgId);
    return orgId;
  }

  protected void givenNoContractCoverageForProduct(Product product) {
    contractsWiremock.forContractsAPI().stubNoContractCoverage(orgId, product);
  }

  protected void givenNoContractCoverageForProduct(String orgId, Product product) {
    contractsWiremock.forContractsAPI().stubNoContractCoverage(orgId, product);
  }

  protected BillableUsage givenFirstRemittanceWithStatus(
      Product product,
      Metric metric,
      BillingProvider billingProvider,
      double value,
      String billingAccountId,
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      RemittanceStatus expectedRemittanceStatus) {
    givenNoContractCoverageForProduct(product);
    var tallySummary =
        TallySummaryBuilder.buildTallySummary(
            orgId, product.getName(), metric.getId(), value, billingProvider, billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, product, value),
            1);
    assertEquals(1, billableUsages.size(), "Expected exactly 1 billable usage for first tally");
    BillableUsage billableUsage = billableUsages.getFirst();
    thenRemittanceStatusIs(billableUsage, RemittanceStatus.PENDING);

    OffsetDateTime billedOn =
        status == BillableUsage.Status.SUCCEEDED ? OffsetDateTime.now(ZoneOffset.UTC) : null;
    kafkaBridge.produceKafkaMessage(
        BILLABLE_USAGE_STATUS,
        buildBillableUsageAggregate(
            orgId,
            product.getName(),
            metric.getId(),
            billingAccountId,
            status,
            errorCode,
            billedOn,
            List.of(billableUsage.getUuid().toString()),
            null));
    thenRemittanceStatusIs(billableUsage, expectedRemittanceStatus);
    return billableUsage;
  }

  protected BillableUsageAggregate whenHourlyAggregateContainsRemittances(
      Collection<BillableUsage> usages) {

    var expectedRemittanceUuids =
        usages.stream().map(BillableUsage::getUuid).map(UUID::toString).collect(Collectors.toSet());

    // Ensure every usage is on BILLABLE_USAGE before any flush. Flush advances Kafka Streams
    // event-time; if the window closes (window + grace) before Streams has appended a usage,
    // that remittance is dropped and can never appear in a later partial aggregate.
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesUuids(expectedRemittanceUuids),
        expectedRemittanceUuids.size());

    for (BillableUsage usage : usages) {
      if (usage.getTallyId() != null) {
        thenRemittanceStatusIs(usage, RemittanceStatus.PENDING);
      }
    }

    // Flush once per outer retry: merge covers the remaining case where suppress still
    // emits a clean split rollup.
    return AwaitilityUtils.until(
        () -> {
          service.flushBillableUsageAggregationTopic();
          List<BillableUsageAggregate> partials =
              kafkaBridge.waitForKafkaMessage(
                  BILLABLE_USAGE_HOURLY_AGGREGATE,
                  MessageValidators.billableUsageAggregateForExpectedRemittances(
                      orgId, expectedRemittanceUuids),
                  1,
                  AwaitilitySettings.using(Duration.ofMillis(500), Duration.ofSeconds(12)));
          return mergeHourlyAggregates(partials, expectedRemittanceUuids);
        },
        Objects::nonNull,
        AwaitilitySettings.using(Duration.ofSeconds(2), Duration.ofSeconds(90)));
  }

  protected List<BillableUsageAggregate> whenHourlyAggregatesAreReceived(
      String orgId, int expectedCount) {
    return kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_HOURLY_AGGREGATE,
        MessageValidators.billableUsageAggregateMatchesOrg(orgId),
        expectedCount,
        AwaitilitySettings.using(Duration.ofSeconds(2), Duration.ofSeconds(90))
            .onConditionNotMet(service::flushBillableUsageAggregationTopic));
  }

  protected List<MonthlyRemittance> getAccountRemittances(
      Product product, MetricId metricId, BillingProvider billingProvider) {
    return service.getRemittances(
        orgId, product, metricId, billingProvider.toTallyApiModel().value(), billingAccountId);
  }

  protected List<MonthlyRemittance> remittedRemittances(Product product, MetricId metricId) {
    return getAccountRemittances(product, metricId, BillingProvider.AWS).stream()
        .filter(row -> !COVERED.name().equalsIgnoreCase(row.getRemittanceStatus()))
        .toList();
  }

  protected List<MonthlyRemittance> coveredRemittances(Product product, MetricId metricId) {
    return getAccountRemittances(product, metricId, BillingProvider.AWS).stream()
        .filter(row -> COVERED.name().equalsIgnoreCase(row.getRemittanceStatus()))
        .toList();
  }

  protected void thenAccountRemittedUsageRowCount(
      Product product, MetricId metricId, int expectedRowCount) {
    AwaitilityUtils.untilAsserted(() ->
        assertEquals(
            expectedRowCount,
            remittedRemittances(product, metricId).size(),
            "Unexpected monthly overage remittance row count"));
  }

  protected double thenAccountRemittedUsageEquals(
      Product product, MetricId metricId, double expectedRemittedValue) {
    AwaitilityUtils.untilAsserted(() ->
        assertEquals(
            expectedRemittedValue,
            remittedRemittances(product, metricId).stream()
                .mapToDouble(MonthlyRemittance::getRemittedValue)
                .sum(),
            0.001,
            "Account remittance mismatch for metric " + metricId.toString()));
    return expectedRemittedValue;
  }

  protected void thenAccountRemittedUsageEquals(
      Product product, MetricId metricId, double expectedRemittedValue, String expectedLicenseId) {
    AwaitilityUtils.untilAsserted(() -> {
      if (expectedRemittedValue == 0.0 && expectedLicenseId == null) {
        assertEquals(
            0.0,
            remittedRemittances(product, metricId).stream()
                .mapToDouble(MonthlyRemittance::getRemittedValue)
                .sum(),
            0.001,
            "Expected no overage remittance");
        return;
      }
      MonthlyRemittance remittance =
          remittedRemittances(product, metricId).stream()
              .filter(row -> Objects.equals(expectedLicenseId, row.getLicenseId()))
              .findFirst()
              .orElseThrow(() ->
                  new AssertionError(
                      "Expected account remittance for licenseId "
                          + expectedLicenseId
                          + " metric "
                          + metricId.toString()));
      assertNotNull(remittance.getRemittedValue(), "Remitted value should be present");
      assertEquals(
          expectedRemittedValue,
          remittance.getRemittedValue(),
          0.001,
          "Account remittance value mismatch");
      assertEquals(
          expectedLicenseId, remittance.getLicenseId(), "Account remittance licenseId mismatch");
    });
  }

  protected void thenAccountRemittedUsageSumEquals(
      Product product, MetricId metricId, double expectedRemittedTotal) {
    AwaitilityUtils.untilAsserted(() ->
        assertEquals(
            expectedRemittedTotal,
            remittedRemittances(product, metricId).stream()
                .mapToDouble(MonthlyRemittance::getRemittedValue)
                .sum(),
            0.001,
            "Account remittance total mismatch"));
  }

  protected void thenAccountCoveredUsageEquals(
      Product product, MetricId metricId, double expectedCoveredValue) {
    AwaitilityUtils.untilAsserted(() ->
        assertEquals(
            expectedCoveredValue,
            coveredRemittances(product, metricId).stream()
                .mapToDouble(MonthlyRemittance::getRemittedValue)
                .sum(),
            0.001,
            "COVERED remittance mismatch for metric " + metricId.toString()));
  }

  protected void thenAccountCoveredUsageEquals(
      Product product, MetricId metricId, double expectedCoveredValue, String expectedLicenseId) {
    AwaitilityUtils.untilAsserted(() -> {
      List<MonthlyRemittance> forLicense =
          coveredRemittances(product, metricId).stream()
              .filter(row -> Objects.equals(expectedLicenseId, row.getLicenseId()))
              .toList();
      if (forLicense.isEmpty()) {
        throw new AssertionError(
            "Expected COVERED monthly remittance for licenseId "
                + expectedLicenseId
                + " metric "
                + metricId.toString());
      }
      assertEquals(
          expectedCoveredValue,
          forLicense.stream().mapToDouble(MonthlyRemittance::getRemittedValue).sum(),
          0.001,
          "COVERED remittance value mismatch");
    });
  }

  protected void thenAccountCoveredUsageSumEquals(
      Product product, MetricId metricId, double expectedCoveredValue) {
    AwaitilityUtils.untilAsserted(() ->
        assertEquals(
            expectedCoveredValue,
            coveredRemittances(product, metricId).stream()
                .mapToDouble(MonthlyRemittance::getRemittedValue)
                .sum(),
            0.001,
            "COVERED remittance sum mismatch"));
  }

  protected void thenAccountCoveredUsageForPeriodEquals(
      Product product, MetricId metricId, double expectedCoveredValue, String accumulationPeriod) {
    AwaitilityUtils.untilAsserted(() ->
        assertEquals(
            expectedCoveredValue,
            coveredRemittances(product, metricId).stream()
                .filter(row -> accumulationPeriod.equals(row.getAccumulationPeriod()))
                .mapToDouble(MonthlyRemittance::getRemittedValue)
                .sum(),
            0.001,
            "COVERED remittance mismatch for period " + accumulationPeriod));
  }

  protected void thenAccountRemittedUsageForPeriodEquals(
      Product product, MetricId metricId, double expectedRemittedValue, String accumulationPeriod) {
    AwaitilityUtils.untilAsserted(() ->
        assertEquals(
            expectedRemittedValue,
            remittedRemittances(product, metricId).stream()
                .filter(row -> accumulationPeriod.equals(row.getAccumulationPeriod()))
                .mapToDouble(MonthlyRemittance::getRemittedValue)
                .sum(),
            0.001,
            "Account remittance mismatch for period " + accumulationPeriod));
  }

  protected void thenAccountRemittedUsageForPeriodEquals(
      Product product,
      MetricId metricId,
      double expectedRemittedValue,
      String accumulationPeriod,
      String expectedLicenseId) {
    AwaitilityUtils.untilAsserted(() -> {
      MonthlyRemittance remittance =
          remittedRemittances(product, metricId).stream()
              .filter(row -> accumulationPeriod.equals(row.getAccumulationPeriod()))
              .filter(row -> expectedLicenseId.equals(row.getLicenseId()))
              .findFirst()
              .orElseThrow(() ->
                  new AssertionError(
                      "No overage remittance for license "
                          + expectedLicenseId
                          + " period "
                          + accumulationPeriod));
      assertNotNull(remittance.getRemittedValue(), "Remitted value should be present");
      assertEquals(
          expectedRemittedValue,
          remittance.getRemittedValue(),
          0.001,
          "Remittance mismatch for period " + accumulationPeriod);
    });
  }

  protected TallyRemittance thenRemittanceStatusIs(
      BillableUsage usage, RemittanceStatus expectedStatus) {
    Objects.requireNonNull(usage, "usage must not be null");
    return thenRemittanceStatusIs(usage.getTallyId(), expectedStatus);
  }

  protected TallyRemittance thenRemittanceStatusIs(UUID tallyId, RemittanceStatus expectedStatus) {
    Objects.requireNonNull(tallyId, "tallyId must not be null");
    return AwaitilityUtils.until(
        () -> {
          List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
          if (remittances == null) {
            return null;
          }

          var matched =
              remittances.stream()
                  .filter(r -> expectedStatus.equals(RemittanceStatus.valueOf(r.getStatus())))
                  .toList();

          if (matched.size() != 1) {
            return null;
          }

          return matched.getFirst();
        },
        Objects::nonNull);
  }

  protected TallyRemittance thenRemittanceForMetricStatusIs(
      UUID tallyId, MetricId metricId, RemittanceStatus expectedStatus) {
    Objects.requireNonNull(tallyId, "tallyId must not be null");
    Objects.requireNonNull(metricId, "metricId must not be null");
    return AwaitilityUtils.until(
        () -> {
          List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
          if (remittances == null) {
            return null;
          }

          var matched =
              remittances.stream()
                  .filter(r -> expectedStatus.equals(RemittanceStatus.valueOf(r.getStatus())))
                  .filter(r -> metricId.toString().equals(r.getMetricId()))
                  .toList();

          if (matched.size() != 1) {
            return null;
          }

          return matched.getFirst();
        },
        Objects::nonNull);
  }

  protected TallyRemittance thenRemittanceValueIs(BillableUsage usage, double expectedValue) {
    Objects.requireNonNull(usage, "usage must not be null");
    Objects.requireNonNull(usage.getTallyId(), "tallyId must not be null");
    return AwaitilityUtils.until(
        () -> {
          List<TallyRemittance> remittances = service.getRemittancesByTally(usage.getTallyId());
          if (remittances == null) {
            return null;
          }

          return remittances.getFirst();
        },
        r ->
            r != null
                && r.getRemittedPendingValue() != null
                && Math.abs(expectedValue - r.getRemittedPendingValue()) <= 0.001);
  }
}
