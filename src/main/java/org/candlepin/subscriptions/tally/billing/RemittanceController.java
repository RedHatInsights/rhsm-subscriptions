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
package org.candlepin.subscriptions.tally.billing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.RemittanceSummaryProjection;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.retention.TallyRetentionPolicy;
import org.candlepin.subscriptions.tally.TallySummaryMapper;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class RemittanceController {

  private ApplicationClock clock;
  private final TagProfile tagProfile;
  private final BillableUsageRemittanceRepository remittanceRepository;
  private final TallySnapshotRepository snapshotRepository;
  private final TallySummaryMapper summaryMapper;
  private final BillableUsageMapper billableUsageMapper;
  private final BillableUsageController billableUsageController;
  private final TallyRetentionPolicy retention;

  public RemittanceController(
      ApplicationClock clock,
      TagProfile tagProfile,
      BillableUsageRemittanceRepository remittanceRepository,
      TallySnapshotRepository snapshotRepository,
      TallySummaryMapper summaryMapper,
      BillableUsageMapper billableUsageMapper,
      BillableUsageController billableUsageController,
      TallyRetentionPolicy retention) {
    this.clock = clock;
    this.tagProfile = tagProfile;
    this.remittanceRepository = remittanceRepository;
    this.snapshotRepository = snapshotRepository;
    this.summaryMapper = summaryMapper;
    this.billableUsageMapper = billableUsageMapper;
    this.billableUsageController = billableUsageController;
    this.retention = retention;
  }

  public List<BillableUsageRemittanceEntity> findSyncableRemittance() {
    return remittanceRepository.filterBy(
        BillableUsageRemittanceFilter.builder().granularity(Granularity.MONTHLY).build());
  }

  // A monthly remittance can only be synced if all HOURLY snapshots are present.
  // The hourly retention policy may have deleted snapshots on the monthly boundary,
  // which makes it impossible to build the corresponding HOURLY remittance.
  //
  // Determine the cutoff date based on the Hourly tally retention policy.
  private OffsetDateTime getRemittanceCutoffDate() {
    OffsetDateTime hourlyCutoff = retention.getCutoffDate(Granularity.HOURLY);
    if (hourlyCutoff.equals(clock.startOfMonth(hourlyCutoff))) {
      return hourlyCutoff;
    }
    return clock.startOfMonth(hourlyCutoff.plusMonths(1));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void syncRemittance(BillableUsageRemittanceEntity rem) {
    OffsetDateTime earliestRemittanceDate = getRemittanceCutoffDate();

    OffsetDateTime beginning = dateFromAccumulationPeriod(rem.getKey().getAccumulationPeriod());
    OffsetDateTime ending = clock.endOfMonth(beginning);
    log.debug("CHECKING FOR BILLABLES: {} --> {}", beginning, ending);

    if (beginning.isBefore(earliestRemittanceDate)) {
      log.warn(
          "REMITTANCE SYNC: Remittance is outside the hourly tally snapshot retention window {} so it will be purged. {}",
          earliestRemittanceDate,
          rem);
      remittanceRepository.delete(rem);
      return;
    }

    // If there are no associated billables for the remittance, they were deleted as
    // part of the snapshot purge. If that's the case, then we just delete this remittance
    // as it is no longer relevant and will be eventually purged.
    if (!snapshotRepository.hasLatestBillables(
        rem.getKey().getOrgId(),
        rem.getKey().getProductId(),
        ServiceLevel.fromString(rem.getKey().getSla()),
        Usage.fromString(rem.getKey().getUsage()),
        BillingProvider.fromString(rem.getKey().getBillingProvider()),
        rem.getKey().getBillingAccountId(),
        beginning,
        ending,
        new TallyMeasurementKey(
            HardwareMeasurementType.PHYSICAL, Uom.fromValue(rem.getKey().getMetricId())))) {
      log.warn(
          "REMITTANCE SYNC: No hourly billables found for remittance so it will be purged. {}",
          rem);
      remittanceRepository.delete(rem);
      return;
    }

    AtomicReference<OffsetDateTime> latestBillableDate = new AtomicReference<>();
    snapshotRepository
        .getBillableSnapshots(
            rem.getKey().getOrgId(),
            rem.getKey().getProductId(),
            ServiceLevel.fromString(rem.getKey().getSla()),
            Usage.fromString(rem.getKey().getUsage()),
            BillingProvider.fromString(rem.getKey().getBillingProvider()),
            rem.getKey().getBillingAccountId(),
            beginning,
            ending,
            new TallyMeasurementKey(
                HardwareMeasurementType.PHYSICAL, Uom.fromValue(rem.getKey().getMetricId())))
        .map(s -> summaryMapper.mapSnapshots(s.getAccountNumber(), s.getOrgId(), List.of(s)))
        .forEach(
            summary ->
                billableUsageMapper
                    .fromTallySummary(summary)
                    .filter(
                        billable -> {
                          Optional<TagMetric> metricOptional =
                              tagProfile.getTagMetric(
                                  billable.getProductId(),
                                  Uom.fromValue(billable.getUom().value()));
                          return metricOptional.isPresent()
                              && BillingWindow.MONTHLY.equals(
                                  metricOptional.get().getBillingWindow());
                        })
                    .forEach(
                        billable -> {
                          latestBillableDate.set(billable.getSnapshotDate());
                          billableUsageController.processBillableUsage(
                              BillingWindow.MONTHLY, billable);
                        }));
    validateRemittance(rem, latestBillableDate.get());
    log.info("REMITTANCE SYNC: Successfully synced remittance {}", rem);
  }

  private void validateRemittance(
      BillableUsageRemittanceEntity rem, OffsetDateTime latestSnapshotDate) {
    var filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(rem.getKey().getOrgId())
            .billingAccountId(rem.getKey().getBillingAccountId())
            .billingProvider(rem.getKey().getBillingProvider())
            .accumulationPeriod(rem.getKey().getAccumulationPeriod())
            .metricId(rem.getKey().getMetricId())
            .productId(rem.getKey().getProductId())
            .sla(rem.getKey().getSla())
            .usage(rem.getKey().getUsage())
            .granularity(Granularity.HOURLY)
            .build();
    Double hourlyBasedTotal =
        remittanceRepository.getRemittanceSummaries(filter).stream()
            .findFirst()
            .map(RemittanceSummaryProjection::getTotalRemittedPendingValue)
            .orElse(0.0);
    if (!rem.getRemittedPendingValue().equals(hourlyBasedTotal)) {
      throw new RemittanceSyncAlignmentException(rem, hourlyBasedTotal, latestSnapshotDate);
    } else {
      log.debug("Remittance validated after sync! Deleting initial remittance record");
      remittanceRepository.delete(rem);
    }
  }

  private OffsetDateTime dateFromAccumulationPeriod(String accumulationPeriod) {
    if (!StringUtils.hasText(accumulationPeriod)) {
      throw new IllegalArgumentException("Invalid accumulation period: " + accumulationPeriod);
    }

    String[] parts = accumulationPeriod.split("-");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid accumulation period: " + accumulationPeriod);
    }

    return clock
        .startOfCurrentMonth()
        .withYear(Integer.parseInt(parts[0]))
        .withMonth(Integer.parseInt(parts[1]));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void replaceMonthlyRemittance(
      BillableUsageRemittanceEntity toConvert, OffsetDateTime hourlyDate) {
    BillableUsageRemittanceEntityPK hourlyKey =
        BillableUsageRemittanceEntityPK.clone(toConvert.getKey());
    hourlyKey.setRemittancePendingDate(clock.startOfHour(hourlyDate));
    hourlyKey.setGranularity(Granularity.HOURLY);
    remittanceRepository.save(
        new BillableUsageRemittanceEntity(
            hourlyKey, toConvert.getRemittedPendingValue(), toConvert.getAccountNumber()));

    BillableUsageRemittanceEntityPK dailyKey =
        BillableUsageRemittanceEntityPK.clone(toConvert.getKey());
    dailyKey.setRemittancePendingDate(clock.startOfDay(hourlyDate));
    dailyKey.setGranularity(Granularity.DAILY);
    remittanceRepository.save(
        new BillableUsageRemittanceEntity(
            dailyKey, toConvert.getRemittedPendingValue(), toConvert.getAccountNumber()));

    remittanceRepository.deleteById(toConvert.getKey());
  }
}
