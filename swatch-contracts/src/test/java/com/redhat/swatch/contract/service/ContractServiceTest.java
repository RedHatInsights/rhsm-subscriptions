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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.BaseUnitTest;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.Metric;
import com.redhat.swatch.contract.openapi.model.OfferingProductTags;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContractCloudIdentifiers;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.ContractEntity;
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
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractServiceTest extends BaseUnitTest {

  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "BASILISK123";
  private static final String SUBSCRIPTION_NUMBER = "subs123";

  @Inject ContractService contractService;
  @InjectSpy ContractRepository contractRepository;
  @InjectMock OfferingRepository offeringRepository;
  @InjectMock SubscriptionRepository subscriptionRepository;

  @InjectMock SubscriptionSyncService syncService;

  @InjectMock @RestClient SearchApi subscriptionApi;
  @InjectMock MeasurementMetricIdTransformer measurementMetricIdTransformer;

  @Transactional
  @BeforeEach
  public void setup() {
    when(offeringRepository.findById(anyString()))
        .thenAnswer(
            invocation -> {
              Object[] args = invocation.getArguments();
              var offering = new OfferingEntity();
              offering.setSku((String) args[0]);
              return offering;
            });

    contractRepository.deleteAll();
    mockSubscriptionServiceSubscription();
    mockOfferingProductTagsToReturn(List.of("MH123"));
  }

  @Test
  void testSaveContracts() {
    Contract request = givenContractRequest();
    Contract response = contractService.createContract(request);

    ContractEntity entity = contractRepository.findById(UUID.fromString(response.getUuid()));
    assertEquals(request.getSku(), entity.getSku());
    assertEquals(response.getUuid(), entity.getUuid().toString());
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer).translateContractMetricIdsToSubscriptionMetricIds(any());
  }

  @Test
  void testGetContracts() {
    givenExistingContract();
    List<Contract> contractList =
        contractService.getContracts(ORG_ID, PRODUCT_ID, null, null, null, null);
    assertEquals(1, contractList.size());
    assertEquals(2, contractList.get(0).getMetrics().size());
  }

  @Test
  void createPartnerContract_WhenNonNullEntityAndContractNotFoundInDB() {
    var contract = givenPartnerEntitlementContractRequest();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer).translateContractMetricIdsToSubscriptionMetricIds(any());
  }

  @Test
  void createPartnerContract_WhenNullEntityThrowError() {
    mockOfferingProductTagsToReturn(null);
    var contract = givenPartnerEntitlementContractRequest();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Empty value in non-null fields", statusResponse.getMessage());
  }

  @Test
  void whenInvalidPartnerContract_DoNotPersist() {
    var contract = givenPartnerEntitlementContractWithoutProductCode();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Empty value found in UMB message", statusResponse.getMessage());
  }

  @Test
  void createPartnerContract_NotDuplicateContractThenPersist() {
    givenExistingContract();

    PartnerEntitlementContract request = givenPartnerEntitlementContractRequest();

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    assertEquals(
        "Previous contract archived and new contract created", statusResponse.getMessage());
  }

  @Test
  void createPartnerContract_DuplicateContractThenDoNotPersist() {
    PartnerEntitlementContract request = givenPartnerEntitlementContractRequest();
    contractService.createPartnerContract(request);

    StatusResponse statusResponse = contractService.createPartnerContract(request);
    assertEquals("Duplicate record found", statusResponse.getMessage());
  }

  @Test
  void syncContractWithExistingAndNewContracts() {
    givenExistingContract();
    StatusResponse statusResponse = contractService.syncContractByOrgId(ORG_ID);
    assertEquals("Contracts Synced for " + ORG_ID, statusResponse.getMessage());
    // 2 instances of subscription are created, one for the original contract, and one for the
    // update
    verify(subscriptionRepository, times(2)).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer, times(2))
        .translateContractMetricIdsToSubscriptionMetricIds(any());
  }

  @Test
  void syncContractWithEmptyContractsList() {
    StatusResponse statusResponse = contractService.syncContractByOrgId(ORG_ID);
    assertEquals(ORG_ID + " not found in table", statusResponse.getMessage());
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
    cloudIdentifiers.setProductCode("product123");
    contract.setCloudIdentifiers(cloudIdentifiers);
    return contract;
  }

  private SubscriptionEntity givenExistingSubscription() {
    SubscriptionEntity subscription = new SubscriptionEntity();
    when(subscriptionRepository.find(eq(SubscriptionEntity.class), any()))
        .thenReturn(List.of(subscription));
    return subscription;
  }

  private ContractEntity givenExistingContract() {
    Contract created = contractService.createContract(givenContractRequest());
    return contractRepository.findById(UUID.fromString(created.getUuid()));
  }

  private Contract givenContractRequest() {
    Contract contractRequest = new Contract();
    contractRequest.setUuid(UUID.randomUUID().toString());
    contractRequest.setBillingAccountId("billAcct123");
    contractRequest.setStartDate(OffsetDateTime.now());
    contractRequest.setEndDate(OffsetDateTime.now());
    contractRequest.setBillingProvider("test123");
    contractRequest.setSku("BAS123");
    contractRequest.setProductId(PRODUCT_ID);
    contractRequest.setVendorProductCode("product123");
    contractRequest.setOrgId(ORG_ID);
    contractRequest.setSubscriptionNumber(SUBSCRIPTION_NUMBER);

    Metric metric1 = new Metric();
    metric1.setMetricId("instance-hours");
    metric1.setValue(2);

    Metric metric2 = new Metric();
    metric2.setMetricId("cpu-hours");
    metric2.setValue(4);

    contractRequest.setMetrics(List.of(metric1, metric2));
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

  private void mockOfferingProductTagsToReturn(List<String> data) {
    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(data);
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);
  }
}
