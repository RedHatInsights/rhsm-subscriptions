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
package com.redhat.swatch.billable.usage.admin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceFilter;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceErrorCode;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InternalBillableUsageControllerTest {

  private static final String PRODUCT_ID = "rosa";

  @Inject BillableUsageRemittanceRepository remittanceRepo;
  @Inject ApplicationClock clock;
  @Inject InternalBillableUsageController controller;

  @Transactional
  @BeforeEach
  void setup() {
    remittanceRepo.deleteAll();
    BillableUsageRemittanceEntity remittance1 =
        remittance(
            "111",
            "product1",
            BillableUsage.BillingProvider.AWS,
            24.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.SUCCEEDED);
    BillableUsageRemittanceEntity remittance2 =
        remittance(
            "org123",
            "product1",
            BillableUsage.BillingProvider.AWS,
            12.0,
            clock.endOfCurrentQuarter(),
            RemittanceStatus.PENDING);
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org123",
            "product1",
            BillableUsage.BillingProvider.RED_HAT,
            12.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    remittance3.setMetricId("Transfer-gibibytes");
    BillableUsageRemittanceEntity remittance4 =
        remittance(
            "org345",
            "product2",
            BillableUsage.BillingProvider.RED_HAT,
            8.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    BillableUsageRemittanceEntity remittance5 =
        remittance(
            "org345",
            "product3",
            BillableUsage.BillingProvider.AZURE,
            4.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.FAILED);
    BillableUsageRemittanceEntity remittance6 =
        remittance(
            "1234",
            "rosa",
            BillableUsage.BillingProvider.AWS,
            24.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    BillableUsageRemittanceEntity remittance7 =
        remittance(
            "5678",
            "rosa",
            BillableUsage.BillingProvider.AWS,
            24.0,
            clock.startOfCurrentMonth(),
            RemittanceStatus.PENDING);
    remittanceRepo.persist(
        List.of(
            remittance1,
            remittance2,
            remittance3,
            remittance4,
            remittance5,
            remittance6,
            remittance7));
    remittanceRepo.flush();
  }

  @Transactional
  @AfterEach
  public void tearDown() {
    remittanceRepo.deleteAll();
  }

  @Test
  void ifAccountNotFoundDisplayEmptyAccountRemittance() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("not_found")
                .productId("product1")
                .build());
    assertFalse(response.isEmpty());
    assertEquals(0.0, response.get(0).getRemittedValue());
  }

  @Test
  void testFilterByOrgId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder().productId("product1").orgId("111").build());
    assertFalse(response.isEmpty());
    assertEquals(24.0, response.get(0).getRemittedValue());
    assertEquals("Instance-hours", response.get(0).getMetricId());
    assertEquals(BillableUsage.Status.SUCCEEDED.value(), response.get(0).getRemittanceStatus());
  }

  @Test
  void testFilterByAccountAndProduct() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder().orgId("org123").productId("product1").build());
    assertFalse(response.isEmpty());
    assertEquals(2, response.size());
    assertEquals(24.0, response.get(0).getRemittedValue() + response.get(1).getRemittedValue());
    assertEquals(BillableUsage.Status.PENDING.value(), response.get(0).getRemittanceStatus());
  }

  @Test
  void testFilterByAccountAndProductAndMetricId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org123")
                .productId("product1")
                .metricId("Transfer-gibibytes")
                .build());

    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    assertEquals(12.0, response.get(0).getRemittedValue());
    assertEquals("Transfer-gibibytes", response.get(0).getMetricId());
  }

  @Test
  void testFilterByOrgIdAndProductAndMetricId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org123")
                .productId("product1")
                .metricId("Instance-hours")
                .build());
    assertEquals(1, response.size());
    MonthlyRemittance result = response.get(0);
    assertEquals("product1", result.getProductId());
    assertEquals("org123", result.getOrgId());
    assertEquals("Instance-hours", result.getMetricId());
    assertEquals(12, result.getRemittedValue());
    assertEquals(BillableUsage.BillingProvider.AWS.value(), result.getBillingProvider());
  }

  @Test
  void testAccountAndOrgIdShouldReturnEmpty() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .productId("product1")
                .metricId("Instance-hours")
                .build());
    assertTrue(response.isEmpty());
  }

  @Test
  void testFilterByBillingProviderAndOrgId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org123")
                .billingProvider(BillableUsage.BillingProvider.RED_HAT.value())
                .build());
    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    MonthlyRemittance result = response.get(0);
    assertEquals(BillableUsage.BillingProvider.RED_HAT.value(), result.getBillingProvider());
    assertEquals("org123", result.getOrgId());
    assertEquals(12, result.getRemittedValue());
  }

  @Test
  void testFilterByBillingAccountIdAndOrgId() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder()
                .orgId("org345")
                .billingAccountId("org345_product3_ba")
                .build());
    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    MonthlyRemittance result = response.get(0);
    assertEquals("org345_product3_ba", result.getBillingAccountId());
    assertEquals("org345", result.getOrgId());
    assertEquals(BillableUsage.BillingProvider.AZURE.value(), result.getBillingProvider());
    assertEquals(4, result.getRemittedValue());
    assertEquals(BillableUsage.Status.FAILED.value(), result.getRemittanceStatus());
  }

  @Test
  void testResetRemittanceValueForCriteria() {
    int remittancePresent =
        controller.resetBillableUsageRemittance(
            "rosa",
            clock.startOfCurrentMonth().minusDays(1),
            clock.startOfCurrentMonth().plusDays(1),
            Set.of("1234", "5678"),
            null);
    int remittanceNotPresent =
        controller.resetBillableUsageRemittance(
            "rosa",
            clock.startOfCurrentMonth().plusDays(1),
            clock.startOfCurrentMonth().plusDays(2),
            Set.of("1234"),
            null);
    assertEquals(2, remittancePresent);
    assertEquals(0, remittanceNotPresent);
  }

  @Transactional
  @Test
  void testDeleteDataForOrg() {
    givenRemittanceForOrgId("org1");
    givenRemittanceForOrgId("org2");
    controller.deleteDataForOrg("org1");
    var remittances = remittanceRepo.listAll();
    assertFalse(remittances.stream().anyMatch(r -> r.getOrgId().equals("org1")));
    assertTrue(remittances.stream().anyMatch(r -> r.getOrgId().equals("org2")));
  }

  @Transactional
  @Test
  void testReconcileBillableUsageRemittances() {
    remittanceRepo.deleteAll();

    int inProgressCount = 5;
    setupRemittances(inProgressCount, "org-", RemittanceStatus.IN_PROGRESS);

    int sentCount = 3;
    setupRemittances(sentCount, "org-sent-", RemittanceStatus.SENT);

    remittanceRepo.flush();
    String updateUpdatedAtQuery =
        "UPDATE billable_usage_remittance SET updated_at = ? WHERE status = ?";
    remittanceRepo
        .getEntityManager()
        .createNativeQuery(updateUpdatedAtQuery)
        .setParameter(1, OffsetDateTime.now().minusDays(2))
        .setParameter(2, RemittanceStatus.IN_PROGRESS.getValue())
        .executeUpdate();

    remittanceRepo
        .getEntityManager()
        .createNativeQuery(updateUpdatedAtQuery)
        .setParameter(1, OffsetDateTime.now().minusDays(2))
        .setParameter(2, RemittanceStatus.SENT.getValue())
        .executeUpdate();

    // Execute the method
    long numOfStaleDays = 1L;
    remittanceRepo.flush();
    controller.reconcileBillableUsageRemittances(numOfStaleDays);
    remittanceRepo.getEntityManager().clear();

    // Verify that the entities have been updated correctly
    OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(numOfStaleDays);

    List<BillableUsageRemittanceEntity> failedEntities =
        remittanceRepo.find("status = ?1 ", RemittanceStatus.FAILED).list();
    List<BillableUsageRemittanceEntity> unknownEntities =
        remittanceRepo.find("status = ?1", RemittanceStatus.UNKNOWN).list();

    // Verify the counts of updated entities
    assertEquals(
        inProgressCount,
        failedEntities.size(),
        "Expected " + inProgressCount + " entities to be updated from IN_PROGRESS to FAILED");
    assertEquals(
        sentCount,
        unknownEntities.size(),
        "Expected " + sentCount + " entities to be updated from SENT to UNKNOWN");

    // Verify that all entities that were IN_PROGRESS now have the correct error code
    for (BillableUsageRemittanceEntity entity : failedEntities) {
      assertEquals(
          RemittanceErrorCode.SENDING_TO_AGGREGATE_TOPIC,
          entity.getErrorCode(),
          "Entity should have the correct error code");
    }

    // Verify that all entities that were SENT now have a null error code
    for (BillableUsageRemittanceEntity entity : unknownEntities) {
      assertNull(entity.getErrorCode(), "Entity should have a null error code");
    }
  }

  @Transactional
  @Test
  void testReconcileBillableUsageRemittancesNoStaleEntities() {
    // Clean up any existing data
    remittanceRepo.deleteAll();

    int inProgressCount = 3;
    setupRemittances(inProgressCount, "org-recent-", RemittanceStatus.IN_PROGRESS);

    int sentCount = 2;
    setupRemittances(sentCount, "org-sent-recent-", RemittanceStatus.SENT);

    // Execute the method
    remittanceRepo.flush();
    long numOfStaleDays = 1L;
    controller.reconcileBillableUsageRemittances(numOfStaleDays);
    remittanceRepo.getEntityManager().clear();

    // Verify that no entities have been updated
    List<BillableUsageRemittanceEntity> inProgressEntities =
        remittanceRepo.find("status = ?1", RemittanceStatus.IN_PROGRESS).list();
    List<BillableUsageRemittanceEntity> sentEntities =
        remittanceRepo.find("status = ?1", RemittanceStatus.SENT).list();
    List<BillableUsageRemittanceEntity> failedEntities =
        remittanceRepo.find("status = ?1", RemittanceStatus.FAILED).list();
    List<BillableUsageRemittanceEntity> unknownEntities =
        remittanceRepo.find("status = ?1", RemittanceStatus.UNKNOWN).list();

    // Verify the counts of entities
    assertEquals(
        inProgressCount,
        inProgressEntities.size(),
        "Expected " + inProgressCount + " entities to still have IN_PROGRESS status");
    assertEquals(
        sentCount,
        sentEntities.size(),
        "Expected " + sentCount + " entities to still have SENT status");
    assertEquals(0, failedEntities.size(), "Expected no entities to have FAILED status");
    assertEquals(0, unknownEntities.size(), "Expected no entities to have UNKNOWN status");
  }

  private void setupRemittances(int inProgressCount, String orgIdPrefix, RemittanceStatus status) {
    for (int i = 0; i < inProgressCount; i++) {
      BillableUsageRemittanceEntity entity =
          remittance(
              orgIdPrefix + i,
              "stale-test",
              BillableUsage.BillingProvider.AWS,
              12.0,
              clock.endOfCurrentQuarter(),
              status);

      remittanceRepo.persist(entity);
    }
  }

  private void givenRemittanceForOrgId(String orgId) {
    remittanceRepo.persist(
        BillableUsageRemittanceEntity.builder()
            .usage(BillableUsage.Usage.PRODUCTION.value())
            .orgId(orgId)
            .billingProvider(BillableUsage.BillingProvider.AZURE.value())
            .billingAccountId(String.format("%s_%s_ba", orgId, PRODUCT_ID))
            .productId(PRODUCT_ID)
            .accumulationPeriod("mm-DD")
            .sla(BillableUsage.Sla.PREMIUM.value())
            .metricId("Cores")
            .remittancePendingDate(OffsetDateTime.now())
            .remittedPendingValue(2.0)
            .build());
  }

  private BillableUsageRemittanceEntity remittance(
      String orgId,
      String productId,
      BillableUsage.BillingProvider billingProvider,
      Double value,
      OffsetDateTime remittanceDate,
      RemittanceStatus remittanceStatus) {
    return BillableUsageRemittanceEntity.builder()
        .usage(BillableUsage.Usage.PRODUCTION.value())
        .orgId(orgId)
        .billingProvider(billingProvider.value())
        .billingAccountId(String.format("%s_%s_ba", orgId, productId))
        .productId(productId)
        .sla(BillableUsage.Sla.PREMIUM.value())
        .metricId("Instance-hours")
        .accumulationPeriod(AccumulationPeriodFormatter.toMonthId(remittanceDate))
        .remittancePendingDate(remittanceDate)
        .remittedPendingValue(value)
        .status(remittanceStatus)
        .build();
  }
}
