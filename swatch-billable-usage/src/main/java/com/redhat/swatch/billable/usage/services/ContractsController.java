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
import com.redhat.swatch.billable.usage.exceptions.ErrorCode;
import com.redhat.swatch.billable.usage.exceptions.ExternalServiceException;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@ApplicationScoped
public class ContractsController {
  @RestClient DefaultApi contractsApi;

  @RetryWithExponentialBackoff(
      maxRetries = "${CONTRACT_CLIENT_MAX_ATTEMPTS:1}",
      delay = "${CONTRACT_CLIENT_BACK_OFF_INITIAL_INTERVAL_MILLIS:1000ms}",
      maxDelay = "${CONTRACT_CLIENT_BACK_OFF_MAX_INTERVAL_MILLIS:64s}",
      factor = "${CONTRACT_CLIENT_BACK_OFF_MULTIPLIER:2}")
  public List<Contract> getValidContracts(BillableUsage usage) throws ContractMissingException {
    if (!SubscriptionDefinition.isContractEnabled(usage.getProductId())) {
      throw new IllegalStateException(
          String.format("Product %s is not contract enabled.", usage.getProductId()));
    }

    log.debug("Looking up contract information for usage {}", usage);
    List<Contract> contracts;
    try {
      contracts =
          contractsApi.getContract(
              usage.getOrgId(),
              usage.getProductId(),
              usage.getVendorProductCode(),
              usage.getBillingProvider().value(),
              usage.getBillingAccountId(),
              usage.getSnapshotDate());
    } catch (ApiException ex) {
      throw new ExternalServiceException(
          ErrorCode.CONTRACTS_SERVICE_ERROR,
          String.format("Could not look up contract info for usage! %s", usage),
          ex);
    }

    if (contracts == null || contracts.isEmpty()) {
      throw new ContractMissingException(
          String.format("No contract info found for usage! %s", usage));
    }

    return contracts.stream().filter(contract -> isValidContract(contract, usage)).toList();
  }

  private boolean isValidContract(Contract contract, BillableUsage usage) {
    boolean isWithinStart = usage.getSnapshotDate().compareTo(contract.getStartDate()) >= 0;
    boolean isWithinEnd =
        Objects.isNull(contract.getEndDate())
            || usage.getSnapshotDate().isBefore(contract.getEndDate());
    return isWithinStart && isWithinEnd;
  }
}
