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

import static api.BillableUsageTestHelper.createTallySummaryWithDefaults;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;

import api.MessageValidators;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallySummaryConsumerComponentTest extends BaseBillableUsageComponentTest {

  private static final double TOTAL_USAGE = 8.0;
  private static final double BILLING_FACTOR = ROSA.getBillingFactor(CORES);
  private static final String AWS_DIMENSION = ROSA.getMetric(CORES).getAwsDimension();
  private static final double CONTRACT_COVERAGE = 1.0;

  @BeforeAll
  static void subscribeToBillableUsageTopic() {
    kafkaBridge.subscribeToTopic(BILLABLE_USAGE);
  }

  /** Verify tally summary is processed and billable usage is produced with no contract coverage. */
  @Test
  public void testBasicTallyToBillableUsageFlow() {
    // Setup wiremock endpoints
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    // Create and send tally summary
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), TOTAL_USAGE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Verify billable usage is produced
    // Expected: 8.0 × 0.25 = 2.0 four_vcpu_hour units
    double expectedValue = TOTAL_USAGE * BILLING_FACTOR;
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
        1);
  }

  /** Verify billable usage correctly accounts for contract coverage. */
  @Test
  public void testBillableUsageWithContractCoverage() {
    // Setup wiremock endpoints
    contractsWiremock.setupContractCoverage(
        orgId, ROSA.getName(), AWS_DIMENSION, CONTRACT_COVERAGE);

    // Create and send tally summary
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), TOTAL_USAGE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Verify billable usage accounts for contract coverage
    // Expected: (8.0 × 0.25) - 1.0 = 1.0 four_vcpu_hour units
    double expectedValue = TOTAL_USAGE * BILLING_FACTOR - CONTRACT_COVERAGE;
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
        1);
  }
}
