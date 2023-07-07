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
import java.util.List;
import java.util.Objects;
import javax.ws.rs.BadRequestException;
import org.candlepin.subscriptions.billing.admin.api.InternalApi;
import org.candlepin.subscriptions.billing.admin.api.model.MonthlyRemittance;
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.candlepin.subscriptions.db.model.Granularity;
import org.springframework.stereotype.Component;

/** This resource is for exposing administrator REST endpoints for Remittance. */
@Component
public class InternalBillingResource implements InternalApi {

  private final InternalBillingController billingController;

  public InternalBillingResource(InternalBillingController billingController) {
    this.billingController = billingController;
  }

  public List<MonthlyRemittance> getRemittances(
      String productId,
      String accountNumber,
      String orgId,
      String metricId,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    if (Objects.isNull(accountNumber) && Objects.isNull(orgId)) {
      throw new BadRequestException(
          "Must provide either 'accountNumber' or 'orgId' query parameters.");
    }

    if (Objects.nonNull(beginning) && Objects.nonNull(ending) && beginning.isAfter(ending)) {
      throw new BadRequestException("Query parameter 'beginning' must be before 'ending'.");
    }

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder()
            .account(accountNumber)
            .orgId(orgId)
            .productId(productId)
            .metricId(metricId)
            .billingProvider(billingProvider)
            .billingAccountId(billingAccountId)
            .beginning(beginning)
            .ending(ending)
            .granularity(Granularity.HOURLY)
            .build();
    return billingController.process(filter);
  }
}
