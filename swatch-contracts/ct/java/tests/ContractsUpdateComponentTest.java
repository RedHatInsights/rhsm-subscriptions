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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.ContractResponse;
import domain.BillingProvider;
import domain.Contract;
import domain.Product;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ContractsUpdateComponentTest extends BaseContractComponentTest {

  private static final double DEFAULT_CAPACITY = 8.0;
  private static final double INSTANCE_HOURS_CAPACITY = 100.0;

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @TestPlanName("contracts-update-TC001")
  @Test
  void shouldUpdateExistingContractWhenReceivingAnUpdateEvent() {
    // given: An initial contract is created via UMB message
    Contract initialContract = givenContractCreatedViaMessageBroker();

    // update the end date for the contract
    Contract updatedContract =
        initialContract.toBuilder().endDate(OffsetDateTime.now().plusDays(30)).build();
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(updatedContract));
    artemis.forContracts().sendAsText(updatedContract);

    // then: The existing contract should be updated with the new end date
    service.logs().assertContains("Existing contracts and subscriptions updated");
    var contracts = service.getContracts(initialContract);
    Assertions.assertEquals(1, contracts.size());
    Assertions.assertNotNull(contracts.get(0).getEndDate());
    Assertions.assertTrue(contracts.get(0).getEndDate().isAfter(initialContract.getEndDate()));
  }

  @TestPlanName("contracts-update-TC002")
  @Test
  void shouldProcessRedundantContractMessage() {
    // given: An initial contract is created via UMB message
    Contract contract = givenContractCreatedViaMessageBroker();

    // update send the same message again
    artemis.forContracts().sendAsText(contract);

    // then message is ignored as redundant
    service.logs().assertContains("Redundant message ignored");
    var contracts = service.getContracts(contract);
    Assertions.assertEquals(1, contracts.size());
  }

  @TestPlanName("contracts-update-TC003")
  @Test
  void shouldReplaceContractWhenStartDateChanges() {
    // Given: An initial contract created via internal API with specific dates
    OffsetDateTime initialStartDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime initialEndDate = OffsetDateTime.parse("2024-12-31T23:59:59Z");
    Contract initialContract =
        givenContractWithDatesAndMetrics(
            BillingProvider.AWS, initialStartDate, initialEndDate, Map.of(CORES, DEFAULT_CAPACITY));

    // Store the initial UUID for comparison
    var createdContract = service.getContractsByOrgId(orgId).get(0);
    String initialUuid = createdContract.getUuid();

    // When: Update the contract's start and end dates
    // Note: Changing start_date causes the service to create a new contract
    // because it matches existing contracts by billing_provider_id + start_date
    OffsetDateTime updatedStartDate = OffsetDateTime.parse("2024-02-01T00:00:00Z");
    OffsetDateTime updatedEndDate = OffsetDateTime.parse("2025-01-31T23:59:59Z");
    Contract updatedContract =
        initialContract.toBuilder().startDate(updatedStartDate).endDate(updatedEndDate).build();
    whenContractIsUpdatedViaApi(updatedContract);

    // Then: A new contract is created (old one is deleted, new one has different UUID)
    var contracts = service.getContractsByOrgId(orgId);
    Assertions.assertEquals(1, contracts.size(), "Should have exactly one contract");

    var actual = contracts.get(0);
    // UUID is different because changing start_date creates a new contract
    Assertions.assertNotEquals(
        initialUuid, actual.getUuid(), "UUID should be different (new contract created)");
    thenContractDatesShouldBeUpdated(actual, updatedStartDate, updatedEndDate);
    thenContractFieldsShouldRemainUnchanged(actual, initialContract);
  }

  @TestPlanName("contracts-update-TC004")
  @Test
  void shouldUpdateContractEndDateForRenewal() {
    // Given: An initial contract created via internal API with specific dates
    OffsetDateTime startDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime initialEndDate = OffsetDateTime.parse("2024-12-31T23:59:59Z");
    Contract initialContract =
        givenContractWithDatesAndMetrics(
            BillingProvider.AWS, startDate, initialEndDate, Map.of(CORES, DEFAULT_CAPACITY));

    // Store the initial UUID for comparison
    var createdContract = service.getContractsByOrgId(orgId).get(0);
    String initialUuid = createdContract.getUuid();

    // When: Update only the end_date (renewal scenario - keeping start_date the same)
    // Note: Keeping start_date the same allows the service to match and update the existing
    // contract
    OffsetDateTime renewalEndDate = OffsetDateTime.parse("2025-12-31T23:59:59Z");
    Contract renewedContract = initialContract.toBuilder().endDate(renewalEndDate).build();
    whenContractIsUpdatedViaApiExpectingUpdate(renewedContract);

    // Then: The existing contract is updated (UUID remains the same)
    var contracts = service.getContractsByOrgId(orgId);
    Assertions.assertEquals(1, contracts.size(), "Should still have exactly one contract");

    var actual = contracts.get(0);
    // UUID should remain the same because only end_date changed (true update)
    Assertions.assertEquals(
        initialUuid, actual.getUuid(), "UUID should remain unchanged (contract updated)");
    Assertions.assertNotNull(actual.getEndDate(), "end_date should not be null");
    Assertions.assertTrue(
        actual.getEndDate().isEqual(renewalEndDate)
            || actual.getEndDate().isAfter(renewalEndDate.minusSeconds(1)),
        "end_date should be updated to renewal date");
    Assertions.assertTrue(
        actual.getEndDate().isAfter(initialEndDate),
        "end_date should be after the initial end date");
    thenContractFieldsShouldRemainUnchanged(actual, initialContract);
  }

  @TestPlanName("contracts-update-TC005")
  @Test
  void shouldUpdateContractMetricsForUpgrade() {
    // Given: A contract with Cores: 8, Instance-hours: 100
    OffsetDateTime startDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime endDate = OffsetDateTime.parse("2024-12-31T23:59:59Z");
    Contract initialContract =
        givenContractWithDatesAndMetrics(
            BillingProvider.AWS,
            startDate,
            endDate,
            Map.of(CORES, DEFAULT_CAPACITY, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY));

    var initial = service.getContractsByOrgId(orgId).get(0);
    String initialUuid = initial.getUuid();

    // When: Submit updated entitlement with Cores: 16, Instance-hours: 200
    Contract upgradedContract =
        initialContract.toBuilder()
            .subscriptionMeasurements(
                Map.of(CORES, DEFAULT_CAPACITY * 2, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY * 2))
            .build();
    whenContractIsUpdatedViaApiExpectingUpdate(upgradedContract);

    // Then: Existing contract found and updated with new metric values
    var contracts = service.getContractsByOrgId(orgId);
    Assertions.assertEquals(1, contracts.size(), "Should still have exactly one contract");

    var actual = contracts.get(0);
    Assertions.assertEquals(initialUuid, actual.getUuid(), "UUID should remain unchanged");
    Assertions.assertEquals(2, actual.getMetrics().size(), "Should have 2 metrics");
    thenMetricShouldHaveValue(actual, CORES, DEFAULT_CAPACITY * 2);
    thenMetricShouldHaveValue(actual, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY * 2);
  }

  @TestPlanName("contracts-update-TC006")
  @Test
  void shouldUpdateContractFromPurePaygToPaygWithMetrics() {
    // Given: A pure PAYG contract (no metrics - invalid metric filtered out)
    OffsetDateTime startDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime endDate = OffsetDateTime.parse("2024-12-31T23:59:59Z");
    Contract initialContract =
        givenContractWithDatesAndMetrics(
            BillingProvider.AWS, startDate, endDate, Map.of(SOCKETS, 10.0));

    var initial = service.getContractsByOrgId(orgId).get(0);
    String initialUuid = initial.getUuid();
    Assertions.assertEquals(
        0, initial.getMetrics().size(), "Should start with 0 metrics (pure PAYG)");

    // When: Submit updated entitlement with valid metrics (Cores: 8, Instance-hours: 100)
    Contract upgradedContract =
        initialContract.toBuilder()
            .subscriptionMeasurements(
                Map.of(CORES, DEFAULT_CAPACITY, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY))
            .build();
    whenContractIsUpdatedViaApiExpectingUpdate(upgradedContract);

    // Then: Contract now has metrics (upgraded from pure PAYG to PAYG with prepaid)
    var contracts = service.getContractsByOrgId(orgId);
    Assertions.assertEquals(1, contracts.size(), "Should still have exactly one contract");

    var actual = contracts.get(0);
    Assertions.assertEquals(initialUuid, actual.getUuid(), "UUID should remain unchanged");
    Assertions.assertEquals(2, actual.getMetrics().size(), "Should now have 2 metrics");
    thenMetricShouldHaveValue(actual, CORES, DEFAULT_CAPACITY);
    thenMetricShouldHaveValue(actual, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY);
  }

  @TestPlanName("contracts-update-TC007")
  @Test
  void shouldTerminateContractBySettingEndDateToNow() {
    // Given: A contract with an end date in the future
    OffsetDateTime startDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime futureEndDate = OffsetDateTime.now().plusDays(30);
    Contract initialContract =
        givenContractWithDatesAndMetrics(
            BillingProvider.AWS, startDate, futureEndDate, Map.of(CORES, DEFAULT_CAPACITY));

    var initial = service.getContractsByOrgId(orgId).get(0);
    String initialUuid = initial.getUuid();

    // When: Submit entitlement with end_date set to current timestamp (termination)
    OffsetDateTime terminationTime = OffsetDateTime.now();
    Contract terminatedContract = initialContract.toBuilder().endDate(terminationTime).build();
    whenContractIsUpdatedViaApiExpectingUpdate(terminatedContract);

    // Then: Contract is terminated (end_date set to termination timestamp)
    var contracts = service.getContractsByOrgId(orgId);
    Assertions.assertEquals(1, contracts.size(), "Should still have exactly one contract");

    var actual = contracts.get(0);
    Assertions.assertEquals(initialUuid, actual.getUuid(), "UUID should remain unchanged");
    Assertions.assertNotNull(actual.getEndDate(), "end_date should not be null");
    Assertions.assertTrue(
        actual.getEndDate().isBefore(futureEndDate),
        "end_date should be before the original future date");
    // Contract should be inactive (end_date is in the past or very close to now)
    Assertions.assertTrue(
        actual.getEndDate().isBefore(OffsetDateTime.now().plusMinutes(1)),
        "Contract should be terminated (end_date close to now)");
  }

  @TestPlanName("contracts-update-TC008")
  @Test
  void shouldAddNewMetricToExistingContract() {
    // Given: A contract with one metric (Cores: 8)
    OffsetDateTime startDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime endDate = OffsetDateTime.parse("2024-12-31T23:59:59Z");
    Contract initialContract =
        givenContractWithDatesAndMetrics(
            BillingProvider.AWS, startDate, endDate, Map.of(CORES, DEFAULT_CAPACITY));

    var initial = service.getContractsByOrgId(orgId).get(0);
    String initialUuid = initial.getUuid();
    Assertions.assertEquals(1, initial.getMetrics().size(), "Should start with 1 metric");

    // When: Update the contract with an additional metric (add Instance-hours: 100)
    Contract updatedContract =
        initialContract.toBuilder()
            .subscriptionMeasurements(
                Map.of(CORES, DEFAULT_CAPACITY, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY))
            .build();
    whenContractIsUpdatedViaApiExpectingUpdate(updatedContract);

    // Then: Old metric remains, new metric added
    var contracts = service.getContractsByOrgId(orgId);
    Assertions.assertEquals(1, contracts.size(), "Should still have exactly one contract");

    var actual = contracts.get(0);
    Assertions.assertEquals(initialUuid, actual.getUuid(), "UUID should remain unchanged");
    Assertions.assertEquals(2, actual.getMetrics().size(), "Should now have 2 metrics");
    thenMetricShouldHaveValue(actual, CORES, DEFAULT_CAPACITY);
    thenMetricShouldHaveValue(actual, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY);
  }

  @TestPlanName("contracts-update-TC009")
  @Test
  void shouldRemoveMetricFromExistingContract() {
    // Given: A contract with multiple metrics (Cores: 8, Instance-hours: 100)
    OffsetDateTime startDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime endDate = OffsetDateTime.parse("2024-12-31T23:59:59Z");
    Contract initialContract =
        givenContractWithDatesAndMetrics(
            BillingProvider.AWS,
            startDate,
            endDate,
            Map.of(CORES, DEFAULT_CAPACITY, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY));

    var initial = service.getContractsByOrgId(orgId).get(0);
    String initialUuid = initial.getUuid();
    Assertions.assertEquals(2, initial.getMetrics().size(), "Should start with 2 metrics");

    // When: Remove one metric (keep only Cores: 8, remove Instance-hours)
    Contract updatedContract =
        initialContract.toBuilder()
            .subscriptionMeasurements(Map.of(CORES, DEFAULT_CAPACITY))
            .build();
    whenContractIsUpdatedViaApiExpectingUpdate(updatedContract);

    // Then: Specified metric removed, other metric remains
    var contracts = service.getContractsByOrgId(orgId);
    Assertions.assertEquals(1, contracts.size(), "Should still have exactly one contract");

    var actual = contracts.get(0);
    Assertions.assertEquals(initialUuid, actual.getUuid(), "UUID should remain unchanged");
    Assertions.assertEquals(1, actual.getMetrics().size(), "Should now have 1 metric");
    thenMetricShouldHaveValue(actual, CORES, DEFAULT_CAPACITY);
    thenMetricShouldNotExist(actual, INSTANCE_HOURS);
  }

  private Contract givenContractCreatedViaMessageBroker() {
    Contract contract = Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    // Send the contract via Message Broker (Artemis)
    artemis.forContracts().sendAsText(contract);

    // Wait for the contract to be processed
    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));
    return contract;
  }

  private Contract givenContractWithDatesAndMetrics(
      BillingProvider provider,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      Map<MetricId, Double> metrics) {
    Contract contract =
        Contract.buildRosaContract(orgId, provider, metrics).toBuilder()
            .startDate(startDate)
            .endDate(endDate)
            .build();

    givenOfferingIsSynced(contract);
    whenContractIsCreatedViaApi(contract);

    return contract;
  }

  private void givenOfferingIsSynced(Contract contract) {
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));
  }

  private void whenContractIsCreatedViaApi(Contract contract) {
    Response response = service.createContract(contract);
    assertThat("Creating contract should succeed", response.statusCode(), is(HttpStatus.SC_OK));
  }

  private void whenContractIsUpdatedViaApi(Contract contract) {
    Response response = service.createContract(contract);
    assertThat("Updating contract should succeed", response.statusCode(), is(HttpStatus.SC_OK));
  }

  private void whenContractIsUpdatedViaApiExpectingUpdate(Contract contract) {
    Response response = service.createContract(contract);
    assertThat("Updating contract should succeed", response.statusCode(), is(HttpStatus.SC_OK));
    var contractResponse = response.then().extract().as(ContractResponse.class);
    Assertions.assertEquals(
        SUCCESS_MESSAGE,
        contractResponse.getStatus().getStatus(),
        "Status should be SUCCESS for successful operations");
    Assertions.assertEquals(
        EXISTING_CONTRACTS_SYNCED_MESSAGE,
        contractResponse.getStatus().getMessage(),
        "Message should indicate existing contracts were synced");
  }

  private void thenContractDatesShouldBeUpdated(
      com.redhat.swatch.contract.test.model.Contract actual,
      OffsetDateTime expectedStartDate,
      OffsetDateTime expectedEndDate) {
    Assertions.assertNotNull(actual.getStartDate(), "start_date should not be null");
    Assertions.assertNotNull(actual.getEndDate(), "end_date should not be null");
    Assertions.assertTrue(
        actual.getStartDate().isEqual(expectedStartDate)
            || actual.getStartDate().isAfter(expectedStartDate.minusSeconds(1)),
        "start_date should be updated");
    Assertions.assertTrue(
        actual.getEndDate().isEqual(expectedEndDate)
            || actual.getEndDate().isAfter(expectedEndDate.minusSeconds(1)),
        "end_date should be updated");
  }

  private void thenContractFieldsShouldRemainUnchanged(
      com.redhat.swatch.contract.test.model.Contract actual, Contract expected) {
    Assertions.assertEquals(
        expected.getOrgId(), actual.getOrgId(), "org_id should remain unchanged");
    Assertions.assertEquals(
        expected.getOffering().getSku(), actual.getSku(), "SKU should remain unchanged");
    Assertions.assertNotNull(actual.getMetrics(), "Metrics should not be null");
  }

  private void thenMetricShouldHaveValue(
      com.redhat.swatch.contract.test.model.Contract contract,
      MetricId metricId,
      double expectedValue) {
    // Get metric from Product domain object (ROSA product)
    var metric = Product.ROSA.getMetric(metricId);
    Assertions.assertNotNull(metric, metricId.toString() + " metric should exist in ROSA product");

    String dimension =
        BillingProvider.AZURE.toApiModel().equals(contract.getBillingProvider())
            ? metric.getAzureDimension()
            : metric.getAwsDimension();

    var actualMetric =
        contract.getMetrics().stream()
            .filter(m -> m.getMetricId().equals(dimension))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        metricId.toString() + " metric not found in contract metrics"));

    // Note: metric values get converted by billing factor
    double billingFactor = metric.getBillingFactor() != null ? metric.getBillingFactor() : 1.0;
    double expectedValueWithFactor = expectedValue * billingFactor;
    Assertions.assertEquals(
        (int) expectedValueWithFactor,
        actualMetric.getValue().intValue(),
        metricId.toString() + " value should be " + expectedValueWithFactor);
  }

  private void thenMetricShouldNotExist(
      com.redhat.swatch.contract.test.model.Contract contract, MetricId metricId) {
    // Get metric from Product domain object (ROSA product)
    var metric = Product.ROSA.getMetric(metricId);
    if (metric == null) {
      return; // Metric not in product, so it can't exist in contract
    }

    String dimension =
        BillingProvider.AZURE.toApiModel().equals(contract.getBillingProvider())
            ? metric.getAzureDimension()
            : metric.getAwsDimension();

    var actualMetric =
        contract.getMetrics().stream().filter(m -> m.getMetricId().equals(dimension)).findFirst();

    Assertions.assertTrue(
        actualMetric.isEmpty(),
        metricId.toString() + " metric should not exist in contract metrics");
  }
}
