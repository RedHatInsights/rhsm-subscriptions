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
package org.candlepin.subscriptions.tally.billing.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billing.admin.api.model.MonthlyRemittance;
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.BillingProvider;
import org.candlepin.subscriptions.tally.billing.BillingProducer;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
@Import(TestClockConfiguration.class)
@ExtendWith(MockitoExtension.class)
class InternalBillingControllerTest {

  @Autowired BillableUsageRemittanceRepository remittanceRepo;
  @Autowired ApplicationClock clock;
  @Mock BillingProducer billingProducer;

  InternalBillingController controller;

  @BeforeEach
  void setup() {
    controller = new InternalBillingController(remittanceRepo, billingProducer);

    BillableUsageRemittanceEntity remittance1 =
        remittance("111", "product1", BillingProvider.AWS, 24.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance("org123", "product1", BillingProvider.AWS, 12.0, clock.endOfCurrentQuarter());
    BillableUsageRemittanceEntity remittance3 =
        remittance(
            "org123", "product1", BillingProvider.RED_HAT, 12.0, clock.startOfCurrentMonth());
    remittance3.getKey().setMetricId("Transfer-gibibytes");
    BillableUsageRemittanceEntity remittance4 =
        remittance("org345", "product2", BillingProvider.RED_HAT, 8.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance5 =
        remittance("org345", "product3", BillingProvider.AZURE, 4.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance6 =
        remittance("1234", "rosa", BillingProvider.AWS, 24.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance7 =
        remittance("5678", "rosa", BillingProvider.AWS, 24.0, clock.startOfCurrentMonth());
    remittanceRepo.saveAllAndFlush(
        List.of(
            remittance1,
            remittance2,
            remittance3,
            remittance4,
            remittance5,
            remittance6,
            remittance7));
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
  }

  @Test
  void testFilterByAccountAndProduct() {
    var response =
        controller.getRemittances(
            BillableUsageRemittanceFilter.builder().orgId("org123").productId("product1").build());
    assertFalse(response.isEmpty());
    assertEquals(2, response.size());
    assertEquals(24.0, response.get(0).getRemittedValue() + response.get(1).getRemittedValue());
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
    assertEquals(BillingProvider.AWS.value(), result.getBillingProvider());
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
                .billingProvider(BillingProvider.RED_HAT.value())
                .build());
    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    MonthlyRemittance result = response.get(0);
    assertEquals(BillingProvider.RED_HAT.value(), result.getBillingProvider());
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
    assertEquals(BillingProvider.AZURE.value(), result.getBillingProvider());
    assertEquals(4, result.getRemittedValue());
  }

  @Test
  void testResetRemittanceValueForCriteria() {
    int remittancePresent =
        controller.resetBillableUsageRemittance(
            "rosa",
            clock.startOfCurrentMonth().minusDays(1),
            clock.startOfCurrentMonth().plusDays(1),
            Set.of("1234", "5678"));
    int remittanceNotPresent =
        controller.resetBillableUsageRemittance(
            "rosa",
            clock.startOfCurrentMonth().plusDays(1),
            clock.startOfCurrentMonth().plusDays(2),
            Set.of("1234"));
    assertEquals(2, remittancePresent);
    assertEquals(0, remittanceNotPresent);
  }

  @Test
  void testProcessRetries() {
    String orgId = "testProcessRetriesOrg123";
    givenRemittanceWithOldRetryAfter(orgId);

    controller.processRetries(OffsetDateTime.now());

    // verify remittance has been sent
    verify(billingProducer).produce(argThat(b -> b.getOrgId().equals(orgId)));
    // verify retry after is reset
    assertTrue(
        remittanceRepo.findAll().stream()
            .filter(b -> b.getKey().getOrgId().equals(orgId))
            .allMatch(b -> b.getRetryAfter() == null));
  }

  private void givenRemittanceWithOldRetryAfter(String orgId) {
    var remittance =
        remittance(orgId, "product", BillingProvider.AZURE, 4.0, clock.startOfCurrentMonth());
    remittance.setRetryAfter(clock.now().minusMonths(30));
    remittanceRepo.saveAndFlush(remittance);
  }

  private BillableUsageRemittanceEntity remittance(
      String orgId,
      String productId,
      BillingProvider billingProvider,
      Double value,
      OffsetDateTime remittanceDate) {
    BillableUsageRemittanceEntityPK key =
        BillableUsageRemittanceEntityPK.builder()
            .usage(BillableUsage.Usage.PRODUCTION.value())
            .orgId(orgId)
            .billingProvider(billingProvider.value())
            .billingAccountId(String.format("%s_%s_ba", orgId, productId))
            .productId(productId)
            .sla(BillableUsage.Sla.PREMIUM.value())
            .metricId("Instance-hours")
            .accumulationPeriod(InstanceMonthlyTotalKey.formatMonthId(remittanceDate))
            .remittancePendingDate(remittanceDate)
            .build();
    return BillableUsageRemittanceEntity.builder().key(key).remittedPendingValue(value).build();
  }
}
