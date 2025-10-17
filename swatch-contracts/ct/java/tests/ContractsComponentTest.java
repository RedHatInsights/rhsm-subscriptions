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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.response.Response;
import java.time.OffsetDateTime;
import model.ContractTestData;
import model.Offering;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ContractsComponentTest extends BaseContractComponentTest {

  /** Verify prepaid contract is created when all required data is valid. */
  @Test
  @Tag("contract")
  void shouldCreatePrepaidRosaContract_whenAllDataIsValid() {
    String orgId = "org123";
    String subscriptionId = "sub14968327";
    String subscriptionNumber = "14968327";
    String awsCustomerId = "HCwCpt6sqkC";
    String awsAccountId = "168056954830";
    String productCode = "1n58d3s3qpvk22dgew2gal7w3";
    String metricValue = "10";
    String sellerAccountId = "123456789";
    String sourcePartner = "aws_marketplace";
    String billingProvider = "aws";
    String productTag = "rosa";
    String testSku = "TESTMW02393";
    String testMetricName = "four_vcpu_hour";

    Offering offeringData = buildRosaOffering(testSku);
    ContractTestData contractData =
        buildRosaContract(
            testSku,
            testMetricName,
            orgId,
            subscriptionId,
            subscriptionNumber,
            awsCustomerId,
            awsAccountId,
            productCode,
            metricValue,
            sellerAccountId,
            sourcePartner);

    wiremock.forProductAPI().stubOfferingData(offeringData);
    wiremock.forPartnerAPI().stubPartnerEntitlement(contractData);

    // Sync offering needed for contract to persist with the SKU
    Response syncOfferingResponse = service.syncOffering(testSku);
    assertThat("Sync offering call should succeed", syncOfferingResponse.statusCode(), is(200));

    Response createContractResponse = service.createContract(contractData);
    assertThat(
        "Prepaid contract creation should succeed", createContractResponse.statusCode(), is(200));
    service.logs().assertContains("Creating contract");

    // Retrieve and verify contract
    Response getContractsResponse =
        service.getContracts(orgId, billingProvider, awsAccountId, productTag);
    assertThat(
        "Contract retrieval call should succeed", getContractsResponse.statusCode(), is(200));

    getContractsResponse
        .then()
        .body("size()", equalTo(1))
        .body("[0].org_id", equalTo(orgId))
        .body("[0].subscription_number", equalTo(subscriptionNumber))
        .body("[0].billing_account_id", equalTo(awsAccountId))
        .body("[0].sku", equalTo(testSku))
        .body("[0].metrics", notNullValue())
        .body("[0].metrics.size()", greaterThan(0));
  }

  /** Verify pure pay-as-you-go ROSA contract is created when all dimensions are incorrect. */
  @Test
  @Tag("contract")
  void shouldCreatePurePaygRosaContract_whenAllDimensionsAreIncorrect() {
    String orgId = "org234";
    String subscriptionId = "sub14968327";
    String subscriptionNumber = "24968327";
    String awsCustomerId = "CCwCpt6sqkC";
    String awsAccountId = "268056954830";
    String productCode = "2n58d3s3qpvk22dgew2gal7w3";
    String metricValue = "10";
    String sellerAccountId = "223456789";
    String sourcePartner = "aws_marketplace";
    String billingProvider = "aws";
    String productTag = "rosa";
    String testSku = "TEST1MW02393";
    String testMetricName = "premium_support"; // Invalid metric for ROSA

    Offering offeringData = buildRosaOffering(testSku);
    ContractTestData contractData =
        buildRosaContract(
            testSku,
            testMetricName,
            orgId,
            subscriptionId,
            subscriptionNumber,
            awsCustomerId,
            awsAccountId,
            productCode,
            metricValue,
            sellerAccountId,
            sourcePartner);

    wiremock.forProductAPI().stubOfferingData(offeringData);
    wiremock.forPartnerAPI().stubPartnerEntitlement(contractData);

    // Sync offering needed for contract to persist with the SKU
    Response syncOfferingResponse = service.syncOffering(testSku);
    assertThat("Sync offering call should succeed", syncOfferingResponse.statusCode(), is(200));

    Response createContractResponse = service.createContract(contractData);
    assertThat(
        "Prepaid contract creation should succeed", createContractResponse.statusCode(), is(200));
    service.logs().assertContains("Creating contract");

    // Retrieve and verify contract
    Response getContractsResponse =
        service.getContracts(orgId, billingProvider, awsAccountId, productTag);
    assertThat(
        "Contract retrieval call should succeed", getContractsResponse.statusCode(), is(200));

    getContractsResponse
        .then()
        .body("size()", equalTo(1))
        .body("[0].org_id", equalTo(orgId))
        .body("[0].subscription_number", equalTo(subscriptionNumber))
        .body("[0].billing_account_id", equalTo(awsAccountId))
        .body("[0].sku", equalTo(testSku))
        .body("[0].metrics.size()", equalTo(0));
  }

  private Offering buildRosaOffering(String sku) {
    return Offering.builder()
        .sku(sku)
        .description("Test component for ROSA")
        .level1("OpenShift")
        .level2("ROSA - RH OpenShift on AWS")
        .metered("Y")
        .build();
  }

  private ContractTestData buildRosaContract(
      String sku,
      String metricName,
      String orgId,
      String subscriptionId,
      String subscriptionNumber,
      String awsCustomerId,
      String awsAccountId,
      String productCode,
      String metricValue,
      String sellerAccountId,
      String sourcePartner) {
    return ContractTestData.builder()
        .orgId(orgId)
        .subscriptionId(subscriptionId)
        .subscriptionNumber(subscriptionNumber)
        .awsCustomerId(awsCustomerId)
        .awsAccountId(awsAccountId)
        .productCode(productCode)
        .sku(sku)
        .metricName(metricName)
        .metricValue(metricValue)
        .sellerAccountId(sellerAccountId)
        .startDate(OffsetDateTime.now().minusDays(1))
        .endDate(OffsetDateTime.now().plusDays(1))
        .sourcePartner(sourcePartner)
        .build();
  }
}
