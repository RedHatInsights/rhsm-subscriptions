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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billing.admin.api.model.MonthlyRemittance;
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.RemittanceSummaryProjection;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.tally.billing.BillingProducer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class InternalBillingController {
  private final BillableUsageRemittanceRepository remittanceRepository;
  private final BillingProducer billingProducer;

  public InternalBillingController(
      BillableUsageRemittanceRepository remittanceRepository, BillingProducer billingProducer) {
    this.remittanceRepository = remittanceRepository;
    this.billingProducer = billingProducer;
  }

  public List<MonthlyRemittance> getRemittances(BillableUsageRemittanceFilter filter) {
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

  public long processRetries(OffsetDateTime asOf) {
    List<BillableUsageRemittanceEntity> remittances =
        remittanceRepository.findByRetryAfterLessThan(asOf);
    for (BillableUsageRemittanceEntity remittance : remittances) {
      // re-trigger billable usage
      billingProducer.produce(toBillableUsage(remittance));
      // reset the retry after column
      remittance.setRetryAfter(null);
    }

    // to save the retry after column for all the entities
    remittanceRepository.saveAll(remittances);
    return remittances.size();
  }

  private BillableUsage toBillableUsage(BillableUsageRemittanceEntity remittance) {
    return new BillableUsage()
        .withOrgId(remittance.getOrgId())
        .withId(remittance.getTallyId())
        .withSnapshotDate(remittance.getRemittancePendingDate())
        .withProductId(remittance.getProductId())
        .withSla(BillableUsage.Sla.fromValue(remittance.getSla()))
        .withUsage(BillableUsage.Usage.fromValue(remittance.getUsage()))
        .withBillingProvider(
            BillableUsage.BillingProvider.fromValue(remittance.getBillingProvider()))
        .withBillingAccountId(remittance.getBillingAccountId())
        .withUom(remittance.getMetricId())
        .withMetricId(remittance.getMetricId())
        .withValue(remittance.getRemittedPendingValue())
        .withHardwareMeasurementType(remittance.getHardwareMeasurementType())
        .withStatus(BillableUsage.Status.fromValue(remittance.getStatus().getValue()));
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

  @Transactional
  public int resetBillableUsageRemittance(
      String productId, OffsetDateTime start, OffsetDateTime end, Set<String> orgIds) {
    return remittanceRepository.resetBillableUsageRemittance(productId, start, end, orgIds);
  }
}
