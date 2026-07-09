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

import static api.BillableUsageTestHelper.createTallySummary;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.BillableUsageSwatchService;
import api.ContractsWiremockService;
import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.Product;
import domain.RemittanceStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-billable-usage")
public class BaseBillableUsageComponentTest {
  static final MetricId CORES = MetricIdUtils.getCores();
  static final MetricId VCPUS = MetricIdUtils.getVCpus();
  static final Product ROSA = Product.ROSA;
  static final Product RHEL_PAYG_ADDON = Product.RHEL_PAYG_ADDON;

  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService()
          .subscribeToTopic(BILLABLE_USAGE)
          .subscribeToTopic(BILLABLE_USAGE_HOURLY_AGGREGATE);

  @Wiremock static ContractsWiremockService contractsWiremock = new ContractsWiremockService();

  @Quarkus(service = "swatch-billable-usage")
  static BillableUsageSwatchService service = new BillableUsageSwatchService();

  protected String orgId;

  @BeforeEach
  void setUp() {
    orgId = RandomUtils.generateRandom();
  }

  /** Wait for remittance to reach expected status using API polling */
  protected void waitForRemittanceStatus(String tallyId, RemittanceStatus expectedStatus) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
              assertNotNull(remittances, "Remittances should exist for tallyId: " + tallyId);
              assertFalse(remittances.isEmpty(), "Should have at least one remittance");
              assertEquals(
                  expectedStatus,
                  RemittanceStatus.valueOf(remittances.get(0).getStatus()),
                  "Expected status "
                      + expectedStatus
                      + " but got "
                      + remittances.get(0).getStatus());
            });
  }

  protected void thenRemittanceStatusIs(String tallyId, RemittanceStatus expectedStatus) {
    waitForRemittanceStatus(tallyId, expectedStatus);
  }

  protected void thenRemittanceHasValue(String tallyId, double expectedValue) {
    List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
    assertNotNull(remittances, "Remittances should exist for tallyId: " + tallyId);
    assertEquals(1, remittances.size(), "Exactly one remittance per tally");
    assertEquals(
        expectedValue,
        remittances.get(0).getRemittedPendingValue(),
        0.001,
        "Remittance should have value " + expectedValue);
  }

  protected void givenNoContractCoverageForRhelAddon() {
    contractsWiremock.setupNoContractCoverage(orgId, RHEL_PAYG_ADDON.getName());
  }

  protected BillableUsage givenFirstRemittanceSucceeded(double value, String billingAccountId) {
    return givenFirstRemittanceWithStatus(
        value, billingAccountId, BillableUsage.Status.SUCCEEDED, null, RemittanceStatus.SUCCEEDED);
  }

  protected BillableUsage givenFirstRemittanceFailed(double value, String billingAccountId) {
    return givenFirstRemittanceWithStatus(
        value,
        billingAccountId,
        BillableUsage.Status.FAILED,
        BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND,
        RemittanceStatus.FAILED);
  }

  private BillableUsage givenFirstRemittanceWithStatus(
      double value,
      String billingAccountId,
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      RemittanceStatus expectedRemittanceStatus) {
    givenNoContractCoverageForRhelAddon();
    var tallySummary =
        createTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            value,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(
                orgId, RHEL_PAYG_ADDON.getName(), value),
            1);
    assertEquals(1, billableUsages.size(), "Expected exactly 1 billable usage for first tally");
    BillableUsage billableUsage = billableUsages.get(0);
    waitForRemittanceStatus(billableUsage.getTallyId().toString(), RemittanceStatus.PENDING);

    OffsetDateTime billedOn =
        status == BillableUsage.Status.SUCCEEDED ? OffsetDateTime.now(ZoneOffset.UTC) : null;
    kafkaBridge.produceKafkaMessage(
        BILLABLE_USAGE_STATUS,
        buildBillableUsageAggregate(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            billingAccountId,
            status,
            errorCode,
            billedOn,
            List.of(billableUsage.getUuid().toString())));
    waitForRemittanceStatus(billableUsage.getTallyId().toString(), expectedRemittanceStatus);
    return billableUsage;
  }

  private BillableUsageAggregate buildBillableUsageAggregate(
      String orgId,
      String productId,
      String metricId,
      String billingAccountId,
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      OffsetDateTime billedOn,
      List<String> remittanceUuids) {
    var aggregateKey = new BillableUsageAggregateKey();
    aggregateKey.setOrgId(orgId);
    aggregateKey.setProductId(productId);
    aggregateKey.setMetricId(metricId);
    aggregateKey.setSla("Premium");
    aggregateKey.setUsage("Production");
    aggregateKey.setBillingProvider(BillingProvider.AWS.name());
    aggregateKey.setBillingAccountId(billingAccountId);

    var aggregate = new BillableUsageAggregate();
    aggregate.setAggregateKey(aggregateKey);
    aggregate.setStatus(status);
    aggregate.setRemittanceUuids(remittanceUuids);
    aggregate.setTotalValue(BigDecimal.valueOf(5.0));
    aggregate.setWindowTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
    aggregate.setAggregateId(UUID.randomUUID());
    Set<OffsetDateTime> snapshotDateSet = new HashSet<>();
    snapshotDateSet.add(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
    aggregate.setSnapshotDates(snapshotDateSet);
    if (errorCode != null) {
      aggregate.setErrorCode(errorCode);
    }
    if (billedOn != null) {
      aggregate.setBilledOn(billedOn);
    }
    return aggregate;
  }
}
