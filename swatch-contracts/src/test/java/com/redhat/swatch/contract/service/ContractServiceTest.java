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
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1EntitlementDates;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlements;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PurchaseV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.BaseUnitTest;
import com.redhat.swatch.contract.exception.ContractValidationFailedException;
import com.redhat.swatch.contract.model.ContractSourcePartnerEnum;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
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
import com.redhat.swatch.contract.resource.WireMockResource;
import io.quarkus.test.InjectMock;
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
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;

@QuarkusTest
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractServiceTest extends BaseUnitTest {

  private static final String ORG_ID = "org123";
  private static final String SKU = "RH000000";
  private static final String PRODUCT_TAG = "MH123";
  private static final String SUBSCRIPTION_NUMBER = "subs123";
  private static final OffsetDateTime DEFAULT_START_DATE =
      OffsetDateTime.parse("2023-06-09T13:59:43.035365Z");
  private static final OffsetDateTime DEFAULT_END_DATE =
      OffsetDateTime.parse("2026-02-15T13:59:43.035365Z");

  @Inject ContractService contractService;
  @Inject ObjectMapper objectMapper;
  @InjectSpy ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;
  @InjectMock SubscriptionRepository subscriptionRepository;

  @InjectMock @RestClient SearchApi subscriptionApi;
  @InjectMock MeasurementMetricIdTransformer measurementMetricIdTransformer;

  @Transactional
  @BeforeEach
  public void setup() {
    contractRepository.deleteAll();
    offeringRepository.deleteAll();

    OfferingEntity offering = new OfferingEntity();
    offering.setSku(SKU);
    offering.setProductTags(Set.of(PRODUCT_TAG));
    offeringRepository.persist(offering);
    mockSubscriptionServiceSubscription();
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
    verify(subscriptionRepository).persist(any(Set.class));
    verify(measurementMetricIdTransformer).translateContractMetricIdsToSubscriptionMetricIds(any());
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
  void createPartnerContract_WhenNonNullEntityAndContractNotFoundInDB() {
    var contract = givenPartnerEntitlementContractRequest();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(any(Set.class));
    verify(measurementMetricIdTransformer, times(2))
        .translateContractMetricIdsToSubscriptionMetricIds(any());
  }

  @Test
  void upsertPartnerContract_WhenNullEntityThrowError() {
    PartnerEntitlementV1 contract = givenContractWithoutRequiredData();
    assertThrows(
        ContractValidationFailedException.class,
        () -> contractService.upsertPartnerContracts(contract, null));
  }

  @Test
  void whenInvalidPartnerContract_DoNotPersist() {
    var contract = givenPartnerEntitlementContractWithoutProductCode();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Bad message, see logs for details", statusResponse.getMessage());
  }

  @Test
  void createPartnerContract_NotDuplicateContractThenPersist() {
    givenExistingContract();
    givenExistingSubscription("1234:agb1:1fa");

    PartnerEntitlementContract request = givenPartnerEntitlementContractRequest();

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    verify(subscriptionRepository, times(2)).persist(any(Set.class));
    assertEquals("New contract created", statusResponse.getMessage());
  }

  @Test
  void createPartnerContract_UpdateContract() {
    givenExistingContractWithExistingMetrics();
    givenExistingSubscription("1234:agb1:1fa");

    PartnerEntitlementContract request = givenPartnerEntitlementContractRequest();

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    verify(subscriptionRepository, times(2)).persist(any(Set.class));

    verify(contractRepository, times(3)).persist(any(ContractEntity.class));

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
  void createPartnerContract_DuplicateContractThenDoNotPersist() {
    PartnerEntitlementContract request = givenPartnerEntitlementContractRequest();
    contractService.createPartnerContract(request);

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    assertEquals("Existing contracts and subscriptions updated", statusResponse.getMessage());
    verify(subscriptionRepository, times(2)).persist(any(Set.class));
  }

  @Test
  void testCreatePartnerContractDuplicateBillingProviderIdNotPersist() {
    PartnerEntitlementContract request = givenPartnerEntitlementContractRequest();
    contractService.createPartnerContract(request);

    request.getCloudIdentifiers().azureResourceId("dupeId");
    request.getCloudIdentifiers().setAzureOfferId("dupeId");
    request.getCloudIdentifiers().setPlanId("dupeId");
    request.getCloudIdentifiers().setPartner("azure_marketplace");

    givenExistingSubscription("dupeId;dupeId;dupeId");

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    assertEquals("Redundant message ignored", statusResponse.getMessage());
  }

  @Test
  void syncContractWithExistingAndNewContracts() {
    givenExistingContract();
    StatusResponse statusResponse = contractService.syncContractByOrgId(ORG_ID, false);
    assertEquals("Contracts Synced for " + ORG_ID, statusResponse.getMessage());
    // 2 instances of subscription are created, one for the original contract, and one for the
    // update
    verify(subscriptionRepository, times(3)).persist(any(Set.class));
    verify(measurementMetricIdTransformer, times(4))
        .translateContractMetricIdsToSubscriptionMetricIds(any());
  }

  @Test
  void syncContractWithEmptyContractsList() {
    StatusResponse statusResponse = contractService.syncContractByOrgId(ORG_ID, false);
    assertEquals("Contracts Synced for " + ORG_ID, statusResponse.getMessage());
  }

  @Test
  void testCreatePartnerContractSetsCorrectDimensionAzure() throws Exception {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("subnum");
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("vCPU").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));

    mockPartnerApi();

    ArgumentCaptor<ContractEntity> contractSaveCapture =
        ArgumentCaptor.forClass(ContractEntity.class);
    contractService.createPartnerContract(contract);
    verify(contractRepository).persist(contractSaveCapture.capture());
    var actualContract = contractSaveCapture.getValue();
    var expectedMetric =
        ContractMetricEntity.builder()
            .metricId("vCPU")
            .value(4)
            .contract(actualContract)
            .contractUuid(actualContract.getUuid())
            .build();
    assertTrue(contractSaveCapture.getValue().getMetrics().contains(expectedMetric));
  }

  @Test
  void testCreatePartnerContractCreatesAzureSubscription() throws Exception {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("subnum");
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("vCPU").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));

    mockPartnerApi();

    StatusResponse statusResponse = contractService.createPartnerContract(contract);
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
    contract.setRedHatSubscriptionNumber("subnum");
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("vCPU").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));

    mockPartnerApi();

    ArgumentCaptor<Set<SubscriptionEntity>> subscriptionSaveCapture =
        ArgumentCaptor.forClass(Set.class);
    contractService.createPartnerContract(contract);
    verify(subscriptionRepository).persist(subscriptionSaveCapture.capture());
    subscriptionSaveCapture.getValue();
    assertEquals(
        "a69ff71c-aa8b-43d9-dea8-822fab4bbb86;rh-rhel-sub-1yr;azureProductCode;eadf26ee-6fbc-4295-9a9e-25d4fea8951d_2019-05-31",
        subscriptionSaveCapture.getValue().iterator().next().getBillingProviderId());
  }

  @Test
  void testDeleteContractDeletesSubscription() {
    ContractEntity contract = givenExistingContract();
    SubscriptionEntity subscription = givenExistingSubscription();
    contractService.deleteContract(contract.getUuid().toString());
    verify(subscriptionRepository).delete(subscription);
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
    verify(subscriptionRepository).persist(expectedSubscription);
  }

  @Test
  void testExistingSubscriptionSyncedWithITGatewayResponse() throws Exception {
    var subscription = new SubscriptionEntity();
    subscription.setStartDate(DEFAULT_START_DATE);
    when(subscriptionRepository.findBySubscriptionNumber(any())).thenReturn(List.of(subscription));
    when(subscriptionRepository.findOne(any(), any())).thenReturn(Optional.of(subscription));
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    ArgumentCaptor<Set<SubscriptionEntity>> subscriptionsSaveCapture =
        ArgumentCaptor.forClass(Set.class);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(subscriptionsSaveCapture.capture());
    var persistedSubscriptions = subscriptionsSaveCapture.getValue();
    assertEquals(1, persistedSubscriptions.size());
    assertEquals(DEFAULT_END_DATE, persistedSubscriptions.iterator().next().getEndDate());
    verify(subscriptionRepository, times(0)).delete(any());
  }

  @Test
  void testExistingSubscriptionNotInITGatewayResponseIsDeleted() throws Exception {
    var subscription = new SubscriptionEntity();
    subscription.setStartDate(OffsetDateTime.parse("2023-06-09T04:00:00.035365Z"));
    when(subscriptionRepository.findBySubscriptionNumber(any())).thenReturn(List.of(subscription));
    when(subscriptionRepository.findOne(any(), any())).thenReturn(Optional.of(subscription));
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(any(Set.class));
    verify(subscriptionRepository, times(1)).delete(subscription);
  }

  @Test
  void testContractSyncedWhenNoContractDimensionsExist() throws Exception {
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi(createPartnerApiResponseNoContractDimensions());
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(any(Set.class));
  }

  @Test
  void testContractMetricValueUpdated() throws Exception {
    var contract = givenAzurePartnerEntitlementContract();
    mockPartnerApi();
    contractService.createPartnerContract(contract);
    var updatedApiResponse = createPartnerApiResponse();
    updatedApiResponse
        .getContent()
        .get(0)
        .getPurchase()
        .getContracts()
        .get(0)
        .getDimensions()
        .get(0)
        .setValue("999");
    mockPartnerApi(updatedApiResponse);
    contractService.createPartnerContract(contract);
    var contracts = contractRepository.findAll();
    var persistedContract =
        contracts.stream()
            .filter(entity -> entity.getStartDate().equals(DEFAULT_START_DATE))
            .findFirst()
            .get();
    assertEquals(999, persistedContract.getMetrics().iterator().next().getValue());
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

  private static PartnerEntitlementContract givenPartnerEntitlementContractWithoutProductCode() {
    var contract = givenPartnerEntitlementContractRequest();
    contract.getCloudIdentifiers().setProductCode(null);
    return contract;
  }

  private static PartnerEntitlementContract givenPartnerEntitlementContractRequest() {
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

  private static PartnerEntitlementContract givenAzurePartnerEntitlementContract() {
    var contract = new PartnerEntitlementContract();
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("vCPU").dimensionValue("4")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .partner(ContractSourcePartnerEnum.AZURE.getCode())
            .azureResourceId("a69ff71c-aa8b-43d9-dea8-822fab4bbb86")
            .azureOfferId("azureProductCode")
            .planId("rh-rhel-sub-1yr"));
    return contract;
  }

  private SubscriptionEntity givenExistingSubscription() {
    return givenExistingSubscription(null);
  }

  private SubscriptionEntity givenExistingSubscription(String billingProviderId) {
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setBillingProviderId(billingProviderId);
    when(subscriptionRepository.find(eq(SubscriptionEntity.class), any()))
        .thenReturn(List.of(subscription));
    when(subscriptionRepository.findOne(any(), any())).thenReturn(Optional.of(subscription));
    return subscription;
  }

  private ContractEntity givenExistingContract() {
    return givenExistingContract(givenContractRequest());
  }

  private ContractEntity givenExistingContractWithExistingMetrics() {
    var request = givenContractRequest();
    var contract = request.getPartnerEntitlement().getPurchase().getContracts().get(0);

    // existing metrics are coming from WireMockResource.stubForRhPartnerApi() method
    var metric1 = new DimensionV1();
    metric1.setName("foobar");
    metric1.setValue("1000000");

    var metric2 = new DimensionV1();
    metric2.setName("cpu-hours");
    metric2.setValue("1000000");
    contract.setDimensions(List.of(metric1, metric2));
    return givenExistingContract(request);
  }

  private ContractEntity givenExistingContract(ContractRequest request) {
    ContractResponse created = contractService.createContract(request);
    return contractRepository.findById(UUID.fromString(created.getContract().getUuid()));
  }

  private ContractRequest givenContractRequest() {
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
    entitlement
        .getEntitlementDates()
        .setStartDate(OffsetDateTime.parse("2023-03-17T12:29:48.569Z"));
    entitlement.getEntitlementDates().setEndDate(OffsetDateTime.parse("2024-03-17T12:29:48.569Z"));
    entitlement.setSourcePartner(ContractSourcePartnerEnum.AWS.getCode());
    rhEntitlement.setSku(SKU);
    purchase.setVendorProductCode("1234567890abcdefghijklmno");
    cloudIdentifiers.setProductCode("1234567890abcdefghijklmno");
    entitlement.setRhAccountId(ORG_ID);
    rhEntitlement.setSubscriptionNumber(SUBSCRIPTION_NUMBER);
    contract.setRedHatSubscriptionNumber(SUBSCRIPTION_NUMBER);

    var metric1 = new DimensionV1();
    metric1.setName("instance-hours");
    metric1.setValue("2");

    var metric2 = new DimensionV1();
    metric2.setName("cpu-hours");
    metric2.setValue("4");

    var saasContract = new SaasContractV1();
    purchase.setContracts(List.of(saasContract));
    saasContract.setDimensions(List.of(metric1, metric2));
    saasContract.setStartDate(entitlement.getEntitlementDates().getStartDate());
    contract.setCloudIdentifiers(cloudIdentifiers);

    ContractRequest contractRequest = new ContractRequest();
    contractRequest.setPartnerEntitlement(entitlement);
    entitlement.setPartnerIdentities(partnerIdentity);
    entitlement.setPurchase(purchase);
    entitlement.setRhEntitlements(List.of(rhEntitlement));
    return contractRequest;
  }

  private void mockSubscriptionServiceSubscription() {
    Subscription subscription = new Subscription();
    subscription.setId(42);
    try {
      when(subscriptionApi.getSubscriptionBySubscriptionNumber(any()))
          .thenReturn(List.of(subscription));
    } catch (ApiException e) {
      fail(e);
    }
  }

  private void mockPartnerApi() throws Exception {
    mockPartnerApi(createPartnerApiResponse());
  }

  private void mockPartnerApi(PartnerEntitlements response) throws Exception {
    stubFor(
        WireMock.any(urlMatching("/mock/partnerApi/v1/partnerSubscriptions"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(objectMapper.writeValueAsString(response))));
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
            .rhEntitlements(List.of(new RhEntitlementV1().sku(SKU).subscriptionNumber("testSubId")))
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
                                .dimensions(List.of(new DimensionV1().name("vCPU").value("4"))))));

    return new PartnerEntitlements().content(List.of(entitlement));
  }

  private PartnerEntitlements createPartnerApiResponseNoContractDimensions() throws Exception {
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
}
