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
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.TestPlanName;
import domain.BillingProvider;
import domain.ContractStub;
import domain.RemittanceErrorCode;
import domain.RemittanceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.Test;

public class BillableUsageStatusComponentTest extends BaseBillableUsageComponentTest {

  private static final double VALUE = 8.0;
  private static final double BILLING_FACTOR = ROSA.getBillingFactor(CORES);
  private static final String LICENSE_A = "arn:aws:license-manager:1:license:a";
  private static final String LICENSE_B = "arn:aws:license-manager:1:license:b";

  @Test
  @TestPlanName("billable-usage-status-TC001")
  void shouldAlignRemittanceLicensesWhenStatusHasLicenseId() {
    // Given: two pending remittances for the same billing account (same hourly
    // aggregate key), stamped with different licenses as contracts change
    BillableUsage usageA = givenPendingRemittanceWithLicense(LICENSE_A, VALUE);
    BillableUsage usageB = givenPendingRemittanceWithLicense(LICENSE_B, VALUE * 2);

    // When: status arrives with the final metering license
    whenStatusUpdateIsSent(
        BillableUsage.Status.SUCCEEDED,
        null,
        OffsetDateTime.now(ZoneOffset.UTC),
        List.of(usageA.getUuid().toString(), usageB.getUuid().toString()),
        LICENSE_B);

    // Then: both remittances succeed and share that license
    thenRemittanceStatusIs(usageA.getTallyId().toString(), RemittanceStatus.SUCCEEDED);
    thenRemittanceStatusIs(usageB.getTallyId().toString(), RemittanceStatus.SUCCEEDED);
    assertEquals(LICENSE_B, remittanceLicenseForTally(usageA.getTallyId().toString()));
    assertEquals(LICENSE_B, remittanceLicenseForTally(usageB.getTallyId().toString()));
  }

  @Test
  @TestPlanName("billable-usage-status-TC002")
  void shouldClearRemittanceLicenseWhenStatusLicenseIsNull() {
    // Given: a pending remittance stamped with a license
    BillableUsage usage = givenPendingRemittanceWithLicense(LICENSE_A, VALUE);

    // When: status arrives without a licenseId
    whenStatusUpdateIsSent(
        BillableUsage.Status.SUCCEEDED,
        null,
        OffsetDateTime.now(ZoneOffset.UTC),
        List.of(usage.getUuid().toString()),
        null);

    // Then: status updates and remittance license mirrors the status payload
    thenRemittanceStatusIs(usage.getTallyId().toString(), RemittanceStatus.SUCCEEDED);
    assertNull(remittanceLicenseForTally(usage.getTallyId().toString()));
  }

  @Test
  @TestPlanName("billable-usage-status-TC003")
  void shouldUpdateRemittanceWithSucceededStatus() {
    // Given: a pending remittance exists
    BillableUsage billableUsage = givenPendingRemittanceExists();
    String tallyId = billableUsage.getTallyId().toString();
    OffsetDateTime expectedBilledOnTime = OffsetDateTime.now(ZoneOffset.UTC);

    // When: status update with SUCCEEDED is published
    whenStatusUpdateIsSent(
        BillableUsage.Status.SUCCEEDED,
        null,
        expectedBilledOnTime,
        List.of(billableUsage.getUuid().toString()),
        null);

    // Then: remittance is SUCCEEDED with billedOn populated
    thenRemittanceStatusIs(tallyId, RemittanceStatus.SUCCEEDED);
    List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
    assertNotNull(remittances, "Remittances should exist");
    assertFalse(remittances.isEmpty(), "Should have at least one remittance");
    thenBilledOnIsNear(remittances.getFirst(), expectedBilledOnTime);
  }

  @Test
  @TestPlanName("billable-usage-status-TC004")
  void shouldUpdateRemittanceWithFailedStatus() {
    // Given: a pending remittance exists
    BillableUsage billableUsage = givenPendingRemittanceExists();
    String tallyId = billableUsage.getTallyId().toString();

    // When: status update with FAILED is published
    whenStatusUpdateIsSent(
        BillableUsage.Status.FAILED,
        BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND,
        null,
        List.of(billableUsage.getUuid().toString()),
        null);

    // Then: remittance is FAILED with the expected error code
    thenRemittanceStatusIs(tallyId, RemittanceStatus.FAILED);
    List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
    assertNotNull(remittances, "Remittances should exist");
    assertFalse(remittances.isEmpty(), "Should have at least one remittance");
    thenErrorCodeIs(remittances.getFirst(), RemittanceErrorCode.SUBSCRIPTION_NOT_FOUND.name());
  }

  private BillableUsage givenPendingRemittanceExists() {
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
    whenRosaTallyIsSent(VALUE);

    double expectedValue = VALUE * BILLING_FACTOR;
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
            1);
    assertEquals(1, billableUsages.size(), "Expected exactly 1 billable usage message");
    BillableUsage usage = billableUsages.getFirst();
    waitForRemittanceStatus(usage.getTallyId().toString(), RemittanceStatus.PENDING);
    return usage;
  }

  private BillableUsage givenPendingRemittanceWithLicense(String licenseId, double tallyValue) {
    OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1);
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    contractsWiremock.setupContracts(
        orgId, ROSA.getName(), List.of(new ContractStub(start, end, Map.of(), licenseId)));

    TallySummary tallySummary = whenRosaTallyIsSent(tallyValue);
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();
    waitForRemittanceStatus(tallyId, RemittanceStatus.PENDING);
    assertEquals(licenseId, remittanceLicenseForTally(tallyId), "Remittance license mismatch");

    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            new DefaultMessageValidator<>(
                usage ->
                    orgId.equals(usage.getOrgId())
                        && ROSA.getName().equals(usage.getProductId())
                        && usage.getTallyId() != null
                        && tallyId.equals(usage.getTallyId().toString()),
                BillableUsage.class),
            1);
    assertEquals(1, billableUsages.size(), "Expected exactly 1 billable usage for tally");
    return billableUsages.getFirst();
  }

  private TallySummary whenRosaTallyIsSent(double tallyValue) {
    TallySummary tallySummary =
        createTallySummary(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            tallyValue,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private void whenStatusUpdateIsSent(
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      OffsetDateTime billedOn,
      List<String> remittanceUuids,
      String licenseId) {
    kafkaBridge.produceKafkaMessage(
        BILLABLE_USAGE_STATUS,
        buildBillableUsageAggregate(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            billingAccountId,
            status,
            errorCode,
            billedOn,
            remittanceUuids,
            licenseId));
  }

  private String remittanceLicenseForTally(String tallyId) {
    List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
    assertNotNull(remittances, "Remittances should exist for tally " + tallyId);
    assertFalse(remittances.isEmpty(), "Should have at least one remittance");
    return remittances.getFirst().getLicenseId();
  }

  private void thenBilledOnIsNear(TallyRemittance remittance, OffsetDateTime expectedTime) {
    assertNotNull(remittance.getBilledOn(), "billedOn field should be present");
    Duration timeDiff = Duration.between(expectedTime, remittance.getBilledOn()).abs();
    assertTrue(
        timeDiff.toSeconds() <= 1,
        String.format(
            "billedOn timestamp %s should be within 1 second of expected time %s",
            remittance.getBilledOn(), expectedTime));
  }

  private void thenErrorCodeIs(TallyRemittance remittance, String expectedErrorCode) {
    assertNotNull(remittance.getErrorCode(), "errorCode field should be present");
    assertEquals(
        expectedErrorCode,
        remittance.getErrorCode(),
        String.format(
            "Expected error code '%s' but got '%s'", expectedErrorCode, remittance.getErrorCode()));
  }
}
