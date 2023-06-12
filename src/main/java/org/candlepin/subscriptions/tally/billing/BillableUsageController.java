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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BillableUsageController {

  private final ApplicationClock clock;
  private final BillingProducer billingProducer;
  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;
  private final TallySnapshotRepository snapshotRepository;
  private final TagProfile tagProfile;
  private final ContractsController contractsController;

  public BillableUsageController(
      ApplicationClock clock,
      BillingProducer billingProducer,
      BillableUsageRemittanceRepository billableUsageRemittanceRepository,
      TallySnapshotRepository snapshotRepository,
      TagProfile tagProfile,
      ContractsController contractsController) {
    this.clock = clock;
    this.billingProducer = billingProducer;
    this.billableUsageRemittanceRepository = billableUsageRemittanceRepository;
    this.snapshotRepository = snapshotRepository;
    this.tagProfile = tagProfile;
    this.contractsController = contractsController;
  }

  public void submitBillableUsage(BillingWindow billingWindow, BillableUsage usage) {
    // Send the message last to ensure that remittance has been updated.
    // If the message fails to send, it will roll back the transaction.
    billingProducer.produce(processBillableUsage(billingWindow, usage));
  }

  public BillableUsage processBillableUsage(BillingWindow billingWindow, BillableUsage usage) {
    BillableUsage toBill;
    switch (billingWindow) {
      case HOURLY:
        toBill = produceHourlyBillable(usage);
        break;
      case MONTHLY:
        toBill = produceMonthlyBillable(usage);
        break;
      default:
        throw new UnsupportedOperationException(
            "Unsupported billing window specified when producing billable usage: " + billingWindow);
    }
    return toBill;
  }

  public boolean isReTally(BillableUsage billableUsage) {
    BillableUsageRemittanceEntityPK key = BillableUsageRemittanceEntityPK.keyFrom(billableUsage);
    var filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(key.getOrgId())
            .billingAccountId(key.getBillingAccountId())
            .billingProvider(key.getBillingProvider())
            .accumulationPeriod(key.getAccumulationPeriod())
            .metricId(key.getMetricId())
            .productId(key.getProductId())
            .granularity(Granularity.HOURLY)
            .beginning(billableUsage.getSnapshotDate())
            .build();
    return billableUsageRemittanceRepository.existsBy(filter);
  }

  public double getTotalRemitted(BillableUsage billableUsage) {
    BillableUsageRemittanceEntityPK key = BillableUsageRemittanceEntityPK.keyFrom(billableUsage);
    var filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(key.getOrgId())
            .billingAccountId(key.getBillingAccountId())
            .billingProvider(key.getBillingProvider())
            .accumulationPeriod(key.getAccumulationPeriod())
            .metricId(key.getMetricId())
            .productId(key.getProductId())
            .granularity(Granularity.HOURLY)
            .build();
    return billableUsageRemittanceRepository.getRemittanceSummaries(filter).stream()
        .findFirst()
        .map(RemittanceSummaryProjection::getTotalRemittedPendingValue)
        .orElse(0.0);
  }

  /**
   * Find the latest remitted value and billing factor used for that remittance in the database.
   * Convert it to use the billing factor that's currently listed in the tag profile. This might be
   * a no-op if the factor hasn't changed. BillableUsage should be the difference between the
   * current usage and the previous usage at the newest tag profile billing factor. Integer-only
   * billing is then applied before remitting. calculations that are need to bill any unbilled
   * amount and to record any unbilled amount
   *
   * @param applicableUsage The total amount of measured usage used during the calculation.
   * @param usage The specific event within a given month to determine what need to be billed
   * @param remittanceTotal The previous record amount for remitted amount
   * @return calculations that are need to bill any un-billed amount and to record any un-billed
   *     amount
   */
  private BillableUsageCalculation calculateBillableUsage(
      double applicableUsage, BillableUsage usage, double remittanceTotal) {
    Quantity<MetricUnit> totalUsage = Quantity.of(applicableUsage);
    var billingUnit = new BillingUnit(tagProfile, usage);
    Quantity<MetricUnit> currentRemittance = Quantity.of(remittanceTotal);
    Quantity<BillingUnit> billableValue =
        totalUsage
            .subtract(currentRemittance)
            .to(billingUnit)
            // only emit integers for billing
            .ceil()
            // Message could have been received out of order via another process
            // or usage is credited, nothing to bill in either case.
            .positiveOrZero();

    log.debug(
        "Running total: {}, already remitted: {}, to be remitted: {}",
        totalUsage,
        currentRemittance,
        billableValue);

    return BillableUsageCalculation.builder()
        .billableValue(billableValue.getValue())
        .remittedValue(billableValue.to(new MetricUnit()).getValue())
        .remittanceDate(usage.getSnapshotDate())
        .billingFactor(billingUnit.getBillingFactor())
        .build();
  }

  private BillableUsage produceHourlyBillable(BillableUsage usage) {
    log.debug("Processing hourly billable usage {}", usage);
    return usage;
  }

  private BillableUsage produceMonthlyBillable(BillableUsage usage) {
    log.info(
        "Processing monthly billable usage for orgId={} productId={} uom={} provider={}, billingAccountId={} snapshotDate={}",
        usage.getOrgId(),
        usage.getProductId(),
        usage.getUom(),
        usage.getBillingProvider(),
        usage.getBillingAccountId(),
        usage.getSnapshotDate());
    log.debug("Usage: {}", usage);

    if (billableUsageRemittanceRepository.existsById(
        BillableUsageRemittanceEntityPK.keyFrom(usage))) {
      log.debug("Skipping usage since remittance already exists.");
      return null;
    }

    Double currentlyMeasuredTotal;

    // if re-tallying we need to collect total for whole month.
    if (isReTally(usage)) {
      log.debug("Re-tallying, using measured total for entire month");
      currentlyMeasuredTotal =
          getCurrentlyMeasuredTotal(
              usage,
              clock.startOfMonth(usage.getSnapshotDate()),
              clock.endOfMonth(usage.getSnapshotDate()));
    } else {
      currentlyMeasuredTotal =
          getCurrentlyMeasuredTotal(
              usage, clock.startOfMonth(usage.getSnapshotDate()), usage.getSnapshotDate());
    }

    Optional<Double> contractValue = Optional.of(0.0);
    if (tagProfile.isTagContractEnabled(usage.getProductId())) {
      try {
        contractValue = Optional.of(contractsController.getContractCoverage(usage));
        log.debug("Adjusting usage based on contracted amount of {}", contractValue);
      } catch (ContractMissingException ex) {
        if (usage.getSnapshotDate().isAfter(OffsetDateTime.now().minus(30, ChronoUnit.MINUTES))) {
          log.warn(
              "{} - Unable to retrieve contract for usage less than {} minutes old. Usage: {}",
              ErrorCode.CONTRACT_NOT_AVAILABLE,
              30,
              usage);
        } else {
          log.error(
              "{} - Unable to retrieve contract for usage older than {} minutes old. Usage: {}",
              ErrorCode.CONTRACT_NOT_AVAILABLE,
              30,
              usage);
        }
        return null;
      }
    }

    Quantity<BillingUnit> contractAmount =
        Quantity.fromContractCoverage(tagProfile, usage, contractValue.get());
    double applicableUsage =
        Quantity.of(currentlyMeasuredTotal)
            .subtract(contractAmount)
            .positiveOrZero() // ignore usage less than the contract amount
            .getValue();

    var totalRemitted = getTotalRemitted(usage);
    BillableUsageCalculation usageCalc =
        calculateBillableUsage(applicableUsage, usage, totalRemitted);

    log.debug(
        "Processing monthly billable usage: Usage: {}, Applicable: {}, Current total: {}, Current remittance: {}, New billable: {}",
        usage,
        applicableUsage,
        currentlyMeasuredTotal,
        totalRemitted,
        usageCalc);

    // Update the reported usage value to the newly calculated one.
    usage.setValue(usageCalc.getBillableValue());
    usage.setBillingFactor(usageCalc.getBillingFactor());

    updateRemittance(usage, usageCalc);

    log.info(
        "Finished producing monthly billable for orgId={} productId={} uom={} provider={}, snapshotDate={}",
        usage.getOrgId(),
        usage.getProductId(),
        usage.getUom(),
        usage.getBillingProvider(),
        usage.getSnapshotDate());
    return usage;
  }

  private void updateRemittance(BillableUsage usage, BillableUsageCalculation usageCalc) {
    var newRemittance =
        BillableUsageRemittanceEntity.builder()
            .key(BillableUsageRemittanceEntityPK.keyFrom(usage))
            .build();
    // Remitted value should be set to usages metric_value rather than billing_value
    newRemittance.setRemittedPendingValue(usageCalc.getRemittedValue());
    newRemittance.getKey().setRemittancePendingDate(usageCalc.getRemittanceDate());
    newRemittance.getKey().setGranularity(Granularity.HOURLY);
    newRemittance.setAccountNumber(usage.getAccountNumber());
    log.debug("Creating new remittance for update: {}", newRemittance);
    billableUsageRemittanceRepository.save(newRemittance);
    updateDailyRemittance(usage, newRemittance);
  }

  private void updateDailyRemittance(
      BillableUsage usage, BillableUsageRemittanceEntity hourlyRemittance) {
    var dailyRemittance = getDailyRemittance(usage);
    var totalDailyRemittedValue =
        dailyRemittance.getRemittedPendingValue() + hourlyRemittance.getRemittedPendingValue();
    dailyRemittance.setRemittedPendingValue(totalDailyRemittedValue);
    billableUsageRemittanceRepository.save(dailyRemittance);
  }

  private BillableUsageRemittanceEntity getDailyRemittance(BillableUsage usage) {
    var key = BillableUsageRemittanceEntityPK.keyFrom(usage, Granularity.DAILY);
    key.setRemittancePendingDate(clock.startOfDay(usage.getSnapshotDate()));
    return billableUsageRemittanceRepository
        .findById(key)
        .orElse(
            BillableUsageRemittanceEntity.builder()
                .key(key)
                .accountNumber(usage.getAccountNumber())
                .remittedPendingValue(0.0)
                .build());
  }

  private Double getCurrentlyMeasuredTotal(
      BillableUsage usage, OffsetDateTime beginning, OffsetDateTime ending) {
    // NOTE: We are filtering billable usage to PHYSICAL hardware as that's the only
    //       hardware type set when metering.
    TallyMeasurementKey measurementKey =
        new TallyMeasurementKey(
            HardwareMeasurementType.PHYSICAL, Uom.fromValue(usage.getUom().value()));
    return snapshotRepository.sumMeasurementValueForPeriod(
        usage.getOrgId(),
        usage.getProductId(),
        // Billable usage has already been filtered to HOURLY only.
        Granularity.HOURLY,
        ServiceLevel.fromString(usage.getSla().value()),
        Usage.fromString(usage.getUsage().value()),
        BillingProvider.fromString(usage.getBillingProvider().value()),
        usage.getBillingAccountId(),
        beginning,
        ending,
        measurementKey);
  }
}
