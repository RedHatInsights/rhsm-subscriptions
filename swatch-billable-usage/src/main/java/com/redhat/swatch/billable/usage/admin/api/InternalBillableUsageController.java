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

import static com.redhat.swatch.billable.usage.data.RemittanceErrorCode.SENDING_TO_AGGREGATE_TOPIC;
import static java.util.Optional.ofNullable;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceFilter;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceErrorCode;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.data.RemittanceSummaryProjection;
import com.redhat.swatch.billable.usage.model.RemittanceMapper;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class InternalBillableUsageController {
  public static final String USAGE_STATUS_PUSH_TO_FAILED_METRIC =
      "rhsm-subscriptions.swatch-billable-usage-push-in-progress-to-failed-status";
  public static final String USAGE_STATUS_PUSH_TO_UNKNOWN_METRIC =
      "rhsm-subscriptions.swatch-billable-usage-push-sent-to-unknown-status";

  private final BillableUsageRemittanceRepository remittanceRepository;
  private final RemittanceMapper remittanceMapper;
  private final MeterRegistry meterRegistry;

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

  public List<TallyRemittance> getRemittancesByTally(BillableUsageRemittanceFilter filter) {
    return remittanceMapper.map(remittanceRepository.find(filter));
  }

  @Transactional
  public int resetBillableUsageRemittance(
      String productId,
      OffsetDateTime start,
      OffsetDateTime end,
      Set<String> orgIds,
      Set<String> billingAccountIds) {
    return remittanceRepository.resetBillableUsageRemittance(
        productId, start, end, orgIds, billingAccountIds);
  }

  @Transactional
  public void reconcileBillableUsageRemittances(long days) {
    remittanceRepository
        .findStaleInProgress(days)
        .forEach(
            entity -> {
              entity.setStatus(RemittanceStatus.FAILED);
              entity.setErrorCode(SENDING_TO_AGGREGATE_TOPIC);
              remittanceRepository.updateStatusByIdIn(
                  List.of(entity.getUuid().toString()),
                  entity.getStatus(),
                  entity.getBilledOn(),
                  entity.getErrorCode());
              meterRegistry.counter(USAGE_STATUS_PUSH_TO_FAILED_METRIC).increment();
              log.info("Billable Usage Remittance Stuck Status Change: {}", entity);
            });

    remittanceRepository
        .findStaleSent(days)
        .forEach(
            entity -> {
              entity.setStatus(RemittanceStatus.UNKNOWN);
              remittanceRepository.updateStatusByIdIn(
                  List.of(entity.getUuid().toString()),
                  entity.getStatus(),
                  entity.getBilledOn(),
                  null);
              meterRegistry.counter(USAGE_STATUS_PUSH_TO_UNKNOWN_METRIC).increment();
              log.info("Billable Usage Remittance Stuck Status Change: {}", entity);
            });
  }

  @Transactional
  public void deleteDataForOrg(String orgId) {
    remittanceRepository.deleteByOrgId(orgId);
  }

  private List<MonthlyRemittance> transformUsageToMonthlyRemittance(
      List<RemittanceSummaryProjection> remittanceSummaryProjections) {
    List<MonthlyRemittance> remittances = new ArrayList<>();
    if (remittanceSummaryProjections.isEmpty()) {
      return Collections.emptyList();
    }

    for (RemittanceSummaryProjection entity : remittanceSummaryProjections) {
      // Remove this null assignment once we start adding statuses in prod
      // https://issues.redhat.com/browse/SWATCH-2289
      var remittanceStatus =
          Objects.nonNull(entity.getStatus()) ? entity.getStatus().getValue() : "null";
      MonthlyRemittance accountRemittance =
          new MonthlyRemittance()
              .orgId(entity.getOrgId())
              .productId(entity.getProductId())
              .metricId(entity.getMetricId())
              .billingProvider(entity.getBillingProvider())
              .billingAccountId(entity.getBillingAccountId())
              .remittedValue(entity.getTotalRemittedPendingValue())
              .remittanceDate(entity.getRemittancePendingDate())
              .accumulationPeriod(entity.getAccumulationPeriod())
              .remittanceStatus(remittanceStatus)
              .remittanceErrorCode(
                  ofNullable(entity.getErrorCode())
                      .map(RemittanceErrorCode::getValue)
                      .orElse("null"));
      remittances.add(accountRemittance);
    }
    log.debug("Found {} remittances for this account", remittances.size());
    return remittances;
  }
}
