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
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class BillableUsageController {

  private final ApplicationClock clock;
  private final BillingProducer billingProducer;
  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;
  private final TallySnapshotRepository snapshotRepository;

  public BillableUsageController(
      ApplicationClock clock,
      BillingProducer billingProducer,
      BillableUsageRemittanceRepository billableUsageRemittanceRepository,
      TallySnapshotRepository snapshotRepository) {
    this.clock = clock;
    this.billingProducer = billingProducer;
    this.billableUsageRemittanceRepository = billableUsageRemittanceRepository;
    this.snapshotRepository = snapshotRepository;
  }

  public void submitBillableUsage(BillingWindow billingWindow, BillableUsage usage) {
    switch (billingWindow) {
      case HOURLY:
        produceHourlyBillable(usage);
        break;
      case MONTHLY:
        produceMonthlyBillable(usage);
        break;
      default:
        throw new UnsupportedOperationException(
            "Unsupported billing window specified when producing billable usage: " + billingWindow);
    }
  }

  private BillableUsageRemittanceEntity getLatestRemittance(BillableUsage billableUsage) {
    BillableUsageRemittanceEntityPK key =
        BillableUsageRemittanceEntityPK.builder()
            .usage(billableUsage.getUsage().value())
            .accountNumber(billableUsage.getAccountNumber())
            .billingProvider(billableUsage.getBillingProvider().value())
            .billingAccountId(billableUsage.getBillingAccountId())
            .productId(billableUsage.getProductId())
            .sla(billableUsage.getSla().value())
            .metricId(billableUsage.getUom().value())
            .accumulationPeriod(getAccumulationPeriod(billableUsage.getSnapshotDate()))
            .build();

    return billableUsageRemittanceRepository
        .findById(key)
        .orElse(BillableUsageRemittanceEntity.builder().key(key).remittedValue(0.0).build());
  }

  private BillableUsageCalculation calculateBillableUsage(
      double measuredTotal, double currentRemittedValue) {
    double adjustedMeasuredTotal = Math.ceil(measuredTotal);
    double billableValue = adjustedMeasuredTotal - currentRemittedValue;
    if (billableValue < 0) {
      // Message could have been received out of order via another process,
      // or on re-tally we have already billed for this usage and using
      // credit. There's nothing to bill in this case.
      billableValue = 0.0;
    }
    double remittedValue = currentRemittedValue + billableValue;

    return BillableUsageCalculation.builder()
        .billableValue(billableValue)
        .remittedValue(remittedValue)
        .remittanceDate(clock.now())
        .build();
  }

  private void produceHourlyBillable(BillableUsage usage) {
    log.debug("Processing hourly billable usage {}", usage);
    billingProducer.produce(usage);
  }

  private void produceMonthlyBillable(BillableUsage usage) {
    log.debug("Processing monthly billable usage {}", usage);
    Double currentMontlyTotal =
        getCurrentlyMeasuredTotal(
            usage, clock.startOfMonth(usage.getSnapshotDate()), usage.getSnapshotDate());
    BillableUsageRemittanceEntity remittance = getLatestRemittance(usage);
    BillableUsageCalculation usageCalc =
        calculateBillableUsage(currentMontlyTotal, remittance.getRemittedValue());

    log.debug("Current montly total: {}", currentMontlyTotal);
    log.debug("Current remittance: {}", remittance);
    log.debug("New billable usage calculation: {}", usageCalc);

    // Update the reported usage value to the newly calculated one.
    usage.setValue(usageCalc.getBillableValue());

    // The orgId might not have been available when remittance
    // was originally created, so we attempt to set here.
    if (updateRemittance(
        remittance,
        usage.getOrgId(),
        usageCalc.getRemittedValue(),
        usageCalc.getRemittanceDate())) {
      // Only send the update if we need to.
      log.debug("Updating remittance: {}", remittance);
      billableUsageRemittanceRepository.save(remittance);
    }

    // Send the message last to ensure that remittance has been updated.
    // If the message fails to send, it will roll back the transaction.
    billingProducer.produce(usage);
  }

  private String getAccumulationPeriod(OffsetDateTime reference) {
    return InstanceMonthlyTotalKey.formatMonthId(reference);
  }

  private boolean updateRemittance(
      BillableUsageRemittanceEntity remittance,
      String orgId,
      Double remittedValue,
      OffsetDateTime remittedDate) {
    boolean updated = false;
    if (!Objects.equals(remittance.getRemittanceDate(), remittedDate)) {
      remittance.setRemittanceDate(remittedDate);
      updated = true;
    }
    if (!Objects.equals(remittance.getOrgId(), orgId) && StringUtils.hasText(orgId)) {
      remittance.setOrgId(orgId);
      updated = true;
    }
    if (!Objects.equals(remittance.getRemittedValue(), remittedValue)) {
      remittance.setRemittedValue(remittedValue);
      updated = true;
    }
    return updated;
  }

  private Double getCurrentlyMeasuredTotal(
      BillableUsage usage, OffsetDateTime beginning, OffsetDateTime ending) {
    // NOTE: We are filtering billable usage to PHYSICAL hardware as that's the only
    //       hardware type set when metering.
    TallyMeasurementKey measurementKey =
        new TallyMeasurementKey(
            HardwareMeasurementType.PHYSICAL, Uom.fromValue(usage.getUom().value()));
    return snapshotRepository.sumMeasurementValueForPeriod(
        usage.getAccountNumber(),
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
