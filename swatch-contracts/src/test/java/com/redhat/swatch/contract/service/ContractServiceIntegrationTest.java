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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1EntitlementDates;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PurchaseV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.RhEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.contract.exception.ContractNotAssociatedToOrgException;
import com.redhat.swatch.contract.exception.ContractValidationFailedException;
import com.redhat.swatch.contract.model.ContractSourcePartnerEnum;
import com.redhat.swatch.contract.openapi.model.ContractRequest;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContractCloudIdentifiers;
import com.redhat.swatch.contract.repository.ContractRepository;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.test.resources.PostgresTestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PostgresTestProfile.class)
class ContractServiceIntegrationTest {

  private static final String ORG_ID = "5528";
  private static final String SKU = "RH0005528";
  private static final String PRODUCT_TAG = "rosa";
  private static final String SUBSCRIPTION_NUMBER = "num5528";
  private static final String SUBSCRIPTION_ID = "5528";

  private static final OffsetDateTime DEFAULT_END_DATE =
      OffsetDateTime.now(ZoneOffset.UTC).plusYears(4).truncatedTo(ChronoUnit.MICROS);

  @Inject ContractService contractService;
  @Inject ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;
  @Inject SubscriptionRepository subscriptionRepository;

  @Transactional
  @BeforeEach
  void setUp() {
    subscriptionRepository.deleteAll();
    contractRepository.deleteAll();
    offeringRepository.deleteAll();

    OfferingEntity offering = new OfferingEntity();
    offering.setSku(SKU);
    offering.setProductTags(Set.of(PRODUCT_TAG));
    offering.setMetered(true);
    offeringRepository.persist(offering);
  }

  @Test
  void concurrentUpsertPartnerContractsWithoutSubscriptionNumberShouldLeaveSingleContractRow()
      throws Exception {
    var entitlement = givenAwsEntitlementWithoutSubscriptionNumber();

    runConcurrent(
        () -> upsertPartnerContractsInNewTransaction(entitlement),
        () -> upsertPartnerContractsInNewTransaction(entitlement));

    assertEquals(
        1,
        contractRepository.listAll().size(),
        "Expected a single contract row when subscription number is absent (lock on"
            + " billingProviderId)");
    assertEquals(
        0,
        subscriptionRepository.listAll().size(),
        "Subscription sync is skipped when entitlement has no subscription number");
  }

  @Test
  void concurrentCreateContractCallsShouldLeaveSingleContractAndSubscription() throws Exception {
    var request = givenContractRequest();

    runConcurrent(
        () -> contractService.createContract(request),
        () -> contractService.createContract(request));

    assertEquals(
        1,
        subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER).size(),
        "createContract bypasses advisory lock via CDI self-invocation");
    assertEquals(
        1,
        contractRepository.listAll().size(),
        "Expected a single contract row after concurrent createContract calls");
  }

  private void upsertPartnerContractsInNewTransaction(PartnerEntitlementV1 entitlement) {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              try {
                contractService.upsertPartnerContracts(entitlement, null);
              } catch (ContractNotAssociatedToOrgException | ContractValidationFailedException e) {
                throw new RuntimeException(e);
              }
            });
  }

  private PartnerEntitlementV1 givenAwsEntitlementWithoutSubscriptionNumber() {
    var entitlement = givenContractRequest().getPartnerEntitlement();
    entitlement.getRhEntitlements().getFirst().setSubscriptionNumber(null);
    return entitlement;
  }

  private void runConcurrent(Runnable first, Runnable second) throws InterruptedException {
    var startLatch = new CountDownLatch(1);
    var doneLatch = new CountDownLatch(2);
    var failure = new AtomicReference<Throwable>();

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (Runnable task : List.of(first, second)) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                QuarkusTransaction.requiringNew().run(task);
              } catch (Throwable t) {
                failure.compareAndSet(null, t);
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      assertTrue(
          doneLatch.await(30, TimeUnit.SECONDS), "Concurrent contract upsert did not finish");
    }

    if (failure.get() != null) {
      if (isSubscriptionDuplicateKeyRace(failure.get())) {
        throw new AssertionError(
            "Concurrent createContract calls raced without advisory lock (subscription_pkey"
                + " violation)",
            failure.get());
      }
      throw new AssertionError("Concurrent contract upsert failed", failure.get());
    }
  }

  private static boolean isSubscriptionDuplicateKeyRace(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      var message = current.getMessage();
      if (message != null && message.contains("subscription_pkey")) {
        return true;
      }
    }
    return false;
  }

  private ContractRequest givenContractRequest() {
    var contract = new PartnerEntitlementContract();
    var entitlement = new PartnerEntitlementV1();
    var cloudIdentifiers = new PartnerEntitlementContractCloudIdentifiers();
    var partnerIdentity = new PartnerIdentityV1();
    var rhEntitlement = new RhEntitlementV1();
    var purchase = new PurchaseV1();
    var startDate = OffsetDateTime.parse("2023-03-17T12:29:48.569Z");

    partnerIdentity.setCustomerAwsAccountId("billAcct5528");
    partnerIdentity.setAwsCustomerId("HSwCpt6sqkC");
    partnerIdentity.setSellerAccountId("568056954830");
    entitlement.setEntitlementDates(new PartnerEntitlementV1EntitlementDates());
    entitlement.getEntitlementDates().setStartDate(startDate);
    entitlement.getEntitlementDates().setEndDate(DEFAULT_END_DATE);
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

  private static DimensionV1 controlPlaneDimension(String value) {
    return new DimensionV1().name("control_plane").value(value);
  }

  private static DimensionV1 fourCpuHourDimension(String value) {
    return new DimensionV1().name("four_cpu_hour").value(value);
  }
}
