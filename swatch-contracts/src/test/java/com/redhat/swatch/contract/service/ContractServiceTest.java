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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.BaseUnitTest;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.openapi.model.*;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.ContractRepository;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import java.time.OffsetDateTime;
import java.util.*;
import javax.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractServiceTest extends BaseUnitTest {
  @Inject ContractService contractService;
  @InjectMock ContractRepository contractRepository;
  @InjectMock OfferingRepository offeringRepository;
  @InjectMock SubscriptionRepository subscriptionRepository;

  @InjectMock SubscriptionSyncService syncService;

  @InjectMock @RestClient SearchApi subscriptionApi;
  @InjectMock MeasurementMetricIdTransformer measurementMetricIdTransformer;
  ContractEntity actualContract1;

  Contract contractDto;

  @Captor ArgumentCaptor<ContractEntity> contractArgumentCaptor;

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
  }

  @BeforeAll
  public void setupTestData() {
    actualContract1 = new ContractEntity();
    var uuid = UUID.randomUUID();
    actualContract1.setUuid(uuid);
    actualContract1.setBillingAccountId("billAcct123");
    actualContract1.setStartDate(OffsetDateTime.now());
    actualContract1.setEndDate(OffsetDateTime.now());
    actualContract1.setBillingProvider("test123");
    actualContract1.setVendorProductCode("product123");
    actualContract1.setSku("BAS123");
    actualContract1.setProductId("BASILISK123");
    actualContract1.setOrgId("org123");
    actualContract1.setLastUpdated(OffsetDateTime.now());
    actualContract1.setSubscriptionNumber("test");

    ContractMetricEntity contractMetric1 = new ContractMetricEntity();
    contractMetric1.setContractUuid(uuid);
    contractMetric1.setMetricId("cpu-hours");
    contractMetric1.setValue(2);

    ContractMetricEntity contractMetric2 = new ContractMetricEntity();
    contractMetric2.setContractUuid(uuid);
    contractMetric2.setMetricId("instance-hours");
    contractMetric2.setValue(4);

    actualContract1.setMetrics(Set.of(contractMetric1, contractMetric2));

    contractDto = new Contract();
    contractDto.setUuid(uuid.toString());
    contractDto.setBillingAccountId("billAcct123");
    contractDto.setStartDate(OffsetDateTime.now());
    contractDto.setEndDate(OffsetDateTime.now());
    contractDto.setBillingProvider("test123");
    contractDto.setSku("BAS123");
    contractDto.setProductId("BASILISK123");
    contractDto.setVendorProductCode("product123");
    contractDto.setOrgId("org123");
    contractDto.setSubscriptionNumber("test");

    Metric metric1 = new Metric();
    metric1.setMetricId("instance-hours");
    metric1.setValue(2);

    Metric metric2 = new Metric();
    metric2.setMetricId("cpu-hours");
    metric2.setValue(4);

    contractDto.setMetrics(List.of(metric1, metric2));
  }

  @Test
  void testSaveContracts() {
    doNothing().when(contractRepository).persist(any(ContractEntity.class));
    Contract contractResponse = contractService.createContract(contractDto);
    verify(contractRepository, times(1)).persist(contractArgumentCaptor.capture());
    ContractEntity contract = contractArgumentCaptor.getValue();
    assertEquals(contractDto.getSku(), contract.getSku());
    assertEquals(contractResponse.getUuid(), contract.getUuid().toString());
  }

  @Test
  void testGetContracts() {
    when(contractRepository.getContracts(any())).thenReturn((List.of(actualContract1)));
    var spec =
        ContractEntity.orgIdEquals("org123").and(ContractEntity.productIdEquals("BASILISK123"));
    List<Contract> contractList =
        contractService.getContracts("org123", "BASILISK123", null, null, null, null);
    verify(contractRepository).getContracts(any());
    assertEquals(1, contractList.size());
    assertEquals(2, contractList.get(0).getMetrics().size());
  }

  @Test
  void createPartnerContract_WhenNonNullEntityAndContractNotFoundInDB() throws ApiException {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("12400374");

    PartnerEntitlementContractCloudIdentifiers cloudIdentifiers =
        new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers.setAwsCustomerId("HSwCpt6sqkC");
    cloudIdentifiers.setAwsCustomerAccountId("568056954830");
    cloudIdentifiers.setProductCode("product123");
    contract.setCloudIdentifiers(cloudIdentifiers);

    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(List.of("MH123"));
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);
    mockSubscriptionServiceSubscription();
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("New contract created", statusResponse.getMessage());
  }

  @Test
  void createPartnerContract_WhenNullEntityThrowError() {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("12400374");

    PartnerEntitlementContractCloudIdentifiers cloudIdentifiers =
        new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers.setAwsCustomerId("HSwCpt6sqkC");
    cloudIdentifiers.setAwsCustomerAccountId("568056954830");
    cloudIdentifiers.setProductCode("product123");
    contract.setCloudIdentifiers(cloudIdentifiers);

    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(null);
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Empty value in non-null fields", statusResponse.getMessage());
  }

  @Test
  void whenInvalidPartnerContract_DoNotPersist() {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("12400374");

    PartnerEntitlementContractCloudIdentifiers cloudIdentifiers =
        new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers.setAwsCustomerId("HSwCpt6sqkC");
    cloudIdentifiers.setAwsCustomerAccountId("568056954830");
    contract.setCloudIdentifiers(cloudIdentifiers);

    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(null);
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);
    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Empty value found in UMB message", statusResponse.getMessage());
  }

  @Test
  void createPartnerContract_NotDuplicateContractThenPersist() throws ApiException {
    ContractEntity incomingContract = new ContractEntity();
    var uuid = UUID.randomUUID();
    OffsetDateTime offsetDateTime = OffsetDateTime.now();

    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("12400374");

    PartnerEntitlementContractCloudIdentifiers cloudIdentifiers =
        new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers.setAwsCustomerId("HSwCpt6sqkC");
    cloudIdentifiers.setAwsCustomerAccountId("568056954830");
    cloudIdentifiers.setProductCode("product123");
    contract.setCloudIdentifiers(cloudIdentifiers);

    ContractEntity existingContract = new ContractEntity();
    existingContract.setUuid(uuid);
    existingContract.setBillingAccountId("568056954830");
    existingContract.setStartDate(offsetDateTime);
    existingContract.setEndDate(null);
    existingContract.setBillingProvider("aws");
    existingContract.setSku("RH000000");
    existingContract.setProductId("BASILISK123");
    existingContract.setVendorProductCode("product123");
    existingContract.setOrgId("org123");
    existingContract.setLastUpdated(offsetDateTime);
    existingContract.setSubscriptionNumber("12400374");

    ContractMetricEntity contractMetric4 = new ContractMetricEntity();
    contractMetric4.setContractUuid(uuid);
    contractMetric4.setMetricId("test_dim_1");
    contractMetric4.setValue(5);

    existingContract.setMetrics(Set.of(contractMetric4));

    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(List.of("MH123"));
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);

    when(contractRepository.getContracts(any())).thenReturn(List.of(existingContract));

    mockSubscriptionServiceSubscription();

    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals(
        "Previous contract archived and new contract created", statusResponse.getMessage());
  }

  private void mockSubscriptionServiceSubscription() throws ApiException {
    Subscription subscription = new Subscription();
    subscription.setId(42);
    when(subscriptionApi.getSubscriptionBySubscriptionNumber(any()))
        .thenReturn(List.of(subscription));
  }

  @Test
  void createPartnerContract_DuplicateContractThenDoNotPersist() throws ApiException {
    ContractEntity incomingContract = new ContractEntity();
    var uuid = UUID.randomUUID();
    OffsetDateTime offsetDateTime = OffsetDateTime.now();

    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("12400374");

    PartnerEntitlementContractCloudIdentifiers cloudIdentifiers =
        new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers.setAwsCustomerId("HSwCpt6sqkC");
    cloudIdentifiers.setAwsCustomerAccountId("568056954830");
    cloudIdentifiers.setProductCode("product123");
    contract.setCloudIdentifiers(cloudIdentifiers);

    ContractEntity existingContract = new ContractEntity();
    existingContract.setUuid(uuid);
    existingContract.setBillingAccountId("568056954830");
    existingContract.setStartDate(offsetDateTime);
    existingContract.setEndDate(null);
    existingContract.setBillingProvider("aws");
    existingContract.setSku("RH000000");
    existingContract.setProductId("BASILISK123");
    existingContract.setVendorProductCode("product123");
    existingContract.setOrgId("org123");
    existingContract.setLastUpdated(offsetDateTime);
    existingContract.setSubscriptionNumber("12400374");

    ContractMetricEntity contractMetric4 = new ContractMetricEntity();
    contractMetric4.setContractUuid(uuid);
    contractMetric4.setMetricId("foobar");
    contractMetric4.setValue(1000000);

    ContractMetricEntity contractMetric5 = new ContractMetricEntity();
    contractMetric5.setContractUuid(uuid);
    contractMetric5.setMetricId("cpu-hours");
    contractMetric5.setValue(1000000);

    existingContract.setMetrics(Set.of(contractMetric4, contractMetric5));

    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(List.of("BASILISK123"));
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);
    mockSubscriptionServiceSubscription();
    when(contractRepository.getContracts(any())).thenReturn(List.of(existingContract));

    StatusResponse statusResponse = contractService.createPartnerContract(contract);
    assertEquals("Duplicate record found", statusResponse.getMessage());
  }

  @Test
  void syncContractWIthExistingAndNewContracts() throws ApiException {
    var updateContract = new ContractEntity();
    updateContract.setUuid(UUID.randomUUID());
    updateContract.setOrgId("org123");
    updateContract.setSubscriptionNumber("123456");
    updateContract.setBillingProvider("redhat_fake");
    updateContract.setBillingAccountId("896801664647");
    updateContract.setStartDate(OffsetDateTime.now().minusDays(2));
    updateContract.setEndDate(OffsetDateTime.now());
    updateContract.setProductId("BASILISK123");
    updateContract.setSku("MW01484");
    when(contractRepository.getContracts(any()))
        .thenReturn(List.of(actualContract1, updateContract));
    when(contractRepository.findContract(any())).thenReturn(updateContract);

    // mock sync call for updating contracts
    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(List.of("BASILISK123"));
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);
    mockSubscriptionServiceSubscription();

    StatusResponse statusResponse = contractService.syncContractByOrgId(updateContract.getOrgId());
    assertEquals("Contracts Synced for " + updateContract.getOrgId(), statusResponse.getMessage());
  }

  @Test
  void syncContractWIthEmptyContractsList() {
    var updateContract = new ContractEntity();
    updateContract.setUuid(UUID.randomUUID());
    updateContract.setOrgId("org123");
    updateContract.setSubscriptionNumber("123456");
    updateContract.setBillingProvider("redhat_fake");
    updateContract.setBillingAccountId("896801664647");
    updateContract.setStartDate(OffsetDateTime.now().minusDays(2));
    updateContract.setEndDate(OffsetDateTime.now());
    updateContract.setProductId("BASILISK123");
    updateContract.setVendorProductCode("1234567890abcdefghijklmno");
    updateContract.setSku("MW01484");
    when(contractRepository.getContracts(any())).thenReturn(Collections.emptyList());
    when(contractRepository.findContract(any())).thenReturn(updateContract);

    // mock sync call for updating contracts
    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(List.of("BASILISK123"));
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);

    StatusResponse statusResponse = contractService.syncContractByOrgId(updateContract.getOrgId());
    assertEquals(updateContract.getOrgId() + " not found in table", statusResponse.getMessage());
  }

  @Test
  void testCreateContractCreatesSubscription() {
    contractService.createContract(contractDto);
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer).translateContractMetricIdsToSubscriptionMetricIds(any());
  }

  @Test
  void testCreatePartnerContractCreatesSubscription() throws ApiException {
    var contract = new PartnerEntitlementContract();
    contract.setRedHatSubscriptionNumber("subnum");
    contract.setCurrentDimensions(
        List.of(new Dimension().dimensionName("name").dimensionValue("value")));
    contract.setCloudIdentifiers(
        new PartnerEntitlementContractCloudIdentifiers()
            .awsCustomerAccountId("foo")
            .awsCustomerId("bar")
            .productCode("foobar"));
    mockSubscriptionServiceSubscription();
    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(List.of("MH123"));
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);

    contractService.createPartnerContract(contract);

    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer).translateContractMetricIdsToSubscriptionMetricIds(any());
  }

  @Test
  void testDeleteContractDeletesSubscription() {
    UUID uuid = UUID.randomUUID();
    ContractEntity contract = new ContractEntity();
    when(contractRepository.findContract(uuid)).thenReturn(contract);
    SubscriptionEntity subscription = new SubscriptionEntity();
    when(subscriptionRepository.find(eq(SubscriptionEntity.class), any()))
        .thenReturn(List.of(subscription));
    contractService.deleteContract(uuid.toString());
    verify(subscriptionRepository).delete(subscription);
    verify(contractRepository).delete(contract);
  }

  @Test
  void testDeleteContractNoopWhenMissing() {
    UUID uuid = UUID.randomUUID();
    when(contractRepository.findContract(uuid)).thenReturn(null);
    when(subscriptionRepository.find(eq(SubscriptionEntity.class), any())).thenReturn(List.of());
    contractService.deleteContract(uuid.toString());
    verify(subscriptionRepository, times(0)).delete(any());
    verify(contractRepository, times(0)).delete(any());
  }

  @Test
  void testSyncContractByOrgIdCreatesSubscriptions() throws ApiException {
    var updateContract = new ContractEntity();
    updateContract.setUuid(UUID.randomUUID());
    updateContract.setOrgId("org123");
    updateContract.setSubscriptionNumber("123456");
    updateContract.setBillingProvider("redhat_fake");
    updateContract.setBillingAccountId("896801664647");
    updateContract.setStartDate(OffsetDateTime.now().minusDays(2));
    updateContract.setEndDate(OffsetDateTime.now());
    updateContract.setProductId("BASILISK123");
    updateContract.setSku("MW01484");
    when(contractRepository.getContracts(any()))
        .thenReturn(List.of(actualContract1, updateContract));
    when(contractRepository.findContract(any())).thenReturn(updateContract);

    // mock sync call for updating contracts
    OfferingProductTags productTags = new OfferingProductTags();
    productTags.data(List.of("BASILISK123"));
    when(syncService.getOfferingProductTags(any())).thenReturn(productTags);
    mockSubscriptionServiceSubscription();

    contractService.syncContractByOrgId("org123");
    // 2 instances of subscription are created, one for the original contract, and one for the
    // update
    verify(subscriptionRepository, times(2)).persist(any(SubscriptionEntity.class));
    verify(measurementMetricIdTransformer, times(2))
        .translateContractMetricIdsToSubscriptionMetricIds(any());
  }
}
