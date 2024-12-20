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
package com.redhat.swatch.contract.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.redhat.swatch.contract.model.MeasurementMetricIdTransformer.MEASUREMENT_TYPE_DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1EntitlementDates;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlements;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PurchaseV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.contract.BaseUnitTest;
import com.redhat.swatch.contract.exception.ContractValidationFailedException;
import com.redhat.swatch.contract.model.ContractSourcePartnerEnum;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.model.PartnerEntitlementsRequest;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.ContractRequest;
import com.redhat.swatch.contract.openapi.model.ContractResponse;
import com.redhat.swatch.contract.openapi.model.Dimension;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContractCloudIdentifiers;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.ContractRepository;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.test.resources.InjectWireMock;
import com.redhat.swatch.contract.test.resources.WireMockResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

@QuarkusTest
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
class ContractServiceTest extends BaseUnitTest {

  private static final String ORG_ID = "org123";
  private static final String SKU = "RH000000";
  private static final String PRODUCT_TAG = "rosa";
  private static final String SUBSCRIPTION_NUMBER = "13294886";
  private static final String SUBSCRIPTION_ID = "123456";
  private static final OffsetDateTime DEFAULT_START_DATE =
      OffsetDateTime.parse("2023-06-09T13:59:43.035365Z");
  private static final OffsetDateTime DEFAULT_END_DATE =
      OffsetDateTime.parse("2026-02-15T13:59:43.035365Z");

  @Inject ContractService contractService;
  @Inject ObjectMapper objectMapper;
  @InjectSpy ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;
  @InjectSpy SubscriptionRepository subscriptionRepository;
  @InjectWireMock WireMockServer wireMockServer;
  @InjectSpy MeasurementMetricIdTransformer measurementMetricIdTransformer;

  @Transactional
  @BeforeEach
  public void setup() {
    WireMockResource.setup(wireMockServer);
    subscriptionRepository.deleteAll();
    contractRepository.deleteAll();
    offeringRepository.deleteAll();
    OfferingEntity offering = new OfferingEntity();
    offering.setSku(SKU);
    offering.setProductTags(Set.of(PRODUCT_TAG));
    offeringRepository.persist(offering);
  }

  @Test
  void testSaveContracts() {
    ContractRequest request = givenContractRequest();
    Contract response = contractService.createContract(request).getContract();

    ContractEntity entity = contractRepository.findById(UUID.fromString(response.getUuid()));
    assertEquals(
        request.getPartnerEntitlement().getRhEntitlements().get(0).getSku(),
        entity.getOffering().getSku());
    assertEquals(response.getUuid(), entity.getUuid().toString());
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer).mapContractMetricsToSubscriptionMeasurements(any());
  }

  @Test
  void testGetContracts() {
    givenExistingContract();
    List<Contract> contractList =
        contractService.getContracts(ORG_ID, PRODUCT_TAG, null, null, null, null);
    assertEquals(1, contractList.size());
    assertEquals(2, contractList.get(0).getMetrics().size());
  }

  @Test
  void createPartnerContractWhenNonNullEntityAndContractNotFoundInDB() {
    var contract = givenPartnerEntitlementContractRequest();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository, times(2)).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer, times(2))
        .mapContractMetricsToSubscriptionMeasurements(any());
  }

  @Test
  void createPartnerContractWhenExistingContractThenStatusReturnsRedundantMessage() {
    // given an existing contract
    var contract = givenPartnerEntitlementContractRequest();
    contractService.createPartnerContract(contract);
    // when we send the same request again
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    // we should get redundant message ignored
    assertEquals("Redundant message ignored", statusResponse.getMessage());
  }

  @Test
  void upsertPartnerContractWhenNullEntityThrowError() {
    PartnerEntitlementV1 contract = givenContractWithoutRequiredData();
    assertThrows(
        ContractValidationFailedException.class,
        () -> contractService.upsertPartnerContracts(contract, null));
  }

  @Test
  void whenInvalidPartnerContractDoNotPersist() {
    var contract = givenPartnerEntitlementContractWithoutProductCode();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Bad message, see logs for details", statusResponse.getMessage());
  }

  @Test
  void createPartnerContractNotDuplicateContractThenPersist() {
    givenExistingContract();
    givenExistingSubscriptionWithBillingProviderId("1234:agb1:1fa");

    var request = givenPartnerEntitlementContractRequest();

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    verify(subscriptionRepository, times(2)).persist(any(SubscriptionEntity.class));
    assertEquals("New contract created", statusResponse.getMessage());
  }

  @Test
  void createPartnerContractUpdateContract() {
    givenExistingContractWithExistingMetrics();
    givenExistingSubscriptionWithBillingProviderId("1234:agb1:1fa");

    var request = givenPartnerEntitlementContractRequest();

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    verify(subscriptionRepository, times(2)).persist(any(SubscriptionEntity.class));
    verify(contractRepository, times(2)).persist(any(ContractEntity.class));

    ArgumentCaptor<ContractEntity> contractSaveCapture =
        ArgumentCaptor.forClass(ContractEntity.class);
    verify(contractRepository).delete(contractSaveCapture.capture());
    // Contract with invalid start_date is deleted
    assertEquals(
        OffsetDateTime.parse("2023-03-17T12:29:48.569Z"),
        contractSaveCapture.getValue().getStartDate());
    assertEquals("New contract created", statusResponse.getMessage());
  }

  @Test
  void syncContractWithExistingAndNewContracts() {
    givenExistingContract();
    StatusResponse statusResponse = contractService.syncContractByOrgId(ORG_ID, false);
    assertEquals("Contracts Synced for " + ORG_ID, statusResponse.getMessage());
    // 2 instances of subscription are created, one for the original contract, and one for the
    // update
    verify(subscriptionRepository, times(4)).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer, times(4))
        .mapContractMetricsToSubscriptionMeasurements(any());
  }

  @Test
  void syncContractWithEmptyContractsList() {
    StatusResponse statusResponse = contractService.syncContractByOrgId(ORG_ID, false);
    assertEquals("Contracts Synced for " + ORG_ID, statusResponse.getMessage());
  }

  @Test
  void testCreatePartnerContractSetsCorrectDimensionAzure() throws Exception {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber(SUBSCRIPTION_NUMBER);
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("four_vcpu_hour").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));

    mockPartnerApi();

    ArgumentCaptor<ContractEntity> contractSaveCapture =
        ArgumentCaptor.forClass(ContractEntity.class);
    contractService.createPartnerContract(PartnerEntitlementsRequest.from(contract));
    verify(contractRepository).persist(contractSaveCapture.capture());
    var actualContract = contractSaveCapture.getValue();
    var expectedMetric =
        ContractMetricEntity.builder()
            .metricId("four_vcpu_hour")
            .value(4)
            .contract(actualContract)
            .contractUuid(actualContract.getUuid())
            .build();
    assertTrue(contractSaveCapture.getValue().getMetrics().contains(expectedMetric));
  }

  @Test
  void testCreatePartnerContractCreatesAzureSubscription() throws Exception {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber(SUBSCRIPTION_NUMBER);
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("four_vcpu_hour").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));

    mockPartnerApi();

    StatusResponse statusResponse =
        contractService.createPartnerContract(PartnerEntitlementsRequest.from(contract));
    assertEquals("New contract created", statusResponse.getMessage());
  }

  @Test
  void testCreateAzureContractMissingRHSubscriptionId() throws Exception {
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
  }

  @Test
  void testCreatePartnerContractCreatesCorrectBillingProviderId() throws Exception {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber(SUBSCRIPTION_NUMBER);
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("four_vcpu_hour").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));

    mockPartnerApi();

    ArgumentCaptor<SubscriptionEntity> subscriptionSaveCapture =
        ArgumentCaptor.forClass(SubscriptionEntity.class);
    contractService.createPartnerContract(PartnerEntitlementsRequest.from(contract));
    verify(subscriptionRepository).persist(subscriptionSaveCapture.capture());
    subscriptionSaveCapture.getValue();
    assertEquals(
        "a69ff71c-aa8b-43d9-dea8-822fab4bbb86;rh-rhel-sub-1yr;azureProductCode;eadf26ee-6fbc-4295-9a9e-25d4fea8951d_2019-05-31;",
        subscriptionSaveCapture.getValue().getBillingProviderId());
  }

  @Test
  void testDeleteContractDeletesSubscription() {
    ContractEntity contract = givenExistingContract();
    SubscriptionEntity subscription = givenExistingSubscription();
    contractService.deleteContract(contract.getUuid().toString());
    verify(subscriptionRepository).delete(argThat(sameSubscription(subscription)));
    verify(contractRepository).delete(argThat(c -> c.getUuid().equals(contract.getUuid())));
  }

  @Test
  void testDeleteContractNoopWhenMissing() {
    contractService.deleteContract(UUID.randomUUID().toString());
    verify(subscriptionRepository, times(0)).delete(any());
    verify(contractRepository, times(0)).delete(any());
  }

  @Test
  void testDeleteContractsByOrgId() {
    givenExistingContract();
    givenExistingSubscription();
    contractService.deleteContractsByOrgId(ORG_ID);
    verify(contractRepository).delete(any());
    verify(subscriptionRepository).delete(any());
  }

  @Test
  void testSyncSubscriptionsForContractsByOrg() {
    givenExistingContract();
    var expectedSubscription = givenExistingSubscription();
    contractService.syncSubscriptionsForContractsByOrg(ORG_ID);
    verify(subscriptionRepository).persist(argThat(sameSubscription(expectedSubscription)));
  }

  @Test
  void testExistingSubscriptionSyncedWithITGatewayResponse() throws Exception {
    givenExistingSubscription();
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    ArgumentCaptor<SubscriptionEntity> subscriptionsSaveCapture =
        ArgumentCaptor.forClass(SubscriptionEntity.class);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(subscriptionsSaveCapture.capture());
    var persistedSubscriptions = subscriptionsSaveCapture.getValue();
    assertEquals(DEFAULT_END_DATE, persistedSubscriptions.getEndDate());
    verify(subscriptionRepository, times(0)).delete(any());
  }

  @Test
  void testExistingSubscriptionNotInITGatewayResponseIsDeleted() throws Exception {
    var subscription =
        givenExistingSubscriptionWithStartDate(OffsetDateTime.parse("2023-06-09T04:00:00.035365Z"));
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(subscriptionRepository, times(1)).delete(argThat(sameSubscription(subscription)));
  }

  @Test
  void testContractSyncedWhenNoContractDimensionsExist() throws Exception {
    var contract = givenAzurePartnerEntitlementContract();
    var stub = mockPartnerApi(createPartnerApiResponseNoContractDimensions());
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    wireMockServer.removeStub(stub);
  }

  @Test
  void testContractSyncedWhenNoPurchaseExist() throws Exception {
    var contract = givenAzurePartnerEntitlementContract();
    var response = createPartnerApiResponseNoContractDimensions();
    response.getContent().get(0).setPurchase(null);
    var stubMapping = mockPartnerApi(response);
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Empty value in non-null fields", statusResponse.getMessage());
    wireMockServer.removeStub(stubMapping);
  }

  /**
   * IT Partner gateway uses nano precision for the start date, when postgresql or hsql uses micro
   * precision. Therefore, we need to address this loss of precision to properly identify the
   * contracts being updated.
   */
  @Test
  void testContractIsUpdatedWhenUsingSameStartDateWithNanoPrecision() {
    var existing = givenExistingContractWithSameStartDateThanInPartnerGateway();
    // when receive an update using exactly the same start date
    var request = givenPartnerEntitlementContractRequest();
    contractService.createPartnerContract(request);
    // then existing contract is updated
    verify(contractRepository)
        .persist(
            argThat(
                (ArgumentMatcher<ContractEntity>)
                    actual -> existing.getUuid().equals(actual.getUuid())));
  }

  @Test
  void testContractMetricValueUpdated() throws Exception {
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi();
    contractService.createPartnerContract(contract);
    // by default, the metric value is 4.0:
    assertContractMetricIs("four_vcpu_hour", 4);
    assertSubscriptionMetricIs("Cores", 4);

    // when updating the contract metric
    var updatedApiResponse = createPartnerApiResponse(fourCpuHourDimension("999"));
    mockPartnerApi(updatedApiResponse);
    contractService.createPartnerContract(contract);

    // then the metric is updated
    assertContractMetricIs("four_vcpu_hour", 999);
    assertSubscriptionMetricIs("Cores", 999);
  }

  @Test
  void testContractNewMetricAddedToExisting() throws Exception {
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi(
        createPartnerApiResponse(fourCpuHourDimension("4"), controlPlaneDimension("99")));
    contractService.createPartnerContract(contract);

    assertContractMetricIs("four_vcpu_hour", 4);
    assertSubscriptionMetricIs("Cores", 4);
    assertContractMetricIs("control_plane", 99);
    assertSubscriptionMetricIs("Instance-hours", 99);
  }

  private static PartnerEntitlementV1 givenContractWithoutRequiredData() {
    PartnerEntitlementV1 entitlement = new PartnerEntitlementV1();
    entitlement.setRhAccountId(ORG_ID);
    entitlement.setPartnerIdentities(new PartnerIdentityV1());
    entitlement.setSourcePartner(ContractSourcePartnerEnum.AWS.getCode());
    entitlement.setPurchase(new PurchaseV1());
    entitlement.getPurchase().setContracts(new ArrayList<>());
    return entitlement;
  }

  private static PartnerEntitlementsRequest givenPartnerEntitlementContractWithoutProductCode() {
    var contract = createPartnerEntitlementContract();
    contract.getCloudIdentifiers().setProductCode(null);
    return PartnerEntitlementsRequest.from(contract);
  }

  private static PartnerEntitlementsRequest givenPartnerEntitlementContractRequest() {
    return PartnerEntitlementsRequest.from(createPartnerEntitlementContract());
  }

  private static PartnerEntitlementContract createPartnerEntitlementContract() {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber(SUBSCRIPTION_NUMBER);

    PartnerEntitlementContractCloudIdentifiers cloudIdentifiers =
        new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers.setAwsCustomerId("HSwCpt6sqkC");
    cloudIdentifiers.setAwsCustomerAccountId("568056954830");
    cloudIdentifiers.setProductCode("1234567890abcdefghijklmno");
    contract.setCloudIdentifiers(cloudIdentifiers);
    return contract;
  }

  private static PartnerEntitlementsRequest givenAzurePartnerEntitlementContract() {
    var contract = new PartnerEntitlementContract();
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("four_vcpu_hour").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));
    return PartnerEntitlementsRequest.from(contract);
  }

  private SubscriptionEntity givenExistingSubscription() {
    return givenExistingSubscription(null, DEFAULT_START_DATE);
  }

  private SubscriptionEntity givenExistingSubscriptionWithBillingProviderId(
      String billingProviderId) {
    return givenExistingSubscription(billingProviderId, DEFAULT_START_DATE);
  }

  private SubscriptionEntity givenExistingSubscriptionWithStartDate(OffsetDateTime startDate) {
    return givenExistingSubscription(null, startDate);
  }

  @Transactional
  SubscriptionEntity givenExistingSubscription(String billingProviderId, OffsetDateTime startDate) {
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setSubscriptionId(SUBSCRIPTION_ID);
    subscription.setSubscriptionNumber(SUBSCRIPTION_NUMBER);
    subscription.setStartDate(startDate);
    subscription.setBillingProviderId(billingProviderId);
    subscriptionRepository.persist(subscription);
    // to not interfere with the verifications
    reset(subscriptionRepository);
    return subscription;
  }

  private ContractEntity givenExistingContractWithSameStartDateThanInPartnerGateway() {
    return givenExistingContract(
        givenContractRequestWithDates(
            WireMockResource.DEFAULT_START_DATE, WireMockResource.DEFAULT_END_DATE));
  }

  private ContractEntity givenExistingContract() {
    return givenExistingContract(givenContractRequest());
  }

  private ContractEntity givenExistingContractWithExistingMetrics() {
    var request = givenContractRequest();
    var contract = request.getPartnerEntitlement().getPurchase().getContracts().get(0);

    // existing metrics are coming from WireMockResource.stubForRhPartnerApi() method
    var metric1 = new DimensionV1();
    metric1.setName("Instance-hours");
    metric1.setValue("1000000");

    var metric2 = new DimensionV1();
    metric2.setName("Cores");
    metric2.setValue("1000000");
    contract.setDimensions(List.of(metric1, metric2));
    return givenExistingContract(request);
  }

  private ContractEntity givenExistingContract(ContractRequest request) {
    ContractResponse created = contractService.createContract(request);
    var entity = contractRepository.findById(UUID.fromString(created.getContract().getUuid()));
    // reset the invocations of this repository, so it does not mix up with the assertions.
    reset(contractRepository);
    return entity;
  }

  private ContractRequest givenContractRequest() {
    return givenContractRequestWithDates("2023-03-17T12:29:48.569Z", "2024-03-17T12:29:48.569Z");
  }

  private ContractRequest givenContractRequestWithDates(String startDate, String endDate) {
    var contract = new PartnerEntitlementContract();
    var entitlement = new PartnerEntitlementV1();
    var cloudIdentifiers = new PartnerEntitlementContractCloudIdentifiers();
    var partnerIdentity = new PartnerIdentityV1();
    var rhEntitlement = new RhEntitlementV1();
    var purchase = new PurchaseV1();

    partnerIdentity.setCustomerAwsAccountId("billAcct123");
    partnerIdentity.setAwsCustomerId("HSwCpt6sqkC");
    partnerIdentity.setSellerAccountId("568056954830");
    entitlement.setEntitlementDates(new PartnerEntitlementV1EntitlementDates());
    entitlement.getEntitlementDates().setStartDate(OffsetDateTime.parse(startDate));
    entitlement.getEntitlementDates().setEndDate(OffsetDateTime.parse(endDate));
    entitlement.setSourcePartner(ContractSourcePartnerEnum.AWS.getCode());
    rhEntitlement.setSku(SKU);
    purchase.setVendorProductCode("1234567890abcdefghijklmno");
    cloudIdentifiers.setProductCode("1234567890abcdefghijklmno");
    entitlement.setRhAccountId(ORG_ID);
    rhEntitlement.setSubscriptionNumber(SUBSCRIPTION_NUMBER);
    contract.setRedHatSubscriptionNumber(SUBSCRIPTION_NUMBER);

    var saasContract = new SaasContractV1();
    purchase.setContracts(List.of(saasContract));
    saasContract.setDimensions(List.of(controlPlaneDimension("2"), fourCpuHourDimension("4")));
    saasContract.setStartDate(entitlement.getEntitlementDates().getStartDate());
    contract.setCloudIdentifiers(cloudIdentifiers);

    ContractRequest contractRequest = new ContractRequest();
    contractRequest.setSubscriptionId(SUBSCRIPTION_ID);
    contractRequest.setPartnerEntitlement(entitlement);
    entitlement.setPartnerIdentities(partnerIdentity);
    entitlement.setPurchase(purchase);
    entitlement.setRhEntitlements(List.of(rhEntitlement));
    return contractRequest;
  }

  @Transactional
  void assertContractMetricIs(String metricId, double expectedValue) {
    contractRepository.flush();
    var contracts = contractRepository.listAll();
    assertEquals(1, contracts.size());
    var metric = contracts.get(0).getMetric(metricId);
    assertNotNull(metric);
    assertEquals(expectedValue, metric.getValue());
  }

  @Transactional
  void assertSubscriptionMetricIs(String metricId, double expectedValue) {
    subscriptionRepository.flush();
    var subscriptions = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    assertEquals(1, subscriptions.size());
    // expected value is actually the value * billing_factor
    double billingFactor =
        Optional.ofNullable(
                Variant.findByTag(PRODUCT_TAG)
                    .orElseThrow()
                    .getSubscription()
                    .getMetric(metricId)
                    .orElseThrow()
                    .getBillingFactor())
            .orElse(1.0);
    assertEquals(
        expectedValue / billingFactor,
        subscriptions.get(0).getSubscriptionMeasurement(metricId, MEASUREMENT_TYPE_DEFAULT));
  }

  private void mockPartnerApi() throws Exception {
    mockPartnerApi(createPartnerApiResponse());
  }

  private StubMapping mockPartnerApi(PartnerEntitlements response) throws Exception {
    return wireMockServer.stubFor(
        WireMock.any(urlMatching("/mock/partnerApi/v1/partnerSubscriptions"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(objectMapper.writeValueAsString(response))));
  }

  private PartnerEntitlements createPartnerApiResponse(DimensionV1... dimensions) {
    PartnerEntitlements entitlements = createPartnerApiResponse();
    entitlements
        .getContent()
        .get(0)
        .getPurchase()
        .getContracts()
        .get(0)
        .setDimensions(Stream.of(dimensions).toList());
    return entitlements;
  }

  private PartnerEntitlements createPartnerApiResponse() {
    var entitlement =
        new PartnerEntitlementV1()
            .entitlementDates(
                new PartnerEntitlementV1EntitlementDates()
                    .startDate(OffsetDateTime.parse("2023-03-17T12:29:48.569Z"))
                    .endDate(OffsetDateTime.parse("2024-03-17T12:29:48.569Z")))
            .rhAccountId("7186626")
            .sourcePartner(ContractSourcePartnerEnum.AZURE.getCode())
            .partnerIdentities(
                new PartnerIdentityV1()
                    .azureSubscriptionId("fa650050-dedd-4958-b901-d8e5118c0a5f")
                    .azureCustomerId("eadf26ee-6fbc-4295-9a9e-25d4fea8951d_2019-05-31"))
            .rhEntitlements(
                List.of(new RhEntitlementV1().sku(SKU).subscriptionNumber(SUBSCRIPTION_NUMBER)))
            .purchase(
                new PurchaseV1()
                    .vendorProductCode("azureProductCode")
                    .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
                    .contracts(
                        List.of(
                            new SaasContractV1()
                                .startDate(DEFAULT_START_DATE)
                                .endDate(DEFAULT_END_DATE)
                                .planId("rh-rhel-sub-1yr")
                                .dimensions(List.of(fourCpuHourDimension("4"))))));
    return new PartnerEntitlements().content(List.of(entitlement));
  }

  private DimensionV1 controlPlaneDimension(String value) {
    return dimension("control_plane", value);
  }

  private DimensionV1 fourCpuHourDimension(String value) {
    return dimension("four_vcpu_hour", value);
  }

  private DimensionV1 dimension(String name, String value) {
    return new DimensionV1().name(name).value(value);
  }

  private PartnerEntitlements createPartnerApiResponseNoContractDimensions() {
    var entitlement =
        new PartnerEntitlementV1()
            .entitlementDates(
                new PartnerEntitlementV1EntitlementDates()
                    .startDate(OffsetDateTime.parse("2023-03-17T12:29:48.569Z"))
                    .endDate(OffsetDateTime.parse("2024-03-17T12:29:48.569Z")))
            .rhAccountId("7186626")
            .sourcePartner(ContractSourcePartnerEnum.AZURE.getCode())
            .partnerIdentities(
                new PartnerIdentityV1()
                    .azureSubscriptionId("fa650050-dedd-4958-b901-d8e5118c0a5f")
                    .azureCustomerId("eadf26ee-6fbc-4295-9a9e-25d4fea8951d_2019-05-31"))
            .rhEntitlements(List.of(new RhEntitlementV1().sku(SKU).subscriptionNumber("testSubId")))
            .purchase(
                new PurchaseV1()
                    .vendorProductCode("azureProductCode")
                    .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
                    .contracts(new ArrayList<>()));

    return new PartnerEntitlements().content(List.of(entitlement));
  }

  private static ArgumentMatcher<SubscriptionEntity> sameSubscription(SubscriptionEntity expected) {
    return actual -> actual.getSubscriptionNumber().equals(expected.getSubscriptionNumber());
  }
}
