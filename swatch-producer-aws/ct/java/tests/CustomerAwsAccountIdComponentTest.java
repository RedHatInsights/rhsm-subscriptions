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
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CustomerAwsAccountIdComponentTest extends BaseAwsComponentTest {

  private static final double TOTAL_VALUE = 2.0;
  private static final String CUSTOMER_AWS_ACCOUNT_ID = "123456789012";

  @BeforeEach
  void setUpFlagDisabled() {
    unleash.disableUseCustomerAwsAccountId();
  }

  @AfterEach
  void tearDown() {
    unleash.disableUseCustomerAwsAccountId();
  }

  @Test
  void testUsesCustomerIdentifierWhenFlagOff() {
    String metricId = Product.ROSA.getFirstMetric().getId();

    wiremock.setupAwsUsageContext(
        awsAccountId,
        awsSellerAccountId,
        rhSubscriptionId,
        customerId,
        productCode,
        CUSTOMER_AWS_ACCOUNT_ID);

    produceAggregateAndWaitForSuccess(metricId);

    wiremock.verifyBatchMeterUsageCustomerIdentifier(customerId);
  }

  @Test
  void testUsesCustomerAwsAccountIdWhenFlagOn() {
    String metricId = Product.ROSA.getFirstMetric().getId();

    unleash.enableUseCustomerAwsAccountId();
    wiremock.setupAwsUsageContext(
        awsAccountId,
        awsSellerAccountId,
        rhSubscriptionId,
        customerId,
        productCode,
        CUSTOMER_AWS_ACCOUNT_ID);

    produceAggregateAndWaitForSuccess(metricId);

    wiremock.verifyBatchMeterUsageCustomerAwsAccountId(CUSTOMER_AWS_ACCOUNT_ID);
  }

  @Test
  void testFallsBackToCustomerIdentifierWhenFlagOnAndAccountIdMissing() {
    String metricId = Product.ROSA.getFirstMetric().getId();

    unleash.enableUseCustomerAwsAccountId();
    wiremock.setupAwsUsageContext(
        awsAccountId, awsSellerAccountId, rhSubscriptionId, customerId, productCode);

    produceAggregateAndWaitForSuccess(metricId);

    wiremock.verifyBatchMeterUsageCustomerIdentifier(customerId);
  }

  private void produceAggregateAndWaitForSuccess(String metricId) {
    BillableUsageAggregate aggregateData =
        createUsageAggregate(Product.ROSA.getName(), awsAccountId, metricId, TOTAL_VALUE, orgId);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregateData);

    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(awsAccountId, BillableUsage.Status.SUCCEEDED),
        1);
  }
}
