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

import static api.AwsTestHelper.createUsageAggregate;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;

import api.MessageValidators;
import com.redhat.swatch.component.tests.api.TestPlanName;
import domain.Product;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.junit.jupiter.api.Test;

public class AwsUsageContextComponentTest extends BaseAwsComponentTest {

  private static final double TOTAL_VALUE = 2.0;

  @TestPlanName("producer-aws-usage-context-TC001")
  @Test
  void shouldClassifyRecentlyTerminatedSubscription() {
    givenContractsReturnsRecentlyTerminatedSubscription();

    whenBillableUsageAggregateIsProduced();

    thenRemittanceFailsWithSubscriptionTerminated();
    thenNoAwsUsageIsSent();
  }

  private void givenContractsReturnsRecentlyTerminatedSubscription() {
    wiremock.setupAwsUsageContextToReturnSubscriptionRecentlyTerminated(awsAccountId);
  }

  private void whenBillableUsageAggregateIsProduced() {
    String metricId = Product.ROSA.getFirstMetric().getId();
    BillableUsageAggregate aggregate =
        createUsageAggregate(Product.ROSA.getName(), awsAccountId, metricId, TOTAL_VALUE, orgId);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregate);
  }

  private void thenRemittanceFailsWithSubscriptionTerminated() {
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateFailure(
            awsAccountId, BillableUsage.ErrorCode.SUBSCRIPTION_TERMINATED),
        1);
  }

  private void thenNoAwsUsageIsSent() {
    wiremock.verifyNoAwsUsage();
  }
}
