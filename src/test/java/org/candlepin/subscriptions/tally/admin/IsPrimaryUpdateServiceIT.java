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
package org.candlepin.subscriptions.tally.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class IsPrimaryUpdateServiceIT {

  private static final String PRODUCT_PAYG = "rosa";
  private static final String PRODUCT_NON_PAYG = "RHEL for x86";
  private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2026-02-01T00:00:00Z");
  private static final OffsetDateTime IN_RANGE_DATE = OffsetDateTime.parse("2026-01-15T00:00:00Z");

  @Autowired private TallySnapshotRepository repository;
  @Autowired private IsPrimaryUpdateService service;

  @Test
  void testUpdateIsPrimaryForPaygProduct() {
    // Given: Realistic PAYG scenario matching data patterns
    String orgId = randomOrgId();
    createPaygSnapshotTestData(orgId);

    // When: Updating is_primary for PAYG product for time range
    int rowsUpdated = service.updateIsPrimarySync(orgId, PRODUCT_PAYG, START_DATE, END_DATE);

    // Then:
    assertEquals(
        4,
        rowsUpdated,
        "Should update 4 PAYG rows with non-_ANY sla, usage, billing_provider, and billing_account_id");
  }

  @Test
  void testUpdateIsPrimaryForNonPaygProduct() {
    // Given: Standard non-PAYG scenario with 8 rows
    String orgId = randomOrgId();
    createNonPaygSnapshotTestData(orgId);

    // When: Updating is_primary for non-PAYG product
    int rowsUpdated = service.updateIsPrimarySync(orgId, PRODUCT_NON_PAYG, START_DATE, END_DATE);

    // Then: Only 4 non-PAYG rows with non-_ANY sla and usage should be updated
    assertEquals(4, rowsUpdated, "Should update 4 non-PAYG rows with non-_ANY sla and usage");
  }

  @Test
  void testUpdateIsPrimaryDateRangeFiltering() {
    // Given: Rows with different snapshot dates
    String orgId = randomOrgId();
    createPaygSnapshotWithDate(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        OffsetDateTime.parse("2025-12-31T23:59:59Z")); // Before range
    createPaygSnapshotWithDate(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        OffsetDateTime.parse("2026-01-01T00:00:00Z")); // Start of range (inclusive)
    createPaygSnapshotWithDate(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        OffsetDateTime.parse("2026-01-15T12:00:00Z")); // Middle of range
    createPaygSnapshotWithDate(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        OffsetDateTime.parse("2026-02-01T00:00:00Z")); // End of range (exclusive)
    createPaygSnapshotWithDate(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        OffsetDateTime.parse("2026-02-01T00:00:01Z")); // After range

    // When: Updating for date range
    int rowsUpdated = service.updateIsPrimarySync(orgId, PRODUCT_PAYG, START_DATE, END_DATE);

    // Then: Should only update rows within [START_DATE, END_DATE)
    assertEquals(
        2, rowsUpdated, "Should update 2 rows: start boundary (inclusive) and middle of range");
  }

  @Test
  void testUpdateIsPrimaryMixedScenarioPaygAndNonPayg() {
    // Given: Both PAYG and non-PAYG scenarios in same org
    String orgId = randomOrgId();

    // Setup PAYG scenario (8 rows, 3 should update)
    createPaygSnapshot(orgId, PRODUCT_PAYG, ServiceLevel.PREMIUM, Usage.PRODUCTION);
    createPaygSnapshot(orgId, PRODUCT_PAYG, ServiceLevel.STANDARD, Usage.DEVELOPMENT_TEST);
    createPaygSnapshot(orgId, PRODUCT_PAYG, ServiceLevel._ANY, Usage.PRODUCTION);
    createPaygSnapshot(orgId, PRODUCT_PAYG, ServiceLevel.PREMIUM, Usage._ANY);
    createPaygSnapshot(orgId, PRODUCT_PAYG, ServiceLevel._ANY, Usage._ANY);
    createNonPaygSnapshot(orgId, PRODUCT_PAYG, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    // Setup non-PAYG scenario (8 rows, 3 should update)
    createNonPaygSnapshot(orgId, PRODUCT_NON_PAYG, ServiceLevel.PREMIUM, Usage.PRODUCTION);
    createNonPaygSnapshot(orgId, PRODUCT_NON_PAYG, ServiceLevel.STANDARD, Usage.DEVELOPMENT_TEST);
    createNonPaygSnapshot(orgId, PRODUCT_NON_PAYG, ServiceLevel._ANY, Usage.PRODUCTION);
    createNonPaygSnapshot(orgId, PRODUCT_NON_PAYG, ServiceLevel.PREMIUM, Usage._ANY);
    createNonPaygSnapshot(orgId, PRODUCT_NON_PAYG, ServiceLevel._ANY, Usage._ANY);
    createPaygSnapshot(orgId, PRODUCT_NON_PAYG, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    // When: Updating PAYG product
    int paygRowsUpdated = service.updateIsPrimarySync(orgId, PRODUCT_PAYG, START_DATE, END_DATE);
    assertEquals(2, paygRowsUpdated, "PAYG product should update 2 PAYG rows");
  }

  @Test
  void testUpdateIsPrimaryForUnknownProduct() {
    // Given: Unknown product defaults to non-PAYG behavior
    String orgId = randomOrgId();
    createNonPaygSnapshot(
        orgId, "unknown-product", ServiceLevel.PREMIUM, Usage.PRODUCTION); // Should update
    createPaygSnapshot(
        orgId, "unknown-product", ServiceLevel.PREMIUM, Usage.PRODUCTION); // Should NOT

    // When: Updating unknown product (defaults to non-PAYG)
    int rowsUpdated = service.updateIsPrimarySync(orgId, "unknown-product", START_DATE, END_DATE);

    // Then: Should update non-PAYG rows only
    assertEquals(1, rowsUpdated, "Unknown product should default to non-PAYG and update 1 row");
  }

  // Setup methods for common test scenarios
  private String randomOrgId() {
    return "org-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /**
   * Creates realistic PAYG test data matching patterns: 48 rows total - 24 DAILY rows (only 2
   * should be updated) - 24 HOURLY rows (only 2 should be updated)
   *
   * <p>Rows that should be updated have ALL non-_ANY values for: sla, usage, billing_provider, and
   * billing_account_id
   */
  private void createPaygSnapshotTestData(String orgId) {
    OffsetDateTime snapshotDate = IN_RANGE_DATE;

    // DAILY rows - First 2 should be updated, rest should NOT
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.DAILY);

    // HOURLY rows - First 2 should be updated, rest should NOT
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider.AWS,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider.AWS,
        "521760247103",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
    createSnapshot(
        orgId,
        PRODUCT_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider.AWS,
        "117622437410",
        snapshotDate,
        Granularity.HOURLY);
  }

  /**
   * Creates non-PAYG test data: 9 DAILY rows total - 4 rows that SHOULD be updated (non-_ANY sla
   * and usage with _ANY billing) - 5 rows that should NOT be updated (have _ANY sla or usage, or
   * wrong billing provider)
   */
  private void createNonPaygSnapshotTestData(String orgId) {
    OffsetDateTime snapshotDate = IN_RANGE_DATE;

    // Valid non-PAYG rows - should be updated (4 rows)
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel.STANDARD,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel.PREMIUM,
        Usage.DEVELOPMENT_TEST,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel.EMPTY,
        Usage.EMPTY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);

    // Invalid rows - sla or usage is _ANY (3 rows)
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel._ANY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel.PREMIUM,
        Usage._ANY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);

    // Invalid rows
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel._ANY,
        Usage._ANY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
    createSnapshot(
        orgId,
        PRODUCT_NON_PAYG,
        ServiceLevel._ANY,
        Usage.EMPTY,
        BillingProvider._ANY,
        "_ANY",
        snapshotDate,
        Granularity.DAILY);
  }

  // Helper methods to create test data
  private void createPaygSnapshot(String orgId, String productId, ServiceLevel sla, Usage usage) {
    createSnapshotWithDate(
        orgId, productId, sla, usage, BillingProvider.AWS, "aws-account-123", IN_RANGE_DATE);
  }

  private void createNonPaygSnapshot(
      String orgId, String productId, ServiceLevel sla, Usage usage) {
    createSnapshotWithDate(
        orgId, productId, sla, usage, BillingProvider._ANY, "_ANY", IN_RANGE_DATE);
  }

  private void createPaygSnapshotWithDate(
      String orgId, String productId, ServiceLevel sla, Usage usage, OffsetDateTime date) {
    createSnapshotWithDate(
        orgId, productId, sla, usage, BillingProvider.AWS, "aws-account-123", date);
  }

  private void createSnapshot(
      String orgId,
      String productId,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime snapshotDate,
      Granularity granularity) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setOrgId(orgId);
    snapshot.setProductId(productId);
    snapshot.setServiceLevel(sla);
    snapshot.setUsage(usage);
    snapshot.setBillingProvider(billingProvider);
    snapshot.setBillingAccountId(billingAccountId);
    snapshot.setSnapshotDate(snapshotDate);
    snapshot.setGranularity(granularity);
    snapshot.setPrimary(false);
    repository.saveAndFlush(snapshot);
  }

  private void createSnapshotWithDate(
      String orgId,
      String productId,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime snapshotDate) {
    createSnapshot(
        orgId,
        productId,
        sla,
        usage,
        billingProvider,
        billingAccountId,
        snapshotDate,
        Granularity.DAILY);
  }
}
