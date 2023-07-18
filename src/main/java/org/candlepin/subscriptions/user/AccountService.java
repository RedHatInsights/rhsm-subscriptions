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
package org.candlepin.subscriptions.user;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Optional;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.user.api.model.AccountCriteria;
import org.candlepin.subscriptions.user.api.model.AccountSearch;
import org.candlepin.subscriptions.user.api.resources.AccountApi;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/** Wraps IT User Service account APIs in more convenient interfaces. */
@Component
public class AccountService {

  private final AccountApi accountApi;
  private final RetryTemplate accountLookupRetryTemplate;

  @Autowired
  public AccountService(
      AccountApi accountApi,
      @Qualifier("userServiceRetry") RetryTemplate userServiceRetryTemplate) {
    this.accountApi = accountApi;
    this.accountLookupRetryTemplate = userServiceRetryTemplate;
  }

  public String lookupOrgId(String accountNumber) {
    return accountLookupRetryTemplate.execute(ctx -> tryLookupOrgId(accountNumber));
  }

  private String tryLookupOrgId(String accountNumber) {
    try {
      // If the account isn't found, the service returns a 204 which comes back to us as a null
      var account =
          Optional.ofNullable(
              accountApi.findAccount(
                  new AccountSearch().by(new AccountCriteria().ebsAccountNumber(accountNumber))));
      if (account.isEmpty()) {
        // Don't log a stacktrace
        MDC.put("ACCOUNT_LOOKUP_FAILED", Boolean.TRUE.toString());
        throw new SubscriptionsException(
            ErrorCode.ACCOUNT_MISSING_ERROR,
            // use 404 here instead of 204, as 204 would be treated as a success, which
            // leads to subtle NPE(s) when the response is interpreted.
            Status.NOT_FOUND,
            String.format("Account number %s not found", accountNumber),
            (String) null);
      }
      return account.get().getId();
    } catch (ApiException e) {
      throw new SubscriptionsException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          Response.Status.INTERNAL_SERVER_ERROR,
          "Error looking up orgId",
          e);
    }
  }

  public String lookupAccountNumber(String orgId) {
    return accountLookupRetryTemplate.execute(ctx -> tryLookupAccountNumber(orgId));
  }

  private String tryLookupAccountNumber(String orgId) {
    try {
      // If the account isn't found, the service returns a 204 which comes back to us as a null
      var account =
          Optional.ofNullable(
              accountApi.findAccount(new AccountSearch().by(new AccountCriteria().id(orgId))));
      if (account.isEmpty()) {
        // Don't log a stacktrace
        MDC.put("ACCOUNT_LOOKUP_FAILED", Boolean.TRUE.toString());
        throw new SubscriptionsException(
            ErrorCode.ACCOUNT_MISSING_ERROR,
            // use 404 here instead of 204, as 204 would be treated as a success, which
            // leads to subtle NPE(s) when the response is interpreted.
            Status.NOT_FOUND,
            String.format("Account w/ orgId %s not found", orgId),
            (String) null);
      }

      // The generated Account object marks getEbsAccountNumber with the Nonnull annotation but I
      // don't trust the deserialization that far.
      return Optional.ofNullable(account.get().getEbsAccountNumber()) // NOSONAR
          .orElseThrow(
              () ->
                  new SubscriptionsException(
                      ErrorCode.REQUEST_PROCESSING_ERROR,
                      Response.Status.INTERNAL_SERVER_ERROR,
                      String.format("Account w/ orgId %s has no account number", orgId),
                      (String) null));
    } catch (ApiException e) {
      throw new SubscriptionsException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          Response.Status.INTERNAL_SERVER_ERROR,
          "Error looking up account number",
          e);
    }
  }
}
