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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class LicenseArnComponentTest extends BaseAwsComponentTest {

  private static final double TOTAL_VALUE = 2.0;
  private static final String LICENSE_ID =
      "arn:aws:license-manager:us-east-1:123456789012:license:swatch-test-license";
  private static final String CUSTOMER_AWS_ACCOUNT_ID = "123456789012";

  @AfterEach
  void tearDown() {
    unleash.disableUseLicense();
  }

  @TestPlanName("producer-aws-license-TC001")
  @Test
  void shouldOmitLicenseArnWhenFlagOff() {
    givenUseLicenseDisabled();
    givenContractsReturnsAwsUsageContext();

    whenBillableUsageAggregateWithLicenseIsProduced();

    thenRemittanceSucceeds();
    thenLicenseArnIsAbsent();
    thenProductCodeIsPresent();
    wiremock.verifyAwsUsageContextLicenseIdAbsent();
  }

  @TestPlanName("producer-aws-license-TC002")
  @Test
  void shouldSetLicenseArnWhenFlagOn() {
    givenUseLicenseEnabled();
    givenContractsReturnsAwsUsageContext();

    whenBillableUsageAggregateWithLicenseIsProduced();

    thenRemittanceSucceedsWithLicense();
    thenLicenseArnMatchesAggregate();
    thenProductCodeIsPresent();
  }

  @TestPlanName("producer-aws-license-TC003")
  @Test
  void shouldOmitLicenseArnWhenFlagOnButLicenseMissing() {
    givenUseLicenseEnabled();
    givenContractsReturnsAwsUsageContext();

    whenBillableUsageAggregateWithoutLicenseIsProduced();

    thenRemittanceSucceeds();
    thenLicenseArnIsAbsent();
    thenProductCodeIsPresent();
  }

  @TestPlanName("producer-aws-license-TC004")
  @Test
  void shouldSetLicenseArnWithCustomerAwsAccountId() {
    givenUseLicenseEnabled();
    givenContractsReturnsAwsUsageContextWithCustomerAwsAccountId();

    whenBillableUsageAggregateWithLicenseIsProduced();

    thenRemittanceSucceedsWithLicense();
    wiremock.verifyBatchMeterUsageCustomerAwsAccountId(CUSTOMER_AWS_ACCOUNT_ID);
    thenLicenseArnMatchesAggregate();
    thenProductCodeIsPresent();
  }

  @TestPlanName("producer-aws-license-TC005")
  @Test
  void shouldPassLicenseIdToGetAwsUsageContext() {
    givenUseLicenseEnabled();
    givenContractsReturnsAwsUsageContext();

    whenBillableUsageAggregateWithLicenseIsProduced();

    thenRemittanceSucceedsWithLicense();
    wiremock.verifyAwsUsageContextLicenseId(LICENSE_ID);
  }

  @TestPlanName("producer-aws-license-TC006")
  @Test
  void shouldEmitStatusWithLicenseIdOnSuccess() {
    givenUseLicenseEnabled();
    givenContractsReturnsAwsUsageContext();

    whenBillableUsageAggregateWithLicenseIsProduced();

    thenRemittanceSucceedsWithLicense();
  }

  @TestPlanName("producer-aws-license-TC007")
  @Test
  void shouldEmitStatusWithLicenseIdOnFailure() {
    givenUseLicenseEnabled();
    givenContractsReturnsSubscriptionNotFound();

    whenBillableUsageAggregateWithLicenseIsProduced();

    thenRemittanceFailsWithLicense();
    thenNoAwsUsageIsSent();
  }

  private void givenUseLicenseEnabled() {
    unleash.enableUseLicense();
  }

  private void givenUseLicenseDisabled() {
    unleash.disableUseLicense();
  }

  private void givenContractsReturnsAwsUsageContext() {
    wiremock.setupAwsUsageContext(
        awsAccountId, awsSellerAccountId, rhSubscriptionId, customerId, productCode);
  }

  private void givenContractsReturnsAwsUsageContextWithCustomerAwsAccountId() {
    wiremock.setupAwsUsageContext(
        awsAccountId,
        awsSellerAccountId,
        rhSubscriptionId,
        customerId,
        productCode,
        CUSTOMER_AWS_ACCOUNT_ID);
  }

  private void givenContractsReturnsSubscriptionNotFound() {
    wiremock.setupAwsUsageContextToReturnSubscriptionNotFound(awsAccountId);
  }

  private void whenBillableUsageAggregateWithLicenseIsProduced() {
    String metricId = Product.ROSA.getFirstMetric().getId();
    BillableUsageAggregate aggregate =
        createUsageAggregate(
            Product.ROSA.getName(), awsAccountId, metricId, TOTAL_VALUE, orgId, LICENSE_ID);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregate);
  }

  private void whenBillableUsageAggregateWithoutLicenseIsProduced() {
    String metricId = Product.ROSA.getFirstMetric().getId();
    BillableUsageAggregate aggregate =
        createUsageAggregate(Product.ROSA.getName(), awsAccountId, metricId, TOTAL_VALUE, orgId);
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_HOURLY_AGGREGATE, aggregate);
  }

  private void thenRemittanceSucceeds() {
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(awsAccountId, BillableUsage.Status.SUCCEEDED),
        1);
  }

  private void thenRemittanceSucceedsWithLicense() {
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(
            awsAccountId, BillableUsage.Status.SUCCEEDED, LICENSE_ID),
        1);
  }

  private void thenRemittanceFailsWithLicense() {
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(awsAccountId, BillableUsage.Status.FAILED, LICENSE_ID),
        1);
  }

  private void thenLicenseArnMatchesAggregate() {
    wiremock.verifyBatchMeterUsageLicenseArn(LICENSE_ID);
  }

  private void thenLicenseArnIsAbsent() {
    wiremock.verifyBatchMeterUsageLicenseArnAbsent();
  }

  private void thenProductCodeIsPresent() {
    wiremock.verifyBatchMeterUsageProductCode(productCode);
  }

  private void thenNoAwsUsageIsSent() {
    wiremock.verifyNoAwsUsage();
  }
}
