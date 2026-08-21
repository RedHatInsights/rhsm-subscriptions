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

import com.redhat.swatch.billable.usage.exceptions.ContractMissingException;
import com.redhat.swatch.billable.usage.services.model.ContractCoverage;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

@Slf4j
@ApplicationScoped
public class ContractCoverageService {

  private final ContractsController contractsController;
  private final ApplicationClock clock;

  public ContractCoverageService(ContractsController contractsController, ApplicationClock clock) {
    this.contractsController = contractsController;
    this.clock = clock;
  }

  public ContractCoverage getContractCoverage(BillableUsage usage) throws ContractMissingException {
    String contractMetricId = getContractMetricId(usage);
    boolean isGratis = isGratisContract(usage);
    double total = 0.0;
    String licenseId = null;
    OffsetDateTime newestStartDate = null;

    List<Contract> contracts = contractsController.getValidContracts(usage);
    for (Contract contract : contracts) {
      total += getValueByContractMetricId(contract, contractMetricId);
      isGratis &= isContractCompatibleWithGratis(contract, usage);
      if (isNewerOverageLicense(contract, newestStartDate, licenseId)) {
        newestStartDate = contract.getStartDate();
        licenseId = contract.getLicenseId();
      }
    }

    ContractCoverage coverage =
        ContractCoverage.builder()
            .metricId(contractMetricId)
            .gratis(isGratis)
            .total(total)
            .licenseId(licenseId)
            .build();
    log.debug("Total contract coverage is {} for usage {} ", coverage, usage);
    return coverage;
  }

  private static boolean isGratisContract(BillableUsage usage) {
    MetricId metricId = MetricId.fromString(usage.getMetricId());
    return SubscriptionDefinition.isMetricGratis(usage.getProductId(), metricId);
  }

  private static double getValueByContractMetricId(Contract contract, String contractMetricId) {
    return contract.getMetrics().stream()
        .filter(metric -> metric.getMetricId().equals(contractMetricId))
        .map(Metric::getValue)
        .reduce(0, Integer::sum);
  }

  private static String getContractMetricId(BillableUsage usage) {
    BillableUsage.BillingProvider billingProvider = usage.getBillingProvider();
    String productId = usage.getProductId();
    MetricId metricId = MetricId.fromString(usage.getMetricId());

    String contractMetricId = null;
    String measurementMetricId = metricId.toString();
    if (BillableUsage.BillingProvider.AWS.equals(billingProvider)) {
      contractMetricId = SubscriptionDefinition.getAwsDimension(productId, measurementMetricId);
    } else if (BillableUsage.BillingProvider.RED_HAT.equals(billingProvider)) {
      contractMetricId = SubscriptionDefinition.getRhmMetricId(productId, measurementMetricId);
    } else if (BillableUsage.BillingProvider.AZURE.equals(billingProvider)) {
      contractMetricId = SubscriptionDefinition.getAzureDimension(productId, measurementMetricId);
    }

    if (contractMetricId == null || contractMetricId.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Contract metric ID is not configured for billingProvider=%s product=%s metric=%s",
              usage.getBillingProvider(), usage.getProductId(), usage.getMetricId()));
    }

    return contractMetricId;
  }

  /**
   * Check whether contract start date applies the condition for a gratis usage: contract starts on
   * the current month. See more in <a
   * href="https://issues.redhat.com/browse/SWATCH-2571">SWATCH-2571</a>. contract starting in Jan
   * First part billable usage in Jan -> gratis 'jan 2' != null && 'jan 2'.isAfter(startOfMonth('jan
   * 4')) -> true && 'jan 2'.isAfter('jan 1') = true. Second part billable usage in Feb -> not 'jan
   * 2' != null && 'jan 2'.isAfter(startOfMonth('feb 4')) -> true && 'jan 2'.isAfter('feb 1') =
   * false
   */
  private boolean isContractCompatibleWithGratis(Contract contract, BillableUsage usage) {
    OffsetDateTime startDate = contract.getStartDate();
    return startDate != null && startDate.isAfter(clock.startOfMonth(usage.getSnapshotDate()));
  }

  /**
   * Overage license selection: newest startDate wins; equal startDate → lexicographically smaller
   * licenseId (deterministic fallback). Contracts without licenseId are ignored for selection
   * (legacy); coverage still sums all valid contracts.
   */
  private static boolean isNewerOverageLicense(
      Contract contract, OffsetDateTime newestStartDate, String selectedLicenseId) {
    String licenseId = contract.getLicenseId();
    if (licenseId == null || licenseId.isBlank()) {
      return false;
    }
    // Use the candidate licenseId if and only if:
    // - candidate agreement is newer
    if (newestStartDate == null || newestStartDate.isBefore(contract.getStartDate())) {
      return true;
    }
    // - or candidate has the same start date, then choose it by lexicographical order
    if (newestStartDate.isEqual(contract.getStartDate())) {
      return licenseId.compareTo(selectedLicenseId) < 0;
    }
    return false;
  }
}
