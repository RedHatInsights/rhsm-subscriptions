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

import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billing.admin.api.InternalApi;
import org.candlepin.subscriptions.billing.admin.api.model.DefaultResponse;
import org.candlepin.subscriptions.billing.admin.api.model.MonthlyRemittance;
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.springframework.stereotype.Component;

/** This resource is for exposing administrator REST endpoints for Remittance. */
@Slf4j
@Component
public class InternalBillingResource implements InternalApi {
  private static final String SUCCESS_STATUS = "Success";
  private static final String REJECTED_STATUS = "Rejected";

  private final InternalBillingController billingController;
  private final ApplicationClock clock;

  public InternalBillingResource(
      InternalBillingController billingController, ApplicationClock clock) {
    this.billingController = billingController;
    this.clock = clock;
  }

  public List<MonthlyRemittance> getRemittances(
      String productId,
      String orgId,
      String metricId,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    if (Objects.isNull(orgId)) {
      throw new BadRequestException("Must provide 'orgId' query parameters.");
    }

    if (Objects.nonNull(beginning) && Objects.nonNull(ending) && beginning.isAfter(ending)) {
      throw new BadRequestException("Query parameter 'beginning' must be before 'ending'.");
    }

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(orgId)
            .productId(productId)
            .metricId(metricId)
            .billingProvider(billingProvider)
            .billingAccountId(billingAccountId)
            .beginning(beginning)
            .ending(ending)
            .build();
    return billingController.getRemittances(filter);
  }

  @Override
  public DefaultResponse processRetries(OffsetDateTime asOf) {
    OffsetDateTime effectiveAsOf = Optional.ofNullable(asOf).orElse(clock.now());
    log.info("Retry billable usage remittances as of {}", effectiveAsOf);
    try {
      long remittances = billingController.processRetries(effectiveAsOf);
      log.debug("Retried {} billable usage remittances with as of {}", remittances, effectiveAsOf);
      return getDefaultResponse(SUCCESS_STATUS);
    } catch (Exception e) {
      log.error("Error retrying billable usage remittances", e);
      return getDefaultResponse(REJECTED_STATUS);
    }
  }

  @Override
  public DefaultResponse resetBillableUsageRemittance(
      Set<String> orgIds, String productId, OffsetDateTime start, OffsetDateTime end) {
    int updatedRemittance = 0;
    try {
      updatedRemittance =
          billingController.resetBillableUsageRemittance(productId, start, end, orgIds);
    } catch (Exception e) {
      log.warn("Billable usage remittance update failed.", e);
      return getDefaultResponse(REJECTED_STATUS);
    }
    if (updatedRemittance > 0) {
      return getDefaultResponse(SUCCESS_STATUS);
    } else {
      throw new BadRequestException(
          String.format(
              "No record found for billable usage remittance for productId %s and between start %s and end date %s and orgIds %s",
              productId, start, end, orgIds));
    }
  }

  private DefaultResponse getDefaultResponse(String status) {
    var response = new DefaultResponse();
    response.setStatus(status);
    return response;
  }
}
