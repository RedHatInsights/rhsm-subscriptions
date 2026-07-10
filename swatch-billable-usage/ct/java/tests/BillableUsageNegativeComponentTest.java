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
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;

import com.redhat.swatch.component.tests.api.TestPlanName;
import org.junit.jupiter.api.Test;

public class BillableUsageNegativeComponentTest extends BaseBillableUsageComponentTest {

  @Test
  @TestPlanName("billable-usage-negative-TC001")
  public void shouldSurviveNullTallyMessage() {
    // Given: Contract with zero coverage so all probe usage becomes billable
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    // When: Null message published; valid probe follows to confirm consumer is still alive
    kafkaBridge.produceKafkaMessage(TALLY, null);
    kafkaBridge.produceKafkaMessage(
        TALLY, createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), 8.0));

    // Then: Only probe produces billable usage; service remains healthy
    assertInvalidTallyIsDropped(orgId);
  }

  @Test
  @TestPlanName("billable-usage-negative-TC002")
  public void shouldSurviveMalformedTallyDeserialization() {
    // Given: Contract with zero coverage so all probe usage becomes billable
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    // When: Malformed message published; valid probe follows to confirm consumer is still alive
    kafkaBridge.produceKafkaMessage(TALLY, "not-a-valid-tally-summary");
    kafkaBridge.produceKafkaMessage(
        TALLY, createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), 8.0));

    // Then: Only probe produces billable usage; service remains healthy
    assertInvalidTallyIsDropped(orgId);
  }
}
