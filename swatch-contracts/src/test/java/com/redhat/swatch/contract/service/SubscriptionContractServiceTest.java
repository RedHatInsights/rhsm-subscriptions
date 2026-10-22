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

import static com.redhat.swatch.contract.model.MeasurementMetricIdTransformer.MEASUREMENT_TYPE_DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionContractServiceTest {

  private static final String ORG_ID = "org123";
  private static final String OTHER_ORG_ID = "org456";
  private static final String SKU = "MW01485";
  private static final String PRODUCT_TAG = "rosa";
  private static final String VENDOR_PRODUCT_CODE = "6n58d3s3qpvk22dgew2gal7w3";
  private static final String BILLING_ACCOUNT_ID = "123456789101";
  private static final String LICENSE_ID = "arn:aws:license-manager:license";

  private static final String CORES = "Cores";

  private static final String AWS_CORES_DIMENSION = "four_vcpu_hour";
  private static final double CORES_BILLING_FACTOR = 0.25;

  private static final OffsetDateTime START =
      OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime END =
      OffsetDateTime.of(2024, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  @Inject SubscriptionContractService service;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject OfferingRepository offeringRepository;

  @TestTransaction
  @Test
  void testFiltersByOrgId() {
    givenSubscription("sub-1", ORG_ID);
    givenSubscription("sub-2", OTHER_ORG_ID);

    var contracts = whenGetContracts(ORG_ID, null, null, null, null, null);

    assertEquals(1, contracts.size());
    assertEquals("sub-1", contracts.get(0).getSubscriptionNumber());
  }

  @TestTransaction
  @Test
  void testMapsAllContractFields() {
    givenSubscription("sub-1", ORG_ID);

    var contract = whenGetContracts(ORG_ID, null, null, null, null, null).get(0);

    assertNull(contract.getUuid(), "subscriptions have no contract UUID");
    assertEquals("sub-1", contract.getSubscriptionNumber());
    assertEquals(SKU, contract.getSku());
    assertEquals(START, contract.getStartDate());
    assertEquals(END, contract.getEndDate());
    assertEquals(ORG_ID, contract.getOrgId());
    assertEquals(BillingProvider.AWS.getValue(), contract.getBillingProvider());
    assertEquals("billing-provider-id", contract.getBillingProviderId());
    assertEquals(BILLING_ACCOUNT_ID, contract.getBillingAccountId());
    assertEquals(List.of(PRODUCT_TAG), contract.getProductTags());
    assertEquals(VENDOR_PRODUCT_CODE, contract.getVendorProductCode());
    assertEquals(LICENSE_ID, contract.getLicenseId());
  }

  @TestTransaction
  @Test
  void testTranslatesMeasurementsBackToVendorDimensions() {
    // 400 Cores on the subscription corresponds to 100 four_vcpu_hour on the contract
    givenSubscription("sub-1", ORG_ID, 400.0);

    var contract = whenGetContracts(ORG_ID, null, null, null, null, null).get(0);

    assertEquals(1, contract.getMetrics().size());
    var metric = contract.getMetrics().get(0);
    assertEquals(AWS_CORES_DIMENSION, metric.getMetricId());
    assertEquals((int) (400.0 * CORES_BILLING_FACTOR), metric.getValue());
  }

  @TestTransaction
  @Test
  void testSkipsSubscriptionsWithoutBillingProviderId() {
    SubscriptionEntity nonContract = givenSubscription("sub-1", ORG_ID);
    nonContract.setBillingProviderId(null);
    subscriptionRepository.persistAndFlush(nonContract);

    assertTrue(whenGetContracts(ORG_ID, null, null, null, null, null).isEmpty());
  }

  @TestTransaction
  @Test
  void testFiltersByProductTag() {
    givenSubscription("sub-1", ORG_ID);

    assertEquals(1, whenGetContracts(ORG_ID, PRODUCT_TAG, null, null, null, null).size());
    assertTrue(whenGetContracts(ORG_ID, "BASILISK", null, null, null, null).isEmpty());
  }

  @TestTransaction
  @Test
  void testFiltersByBillingProvider() {
    givenSubscription("sub-1", ORG_ID);

    assertEquals(
        1, whenGetContracts(ORG_ID, null, BillingProvider.AWS.getValue(), null, null, null).size());
    assertTrue(
        whenGetContracts(ORG_ID, null, BillingProvider.AZURE.getValue(), null, null, null)
            .isEmpty());
  }

  @TestTransaction
  @Test
  void testFiltersByBillingAccountIdExactly() {
    givenSubscription("sub-1", ORG_ID);

    assertEquals(1, whenGetContracts(ORG_ID, null, null, BILLING_ACCOUNT_ID, null, null).size());
    // the contracts-table mode matches exactly, so a prefix must not match
    assertTrue(whenGetContracts(ORG_ID, null, null, "12345", null, null).isEmpty());
  }

  @TestTransaction
  @Test
  void testFiltersByVendorProductCode() {
    givenSubscription("sub-1", ORG_ID);

    assertEquals(1, whenGetContracts(ORG_ID, null, null, null, VENDOR_PRODUCT_CODE, null).size());
    assertTrue(whenGetContracts(ORG_ID, null, null, null, "other-code", null).isEmpty());
  }

  @TestTransaction
  @Test
  void testFiltersByTimestamp() {
    givenSubscription("sub-1", ORG_ID);

    assertEquals(1, whenGetContracts(ORG_ID, null, null, null, null, START).size());
    assertTrue(whenGetContracts(ORG_ID, null, null, null, null, START.minusDays(1)).isEmpty());
    // the end date is exclusive, matching ContractEntity.activeOn
    assertTrue(whenGetContracts(ORG_ID, null, null, null, null, END).isEmpty());
    assertEquals(1, whenGetContracts(ORG_ID, null, null, null, null, END.minusDays(1)).size());
  }

  @TestTransaction
  @Test
  void testReturnsAllActiveSegmentsOfASubscription() {
    // unlike the subscriptions report queries, the contracts API does not collapse a subscription
    // down to its most recent segment
    givenSubscription("sub-1", ORG_ID, 400.0, START, START.plusMonths(1));
    givenSubscription("sub-1", ORG_ID, 800.0, START.plusMonths(1), END);

    assertEquals(2, whenGetContracts(ORG_ID, null, null, null, null, null).size());
  }

  private List<Contract> whenGetContracts(
      String orgId,
      String productTag,
      String billingProvider,
      String billingAccountId,
      String vendorProductCode,
      OffsetDateTime timestamp) {
    return service.getContracts(
        orgId, productTag, billingProvider, billingAccountId, vendorProductCode, timestamp);
  }

  private SubscriptionEntity givenSubscription(String subscriptionNumber, String orgId) {
    return givenSubscription(subscriptionNumber, orgId, 400.0);
  }

  private SubscriptionEntity givenSubscription(
      String subscriptionNumber, String orgId, double cores) {
    return givenSubscription(subscriptionNumber, orgId, cores, START, END);
  }

  private SubscriptionEntity givenSubscription(
      String subscriptionNumber,
      String orgId,
      double cores,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setSubscriptionNumber(subscriptionNumber);
    subscription.setOrgId(orgId);
    subscription.setOffering(givenOffering());
    subscription.setQuantity(1L);
    subscription.setStartDate(startDate);
    subscription.setEndDate(endDate);
    subscription.setBillingProvider(BillingProvider.AWS);
    subscription.setBillingProviderId("billing-provider-id");
    subscription.setBillingAccountId(BILLING_ACCOUNT_ID);
    subscription.setVendorProductCode(VENDOR_PRODUCT_CODE);
    subscription.setLicenseId(LICENSE_ID);
    subscription.addSubscriptionMeasurement(CORES, MEASUREMENT_TYPE_DEFAULT, cores);
    subscriptionRepository.persistAndFlush(subscription);
    return subscription;
  }

  private OfferingEntity givenOffering() {
    OfferingEntity existing = offeringRepository.findById(SKU);
    if (existing != null) {
      return existing;
    }

    OfferingEntity offering =
        OfferingEntity.builder().sku(SKU).productTags(Set.of(PRODUCT_TAG)).build();
    offeringRepository.persistAndFlush(offering);
    return offering;
  }
}
