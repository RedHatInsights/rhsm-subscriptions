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
package com.redhat.swatch.billable.usage.services;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceFilter;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.data.RemittanceSummaryProjection;
import com.redhat.swatch.billable.usage.exceptions.ContractCoverageException;
import com.redhat.swatch.billable.usage.exceptions.ContractMissingException;
import com.redhat.swatch.billable.usage.exceptions.ErrorCode;
import com.redhat.swatch.billable.usage.services.model.BillableUsageCalculation;
import com.redhat.swatch.billable.usage.services.model.BillingUnit;
import com.redhat.swatch.billable.usage.services.model.ContractCoverage;
import com.redhat.swatch.billable.usage.services.model.MetricUnit;
import com.redhat.swatch.billable.usage.services.model.Quantity;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class BillableUsageService {

  private static final ContractCoverage DEFAULT_CONTRACT_COVERAGE =
      ContractCoverage.builder().total(0).gratis(false).build();
  protected static final String COVERED_USAGE_METRIC = "swatch_contract_usage_total";
  protected static final String BILLABLE_USAGE_METRIC = "swatch_billable_usage_total";
  private final ApplicationClock clock;
  private final BillingProducer billingProducer;
  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;
  private final ContractsController contractsController;
  private final MeterRegistry meterRegistry;

  public void submitBillableUsage(BillableUsage usage) {
    // transaction to store the usage into database
    try {
      usage = produceMonthlyBillable(usage);

      if (usage != null && usage.getStatus() != BillableUsage.Status.GRATIS) {
        // transaction to send usage over kafka
        billingProducer.produce(usage);
      }
    } catch (ContractCoverageException exception) {
      log.debug("Skipping billable usage; see previous errors/warnings.", exception);
    }
  }

  @Transactional
  public BillableUsage produceMonthlyBillable(BillableUsage usage)
      throws ContractCoverageException {
    log.info(
        "Processing monthly billable usage for orgId={} productId={} metric={} provider={}, billingAccountId={} snapshotDate={}",
        usage.getOrgId(),
        usage.getProductId(),
        usage.getMetricId(),
        usage.getBillingProvider(),
        usage.getBillingAccountId(),
        usage.getSnapshotDate());
    log.debug("Usage: {}", usage);

    ContractCoverage contractCoverage = DEFAULT_CONTRACT_COVERAGE;
    if (SubscriptionDefinition.isContractEnabled(usage.getProductId())) {
      contractCoverage = getContractCoverage(usage);
      updateCoveredUsageMeter(usage, contractCoverage);
      log.debug("Adjusting usage based on contracted amount of {}", contractCoverage.getTotal());
    }

    Quantity<BillingUnit> contractAmount = Quantity.fromValue(usage, contractCoverage.getTotal());
    double applicableUsage =
        Quantity.of(usage.getCurrentTotal())
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
        usage.getCurrentTotal(),
        totalRemitted,
        usageCalc);

    // Update the reported usage value to the newly calculated one.
    usage.setValue(usageCalc.getBillableValue());
    usage.setBillingFactor(usageCalc.getBillingFactor());

    if (usageCalc.getRemittedValue() > 0) {
      createRemittance(usage, usageCalc, contractCoverage);
      updateBillableUsageMeter(usage, usageCalc);
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

  private ContractCoverage getContractCoverage(BillableUsage usage)
      throws ContractCoverageException {
    try {
      return contractsController.getContractCoverage(usage);
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
      throw new ContractCoverageException(ex);
    } catch (IllegalStateException ise) {
      log.error(
          "Unable to retrieve contract for usage - Usage: {} Reason: {}", usage, ise.getMessage());
      throw new ContractCoverageException(ise);
    }
  }

  protected double getTotalRemitted(BillableUsage billableUsage) {
    var filter = BillableUsageRemittanceFilter.totalRemittedFilter(billableUsage);
    return billableUsageRemittanceRepository.getRemittanceSummaries(filter).stream()
        .map(RemittanceSummaryProjection::getTotalRemittedPendingValue)
        .reduce(Double::sum)
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

  private void createRemittance(
      BillableUsage usage, BillableUsageCalculation usageCalc, ContractCoverage contractCoverage) {
    var newRemittance =
        BillableUsageRemittanceEntity.builder()
            .orgId(usage.getOrgId())
            .billingAccountId(usage.getBillingAccountId())
            .billingProvider(usage.getBillingProvider().value())
            .accumulationPeriod(AccumulationPeriodFormatter.toMonthId(usage.getSnapshotDate()))
            .metricId(MetricId.fromString(usage.getMetricId()).getValue())
            .productId(usage.getProductId())
            .sla(usage.getSla().value())
            .usage(usage.getUsage().value())
            .remittancePendingDate(clock.now())
            .tallyId(usage.getTallyId())
            .status(
                contractCoverage.isGratis() ? RemittanceStatus.GRATIS : RemittanceStatus.PENDING)
            .build();
    // Remitted value should be set to usages metric_value rather than billing_value
    newRemittance.setRemittedPendingValue(usageCalc.getRemittedValue());
    newRemittance.setRemittancePendingDate(usageCalc.getRemittanceDate());
    log.debug("Creating new remittance for update: {}", newRemittance);
    // using saveAndFlush to validate the entity against the database and raise constraints
    // exception before moving forward.
    billableUsageRemittanceRepository.persistAndFlush(newRemittance);
    usage.setStatus(
        contractCoverage.isGratis() ? BillableUsage.Status.GRATIS : BillableUsage.Status.PENDING);
    usage.setUuid(newRemittance.getUuid());
  }

  private void updateBillableUsageMeter(BillableUsage usage, BillableUsageCalculation usageCalc) {
    incrementMetric(BILLABLE_USAGE_METRIC, usage, usageCalc.getRemittedValue());
  }

  private void updateCoveredUsageMeter(BillableUsage usage, ContractCoverage contractCoverage) {
    if (usage.getValue() == null || usage.getCurrentTotal() == null) {
      return;
    }

    double newUsageMetric = usage.getValue();
    double previousTotalUsageMetric = usage.getCurrentTotal() - newUsageMetric;
    double contractCoveredMetric =
        Quantity.fromValue(usage, contractCoverage.getTotal()).toMetricUnits();
    if (previousTotalUsageMetric > contractCoveredMetric) {
      // the metric was already covered by a previous usage, so we don't need to
      // increase again this metric
      return;
    }

    double remainingCoverage = contractCoveredMetric - previousTotalUsageMetric;
    incrementMetric(COVERED_USAGE_METRIC, usage, Math.min(remainingCoverage, newUsageMetric));
  }

  private void incrementMetric(String metric, BillableUsage usage, double value) {
    if (usage.getProductId() == null
        || usage.getMetricId() == null
        || usage.getBillingProvider() == null) {
      return;
    }

    List<String> tags =
        new ArrayList<>(
            List.of(
                "product", usage.getProductId(),
                "metric_id", MetricId.tryGetValueFromString(usage.getMetricId()),
                "billing_provider", usage.getBillingProvider().value()));

    if (usage.getStatus() != null) {
      tags.addAll(List.of("status", usage.getStatus().value()));
    }

    meterRegistry.counter(metric, tags.toArray(new String[0])).increment(value);
  }
}
