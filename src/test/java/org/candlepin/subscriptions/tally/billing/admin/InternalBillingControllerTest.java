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

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class InternalBillingControllerTest {

  @Autowired BillableUsageRemittanceRepository remittanceRepo;

  private final ApplicationClock clock = new FixedClockConfiguration().fixedClock();

  InternalBillingController controller;

  @BeforeEach
  void setup() {
    controller = new InternalBillingController(remittanceRepo);

    BillableUsageRemittanceEntity remittance1 =
        remittance("111", "product1", 24.0, clock.startOfCurrentMonth());
    remittance1.setOrgId("orgId");
    BillableUsageRemittanceEntity remittance2 =
        remittance("account123", "product1", 12.0, clock.endOfCurrentQuarter());
    remittance2.setOrgId("orgId");
    BillableUsageRemittanceEntity remittance3 =
        remittance("account123", "product1", 12.0, clock.startOfCurrentMonth());
    remittance3.getKey().setMetricId(BillableUsage.Uom.TRANSFER_GIBIBYTES.value());
    remittanceRepo.saveAllAndFlush(List.of(remittance1, remittance2, remittance3));
  }

  @Test
  void ifAccountNotFoundDisplayEmptyAccountRemittance() {
    var response = controller.process("", "product1", null, null);
    assertFalse(response.isEmpty());
    assertEquals(0.0, response.get(0).getRemittedValue());
  }

  @Test
  void testFilterByOrgId() {
    var response = controller.process("", "product1", "orgId", null);
    assertFalse(response.isEmpty());
    assertEquals(24.0, response.get(0).getRemittedValue());
    assertEquals(BillableUsage.Uom.INSTANCE_HOURS.value(), response.get(0).getMetricId());
  }

  @Test
  void testFilterByAccountAndProduct() {
    var response = controller.process("account123", "product1", null, null);
    assertFalse(response.isEmpty());
    assertEquals(2, response.size());
    assertEquals(24.0, response.get(0).getRemittedValue() + response.get(1).getRemittedValue());
  }

  @Test
  void testFilterByAccountAndProductAndMetricId() {
    var response =
        controller.process(
            "account123", "product1", null, BillableUsage.Uom.TRANSFER_GIBIBYTES.value());
    assertFalse(response.isEmpty());
    assertEquals(1, response.size());
    assertEquals(12.0, response.get(0).getRemittedValue());
    assertEquals(BillableUsage.Uom.TRANSFER_GIBIBYTES.value(), response.get(0).getMetricId());
  }

  @Test
  void testFilterByOrgIdAndProductAndMetricId() {
    var response =
        controller.process(
            "account123", "product1", "orgId", BillableUsage.Uom.INSTANCE_HOURS.value());
    assertFalse(response.isEmpty());
    assertEquals(2, response.size());
    assertEquals(36.0, response.get(0).getRemittedValue() + response.get(1).getRemittedValue());
    assertEquals(BillableUsage.Uom.INSTANCE_HOURS.value(), response.get(0).getMetricId());
  }

  @Test
  void testAccountAndOrgIdShouldReturnEmpty() {
    var response =
        controller.process(null, "product1", null, BillableUsage.Uom.INSTANCE_HOURS.value());
    assertTrue(response.isEmpty());
  }

  private BillableUsageRemittanceEntity remittance(
      String accountNumber, String productId, Double value, OffsetDateTime remittanceDate) {
    BillableUsageRemittanceEntityPK key =
        BillableUsageRemittanceEntityPK.builder()
            .usage(BillableUsage.Usage.PRODUCTION.value())
            .accountNumber(accountNumber)
            .billingProvider(BillableUsage.BillingProvider.AWS.value())
            .billingAccountId(accountNumber + "_ba")
            .productId(productId)
            .sla(BillableUsage.Sla.PREMIUM.value())
            .metricId(BillableUsage.Uom.INSTANCE_HOURS.value())
            .accumulationPeriod(InstanceMonthlyTotalKey.formatMonthId(remittanceDate))
            .build();
    return BillableUsageRemittanceEntity.builder()
        .key(key)
        .remittanceDate(remittanceDate)
        .remittedValue(value)
        .build();
  }
}
