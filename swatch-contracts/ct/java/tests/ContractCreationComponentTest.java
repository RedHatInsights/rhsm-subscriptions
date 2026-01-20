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

import static api.PartnerApiStubs.PartnerSubscriptionsStubRequest.forContract;
import static domain.Contract.buildRosaContract;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class ContractCreationComponentTest extends BaseContractComponentTest {

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @Test
  void shouldCreatePrepaidRosaContract_whenAllDataIsValid() {
    // The metric Cores is valid for the rosa product
    Contract contractData = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    var getContractsResponse = service.getContracts(contractData);

    assertEquals(1, getContractsResponse.size());
    var actualContract = getContractsResponse.get(0);
    assertEquals(orgId, actualContract.getOrgId());
    assertEquals(contractData.getSubscriptionNumber(), actualContract.getSubscriptionNumber());
    assertEquals(contractData.getBillingAccountId(), actualContract.getBillingAccountId());
    assertEquals(contractData.getOffering().getSku(), actualContract.getSku());
    assertNotNull(actualContract.getMetrics());
    assertEquals(1, actualContract.getMetrics().size());
  }

  /** Verify pure pay-as-you-go ROSA contract is created when all dimensions are incorrect. */
  @Test
  void shouldCreatePurePaygRosaContract_whenAllDimensionsAreIncorrect() {
    // The metric Sockets is NOT valid for the rosa product, so it should be ignored
    Contract contractData = buildRosaContract(orgId, BillingProvider.AWS, Map.of(SOCKETS, 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    var getContractsResponse = service.getContracts(contractData);

    // Having metrics size as zero is what is indicating that this is pure paygo because there are
    // no valid prepaid metric amounts
    assertEquals(1, getContractsResponse.size());
    var actualContract = getContractsResponse.get(0);
    assertEquals(orgId, actualContract.getOrgId());
    assertEquals(contractData.getSubscriptionNumber(), actualContract.getSubscriptionNumber());
    assertEquals(contractData.getBillingAccountId(), actualContract.getBillingAccountId());
    assertEquals(contractData.getOffering().getSku(), actualContract.getSku());
    assertNotNull(actualContract.getMetrics());
    assertEquals(0, actualContract.getMetrics().size());
  }

  @TestPlanName("contracts-creation-TC001")
  @Test
  void shouldProcessValidPaygContractWithOneDimensionForAwsMarketplace() {
    // Given: A valid PAYG contract with one valid dimension (Cores) for AWS Marketplace
    Contract contract =
        givenContractCreatedViaMessageBroker(BillingProvider.AWS, Map.of(CORES, 10.0));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);

    // Verify metrics: Should have exactly 1 metric (Cores)
    // Note: CORES value gets converted by billing factor (10.0 * 0.25 = 2.5, rounded to 2)
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES).getAwsDimension(), 2, "Cores");
  }

  @TestPlanName("contracts-creation-TC002")
  @Test
  void shouldProcessValidPurePaygContractWithoutDimensionsForAwsMarketplace() {
    // Given: A PURE PAYG contract (Sockets metric is invalid for ROSA, so filtered out)
    Contract contract =
        givenContractCreatedViaMessageBroker(BillingProvider.AWS, Map.of(SOCKETS, 10.0));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);

    // Verify metrics: Should have 0 metrics (pure PAYG, no valid prepaid dimensions)
    assertEquals(0, actual.getMetrics().size(), "Pure PAYG contract should have 0 metrics");
  }

  @TestPlanName("contracts-creation-TC003")
  @Test
  void shouldProcessValidPaygContractWithOneDimensionForAzureMarketplace() {
    // Given: A valid PAYG contract with one valid dimension (Cores) for Azure Marketplace
    Contract contract =
        givenContractCreatedViaMessageBroker(BillingProvider.AZURE, Map.of(CORES, 10.0));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAzureBillingProviderId(contract, actual);

    // Verify metrics: Should have exactly 1 metric (Cores)
    // Note: CORES value gets converted by billing factor (10.0 * 0.25 = 2.5, rounded to 2)
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES).getAzureDimension(), 2, "Cores");
  }

  @TestPlanName("contracts-creation-TC004")
  @Test
  void shouldProcessValidPurePaygContractWithoutDimensionsForAzureMarketplace() {
    // Given: A PURE PAYG contract (Sockets metric is invalid for ROSA, so filtered out)
    Contract contract =
        givenContractCreatedViaMessageBroker(BillingProvider.AZURE, Map.of(SOCKETS, 10.0));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAzureBillingProviderId(contract, actual);

    // Verify metrics: Should have 0 metrics (pure PAYG, no valid prepaid dimensions)
    assertEquals(0, actual.getMetrics().size(), "Pure PAYG contract should have 0 metrics");
  }

  @TestPlanName("contracts-creation-TC005")
  @Test
  void shouldProcessContractWithMultipleValidMetrics() {
    // Given: A contract with multiple valid metrics/dimensions (Cores and Instance-hours)
    Contract contract =
        givenContractCreatedViaMessageBroker(
            BillingProvider.AWS, Map.of(CORES, 16.0, INSTANCE_HOURS, 100.0));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);

    // Verify metrics: Should have exactly 2 metrics (Cores and Instance-hours)
    // Note: CORES value gets converted by billing factor (16.0 * 0.25 = 4)
    // Note: INSTANCE_HOURS value is 1:1 (100.0 * 1.0 = 100)
    assertEquals(
        2,
        actual.getMetrics().size(),
        "Contract should have 2 valid metrics (Cores and Instance-hours)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES).getAwsDimension(), 4, "Cores");
    verifyMetric(
        actual,
        contract.getProduct().getMetric(INSTANCE_HOURS).getAwsDimension(),
        100,
        "Instance-hours");
  }

  @TestPlanName("contracts-creation-TC006")
  @Test
  void shouldProcessContractWithMultipleMetricsIncludingInvalidOne() {
    // Given: A contract with multiple metrics where one is invalid (Cores is valid, Sockets is not)
    Contract contract =
        givenContractCreatedViaMessageBroker(
            BillingProvider.AWS, Map.of(CORES, 16.0, SOCKETS, 4.0));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);

    // Verify metrics: Should have only 1 valid metric (Cores), invalid metric (Sockets) filtered
    // out
    // Note: CORES value gets converted by billing factor (16.0 * 0.25 = 4)
    assertEquals(
        1,
        actual.getMetrics().size(),
        "Contract should have only 1 valid metric (Cores), invalid metric (Sockets) filtered out");
    verifyMetric(actual, contract.getProduct().getMetric(CORES).getAwsDimension(), 4, "Cores");
  }

  @TestPlanName("contracts-creation-TC007")
  @Test
  void shouldNotPersistContractWhenRequiredFieldsAreMissing() {
    // Given: A valid contract that we'll send via Artemis with a missing subscription in search API
    // This simulates a scenario where required subscription data is not found
    Contract contract = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    // NOTE: Explicitly stub the search API to return empty array (no subscription found)
    wiremock.forSearchApi().stubSearchApiNotFound(contract.getSubscriptionNumber());

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    // When: Publish message to Kafka topic (via Artemis) without subscription data
    artemis.forContracts().send(contract);

    // Then: Verify the contract was NOT created due to missing required subscription data
    // Wait for message to be processed - contract should NOT be created
    // Note: Using getContractsByOrgId since we expect no contracts to match contract details
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0,
                service.getContractsByOrgId(orgId).size(),
                "Contract should not be created when required subscription data is missing"));
  }

  /**
   * Creates a contract via message broker (Artemis) and waits for it to be processed. This
   * simulates the production flow where contracts are created via messaging.
   */
  private Contract givenContractCreatedViaMessageBroker(
      BillingProvider provider, Map<MetricId, Double> metrics) {
    Contract contract = buildRosaContract(orgId, provider, metrics);
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    artemis.forContracts().send(contract);

    // Wait for contract to be processed - getContracts() validates HTTP 200 internally
    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));

    return contract;
  }

  /**
   * Verifies common contract fields that should be present in all contract responses regardless of
   * billing provider or metrics.
   */
  private void verifyCommonContractFields(
      Contract expected, com.redhat.swatch.contract.test.model.Contract actual) {
    assertNotNull(actual.getUuid());
    assertEquals(expected.getSubscriptionNumber(), actual.getSubscriptionNumber());
    assertEquals(expected.getOffering().getSku(), actual.getSku());
    assertNotNull(actual.getStartDate());
    assertNotNull(actual.getEndDate());
    assertEquals(orgId, actual.getOrgId());
    assertEquals(expected.getBillingAccountId(), actual.getBillingAccountId());
    assertNotNull(actual.getMetrics());
  }

  /**
   * Verifies AWS billing_provider_id follows the format:
   * {vendorProductCode};{awsCustomerId};{sellerAccountId}
   */
  private void verifyAwsBillingProviderId(
      Contract expected, com.redhat.swatch.contract.test.model.Contract actual) {
    assertEquals("aws", actual.getBillingProvider());
    String expectedId =
        expected.getProductCode()
            + ";"
            + expected.getCustomerId()
            + ";"
            + expected.getSellerAccountId();
    assertEquals(
        expectedId,
        actual.getBillingProviderId(),
        "billing_provider_id should follow AWS format: {vendorProductCode};{awsCustomerId};{sellerAccountId}");
  }

  /**
   * Verifies Azure billing_provider_id follows the format:
   * {azureResourceId};{planId};{vendorProductCode};{customer};{clientId}
   */
  private void verifyAzureBillingProviderId(
      Contract expected, com.redhat.swatch.contract.test.model.Contract actual) {
    assertEquals("azure", actual.getBillingProvider());
    String expectedId =
        expected.getResourceId()
            + ";"
            + expected.getPlanId()
            + ";"
            + expected.getProductCode()
            + ";"
            + expected.getCustomerId()
            + ";"
            + expected.getClientId();
    assertEquals(
        expectedId,
        actual.getBillingProviderId(),
        "billing_provider_id should follow Azure format: {azureResourceId};{planId};{vendorProductCode};{customer};{clientId}");
  }

  /**
   * Verifies a specific metric exists in the contract with the expected value.
   *
   * @param contract The contract to verify
   * @param dimension The metric dimension ID (e.g., "four_vcpu_hour", "control_plane")
   * @param expectedValue The expected metric value
   * @param metricName Human-readable metric name for error messages
   */
  private void verifyMetric(
      com.redhat.swatch.contract.test.model.Contract contract,
      String dimension,
      int expectedValue,
      String metricName) {
    var metric =
        contract.getMetrics().stream()
            .filter(m -> m.getMetricId().equals(dimension))
            .findFirst()
            .orElseThrow(() -> new AssertionError(metricName + " metric not found in contract"));
    assertEquals(
        expectedValue,
        metric.getValue().intValue(),
        metricName + " value should be " + expectedValue);
  }
}
