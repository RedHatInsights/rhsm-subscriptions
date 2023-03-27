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

import com.redhat.swatch.contracts.api.model.Contract;
import com.redhat.swatch.contracts.api.model.Metric;
import com.redhat.swatch.contracts.api.resources.DefaultApi;
import com.redhat.swatch.contracts.client.ApiException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/** Encapsulates the billing business logic for contract based billing. */
@Component
@Slf4j
public class ContractsController {

  private final TagProfile tagProfile;
  private final DefaultApi contractsApi;

  @SuppressWarnings("java:S1068")
  private final ContractsClientProperties contractsClientProperties;

  public ContractsController(
      TagProfile tagProfile,
      DefaultApi contractsApi,
      ContractsClientProperties contractsClientProperties) {
    this.tagProfile = tagProfile;
    this.contractsApi = contractsApi;
    this.contractsClientProperties = contractsClientProperties;
  }

  @Retryable(
      value = ExternalServiceException.class,
      maxAttemptsExpression = "#{@contractsClientProperties.getMaxAttempts()}",
      backoff =
          @Backoff(
              delayExpression = "#{@contractsClientProperties.getBackOffInitialInterval()}",
              maxDelayExpression = "#{@contractsClientProperties.getBackOffMaxInterval()}",
              multiplierExpression = "#{@contractsClientProperties.getBackOffMultiplier()}"))
  public Optional<Double> getContractCoverage(BillableUsage usage) {
    if (!tagProfile.isTagContractEnabled(usage.getProductId())) {
      // Contract not enabled for product, nothing to return.
      log.debug(
          "Skipping contract lookup for product {} since it is not contract enabled.",
          usage.getProductId());
      return Optional.empty();
    }
    log.debug("Looking up contract information for usage {}", usage);
    try {
      List<Contract> contracts =
          contractsApi.getContract(
              usage.getOrgId(),
              usage.getProductId(),
              usage.getUom().value(),
              usage.getBillingProvider().value(),
              usage.getBillingAccountId(),
              usage.getSnapshotDate());

      if (contracts == null || contracts.isEmpty()) {
        throw new ExternalServiceException(
            ErrorCode.CONTRACTS_SERVICE_ERROR,
            String.format("No contract info found for usage! %s", usage),
            null);
      }

      Integer totalUnderContract =
          contracts.stream()
              .filter(contract -> isValidContract(contract, usage))
              .map(
                  c ->
                      c.getMetrics().stream()
                          .filter(metric -> metric.getMetricId().equals(usage.getUom().value()))
                          .map(Metric::getValue)
                          .reduce(0, Integer::sum))
              .reduce(0, Integer::sum);
      log.debug("Total contract coverage is {} for usage {} ", totalUnderContract, usage);
      return Optional.of(Double.valueOf(totalUnderContract));
    } catch (ApiException ex) {
      throw new ExternalServiceException(
          ErrorCode.CONTRACTS_SERVICE_ERROR,
          String.format("Could not look up contract info for usage! %s", usage),
          ex);
    }
  }

  private boolean isValidContract(Contract contract, BillableUsage usage) {
    boolean isWithinStart = usage.getSnapshotDate().compareTo(contract.getStartDate()) >= 0;
    boolean isWithinEnd =
        Objects.isNull(contract.getEndDate())
            || usage.getSnapshotDate().isBefore(contract.getEndDate());
    return isWithinStart && isWithinEnd;
  }
}
