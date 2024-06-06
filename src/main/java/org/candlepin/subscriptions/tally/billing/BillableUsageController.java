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
import jakarta.ws.rs.ProcessingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class BillableUsageController {

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
}
