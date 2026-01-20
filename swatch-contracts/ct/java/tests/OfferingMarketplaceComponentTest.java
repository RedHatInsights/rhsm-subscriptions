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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.Contract;
import domain.BillingProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Component tests for marketplace integration with offerings.
 *
 * <p>These tests verify that marketplace contracts correctly integrate offering capacity with
 * billing provider specific dimensions, and that the same offering can be used with different
 * marketplace billing providers.
 */
public class OfferingMarketplaceComponentTest extends BaseContractComponentTest {

  @TestPlanName("offering-marketplace-TC001")
  @Test
  void shouldCreateMarketplaceContractWithOfferingDimensions() {
    // Given: A test marketplace product with offering-based capacity for AWS
    Map<MetricId, Double> capacity = Map.of(CORES, 16.0, INSTANCE_HOURS, 100.0);
    domain.Contract awsContract =
        domain.Contract.buildRosaContract(orgId, BillingProvider.AWS, capacity);
    givenContractIsCreated(awsContract);

    // When: Retrieving the marketplace contract
    Contract actual = whenGetContractByOrgId(orgId);

    // Then: Contract has offering dimensions mapped correctly
    thenContractHasValidFields(actual);
    thenContractMatchesSku(actual, awsContract.getOffering().getSku());
    thenContractHasExpectedMetrics(actual, awsContract);
    thenBillingProviderIdMatchesAwsFormat(actual, awsContract);
  }

  @TestPlanName("offering-marketplace-TC002")
  @Test
  void shouldHandleOfferingCapacityWithDifferentBillingProviders() {
    // Given: A shared offering SKU with contracts for both AWS and Azure
    String sharedSku = RandomUtils.generateRandom();
    Map<MetricId, Double> capacity = Map.of(CORES, 16.0, INSTANCE_HOURS, 100.0);

    domain.Contract awsContract =
        domain.Contract.buildRosaContract(orgId, BillingProvider.AWS, capacity, sharedSku);
    String azureOrgId = givenOrgIdWithSuffix("_azure");
    domain.Contract azureContract =
        domain.Contract.buildRosaContract(azureOrgId, BillingProvider.AZURE, capacity, sharedSku);

    givenContractIsCreated(awsContract);
    givenContractIsCreated(azureContract);

    // When: Retrieving contracts for both billing providers
    Contract actualAws = whenGetContractByOrgId(orgId);
    Contract actualAzure = whenGetContractByOrgId(azureOrgId);

    // Then: AWS contract adapts offering capacity correctly
    thenBillingProviderIs(actualAws, "aws");
    thenBillingProviderIdMatchesAwsFormat(actualAws, awsContract);
    thenContractHasExpectedMetrics(actualAws, awsContract);

    // And: Azure contract adapts offering capacity correctly
    thenBillingProviderIs(actualAzure, "azure");
    thenBillingProviderIdMatchesAzureFormat(actualAzure, azureContract);
    thenContractHasExpectedMetrics(actualAzure, azureContract);

    // And: Both contracts share the same offering SKU
    assertEquals(actualAws.getSku(), actualAzure.getSku(), "Both should use same offering SKU");
  }

  // --- When helpers ---

  private Contract whenGetContractByOrgId(String orgId) {
    List<Contract> contracts = service.getContractsByOrgId(orgId);
    assertFalse(contracts.isEmpty(), "Should have at least one contract for org " + orgId);
    return contracts.get(0);
  }

  // --- Then helpers ---

  private void thenContractHasValidFields(Contract contract) {
    assertNotNull(contract.getUuid(), "Contract UUID should not be null");
    assertNotNull(contract.getSku(), "Contract SKU should not be null");
    assertNotNull(contract.getStartDate(), "Contract start date should not be null");
    assertNotNull(contract.getEndDate(), "Contract end date should not be null");
  }

  private void thenContractMatchesSku(Contract contract, String expectedSku) {
    assertEquals(expectedSku, contract.getSku(), "Contract SKU should match offering SKU");
  }

  private void thenBillingProviderIs(Contract contract, String expectedProvider) {
    assertEquals(expectedProvider, contract.getBillingProvider(), "Billing provider mismatch");
  }

  private void thenContractHasExpectedMetrics(Contract actual, domain.Contract expected) {
    assertNotNull(actual.getMetrics(), "Contract should have metrics");
    Map<String, Double> expectedMetrics = expected.getContractMetrics();

    for (Map.Entry<String, Double> entry : expectedMetrics.entrySet()) {
      var metric =
          actual.getMetrics().stream()
              .filter(m -> m.getMetricId().equals(entry.getKey()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Metric not found: " + entry.getKey()));

      assertEquals(
          entry.getValue(), metric.getValue().doubleValue(), 0.01, "Metric value mismatch");
    }
  }

  private void thenBillingProviderIdMatchesAwsFormat(Contract actual, domain.Contract expected) {
    String expectedId =
        expected.getProductCode()
            + ";"
            + expected.getCustomerId()
            + ";"
            + expected.getSellerAccountId();
    assertEquals(expectedId, actual.getBillingProviderId(), "AWS billing_provider_id format");
  }

  private void thenBillingProviderIdMatchesAzureFormat(Contract actual, domain.Contract expected) {
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
    assertEquals(expectedId, actual.getBillingProviderId(), "Azure billing_provider_id format");
  }
}
