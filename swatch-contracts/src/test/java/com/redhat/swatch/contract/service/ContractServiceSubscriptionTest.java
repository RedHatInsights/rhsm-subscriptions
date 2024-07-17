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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.*;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1EntitlementDates;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PurchaseV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.model.ContractSourcePartnerEnum;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.openapi.model.*;
import com.redhat.swatch.contract.repository.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Similar to ContractServiceTest but uses real SubscriptionRepository to verify subscription
 * changes.
 */
@QuarkusTest
class ContractServiceSubscriptionTest {

  private static final String SKU = "RH000000";
  private static final String SUBSCRIPTION_NUMBER = "testSubscriptionNumber123";
  private static final String PRODUCT_TAG = "MH123";
  private static final OffsetDateTime DEFAULT_START_DATE =
      OffsetDateTime.parse("2023-06-09T13:59:43.035365Z");
  private static final OffsetDateTime DEFAULT_END_DATE =
      OffsetDateTime.parse("2026-02-15T13:59:43.035365Z");

  @Inject ContractService contractService;
  @Inject ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;
  @Inject SubscriptionRepository subscriptionRepository;
  @InjectMock @RestClient PartnerApi partnerApi;
  @InjectMock MeasurementMetricIdTransformer measurementMetricIdTransformer;

  @InjectMock @RestClient SearchApi subscriptionApi;

  @Transactional
  @BeforeEach
  public void setup() {
    WireMock.reset();
    contractRepository.deleteAll();
    offeringRepository.deleteAll();
    subscriptionRepository.deleteAll();
    OfferingEntity offering = new OfferingEntity();
    offering.setSku(SKU);
    offering.setProductTags(Set.of(PRODUCT_TAG));
    offeringRepository.persist(offering);
    mockSubscriptionServiceSubscription();
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
    var persistedContract = contracts.stream().findFirst().get();

    assertEquals(999, persistedContract.getMetrics().iterator().next().getValue());

    var subscriptions = subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    var persistedSubscription = subscriptions.stream().findFirst().get();
    assertEquals(
        999, persistedSubscription.getSubscriptionMeasurement("vCPU", "PHYSICAL").get().getValue());
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
    when(partnerApi.getPartnerEntitlements(any())).thenReturn(response);
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
                                .dimensions(List.of(new DimensionV1().name("vCPU").value("4"))))));

    return new PartnerEntitlements().content(List.of(entitlement));
  }
}
