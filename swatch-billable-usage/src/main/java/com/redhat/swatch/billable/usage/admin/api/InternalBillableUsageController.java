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

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.services.BillingProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class InternalBillableUsageController {
  private final BillableUsageRemittanceRepository remittanceRepository;
  private final BillingProducer billingProducer;

  @Transactional
  public void deleteDataForOrg(String orgId) {
    remittanceRepository.deleteByOrgId(orgId);
  }

  @Transactional
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
    remittanceRepository.persist(remittances);
    return remittances.size();
  }

  private BillableUsage toBillableUsage(BillableUsageRemittanceEntity remittance) {
    // Remove this null assignment once we start adding statuses in prod
    // https://issues.redhat.com/browse/SWATCH-2289
    var remittanceStatus =
        Objects.nonNull(remittance.getStatus())
            ? BillableUsage.Status.fromValue(remittance.getStatus().getValue())
            : null;
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
        .withMetricId(remittance.getMetricId())
        .withValue(remittance.getRemittedPendingValue())
        .withHardwareMeasurementType(remittance.getHardwareMeasurementType())
        .withStatus(remittanceStatus);
  }
}
