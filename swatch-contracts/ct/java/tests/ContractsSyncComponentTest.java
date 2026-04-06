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
import static utils.DateUtils.assertDatesAreEqual;

import api.PartnerApiStubs;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.SkuCapacitySubscription;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    // When: Delete existing data and re-sync contracts
    service.deleteDataForOrg(orgId);
    Response syncResponse = service.syncContractsByOrg(orgId);

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
  void syncAllContractsTwiceShouldNotCreateDuplicateContracts() {
    // Given: One org with a single AWS contract and stubbed upstream data
    Contract contract = givenRosaContractIsCreated(10.0);

    // Verify initial state
    var contractsBefore = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsBefore.size(), "Should have exactly one contract before sync");

    // When: Run syncAllContracts the first time
    Response firstSync = service.syncAllContracts();
    assertThat("First sync should succeed", firstSync.statusCode(), is(HttpStatus.SC_OK));
    firstSync.then().body("status", equalTo(STATUS_ALL_CONTRACTS_SYNCED));

    var contractsAfterFirst = service.getContractsByOrgId(orgId);
    assertEquals(
        1, contractsAfterFirst.size(), "Should still have exactly one contract after first sync");
    String uuidAfterFirst = contractsAfterFirst.get(0).getUuid();

    // When: Run syncAllContracts a second time (same upstream data — idempotency check)
    Response secondSync = service.syncAllContracts();
    assertThat("Second sync should succeed", secondSync.statusCode(), is(HttpStatus.SC_OK));
    secondSync.then().body("status", equalTo(STATUS_ALL_CONTRACTS_SYNCED));

    // Then: No duplicate contracts created; record count is still exactly 1
    var contractsAfterSecond = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsAfterSecond.size(), "Repeat sync must not create duplicate contracts");

    // UUID must be stable — upsert finds the existing record and leaves it unchanged
    String uuidAfterSecond = contractsAfterSecond.get(0).getUuid();
    assertEquals(
        uuidAfterFirst,
        uuidAfterSecond,
        "Contract UUID must remain stable across repeat syncAllContracts calls");
    assertEquals(
        contractsAfterFirst.get(0).getBillingProvider(),
        contractsAfterSecond.get(0).getBillingProvider(),
        "Billing provider must not change across repeat runs");
  }

  @TestPlanName("contracts-sync-TC007")
  @Test
  void syncAllContractsWithMultiProviderOrgShouldPreserveBothContracts() {
    // Given: One org with both AWS and Azure contracts
    String sku = RandomUtils.generateRandom();
    Contract awsContract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    Contract azureContract =
        Contract.buildRosaContract(orgId, BillingProvider.AZURE, Map.of(CORES, 20.0), sku);

    wiremock.forProductAPI().stubOfferingData(awsContract.getOffering());
    // Both contracts are returned by the partner API for this org
    wiremock
        .forPartnerAPI()
        .stubPartnerSubscriptions(forContractsInOrgId(orgId, awsContract, azureContract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(awsContract, azureContract);

    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create both contracts
    givenContractIsCreated(awsContract);
    givenContractIsCreated(azureContract);

    var contractsBefore = service.getContractsByOrgId(orgId);
    assertEquals(2, contractsBefore.size(), "Should have two contracts before sync");

    Set<String> uuidsBefore =
        contractsBefore.stream()
            .map(com.redhat.swatch.contract.test.model.Contract::getUuid)
            .collect(Collectors.toSet());

    // When: syncAllContracts (will iterate this org twice since it has 2 contracts in DB)
    Response syncResponse = service.syncAllContracts();
    assertThat("Sync should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse.then().body("status", equalTo(STATUS_ALL_CONTRACTS_SYNCED));

    // Then: Both contracts still exist after the full loop
    var contractsAfter = service.getContractsByOrgId(orgId);
    assertEquals(
        2,
        contractsAfter.size(),
        "Both AWS and Azure contracts must survive syncAllContracts loop iteration");

    Set<String> providersAfter =
        contractsAfter.stream()
            .map(com.redhat.swatch.contract.test.model.Contract::getBillingProvider)
            .collect(Collectors.toSet());
    assertTrue(
        providersAfter.contains(BillingProvider.AWS.toApiModel()),
        "AWS contract must still exist after syncAllContracts");
    assertTrue(
        providersAfter.contains(BillingProvider.AZURE.toApiModel()),
        "Azure contract must still exist after syncAllContracts");

    // UUIDs should be stable — same records, not new UUIDs from re-creation
    Set<String> uuidsAfter =
        contractsAfter.stream()
            .map(com.redhat.swatch.contract.test.model.Contract::getUuid)
            .collect(Collectors.toSet());
    assertEquals(
        uuidsBefore,
        uuidsAfter,
        "Contract UUIDs must remain stable after syncAllContracts for multi-provider org");
  }

  @TestPlanName("contracts-sync-TC008")
  @Test
  void syncAllContractsAfterOrgLevelSyncShouldNotInsertAdditionalRecords() {
    // Given: An org already synced via the per-org sync endpoint
    givenRosaContractIsCreated(10.0);
    Response orgSync = service.syncContractsByOrg(orgId);
    assertThat("Per-org sync should succeed", orgSync.statusCode(), is(HttpStatus.SC_OK));

    // Capture baseline across all four tables
    var contractsAfterOrgSync = service.getContractsByOrgId(orgId);
    assertEquals(
        1, contractsAfterOrgSync.size(), "Should have exactly one contract after org sync");
    String uuidAfterOrgSync = contractsAfterOrgSync.get(0).getUuid();
    int metricsCountAfterOrgSync = contractsAfterOrgSync.get(0).getMetrics().size();

    var subsAfterOrgSync = service.getSubscriptionsByOrgId(orgId);
    assertEquals(1, subsAfterOrgSync.size(), "Should have exactly one subscription after org sync");
    String subIdAfterOrgSync = subsAfterOrgSync.get(0).getSubscriptionId();
    int measurementCountAfterOrgSync = subsAfterOrgSync.get(0).getMetrics().size();

    // When: Run syncAllContracts on top of the already-synced state
    Response globalSync = service.syncAllContracts();
    assertThat("Global sync should succeed", globalSync.statusCode(), is(HttpStatus.SC_OK));
    globalSync.then().body("status", equalTo(STATUS_ALL_CONTRACTS_SYNCED));

    // Then: No additional records in any table
    var contractsAfterGlobalSync = service.getContractsByOrgId(orgId);
    assertEquals(
        1, contractsAfterGlobalSync.size(), "contracts: no duplicate rows after global sync");
    assertEquals(
        uuidAfterOrgSync,
        contractsAfterGlobalSync.get(0).getUuid(),
        "contracts: UUID must be stable after global sync");
    assertEquals(
        metricsCountAfterOrgSync,
        contractsAfterGlobalSync.get(0).getMetrics().size(),
        "contract_metrics: row count must be stable after global sync");

    var subsAfterGlobalSync = service.getSubscriptionsByOrgId(orgId);
    assertEquals(
        1, subsAfterGlobalSync.size(), "subscriptions: no duplicate rows after global sync");
    assertEquals(
        subIdAfterOrgSync,
        subsAfterGlobalSync.get(0).getSubscriptionId(),
        "subscriptions: subscription_id must be stable after global sync");
    assertEquals(
        measurementCountAfterOrgSync,
        subsAfterGlobalSync.get(0).getMetrics().size(),
        "subscription_measurements: row count must be stable after global sync");
  }

  @TestPlanName("contracts-sync-TC009")
  @Test
  void syncContractsShouldPopulateAllRelatedTablesWithCorrectData() {
    // Given: An AWS ROSA contract with a known CORES metric value
    String sku = RandomUtils.generateRandom();
    double coresCapacity = 10.0;
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, coresCapacity), sku);

    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContractsInOrgId(orgId, contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // When
    Response syncResponse = service.syncContractsByOrg(orgId);
    assertThat("Sync should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));

    // ── contracts table ──────────────────────────────────────────────────
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(1, contracts.size(), "Exactly one contract row must exist");

    var c = contracts.get(0);
    assertNotNull(c.getUuid(), "contracts.uuid must be set");
    assertEquals(orgId, c.getOrgId(), "contracts.org_id must match");
    assertEquals(sku, c.getSku(), "contracts.sku must match");
    assertEquals(
        BillingProvider.AWS.toApiModel(), c.getBillingProvider(), "contracts.billing_provider");
    assertNotNull(
        c.getBillingProviderId(),
        "contracts.billing_provider_id must be populated (pre-cleanup key)");
    assertFalse(
        c.getBillingProviderId().isBlank(), "contracts.billing_provider_id must not be blank");
    assertNotNull(c.getBillingAccountId(), "contracts.billing_account_id must be set");
    assertEquals(
        contract.getBillingAccountId(),
        c.getBillingAccountId(),
        "contracts.billing_account_id must match upstream value");
    assertNotNull(c.getStartDate(), "contracts.start_date must be set");
    assertNotNull(c.getEndDate(), "contracts.end_date must be set");
    assertEquals(
        contract.getSubscriptionNumber(),
        c.getSubscriptionNumber(),
        "contracts.subscription_number must match upstream");
    assertFalse(c.getProductTags().isEmpty(), "contracts.product_tags must not be empty");

    // ── contract_metrics table ────────────────────────────────────────────
    var metrics = c.getMetrics();
    assertFalse(metrics.isEmpty(), "contract_metrics must have at least one row");

    for (var metric : metrics) {
      assertNotNull(metric.getMetricId(), "contract_metrics.metric_id must not be null");
      assertFalse(metric.getMetricId().isBlank(), "contract_metrics.metric_id must not be blank");
      assertNotNull(metric.getValue(), "contract_metrics.value must not be null");
      assertTrue(metric.getValue() > 0, "contract_metrics.value must be positive");
    }

    // No duplicate metric_ids on the same contract
    var metricIds =
        metrics.stream()
            .map(com.redhat.swatch.contract.test.model.Metric::getMetricId)
            .collect(Collectors.toList());
    assertEquals(
        metricIds.size(),
        metricIds.stream().distinct().count(),
        "contract_metrics must not contain duplicate metric_id values for the same contract");

    // ── subscriptions table ───────────────────────────────────────────────
    var subscriptions = service.getSubscriptionsByOrgId(orgId);
    assertEquals(1, subscriptions.size(), "Exactly one subscription row must exist");

    var sub = subscriptions.get(0);
    assertNotNull(sub.getSubscriptionId(), "subscriptions.subscription_id must be set");
    assertEquals(orgId, sub.getOrgId(), "subscriptions.org_id must match");
    assertEquals(sku, sub.getSku(), "subscriptions.sku must match");
    assertEquals(
        BillingProvider.AWS.toApiModel(),
        sub.getBillingProvider().toLowerCase(),
        "subscriptions.billing_provider must match");
    assertNotNull(
        sub.getBillingProviderId(), "subscriptions.billing_provider_id must be populated");
    assertNotNull(sub.getBillingAccountId(), "subscriptions.billing_account_id must be set");
    assertNotNull(sub.getStartDate(), "subscriptions.start_date must be set");
    assertNotNull(sub.getEndDate(), "subscriptions.end_date must be set");

    // ── subscription_measurements table ───────────────────────────────────
    var measurements = sub.getMetrics();
    assertNotNull(measurements, "subscription_measurements must not be null");
    assertFalse(measurements.isEmpty(), "subscription_measurements must have at least one row");

    for (var m : measurements) {
      assertNotNull(m.getMetricId(), "subscription_measurements.metric_id must not be null");
      assertNotNull(m.getValue(), "subscription_measurements.value must not be null");
      assertTrue(m.getValue() > 0, "subscription_measurements.value must be positive");
      assertNotNull(
          m.getMeasurementType(), "subscription_measurements.measurement_type must not be null");
      assertFalse(
          m.getMeasurementType().isBlank(),
          "subscription_measurements.measurement_type must not be blank");
    }

    // ── cross-table consistency ───────────────────────────────────────────
    // contract.subscription_number == subscription.subscription_number
    assertEquals(
        c.getSubscriptionNumber(),
        sub.getSubscriptionNumber(),
        "contracts.subscription_number must equal subscriptions.subscription_number");

    // Both tables must have the same number of distinct metric entries for this
    // contract/subscription
    // (contract_metrics and subscription_measurements use different ID representations — e.g.
    // "four_vcpu_hour" vs "Cores" — so we compare counts, not names)
    assertEquals(
        metrics.size(),
        measurements.size(),
        "contract_metrics and subscription_measurements must have the same number of metric entries");
  }

  @TestPlanName("contracts-sync-TC010")
  @Test
  void shouldSyncAllPagesOfUpstreamEntitlements() {
    // Given: 21 contracts (exceeds the Partner Gateway page size of 20)
    int totalContracts = PartnerApiStubs.PAGE_SIZE + 1;
    givenRosaContractsAreStubbed(totalContracts);

    // When
    Response syncResponse = service.syncContractsByOrg(orgId);

    // Then
    assertEquals(HttpStatus.SC_OK, syncResponse.statusCode());
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(
        totalContracts,
        contracts.size(),
        "All contracts across all pages should be synced, not just the first page");
  }

  @TestPlanName("contracts-sync-TC005")
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

  @TestPlanName("contracts-sync-TC011")
  @Test
  void shouldTerminateContractsAndSubscriptionsWhenMissingFromUpstream() {
    // Given: Create a contract and subscription
    givenRosaContractIsCreated(10.0);

    // Verify contract and subscription exist before sync
    var contractsBeforeSync = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsBeforeSync.size(), "Should have one contract before sync");

    var subscriptionsBeforeSync = service.getSubscriptionsByOrgId(orgId);
    assertEquals(1, subscriptionsBeforeSync.size(), "Should have one subscription before sync");

    // Stub upstream Partner API to return empty entitlements for the org
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContractsInOrgId(orgId));

    // When: Sync contracts
    Response syncResponse = service.syncContractsByOrg(orgId);

    // Then: Verify response indicates no contracts found
    assertThat("Sync should return OK", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse
        .then()
        .body("status", equalTo("FAILED"))
        .body("message", equalTo("No contracts found in upstream for the org " + orgId));

    // Verify contract still exists in database (soft delete, not hard delete)
    var contractsAfterSync = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsAfterSync.size(), "Contract should still exist in database");

    // Verify associated subscription still exists in database (soft delete)
    var subscriptionsAfterSync = service.getSubscriptionsByOrgId(orgId);
    assertEquals(1, subscriptionsAfterSync.size(), "Subscription should still exist in database");

    var terminatedContract = contractsAfterSync.get(0);
    var terminatedSubscription = subscriptionsAfterSync.get(0);

    // Verify both contract and subscription are terminated (end_date is set)
    assertNotNull(terminatedContract.getEndDate(), "Contract should be terminated");
    assertNotNull(terminatedSubscription.getEndDate(), "Subscription should be terminated");

    // Verify both have approximately the same termination timestamp (within 1 second)
    assertDatesAreEqual(terminatedContract.getEndDate(), terminatedSubscription.getEndDate());
  }

  /**
   * TC012 - Partial entitlement disappearance terminates only missing contracts
   *
   * <p>Verify that when an org has a contract but upstream returns a different entitlement
   * (different billing_provider_id), the original contract is terminated while the new
   * entitlement's contract is created.
   */
  @TestPlanName("contracts-sync-TC012")
  @Test
  void shouldTerminateOnlyMissingContractsWhenPartialEntitlementsDisappear() {
    // Setup: Create a contract with known billing_provider_id and end_date in future
    Contract originalContract = givenRosaContractIsCreated(10.0);
    String sku = originalContract.getOffering().getSku();

    var contractsBeforeSync = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsBeforeSync.size(), "Should have one contract before sync");
    String originalUuid = contractsBeforeSync.get(0).getUuid();

    // Stub upstream to return a different entitlement (different billing_provider_id)
    Contract newContract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 20.0), sku);
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContractsInOrgId(orgId, newContract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(newContract);

    // Action: Sync contracts
    Response syncResponse = service.syncContractsByOrg(orgId);

    // Verification
    assertThat("Sync should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse
        .then()
        .body("status", equalTo(STATUS_SUCCESS))
        .body("message", equalTo("Contracts Synced for " + orgId));

    // Verify both contracts exist in database
    var contractsAfterSync = service.getContractsByOrgId(orgId);
    assertEquals(2, contractsAfterSync.size(), "Should have 2 contracts in database");

    // Find original and new contracts
    var originalContractAfterSync =
        contractsAfterSync.stream()
            .filter(c -> c.getUuid().equals(originalUuid))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Original contract not found"));

    var newContractAfterSync =
        contractsAfterSync.stream()
            .filter(c -> !c.getUuid().equals(originalUuid))
            .findFirst()
            .orElseThrow(() -> new AssertionError("New contract not found"));

    // Original contract should be terminated
    assertNotNull(
        originalContractAfterSync.getEndDate(),
        "Original contract should be terminated (not in upstream)");
    OffsetDateTime now = OffsetDateTime.now();
    assertTrue(
        originalContractAfterSync.getEndDate().isAfter(now.minusSeconds(5)),
        "Termination endDate should be approximately now");

    // New contract from upstream should be active
    assertNotNull(newContractAfterSync.getEndDate(), "New contract should have end_date");
    assertTrue(
        newContractAfterSync.getEndDate().isAfter(now),
        "New contract from upstream should be active (end_date in future)");
  }

  /**
   * TC013 - Already-terminated contracts are not re-terminated
   *
   * <p>Verify that contracts with an end_date in the past are not modified during sync when they're
   * missing from upstream. Uses a two-phase sync: first sync terminates the contract, second sync
   * should leave the termination timestamp unchanged.
   */
  @TestPlanName("contracts-sync-TC013")
  @Test
  void shouldNotReTerminateAlreadyTerminatedContracts() {
    givenRosaContractIsCreated(10.0);

    // Phase 1: Sync with empty upstream to terminate the contract
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContractsInOrgId(orgId));
    service.syncContractsByOrg(orgId);

    var contractsAfterFirstSync = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsAfterFirstSync.size());
    var firstEndDate = contractsAfterFirstSync.get(0).getEndDate();
    assertNotNull(firstEndDate, "Contract should be terminated after first sync");

    // Phase 2: Sync again with empty upstream — endDate should NOT change
    Response syncResponse = service.syncContractsByOrg(orgId);

    assertThat("Sync should return OK", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse
        .then()
        .body("status", equalTo("FAILED"))
        .body("message", equalTo("No contracts found in upstream for the org " + orgId));

    var contractsAfterSecondSync = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsAfterSecondSync.size(), "Contract should still exist");
    var secondEndDate = contractsAfterSecondSync.get(0).getEndDate();

    assertEquals(
        firstEndDate, secondEndDate, "Already-terminated contract should not be re-terminated");
  }

  /**
   * TC015 - Azure contract present in upstream is not terminated
   *
   * <p>Verify that an Azure contract with a billing_provider_id matching the upstream response is
   * recognized as present and NOT terminated during sync.
   */
  @TestPlanName("contracts-sync-TC015")
  @Test
  void shouldNotTerminateAzureContractPresentInUpstream() {
    // Given: Create an Azure contract
    String sku = RandomUtils.generateRandom();
    Contract azureContract = Contract.buildAzureContract(orgId, Map.of(CORES, 10.0), sku);
    givenContractIsCreated(azureContract);

    var contractsBefore = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsBefore.size(), "Should have one contract before sync");
    var endDateBefore = contractsBefore.get(0).getEndDate();
    assertNotNull(endDateBefore, "Contract should have an end_date");

    // Stub upstream to return the same Azure entitlement
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContractsInOrgId(orgId, azureContract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(azureContract);

    // When: Sync contracts
    Response syncResponse = service.syncContractsByOrg(orgId);

    // Then: Sync succeeds and contract is NOT terminated
    assertThat("Sync should return OK", syncResponse.statusCode(), is(HttpStatus.SC_OK));
    syncResponse
        .then()
        .body("status", equalTo(STATUS_SUCCESS))
        .body("message", equalTo("Contracts Synced for " + orgId));

    var contractsAfter = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsAfter.size(), "Should still have one contract after sync");
    assertEquals(
        endDateBefore,
        contractsAfter.get(0).getEndDate(),
        "Azure contract end_date should be unchanged (not terminated)");
  }

  private void givenRosaContractsAreStubbed(int count) {
    String sku = RandomUtils.generateRandom();
    List<Contract> contracts = new java.util.ArrayList<>();
    for (int i = 0; i < count; i++) {
      contracts.add(
          Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku));
    }
    wiremock.forProductAPI().stubOfferingData(contracts.get(0).getOffering());
    wiremock
        .forPartnerAPI()
        .stubPartnerSubscriptions(
            PartnerApiStubs.PartnerSubscriptionsStubRequest.forContractsInOrgId(
                orgId, contracts.toArray(new Contract[0])));
    wiremock
        .forSearchApi()
        .stubGetSubscriptionBySubscriptionNumber(contracts.toArray(new Contract[0]));
    Response sync = service.syncOffering(sku);
    assertEquals(HttpStatus.SC_OK, sync.statusCode(), "Sync offering should succeed");
  }
}
