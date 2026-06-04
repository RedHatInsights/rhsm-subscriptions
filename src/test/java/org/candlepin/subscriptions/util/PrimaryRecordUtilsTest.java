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
package org.candlepin.subscriptions.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;

class PrimaryRecordUtilsTest {

  // Using real product tags from the subscription configuration
  private static final String PAYG_PRODUCT = "rhel-for-x86-els-payg";
  private static final String TRADITIONAL_PRODUCT = "rhel-for-x86-els-unconverted";

  @Test
  void testNullSnapshotThrowsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> PrimaryRecordUtils.isPrimaryRecord((TallySnapshot) null));
    assertTrue(exception.getMessage().contains("TallySnapshot cannot be null"));
  }

  @Test
  void testSnapshotWithNullProductIdThrowsException() {
    TallySnapshot snapshot = TallySnapshot.builder().productId(null).build();
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> PrimaryRecordUtils.isPrimaryRecord(snapshot));
    assertTrue(exception.getMessage().contains("productId cannot be null"));
  }

  @Test
  void testProductNotFoundThrowsException() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId("unknown-product")
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .build();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> PrimaryRecordUtils.isPrimaryRecord(snapshot));
    assertTrue(exception.getMessage().contains("unknown-product"));
    assertTrue(exception.getMessage().contains("missing in subscription configuration"));
  }

  @Test
  void testPaygProductWithAllFieldsNotAnyReturnsTrue() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(PAYG_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("123456")
            .build();

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testPaygProductWithSlaAnyReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(PAYG_PRODUCT)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("123456")
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testPaygProductWithUsageAnyReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(PAYG_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage._ANY)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("123456")
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testPaygProductWithBillingProviderAnyReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(PAYG_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider._ANY)
            .billingAccountId("123456")
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testPaygProductWithNullBillingAccountIdReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(PAYG_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId(null)
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testPaygProductWithBillingAccountIdAnyReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(PAYG_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("_ANY")
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testPaygProductWithEmptyStringBillingAccountIdReturnsTrue() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(PAYG_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("")
            .build();

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testTraditionalProductWithSlaAndUsageNotAnyReturnsTrue() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(TRADITIONAL_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider._ANY)
            .billingAccountId("_ANY")
            .build();

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testTraditionalProductWithSlaAnyReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(TRADITIONAL_PRODUCT)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage.PRODUCTION)
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testTraditionalProductWithUsageAnyReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(TRADITIONAL_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage._ANY)
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testTraditionalProductRequiresBillingFieldsToBeAny() {
    // Traditional product is primary when billing fields are _ANY (as required)
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(TRADITIONAL_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider._ANY)
            .billingAccountId("_ANY")
            .build();

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testTraditionalProductWithNonAnyBillingProviderReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(TRADITIONAL_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("_ANY")
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testTraditionalProductWithNonAnyBillingAccountIdReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(TRADITIONAL_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider._ANY)
            .billingAccountId("123456")
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  @Test
  void testTraditionalProductWithNullBillingAccountIdReturnsFalse() {
    TallySnapshot snapshot =
        TallySnapshot.builder()
            .productId(TRADITIONAL_PRODUCT)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider._ANY)
            .billingAccountId(null)
            .build();

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(snapshot));
  }

  // Helper method to create a HostTallyBucket for testing
  private HostTallyBucket createHostTallyBucket(
      String productId,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId) {
    Host host = new Host();
    HostBucketKey key =
        new HostBucketKey(host, productId, sla, usage, billingProvider, billingAccountId, false);
    HostTallyBucket bucket = new HostTallyBucket();
    bucket.setKey(key);
    bucket.setHost(host);
    bucket.setCores(4);
    bucket.setSockets(2);
    bucket.setMeasurementType(HardwareMeasurementType.PHYSICAL);
    return bucket;
  }

  // HostTallyBucket Tests

  @Test
  void testNullBucketThrowsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> PrimaryRecordUtils.isPrimaryRecord((HostTallyBucket) null));
    assertTrue(exception.getMessage().contains("HostTallyBucket cannot be null"));
  }

  @Test
  void testBucketWithNullKeyThrowsException() {
    HostTallyBucket bucket = new HostTallyBucket();
    bucket.setKey(null);
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> PrimaryRecordUtils.isPrimaryRecord(bucket));
    assertTrue(exception.getMessage().contains("key cannot be null"));
  }

  @Test
  void testBucketWithNullProductIdThrowsException() {
    Host host = new Host();
    HostBucketKey key =
        new HostBucketKey(
            host,
            null,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "123456",
            false);
    HostTallyBucket bucket = new HostTallyBucket();
    bucket.setKey(key);
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> PrimaryRecordUtils.isPrimaryRecord(bucket));
    assertTrue(exception.getMessage().contains("productId cannot be null"));
  }

  @Test
  void testBucketProductNotFoundThrowsException() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            "unknown-product",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "123456");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> PrimaryRecordUtils.isPrimaryRecord(bucket));
    assertTrue(exception.getMessage().contains("unknown-product"));
    assertTrue(exception.getMessage().contains("missing in subscription configuration"));
  }

  @Test
  void testPaygBucketWithAllFieldsNotAnyReturnsTrue() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            PAYG_PRODUCT, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider.AWS, "123456");

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testPaygBucketWithSlaAnyReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            PAYG_PRODUCT, ServiceLevel._ANY, Usage.PRODUCTION, BillingProvider.AWS, "123456");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testPaygBucketWithUsageAnyReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            PAYG_PRODUCT, ServiceLevel.PREMIUM, Usage._ANY, BillingProvider.AWS, "123456");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testPaygBucketWithBillingProviderAnyReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            PAYG_PRODUCT, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider._ANY, "123456");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testPaygBucketWithNullBillingAccountIdReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            PAYG_PRODUCT, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider.AWS, null);

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testPaygBucketWithBillingAccountIdAnyReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            PAYG_PRODUCT, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider.AWS, "_ANY");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testPaygBucketWithEmptyStringBillingAccountIdReturnsTrue() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            PAYG_PRODUCT, ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider.AWS, "");

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testTraditionalBucketWithSlaAndUsageNotAnyReturnsTrue() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            TRADITIONAL_PRODUCT,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY");

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testTraditionalBucketWithSlaAnyReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            TRADITIONAL_PRODUCT, ServiceLevel._ANY, Usage.PRODUCTION, BillingProvider._ANY, "_ANY");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testTraditionalBucketWithUsageAnyReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            TRADITIONAL_PRODUCT, ServiceLevel.PREMIUM, Usage._ANY, BillingProvider._ANY, "_ANY");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testTraditionalBucketRequiresBillingFieldsToBeAny() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            TRADITIONAL_PRODUCT,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY");

    assertTrue(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testTraditionalBucketWithNonAnyBillingProviderReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            TRADITIONAL_PRODUCT,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "_ANY");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testTraditionalBucketWithNonAnyBillingAccountIdReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            TRADITIONAL_PRODUCT,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "123456");

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }

  @Test
  void testTraditionalBucketWithNullBillingAccountIdReturnsFalse() {
    HostTallyBucket bucket =
        createHostTallyBucket(
            TRADITIONAL_PRODUCT,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            null);

    assertFalse(PrimaryRecordUtils.isPrimaryRecord(bucket));
  }
}
