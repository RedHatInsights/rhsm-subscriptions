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
package org.candlepin.subscriptions.subscription;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.resources.SearchApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** The Subscription Service wrapper for all subscription service interfaces. */
@Service
@Slf4j
public class SubscriptionService {

  private static final String ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG =
      "Error during attempt to request subscription info";
  private final SearchApi searchApi;
  private final RetryTemplate subscriptionServiceRetryTemplate;
  private final SubscriptionServiceProperties properties;

  public SubscriptionService(
      SearchApi searchApi,
      RetryTemplate subscriptionServiceRetryTemplate,
      SubscriptionServiceProperties properties) {
    this.searchApi = searchApi;
    this.subscriptionServiceRetryTemplate = subscriptionServiceRetryTemplate;
    this.properties = properties;
  }

  /**
   * Object a subscription model by ID.
   *
   * @param id the Subscription ID.
   * @return a subscription model.
   */
  public Subscription getSubscriptionById(String id) {
    Supplier<Subscription> supplier =
        () -> {
          try {
            return searchApi.getSubscriptionById(id);
          } catch (ApiException e) {
            log.info("Api exception from subscription service: {}", e.getMessage());
            throw new ExternalServiceException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
                e);
          }
        };

    return monoRetryWrapper(supplier);
  }

  /**
   * Obtain Subscription Service Subscription Models for an account number. Will attempt to gather
   * "all" pages and combine them.
   *
   * @param accountNumber the account number of the customer. Also refered to as the Oracle account
   *     number.
   * @return a list of Subscription models.
   */
  public List<Subscription> getSubscriptionsByAccountNumber(String accountNumber) {
    var index = 0;
    var pageSize = properties.getPageSize();
    int latestResultCount;

    Set<Subscription> total = new HashSet<>();
    do {
      List<Subscription> subscriptionsByAccountNumber;

      subscriptionsByAccountNumber =
          getSubscriptionsByAccountNumber(accountNumber, index, pageSize);
      latestResultCount = subscriptionsByAccountNumber.size();
      total.addAll(subscriptionsByAccountNumber);
      index = index + pageSize;
    } while (latestResultCount == pageSize);

    return new ArrayList<>(total);
  }

  /**
   * Obtain Subscription Service Subscription Models for an account number.
   *
   * @param accountNumber the account number of the customer. Also refered to as the Oracle account
   *     number.
   * @param index the starting index for results.
   * @param pageSize the number of results in the page.
   * @return a list of Subscription models.
   */
  protected List<Subscription> getSubscriptionsByAccountNumber(
      String accountNumber, int index, int pageSize) {
    Supplier<List<Subscription>> supplier =
        () -> {
          try {
            return searchApi.searchSubscriptionsByAccountNumber(accountNumber, index, pageSize);
          } catch (ApiException e) {
            throw new ExternalServiceException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
                e);
          }
        };

    return fluxRetryWrapper(supplier);
  }

  public List<Subscription> getSubscriptionsByOrgId(String orgId) {
    var index = 0;
    var pageSize = properties.getPageSize();
    int latestResultCount;

    Set<Subscription> total = new HashSet<>();
    do {
      List<Subscription> subscriptionsByOrgId;

      subscriptionsByOrgId = getSubscriptionsByOrgId(orgId, index, pageSize);
      latestResultCount = subscriptionsByOrgId.size();
      total.addAll(subscriptionsByOrgId);
      index = index + pageSize;
    } while (latestResultCount == pageSize);

    return new ArrayList<>(total);
  }

  /**
   * Obtain Subscription Service Subscription Models for an orgId.
   *
   * @param orgId the orgId of the customer.
   * @param index the starting index for results.
   * @param pageSize the number of results in the page.
   * @return a list of Subscription models.
   */
  public List<Subscription> getSubscriptionsByOrgId(String orgId, int index, int pageSize) {
    Supplier<List<Subscription>> supplier =
        () -> {
          try {
            return searchApi.searchSubscriptionsByOrgId(orgId, index, pageSize);
          } catch (ApiException e) {
            throw new ExternalServiceException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
                e);
          }
        };

    return fluxRetryWrapper(supplier);
  }

  private Subscription monoRetryWrapper(Supplier<Subscription> getSubscriptionFunction) {
    return subscriptionServiceRetryTemplate.execute(context -> getSubscriptionFunction.get());
  }

  private List<Subscription> fluxRetryWrapper(
      Supplier<List<Subscription>> getSubscriptionFunction) {
    return subscriptionServiceRetryTemplate.execute(context -> getSubscriptionFunction.get());
  }
}
