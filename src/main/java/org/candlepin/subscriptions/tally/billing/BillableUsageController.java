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

import com.redhat.swatch.billable.usage.api.resources.DefaultApi;
import com.redhat.swatch.billable.usage.client.ApiException;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.RemittanceStatus;
import org.candlepin.subscriptions.db.model.RemittanceSummaryProjection;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.json.BillableUsage;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class BillableUsageController {

  private final ApplicationClock clock;
  private final BillingProducer billingProducer;
  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;
  private final TallySnapshotRepository snapshotRepository;
  private final ContractsController contractsController;
  private final DefaultApi billableUsageApi;

  @SuppressWarnings("unused")
  private final BillableUsageClientProperties billableUsageClientProperties;

  @Retryable(
      retryFor = ExternalServiceException.class,
      maxAttemptsExpression = "#{@billableUsageClientProperties.getMaxAttempts()}",
      backoff =
          @Backoff(
              delayExpression = "#{@billableUsageClientProperties.getBackOffInitialInterval()}",
              maxDelayExpression = "#{@billableUsageClientProperties.getBackOffMaxInterval()}",
              multiplierExpression = "#{@billableUsageClientProperties.getBackOffMultiplier()}"))
  public void deleteRemittancesWithOrg(String orgId) {
    try {
      billableUsageApi.deleteRemittancesAssociatedWithOrg(orgId);
    } catch (ApiException | ProcessingException ex) {
      throw new ExternalServiceException(
          ErrorCode.BILLABLE_USAGE_SERVICE_ERROR,
          String.format("Could not delete remittances with org ID '%s'", orgId),
          ex);
    }
  }

  public void submitBillableUsage(BillableUsage usage) {
    // Send the message last to ensure that remittance has been updated.
    // If the message fails to send, it will roll back the transaction.
    billingProducer.produce(produceMonthlyBillable(usage));
  }

  public double getTotalRemitted(BillableUsage billableUsage) {
    var filter = BillableUsageRemittanceFilter.fromUsage(billableUsage);
    return billableUsageRemittanceRepository.getRemittanceSummaries(filter).stream()
        .findFirst()
        .map(RemittanceSummaryProjection::getTotalRemittedPendingValue)
        .orElse(0.0);
  }

  /**
   * Find the latest remitted value and billing factor used for that remittance in the database.
   * Convert it to use the billing factor that's currently listed in the
   * swatch-product-configuration library. This might be a no-op if the factor hasn't changed.
   * BillableUsage should be the difference between the current usage and the previous usage at the
   * newest swatch-product-configuration library billing factor. Integer-only billing is then
   * applied before remitting. calculations that are need to bill any unbilled amount and to record
   * any unbilled amount
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
    var billingUnit = new BillingUnit(usage);
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
        .remittanceDate(clock.now())
        .billingFactor(billingUnit.getBillingFactor())
        .build();
  }

  private BillableUsage produceMonthlyBillable(BillableUsage usage) {
    log.info(
        "Processing monthly billable usage for orgId={} productId={} metric={} provider={}, billingAccountId={} snapshotDate={}",
        usage.getOrgId(),
        usage.getProductId(),
        usage.getMetricId(),
        usage.getBillingProvider(),
        usage.getBillingAccountId(),
        usage.getSnapshotDate());
    log.debug("Usage: {}", usage);

    // For old messages, the total value for period is not set yet, so we need to keep using the
    // getCurrentlyMeasuredTotal method
    // After SWATCH-2368, we will fully use the provided total value for period from the usage and
    // the getCurrentlyMeasuredTotal method is to be removed.
    Double currentlyMeasuredTotal =
        Optional.ofNullable(usage.getCurrentTotal())
            .orElseGet(
                () ->
                    getCurrentlyMeasuredTotal(
                        usage,
                        clock.startOfMonth(usage.getSnapshotDate()),
                        usage.getSnapshotDate()));

    Optional<Double> contractValue = Optional.of(0.0);
    if (SubscriptionDefinition.isContractEnabled(usage.getProductId())) {
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
      } catch (IllegalStateException ise) {
        log.error(
            "Unable to retrieve contract for usage - Usage: {} Reason: {}",
            usage,
            ise.getMessage());
        return null;
      }
    }

    Quantity<BillingUnit> contractAmount =
        Quantity.fromContractCoverage(usage, contractValue.get());
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

    if (usageCalc.getRemittedValue() > 0) {
      createRemittance(usage, usageCalc);
    } else {
      log.debug("Nothing to remit. Remittance record will not be created.");
    }

    // There were issues with transmitting usage to AWS since the cost event timestamps were in the
    // past. This modification allows us to send usage to AWS if we get it during the current hour
    // of event tally.
    usage.setSnapshotDate(usageCalc.getRemittanceDate());

    log.info(
        "Finished producing monthly billable for orgId={} productId={} metric={} provider={}, snapshotDate={}",
        usage.getOrgId(),
        usage.getProductId(),
        usage.getMetricId(),
        usage.getBillingProvider(),
        usage.getSnapshotDate());
    return usage;
  }

  private void createRemittance(BillableUsage usage, BillableUsageCalculation usageCalc) {
    var newRemittance =
        BillableUsageRemittanceEntity.builder()
            .orgId(usage.getOrgId())
            .billingAccountId(usage.getBillingAccountId())
            .billingProvider(usage.getBillingProvider().value())
            .accumulationPeriod(InstanceMonthlyTotalKey.formatMonthId(usage.getSnapshotDate()))
            .metricId(MetricId.fromString(usage.getMetricId()).getValue())
            .productId(usage.getProductId())
            .sla(usage.getSla().value())
            .usage(usage.getUsage().value())
            .remittancePendingDate(clock.now())
            .tallyId(usage.getId())
            .hardwareMeasurementType(usage.getHardwareMeasurementType())
            .status(RemittanceStatus.PENDING)
            .build();
    // Remitted value should be set to usages metric_value rather than billing_value
    newRemittance.setRemittedPendingValue(usageCalc.getRemittedValue());
    newRemittance.setRemittancePendingDate(usageCalc.getRemittanceDate());
    log.debug("Creating new remittance for update: {}", newRemittance);
    // using saveAndFlush to validate the entity against the database and raise constraints
    // exception before moving forward.
    billableUsageRemittanceRepository.saveAndFlush(newRemittance);
    usage.setStatus(BillableUsage.Status.PENDING);
    usage.setUuid(newRemittance.getUuid());
  }

  private Double getCurrentlyMeasuredTotal(
      BillableUsage usage, OffsetDateTime beginning, OffsetDateTime ending) {
    HardwareMeasurementType hardwareMeasurementType =
        Optional.ofNullable(usage.getHardwareMeasurementType())
            .map(HardwareMeasurementType::fromString)
            .orElse(HardwareMeasurementType.PHYSICAL);
    TallyMeasurementKey measurementKey =
        new TallyMeasurementKey(hardwareMeasurementType, usage.getMetricId());
    return snapshotRepository.sumMeasurementValueForPeriod(
        usage.getOrgId(),
        usage.getProductId(),
        Granularity.HOURLY,
        ServiceLevel.fromString(usage.getSla().value()),
        Usage.fromString(usage.getUsage().value()),
        BillingProvider.fromString(usage.getBillingProvider().value()),
        usage.getBillingAccountId(),
        beginning,
        ending,
        measurementKey);
  }

  public void updateBillableUsageRemittanceWithRetryAfter(
      BillableUsage billableUsage, OffsetDateTime retryAfter) {
    var billableUsageRemittance =
        billableUsageRemittanceRepository.findOne(
            BillableUsageRemittanceFilter.fromUsage(billableUsage));
    if (billableUsageRemittance.isPresent()) {
      billableUsageRemittance.get().setRetryAfter(retryAfter);
      billableUsageRemittanceRepository.save(billableUsageRemittance.get());
    } else {
      log.warn(
          "Unable to find billable usage to update retry_after. BillableUsage: {}", billableUsage);
    }
  }
}
