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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billing.admin.api.model.MonthlyRemittance;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InternalBillingController {
  private final BillableUsageRemittanceRepository remittanceRepository;

  public InternalBillingController(BillableUsageRemittanceRepository remittanceRepository) {
    this.remittanceRepository = remittanceRepository;
  }

  public List<MonthlyRemittance> process(
      String accountNumber, String productId, String orgId, String metricId) {
    if (accountNumber == null && orgId == null) {
      log.debug("Must provide either accountNumber or orgId in query");
      return Collections.emptyList();
    }
    MonthlyRemittance emptyRemittance =
        new MonthlyRemittance()
            .accountNumber(accountNumber)
            .orgId(orgId)
            .productId(productId)
            .metricId(metricId)
            .remittedValue(0.0);
    var remittances = findUsageRemittance(accountNumber, productId, metricId, orgId);
    List<MonthlyRemittance> accountRemittanceList = transformUsageToMonthlyRemittance(remittances);
    if (accountRemittanceList.isEmpty()) {
      log.debug("This Account Remittance could not be found.");
      return List.of(emptyRemittance);
    }
    log.debug(
        "Found {} matches for Account Number: {}", accountRemittanceList.size(), accountNumber);
    return accountRemittanceList;
  }

  private List<BillableUsageRemittanceEntity> findUsageRemittance(
      String accountNumber, String productId, String metricId, String orgId) {
    if (orgId != null) {
      if (metricId == null) {
        return remittanceRepository.findAllRemittancesByOrgId(orgId, productId);
      }
      return remittanceRepository.findAllByOrgIdAndKey_ProductIdAndKey_MetricId(
          orgId, productId, metricId);
    } else if (metricId == null) {
      return remittanceRepository.findAllRemittancesByAccountNumber(accountNumber, productId);
    }
    return remittanceRepository.findAllByKey_AccountNumberAndKey_ProductIdAndKey_MetricId(
        accountNumber, productId, metricId);
  }

  private List<MonthlyRemittance> transformUsageToMonthlyRemittance(
      List<BillableUsageRemittanceEntity> billableUsageRemittanceEntities) {
    List<MonthlyRemittance> remittances = new ArrayList<>();
    if (billableUsageRemittanceEntities.isEmpty()) {
      return Collections.emptyList();
    }

    for (BillableUsageRemittanceEntity entity : billableUsageRemittanceEntities) {
      MonthlyRemittance accountRemittance =
          new MonthlyRemittance()
              .accountNumber(entity.getKey().getAccountNumber())
              .orgId(entity.getOrgId())
              .productId(entity.getKey().getProductId())
              .metricId(entity.getKey().getMetricId())
              .remittedValue(entity.getRemittedValue())
              .remittanceDate(entity.getRemittanceDate())
              .accumulationPeriod(entity.getKey().getAccumulationPeriod());
      remittances.add(accountRemittance);
    }
    log.debug("Found {} remittances for this account", remittances.size());
    return remittances;
  }
}
