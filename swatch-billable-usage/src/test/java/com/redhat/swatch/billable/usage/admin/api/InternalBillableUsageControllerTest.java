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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.services.BillingProducer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InternalBillableUsageControllerTest {

  private static final String PRODUCT_ID = "rosa";

  @Inject BillableUsageRemittanceRepository remittanceRepo;
  @Inject ApplicationClock clock;
  @InjectMock BillingProducer billingProducer;
  @Inject InternalBillableUsageController controller;

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
            .filter(b -> b.getOrgId().equals(orgId))
            .allMatch(b -> b.getRetryAfter() == null));
  }

  @Transactional
  void givenRemittanceWithOldRetryAfter(String orgId) {
    var remittance =
        remittance(
            orgId, "product", "azure", 4.0, clock.startOfCurrentMonth(), RemittanceStatus.PENDING);
    remittance.setRetryAfter(clock.now().minusMonths(30));
    remittanceRepo.persistAndFlush(remittance);
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
      String billingProvider,
      Double value,
      OffsetDateTime remittanceDate,
      RemittanceStatus remittanceStatus) {
    return BillableUsageRemittanceEntity.builder()
        .usage(BillableUsage.Usage.PRODUCTION.value())
        .orgId(orgId)
        .billingProvider(billingProvider)
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
