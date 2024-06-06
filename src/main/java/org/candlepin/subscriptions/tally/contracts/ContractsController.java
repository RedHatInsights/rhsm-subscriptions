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
package org.candlepin.subscriptions.tally.contracts;

import com.redhat.swatch.contracts.api.resources.DefaultApi;
import com.redhat.swatch.contracts.client.ApiException;
import jakarta.ws.rs.ProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/** Encapsulates the billing business logic for contract based billing. */
@Component
@Slf4j
public class ContractsController {

  private final DefaultApi contractsApi;

  @SuppressWarnings("java:S1068")
  private final ContractsClientProperties contractsClientProperties;

  public ContractsController(
      DefaultApi contractsApi, ContractsClientProperties contractsClientProperties) {
    this.contractsApi = contractsApi;
    this.contractsClientProperties = contractsClientProperties;
  }

  @Retryable(
      retryFor = ExternalServiceException.class,
      maxAttemptsExpression = "#{@contractsClientProperties.getMaxAttempts()}",
      backoff =
          @Backoff(
              delayExpression = "#{@contractsClientProperties.getBackOffInitialInterval()}",
              maxDelayExpression = "#{@contractsClientProperties.getBackOffMaxInterval()}",
              multiplierExpression = "#{@contractsClientProperties.getBackOffMultiplier()}"))
  public void deleteContractsWithOrg(String orgId) {
    try {
      contractsApi.deleteContractsByOrg(orgId);
    } catch (ApiException | ProcessingException ex) {
      throw new ExternalServiceException(
          ErrorCode.CONTRACTS_SERVICE_ERROR,
          String.format("Could not delete contracts with org ID '%s'", orgId),
          ex);
    }
  }
}
