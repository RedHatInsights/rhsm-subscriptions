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

import io.micrometer.core.annotation.Timed;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.exception.UnretryableException;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.resources.SearchApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/** The Subscription Service wrapper for all subscription service interfaces. */
@Service
@Slf4j
public class SubscriptionService {

  private static final String ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG =
      "Error during attempt to request subscription info";
  public static final String API_EXCEPTION_FROM_SUBSCRIPTION_SERVICE =
      "Api exception from subscription service: {}";
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
            log.error(API_EXCEPTION_FROM_SUBSCRIPTION_SERVICE, e.getMessage());
            throw new ExternalServiceException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
                e);
          }
        };

    return monoRetryWrapper(supplier);
  }

  @Timed(
      description =
          "Time taken to lookup, via RHIT subscription service, a subscription by subscription number (including retries)",
      value = "swatch_get_subscriptions_by_subscription_number")
  public Subscription getSubscriptionBySubscriptionNumber(String subscriptionNumber) {
    Supplier<Subscription> supplier =
        () -> {
          try {
            List<Subscription> matchingSubscriptions =
                searchApi.getSubscriptionBySubscriptionNumber(subscriptionNumber);
            if (matchingSubscriptions.isEmpty()) {
              throw new SubscriptionNotFoundException(subscriptionNumber);
            }
            if (matchingSubscriptions.size() > 1) {
              throw new ExternalServiceException(
                  ErrorCode.SUBSCRIPTION_SERVICE_REQUEST_ERROR,
                  "Multiple subscriptions found for subscriptionNumber=" + subscriptionNumber,
                  null);
            }
            return matchingSubscriptions.get(0);
          } catch (ApiException e) {
            log.error(API_EXCEPTION_FROM_SUBSCRIPTION_SERVICE, e.getMessage());
            throw new ExternalServiceException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
                e);
          }
        };

    return monoRetryWrapper(supplier);
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
            log.error(API_EXCEPTION_FROM_SUBSCRIPTION_SERVICE, e.getResponseBody());

            if (e.getResponseBody().contains("NumberFormatException")) {
              throw new UnretryableException(
                  ErrorCode.REQUEST_PROCESSING_ERROR,
                  Response.Status.INTERNAL_SERVER_ERROR,
                  ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
                  e);
            }

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
