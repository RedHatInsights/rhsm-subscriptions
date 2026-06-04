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

import static api.AzureTestHelper.createUsageAggregate;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;

import api.MessageValidators;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import domain.Product;
import java.time.Duration;
import org.candlepin.subscriptions.billable.usage.BillableUsage.Status;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ApiMarketplaceAzureComponentTest extends BaseAzureComponentTest {

  private static final double TOTAL_USAGE = 2.0;

  @Test
  public void testAzureMarketplaceTakesTooLongToRespond() {
    // Setup
    var usage = givenUsageForProduct(Product.RHEL_FOR_X86_ELS_PAYG_ADDON);

    // Setup Azure Wiremock endpoints
    wiremock.setupAzureUsageContext(azureResourceId, billingAccountId);
    wiremock.setupAzureSendUsageTakesTooLong(azureResourceId);

    // Send billable usage message to Kafka
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, usage);

    // Verify Azure usage endpoint was called 4 times (1 initial attempt + 3 retries)
    AwaitilityUtils.untilAsserted(
        () -> {
          int actualCallCount = wiremock.countAzureUsageRequests();
          Assertions.assertEquals(
              4,
              actualCallCount,
              "Expected 4 calls (1 initial + 3 retries), but found " + actualCallCount);
        },
        AwaitilitySettings.usingTimeout(Duration.ofSeconds(40)));
    // Verify status topic shows "failed"
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(billingAccountId, Status.FAILED),
        1);
  }

  private BillableUsageAggregate givenUsageForProduct(Product product) {
    return createUsageAggregate(
        product.getName(), billingAccountId, product.getFirstMetric().getId(), TOTAL_USAGE, orgId);
  }
}
