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
import domain.Product;
import java.util.Map;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class SimpleAwsComponentTest extends BaseAwsComponentTest {

  private static final double TOTAL_VALUE = 2.0;

  /** Verify billable usage is sent to AWS Marketplace with Succeeded status. */
  @Test
  public void testValidAwsUsageMessages() {
    String metricId = Product.ROSA.getFirstMetric().getId();

    wiremock.setupAwsUsageContext(awsAccountId, rhSubscriptionId, customerId, productCode);

    BillableUsageAggregate aggregateData =
        createUsageAggregate(Product.ROSA.getName(), awsAccountId, metricId, TOTAL_VALUE, orgId);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregateData);

    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(awsAccountId, BillableUsage.Status.SUCCEEDED),
        1);
  }

  /** Verify null usage message is handled gracefully (no crash, no AWS call). */
  @Test
  @Tag("unhappy")
  public void testNullUsageMessage() {
    String metricId = Product.ROSA.getFirstMetric().getId();

    wiremock.setupAwsUsageContext(awsAccountId, rhSubscriptionId, customerId, productCode);

    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, Map.of());

    kafkaBridge.waitForKafkaMessage(BILLABLE_USAGE_STATUS, MessageValidators.alwaysMatch(), 0);

    wiremock.verifyNoAwsUsage();

    BillableUsageAggregate aggregateData =
        createUsageAggregate(Product.ROSA.getName(), awsAccountId, metricId, TOTAL_VALUE, orgId);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregateData);

    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(awsAccountId, BillableUsage.Status.SUCCEEDED),
        1);
  }

  /**
   * Verify subscription not found scenario results in FAILED status with appropriate error code.
   */
  @Test
  @Tag("unhappy")
  public void testSubscriptionNotFound() {
    String metricId = Product.ROSA.getFirstMetric().getId();

    wiremock.setupAwsUsageContextToReturnSubscriptionNotFound(awsAccountId);

    BillableUsageAggregate aggregateData =
        createUsageAggregate(Product.ROSA.getName(), awsAccountId, metricId, TOTAL_VALUE, orgId);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregateData);

    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateFailure(
            awsAccountId, BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND),
        1);

    wiremock.verifyNoAwsUsage();
  }
}
