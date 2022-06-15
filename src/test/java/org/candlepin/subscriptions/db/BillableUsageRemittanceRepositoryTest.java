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
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.json.BillableUsage.BillingProvider;
import org.candlepin.subscriptions.json.BillableUsage.Sla;
import org.candlepin.subscriptions.json.BillableUsage.Uom;
import org.candlepin.subscriptions.json.BillableUsage.Usage;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureTestDatabase
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class BillableUsageRemittanceRepositoryTest {

  @Autowired private BillableUsageRemittanceRepository repository;
  private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

  @Test
  void saveAndFetch() {
    BillableUsageRemittanceEntity remittance =
        remittance("account123", "product1", 12.0, clock.startOfCurrentMonth());
    repository.saveAndFlush(remittance);
    Optional<BillableUsageRemittanceEntity> fetched = repository.findById(remittance.getKey());
    assertTrue(fetched.isPresent());
    assertEquals(remittance, fetched.get());
  }

  @Test
  void deleteByAccount() {
    BillableUsageRemittanceEntity remittance1 =
        remittance("account123", "product1", 12.0, clock.startOfCurrentMonth());
    BillableUsageRemittanceEntity remittance2 =
        remittance("account555", "product1", 12.0, clock.startOfCurrentMonth());

    List<BillableUsageRemittanceEntity> toSave = List.of(remittance1, remittance2);
    repository.saveAllAndFlush(toSave);

    repository.deleteByKeyAccountNumber("account123");
    repository.flush();
    assertTrue(repository.findById(remittance1.getKey()).isEmpty());
    Optional<BillableUsageRemittanceEntity> found = repository.findById(remittance2.getKey());
    assertTrue(found.isPresent());
    assertEquals(remittance2, found.get());
  }

  private BillableUsageRemittanceEntity remittance(
      String accountNumber, String productId, Double value, OffsetDateTime remittanceDate) {
    BillableUsageRemittanceEntityPK key =
        BillableUsageRemittanceEntityPK.builder()
            .usage(Usage.PRODUCTION.value())
            .accountNumber(accountNumber)
            .billingProvider(BillingProvider.AWS.value())
            .billingAccountId(accountNumber + "_ba")
            .productId(productId)
            .sla(Sla.PREMIUM.value())
            .metricId(Uom.CORES.value())
            .accumulationPeriod(InstanceMonthlyTotalKey.formatMonthId(remittanceDate))
            .build();
    return BillableUsageRemittanceEntity.builder()
        .key(key)
        .remittanceDate(remittanceDate)
        .remittedValue(value)
        .build();
  }
}
