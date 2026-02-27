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
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.ContractRequest;
import com.redhat.swatch.contract.test.model.ContractResponse;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import io.restassured.response.Response;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class ContractCreationComponentTest extends BaseContractComponentTest {

  private static final double DEFAULT_CAPACITY = 10.0;
  private static final double INSTANCE_HOURS_CAPACITY = 18.0;

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @Test
  void shouldCreatePrepaidRosaContract_whenAllDataIsValid() {
    // The metric Cores is valid for the rosa product
    Contract contractData =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
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
    Contract contractData =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(SOCKETS, DEFAULT_CAPACITY));
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
        givenContractCreatedViaMessageBroker(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);

    // Verify metrics: Should have exactly 1 metric (Cores)
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC002")
  @Test
  void shouldProcessValidPurePaygContractWithoutDimensionsForAwsMarketplace() {
    // Given: A PURE PAYG contract (Sockets metric is invalid for ROSA, so filtered out)
    Contract contract =
        givenContractCreatedViaMessageBroker(
            BillingProvider.AWS, Map.of(SOCKETS, DEFAULT_CAPACITY));

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
        givenContractCreatedViaMessageBroker(
            BillingProvider.AZURE, Map.of(CORES, DEFAULT_CAPACITY));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAzureBillingProviderId(contract, actual);

    // Verify metrics: Should have exactly 1 metric (Cores)
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC004")
  @Test
  void shouldProcessValidPurePaygContractWithoutDimensionsForAzureMarketplace() {
    // Given: A PURE PAYG contract (Sockets metric is invalid for ROSA, so filtered out)
    Contract contract =
        givenContractCreatedViaMessageBroker(
            BillingProvider.AZURE, Map.of(SOCKETS, DEFAULT_CAPACITY));

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
            BillingProvider.AWS,
            Map.of(CORES, DEFAULT_CAPACITY, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);

    // Verify metrics: Should have exactly 2 metrics (Cores and Instance-hours)
    assertEquals(
        2,
        actual.getMetrics().size(),
        "Contract should have 2 valid metrics (Cores and Instance-hours)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
    verifyMetric(actual, contract.getProduct().getMetric(INSTANCE_HOURS), INSTANCE_HOURS_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC006")
  @Test
  void shouldProcessContractWithMultipleMetricsIncludingInvalidOne() {
    // Given: A contract with multiple metrics where one is invalid (Cores is valid, Sockets is not)
    Contract contract =
        givenContractCreatedViaMessageBroker(
            BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY, SOCKETS, DEFAULT_CAPACITY));

    // Then: Verify contract was created with all expected fields
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);

    // Verify metrics: Should have only 1 valid metric (Cores), invalid metric (Sockets) filtered
    // out
    assertEquals(
        1,
        actual.getMetrics().size(),
        "Contract should have only 1 valid metric (Cores), invalid metric (Sockets) filtered out");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC007")
  @Test
  void shouldNotPersistContractWhenRequiredFieldsAreMissing() {
    // Given: A valid contract that we'll send via Artemis with a missing subscription in search API
    // This simulates a scenario where required subscription data is not found
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    // NOTE: Explicitly stub the search API to return empty array (no subscription found)
    wiremock.forSearchApi().stubSearchApiNotFound(contract.getSubscriptionNumber());

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    // When: Publish message to Kafka topic (via Artemis) without subscription data
    artemis.forContracts().sendAsText(contract);

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

  @TestPlanName("contracts-creation-TC008")
  @Test
  void shouldCreateValidPaygContractWithOneDimensionForAwsViaApi() {
    // Given: A valid AWS PAYG contract with one valid dimension (Cores)
    Contract contract =
        givenRosaContractWithMetrics(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    // When: POST contract via internal API
    whenContractIsCreatedViaApi(contract);
    // Then: Contract is created with AWS billing provider and 1 metric (Cores)
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC009")
  @Test
  void shouldCreateValidPurePaygContractWithoutDimensionsForAwsViaApi() {
    // Given: An AWS contract without dimensions
    Contract contract = givenRosaContractWithMetrics(BillingProvider.AWS, Map.of());
    // When: POST contract via internal API
    whenContractIsCreatedViaApi(contract);
    // Then: Contract is created as pure PAYG with 0 metrics (invalid dimensions filtered out)
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);
    assertEquals(0, actual.getMetrics().size(), "Pure PAYG contract should have 0 metrics");
  }

  @TestPlanName("contracts-creation-TC010")
  @Test
  void shouldCreateValidPaygContractWithOneDimensionForAzureViaApi() {
    // Given: A valid Azure PAYG contract with one valid dimension (Cores)
    Contract contract =
        givenRosaContractWithMetrics(BillingProvider.AZURE, Map.of(CORES, DEFAULT_CAPACITY));
    // When: POST contract via internal API
    whenContractIsCreatedViaApi(contract);
    // Then: Contract is created with Azure billing provider and 1 metric (Cores)
    var actual = service.getContractsByOrgId(orgId).get(0);
    verifyAzureBillingProviderId(contract, actual);
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC011")
  @Test
  void shouldCreateValidPurePaygContractWithoutDimensionsForAzureViaApi() {
    // Given: An Azure contract without dimensions
    Contract contract = givenRosaContractWithMetrics(BillingProvider.AZURE, Map.of());
    // When: POST contract via internal API
    whenContractIsCreatedViaApi(contract);
    // Then: Contract is created as pure PAYG with 0 metrics (invalid dimensions filtered out)
    var actual = service.getContractsByOrgId(orgId).get(0);
    verifyAzureBillingProviderId(contract, actual);
    assertEquals(0, actual.getMetrics().size(), "Pure PAYG contract should have 0 metrics");
  }

  @TestPlanName("contracts-creation-TC012")
  @Test
  void shouldCreateContractWithMultipleMetricsViaApi() {
    // Given: A contract with multiple valid dimensions (Cores and Instance-hours)
    Contract contract =
        givenRosaContractWithMetrics(
            BillingProvider.AWS,
            Map.of(CORES, DEFAULT_CAPACITY, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY));
    // When: POST contract via internal API
    whenContractIsCreatedViaApi(contract);
    // Then: Contract is created with both metrics stored correctly
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    assertEquals(2, actual.getMetrics().size(), "Should have 2 metrics (Cores and Instance-hours)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
    verifyMetric(actual, contract.getProduct().getMetric(INSTANCE_HOURS), INSTANCE_HOURS_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC013")
  @Test
  void shouldCreateContractWithMultipleMetricsIncludingInvalidOneViaApi() {
    // Given: A contract with one valid (Cores) and one invalid (Sockets) dimension
    Contract contract =
        givenRosaContractWithMetrics(
            BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY, SOCKETS, DEFAULT_CAPACITY));
    // When: POST contract via internal API
    whenContractIsCreatedViaApi(contract);
    // Then: Only the valid metric (Cores) is stored, invalid (Sockets) is filtered out
    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    assertEquals(
        1,
        actual.getMetrics().size(),
        "Should have only 1 valid metric (Cores), invalid (Sockets) filtered out");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("contracts-creation-TC014")
  @Test
  void shouldNotPersistContractWhenRequiredFieldsAreMissingViaApi() {
    // Given: An incomplete contract request with no partner_entitlement or subscription_id
    ContractRequest incompleteRequest = new ContractRequest();
    // When: POST incomplete contract via internal API
    Response response = service.createContract(incompleteRequest);
    // Then: HTTP 400 Bad Request and no contract persisted
    assertEquals(
        HttpStatus.SC_BAD_REQUEST,
        response.statusCode(),
        "Missing required fields should return 400 Bad Request");
    assertEquals(
        0,
        service.getContractsByOrgId(orgId).size(),
        "No contract should be persisted for the org");
  }

  @TestPlanName("contracts-creation-TC015")
  @Test
  void shouldFilterInvalidDimensionsForUnconfiguredSkuViaApi() {
    // Given: A contract with an unconfigured SKU (no product tags) and invalid dimensions
    String unconfiguredSku = "UNCONFIGURED_" + orgId;
    Offering unconfiguredOffering = Offering.buildUnconfiguredOffering(unconfiguredSku);
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY)).toBuilder()
            .offering(unconfiguredOffering)
            .build();
    wiremock.forProductAPI().stubOfferingData(unconfiguredOffering);
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    Response sync = service.syncOffering(unconfiguredSku);
    assertEquals(HttpStatus.SC_OK, sync.statusCode(), "Sync offering should succeed");
    // When: POST contract via internal API
    whenContractIsCreatedViaApi(contract);
    // Then: Contract is created but all dimensions are filtered out for unconfigured SKU
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(1, contracts.size(), "Contract should be created");
    assertEquals(
        0,
        contracts.get(0).getMetrics().size(),
        "All dimensions should be filtered out for unconfigured SKU");
  }

  @TestPlanName("contracts-creation-TC016")
  @Test
  void shouldProcessValidPaygContractWhenReceivingAnObjectMessage() {
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertEquals(HttpStatus.SC_OK, sync.statusCode(), "Sync offering should succeed");

    artemis.forContracts().sendAsSerializable(contract);

    assertTrue(service.isRunning(), "All Health Checks should be UP");

    // Wait for contract to be processed - getContracts() validates HTTP 200 internally
    AwaitilityUtils.untilAsserted(
        () -> {
          var actualContracts = service.getContracts(contract);
          assertEquals(1, actualContracts.size());
          verifyCommonContractFields(contract, actualContracts.get(0));
          assertTrue(service.isRunning(), "All Health Checks should be UP after contract creation");
        });
  }

  private Contract givenRosaContractWithMetrics(
      BillingProvider provider, Map<MetricId, Double> metrics) {
    Contract contract = buildRosaContract(orgId, provider, metrics);
    givenOfferingIsSynced(contract);
    return contract;
  }

  private void givenOfferingIsSynced(Contract contract) {
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertEquals(HttpStatus.SC_OK, sync.statusCode(), "Sync offering should succeed");
  }

  private Contract givenContractCreatedViaMessageBroker(
      BillingProvider provider, Map<MetricId, Double> metrics) {
    Contract contract = buildRosaContract(orgId, provider, metrics);
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    artemis.forContracts().sendAsText(contract);

    // Wait for contract to be processed - getContracts() validates HTTP 200 internally
    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));

    return contract;
  }

  private void whenContractIsCreatedViaApi(Contract contract) {
    Response response = service.createContract(contract);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    var contractResponse = response.then().extract().as(ContractResponse.class);
    assertEquals(SUCCESS_MESSAGE, contractResponse.getStatus().getStatus());
  }

  private void verifyCommonContractFields(
      Contract expected, com.redhat.swatch.contract.test.model.Contract actual) {
    assertNotNull(actual.getUuid());
    assertEquals(expected.getSubscriptionNumber(), actual.getSubscriptionNumber());
    assertEquals(expected.getOffering().getSku(), actual.getSku());
    assertNotNull(actual.getStartDate());
    assertNotNull(actual.getEndDate());
    assertEquals(orgId, actual.getOrgId());
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

  /** Verifies a specific metric exists in the contract with the expected value. */
  private void verifyMetric(
      com.redhat.swatch.contract.test.model.Contract contract,
      Metric metricId,
      double expectedMetricValue) {
    String dimension =
        BillingProvider.AZURE.toApiModel().equals(contract.getBillingProvider())
            ? metricId.getAzureDimension()
            : metricId.getAwsDimension();

    var metric =
        contract.getMetrics().stream()
            .filter(m -> m.getMetricId().equals(dimension))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError(metricId.getId() + " metric not found in contract"));
    // Note: metric values get converted by billing factor
    double expectedValue =
        expectedMetricValue * Optional.ofNullable(metricId.getBillingFactor()).orElse(1.0);
    assertEquals(
        (int) expectedValue,
        metric.getValue().intValue(),
        metricId.getId() + " value should be " + expectedValue);
  }
}
