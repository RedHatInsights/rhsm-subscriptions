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
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.model.RemittanceSummaryProjection;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InternalBillingController {
  private final BillableUsageRemittanceRepository remittanceRepository;

  public InternalBillingController(BillableUsageRemittanceRepository remittanceRepository) {
    this.remittanceRepository = remittanceRepository;
  }

  public List<MonthlyRemittance> process(BillableUsageRemittanceFilter filter) {
    if (filter.getOrgId() == null) {
      log.debug("Must provide orgId in query");
      return Collections.emptyList();
    }
    MonthlyRemittance emptyRemittance =
        new MonthlyRemittance()
            .orgId(filter.getOrgId())
            .productId(filter.getProductId())
            .metricId(filter.getMetricId())
            .billingProvider(filter.getBillingProvider())
            .billingAccountId(filter.getBillingAccountId())
            .remittedValue(0.0);
    var summaries = remittanceRepository.getRemittanceSummaries(filter);
    List<MonthlyRemittance> accountRemittanceList = transformUsageToMonthlyRemittance(summaries);
    if (accountRemittanceList.isEmpty()) {
      log.debug("This Account Remittance could not be found.");
      return List.of(emptyRemittance);
    }
    log.debug("Found {} matches for Org Id: {}", accountRemittanceList.size(), filter.getOrgId());
    return accountRemittanceList;
  }

  private List<MonthlyRemittance> transformUsageToMonthlyRemittance(
      List<RemittanceSummaryProjection> remittanceSummaryProjections) {
    List<MonthlyRemittance> remittances = new ArrayList<>();
    if (remittanceSummaryProjections.isEmpty()) {
      return Collections.emptyList();
    }

    for (RemittanceSummaryProjection entity : remittanceSummaryProjections) {
      MonthlyRemittance accountRemittance =
          new MonthlyRemittance()
              .orgId(entity.getOrgId())
              .productId(entity.getProductId())
              .metricId(entity.getMetricId())
              .billingProvider(entity.getBillingProvider())
              .billingAccountId(entity.getBillingAccountId())
              .remittedValue(entity.getTotalRemittedPendingValue())
              .remittanceDate(entity.getRemittancePendingDate())
              .accumulationPeriod(entity.getAccumulationPeriod());
      remittances.add(accountRemittance);
    }
    log.debug("Found {} remittances for this account", remittances.size());
    return remittances;
  }
}
