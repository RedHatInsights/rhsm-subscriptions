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

import static api.PartnerApiStubs.PartnerSubscriptionsStubRequest.forContractsInOrgId;
import static java.util.Collections.disjoint;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.SkuCapacitySubscription;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class ContractsSyncComponentTest extends BaseContractComponentTest {

  // Status message constants
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_ALL_CONTRACTS_SYNCED = "All Contract are Synced";
  private static final String STATUS_NO_CONTRACTS_FOUND = "No active contract found for the orgIds";

  @TestPlanName("contracts-sync-TC001")
  @Test
  void shouldSyncContractsForSingleOrganization() {
    // Given: Upstream contracts are available for the organization
    String sku = RandomUtils.generateRandom();
    Contract awsContract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    Contract azureContract =
        Contract.buildRosaContract(orgId, BillingProvider.AZURE, Map.of(CORES, 20.0), sku);

    // Stub offering and partner API
    wiremock.forProductAPI().stubOfferingData(awsContract.getOffering());
    wiremock
        .forPartnerAPI()
        .stubPartnerSubscriptions(forContractsInOrgId(orgId, awsContract, azureContract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(awsContract, azureContract);

    // Sync offering needed for contracts to persist with the SKU
    Response syncOffering = service.syncOffering(sku);
    assertThat("Sync offering should succeed", syncOffering.statusCode(), is(HttpStatus.SC_OK));

    // When: Sync contracts for the organization
    Response syncResponse = service.syncContractsByOrg(orgId);

    // Then: Verify sync succeeded
    assertThat("Sync should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse
        .then()
        .body("status", equalTo(STATUS_SUCCESS))
        .body("message", equalTo("Contracts Synced for " + orgId));

    // Verify contracts were created/updated from upstream
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(2, contracts.size(), "Should have synced both AWS and Azure contracts");

    // Verify AWS contract data
    var awsContracts =
        contracts.stream()
            .filter(c -> c.getBillingProvider().equals(BillingProvider.AWS.toApiModel()))
            .toList();
    assertEquals(1, awsContracts.size(), "Should have one AWS contract");
    assertEquals(
        awsContract.getSubscriptionNumber(),
        awsContracts.get(0).getSubscriptionNumber(),
        "AWS contract should have correct subscription number");
    assertEquals(sku, awsContracts.get(0).getSku(), "AWS contract should have correct SKU");

    // Verify Azure contract data
    var azureContracts =
        contracts.stream()
            .filter(c -> c.getBillingProvider().equals(BillingProvider.AZURE.toApiModel()))
            .toList();
    assertEquals(1, azureContracts.size(), "Should have one Azure contract");
    assertEquals(
        azureContract.getSubscriptionNumber(),
        azureContracts.get(0).getSubscriptionNumber(),
        "AWS contract should have correct subscription number");
    assertEquals(sku, azureContracts.get(0).getSku(), "Azure contract should have correct SKU");
  }

  @TestPlanName("contracts-sync-TC002")
  @Test
  void shouldDeleteContractsAndSubscriptionsBeforeSyncing() {
    // Given: Existing contracts for the organization
    String sku = RandomUtils.generateRandom();
    Contract existingContract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);

    // Stub offering
    wiremock.forProductAPI().stubOfferingData(existingContract.getOffering());
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create initial AWS contract directly
    givenContractIsCreated(existingContract);

    // Verify initial contract and subscription exist
    var initialContracts = service.getContractsByOrgId(orgId);
    assertEquals(1, initialContracts.size(), "Should have exactly one initial contract");
    assertEquals(
        BillingProvider.AWS.toApiModel(),
        initialContracts.get(0).getBillingProvider(),
        "Initial contract should be AWS");
    assertEquals(sku, initialContracts.get(0).getSku(), "Initial contract should have correct SKU");

    // Verify AWS PAYG subscription exists and capture subscription IDs
    var initialSkuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(existingContract.getProduct(), orgId, sku);
    assertTrue(initialSkuCapacity.isPresent(), "SKU capacity should exist before sync");
    assertNotNull(
        initialSkuCapacity.get().getSubscriptions(),
        "Should have AWS PAYG subscriptions before delete");
    assertFalse(
        initialSkuCapacity.get().getSubscriptions().isEmpty(),
        "Should have at least one AWS PAYG subscription before delete");

    // Capture initial subscription IDs to verify they are deleted
    var initialSubscriptionIds =
        initialSkuCapacity.get().getSubscriptions().stream()
            .map(SkuCapacitySubscription::getId)
            .collect(toSet());

    // Setup new upstream contracts (different from existing - same SKU, different provider)
    Contract newContract =
        Contract.buildRosaContract(orgId, BillingProvider.AZURE, Map.of(CORES, 20.0), sku);
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContractsInOrgId(orgId, newContract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(newContract);

    // When: Sync with delete_contracts_and_subs flag enabled
    Response syncResponse = service.syncContractsByOrg(orgId, false, true);

    // Then: Verify sync succeeded
    assertThat("Sync should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse.then().body("status", equalTo(STATUS_SUCCESS));

    // Verify old AWS contract was automatically deleted and only new Azure contract remains
    var finalContracts = service.getContractsByOrgId(orgId);
    assertEquals(
        1, finalContracts.size(), "Should have exactly one contract after sync with delete");
    assertEquals(
        BillingProvider.AZURE.toApiModel(),
        finalContracts.get(0).getBillingProvider(),
        "New contract should be Azure");
    assertEquals(sku, finalContracts.get(0).getSku(), "New contract should have correct SKU");

    // Verify all old PAYG subscriptions were deleted (as required by test plan TC002)
    var finalSkuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(existingContract.getProduct(), orgId, sku);

    if (finalSkuCapacity.isPresent()
        && finalSkuCapacity.get().getSubscriptions() != null
        && !finalSkuCapacity.get().getSubscriptions().isEmpty()) {
      // Get IDs of any remaining subscriptions
      var remainingSubscriptionIds =
          finalSkuCapacity.get().getSubscriptions().stream()
              .map(SkuCapacitySubscription::getId)
              .collect(toSet());

      // Verify none of the old AWS subscription IDs remain
      assertTrue(
          disjoint(initialSubscriptionIds, remainingSubscriptionIds),
          "All old AWS PAYG subscriptions should be deleted");
    }
    // If no subscriptions exist at all, that's also valid (old ones were deleted)
  }

  @TestPlanName("contracts-sync-TC003")
  @Test
  void shouldSyncAllContractsAcrossAllOrganizations() {
    // Given: Multiple organizations with contracts
    String firstOrg = givenOrgIdWithSuffix("1");
    String secondOrg = givenOrgIdWithSuffix("2");

    String sku = RandomUtils.generateRandom();

    Contract firstOrgContract =
        Contract.buildRosaContract(firstOrg, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    Contract secondOrgContract =
        Contract.buildRosaContract(secondOrg, BillingProvider.AZURE, Map.of(CORES, 20.0), sku);

    // Stub offering and partner API for both orgs
    wiremock.forProductAPI().stubOfferingData(firstOrgContract.getOffering());
    wiremock
        .forPartnerAPI()
        .stubPartnerSubscriptions(forContractsInOrgId(firstOrg, firstOrgContract));
    wiremock
        .forPartnerAPI()
        .stubPartnerSubscriptions(forContractsInOrgId(secondOrg, secondOrgContract));
    wiremock
        .forSearchApi()
        .stubGetSubscriptionBySubscriptionNumber(firstOrgContract, secondOrgContract);

    // Sync offering
    Response syncOffering = service.syncOffering(sku);
    assertThat("Sync offering should succeed", syncOffering.statusCode(), is(HttpStatus.SC_OK));

    // Create initial contracts for both orgs
    givenContractIsCreated(firstOrgContract);
    givenContractIsCreated(secondOrgContract);

    // When: Sync all contracts
    Response syncResponse = service.syncAllContracts();

    // Then: Verify sync succeeded
    assertThat(
        "Sync all contracts should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse.then().body("status", equalTo(STATUS_ALL_CONTRACTS_SYNCED));

    // Verify contracts still exist for both orgs after sync
    var firstOrgContracts = service.getContractsByOrgId(firstOrg);
    assertEquals(1, firstOrgContracts.size(), "First org should have one contract after sync");
    assertEquals(
        BillingProvider.AWS.toApiModel(),
        firstOrgContracts.get(0).getBillingProvider(),
        "First org contract should be AWS");

    var secondOrgContracts = service.getContractsByOrgId(secondOrg);
    assertEquals(1, secondOrgContracts.size(), "Second org should have one contract after sync");
    assertEquals(
        BillingProvider.AZURE.toApiModel(),
        secondOrgContracts.get(0).getBillingProvider(),
        "Second org contract should be Azure");
  }

  @TestPlanName("contracts-sync-TC004")
  @Test
  void shouldReturnFailedStatusWhenNoContractsExist() {
    // When: Sync all contracts
    Response syncResponse = service.syncAllContracts();

    // Then: Verify appropriate status
    assertThat("Sync should return 200", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse.then().body("status", equalTo(STATUS_NO_CONTRACTS_FOUND));
  }

  @TestPlanName("contracts-sync-TC005")
  @Test
  void shouldSyncSubscriptionsForContractsByOrg() {
    // Given: Contracts exist for the organization without subscriptions
    String sku = RandomUtils.generateRandom();
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);

    // Stub offering and partner API
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContractsInOrgId(orgId, contract));

    // Sync offering
    Response syncOffering = service.syncOffering(sku);
    assertThat("Sync offering should succeed", syncOffering.statusCode(), is(HttpStatus.SC_OK));

    // Create contract
    givenContractIsCreated(contract);

    // When: Sync subscriptions for all contracts of the org
    Response syncResponse = service.syncSubscriptionsForContractsByOrg(orgId);

    // Then: Verify sync succeeded
    assertThat(
        "Sync subscriptions should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse.then().body("status", equalTo(STATUS_SUCCESS));

    // Verify subscriptions were actually created
    var skuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(contract.getProduct(), orgId, sku);
    assertTrue(skuCapacity.isPresent(), "SKU capacity should exist");
    assertNotNull(skuCapacity.get().getSubscriptions(), "Should have subscriptions");
    assertEquals(
        1, skuCapacity.get().getSubscriptions().size(), "Should have exactly one subscription");
  }

  @TestPlanName("contracts-sync-TC006")
  @Test
  void shouldClearAllContractsForOrganization() {
    // Given: Multiple contracts exist for the organization
    String sku = RandomUtils.generateRandom();
    Contract firstContract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    Contract secondContract =
        Contract.buildRosaContract(orgId, BillingProvider.AZURE, Map.of(CORES, 20.0), sku);

    // Create contracts
    givenContractIsCreated(firstContract);
    givenContractIsCreated(secondContract);

    // Verify contracts exist
    var initialContracts = service.getContractsByOrgId(orgId);
    assertEquals(2, initialContracts.size(), "Should have exactly two initial contracts");

    // When: Delete all contracts and subscriptions for the org
    Response deleteResponse = service.deleteDataForOrg(orgId);

    // Then: Verify deletion succeeded
    assertThat("Delete should succeed", deleteResponse.statusCode(), is(HttpStatus.SC_NO_CONTENT));

    // Verify no contracts remain for org_id
    var finalContracts = service.getContractsByOrgId(orgId);
    assertEquals(0, finalContracts.size(), "No contracts should remain");
  }
}
