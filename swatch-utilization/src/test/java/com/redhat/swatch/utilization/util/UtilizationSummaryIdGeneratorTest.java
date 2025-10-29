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
package com.redhat.swatch.utilization.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UtilizationSummaryIdGeneratorTest {

  private static final ProductId RHEL = ProductId.fromString("RHEL for x86");
  private static final ProductId ROSA = ProductId.fromString("rosa");
  private static final Measurement SOCKETS =
      new Measurement().withMetricId(MetricIdUtils.getSockets().getValue());
  private static final Measurement CORES =
      new Measurement().withMetricId(MetricIdUtils.getCores().getValue());

  private final UtilizationSummaryIdGenerator generator = new UtilizationSummaryIdGenerator();

  @Test
  void testGenerateNonMeteredId() {
    UtilizationSummary summary = givenUtilizationUsingNonPaygProduct();

    UUID result = whenGenerateId(summary);

    assertNotNull(result);
  }

  @Test
  void testSameNonMeteredData_generatesSameUuidV5() {
    UtilizationSummary summary1 = givenUtilizationUsingNonPaygProduct();
    UtilizationSummary summary2 = givenUtilizationUsingNonPaygProduct();

    UUID result1 = whenGenerateId(summary1);
    UUID result2 = whenGenerateId(summary2);

    assertNotNull(result1);
    assertNotNull(result2);
    assertEquals(result1, result2, "Same inputs should produce the same UUID");
  }

  @Test
  void testDifferentNonMeteredOrg_generatesDifferentUuids() {
    UtilizationSummary summary1 =
        new UtilizationSummary().withOrgId("org123").withProductId(RHEL.getValue());

    UtilizationSummary summary2 =
        new UtilizationSummary().withOrgId("org456").withProductId(RHEL.getValue());

    UUID result1 = whenGenerateId(summary1);
    UUID result2 = whenGenerateId(summary2);

    assertNotEquals(result1, result2, "Different orgIds should produce different UUIDs");
  }

  @Test
  void testDifferentNonMeteredMetricIds_generatesDifferentUuids() {
    UtilizationSummary summary1 = givenUtilizationUsingNonPaygProduct();
    UtilizationSummary summary2 = givenUtilizationUsingNonPaygProduct();

    UUID result1 = generator.generateId(summary1, SOCKETS);
    UUID result2 = generator.generateId(summary2, CORES);

    assertNotEquals(result1, result2, "Different metricIds should produce different UUIDs");
  }

  @Test
  void testNonMeteredNullOrg_throwsNPE() {
    UtilizationSummary summary =
        new UtilizationSummary().withOrgId(null).withProductId(RHEL.getValue());

    assertThrows(
        NullPointerException.class,
        () -> generator.generateId(summary, SOCKETS),
        "Should throw NullPointerException when orgId is null");
  }

  @Test
  void testNonMeteredNullProductId_throwsNPE() {
    UtilizationSummary summary = new UtilizationSummary().withOrgId("orgId").withProductId(null);

    assertThrows(
        NullPointerException.class,
        () -> generator.generateId(summary, SOCKETS),
        "Should throw NullPointerException when productId is null");
  }

  @Test
  void testNonMeteredNullMetricId_throwsNPE() {
    UtilizationSummary summary = givenUtilizationUsingNonPaygProduct();

    assertThrows(
        NullPointerException.class,
        () -> generator.generateId(summary, null),
        "Should throw NullPointerException when metricId is null");
  }

  @Test
  void testGeneratesMeteredId() {
    UtilizationSummary summary = givenUtilizationUsingPaygProduct("billing-account-456");
    ;

    UUID result = whenGenerateId(summary);

    assertNotNull(result);
  }

  @Test
  void testSameMeteredData_producesSameUuid() {
    UtilizationSummary summary1 = givenUtilizationUsingPaygProduct("billing-account-456");
    ;
    UtilizationSummary summary2 = givenUtilizationUsingPaygProduct("billing-account-456");
    ;

    UUID result1 = whenGenerateId(summary1);
    UUID result2 = whenGenerateId(summary2);

    assertEquals(result1, result2, "Same PAYG inputs should produce the same UUID");
  }

  @Test
  void testMeteredIdAndNonPayg_produceDifferentUuids() {
    UtilizationSummary paygSummary = givenUtilizationUsingPaygProduct("billing-account-456");
    UtilizationSummary nonPaygSummary = givenUtilizationUsingNonPaygProduct();

    UUID paygResult = whenGenerateId(paygSummary);
    UUID nonPaygResult = whenGenerateId(nonPaygSummary);

    assertNotEquals(paygResult, nonPaygResult, "PAYG and non PAYG should produce different UUIDs");
  }

  @Test
  void testDifferentMeteredBillingAccounts_generatesDifferentUuids() {
    UtilizationSummary summary1 = givenUtilizationUsingPaygProduct("billing-account-456");
    UtilizationSummary summary2 = givenUtilizationUsingPaygProduct("billing-account-789");

    UUID result1 = whenGenerateId(summary1);
    UUID result2 = whenGenerateId(summary2);

    assertNotEquals(
        result1, result2, "Different billing account IDs should produce different UUIDs");
  }

  @Test
  void testMeteredIdNullBillingAccount_throwsNPE() {
    UtilizationSummary summary = givenUtilizationUsingPaygProduct(null);

    assertThrows(
        NullPointerException.class,
        () -> whenGenerateId(summary),
        "Should throw NullPointerException when billingAccountId is null");
  }

  @Test
  void testMeteredId_withEmptyBillingAccountId() {
    UtilizationSummary summary = givenUtilizationUsingPaygProduct("");
    UUID result = whenGenerateId(summary);
    assertNotNull(result);
  }

  private UtilizationSummary givenUtilizationUsingPaygProduct(String billingAccountId) {
    return new UtilizationSummary()
        .withOrgId("org123")
        .withProductId(ROSA.getValue())
        .withBillingAccountId(billingAccountId);
  }

  private UtilizationSummary givenUtilizationUsingNonPaygProduct() {
    return new UtilizationSummary().withOrgId("org123").withProductId(RHEL.getValue());
  }

  private UUID whenGenerateId(UtilizationSummary utilization) {
    return generator.generateId(utilization, SOCKETS);
  }
}
