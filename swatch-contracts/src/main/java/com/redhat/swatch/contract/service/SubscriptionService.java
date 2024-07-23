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
package com.redhat.swatch.contract.service;

import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.config.ApplicationConfiguration;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.ExternalServiceException;
import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.exception.SubscriptionNotFoundException;
import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** The Subscription Service wrapper for all subscription service interfaces. */
@ApplicationScoped
@Slf4j
public class SubscriptionService {

  private static final String ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG =
      "Error during attempt to request subscription info";
  public static final String API_EXCEPTION_FROM_SUBSCRIPTION_SERVICE =
      "Api exception from subscription service: {}";
  @Inject @RestClient SearchApi subscriptionApi;
  @Inject ApplicationConfiguration properties;

  /**
   * Object a subscription model by ID.
   *
   * @param id the Subscription ID.
   * @return a subscription model.
   */
  @RetryWithExponentialBackoff(
      maxRetries = "${SUBSCRIPTION_MAX_RETRY_ATTEMPTS:4}",
      delay = "${SUBSCRIPTION_BACK_OFF_INITIAL_INTERVAL:1000ms}",
      maxDelay = "${SUBSCRIPTION_BACK_OFF_MAX_INTERVAL:64s}",
      factor = "${SUBSCRIPTION_BACK_OFF_MULTIPLIER:2}")
  public Subscription getSubscriptionById(String id) {
    try {
      return subscriptionApi.getSubscriptionById(id);
    } catch (ApiException e) {
      log.error(API_EXCEPTION_FROM_SUBSCRIPTION_SERVICE, e.getMessage());
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
          e);
    }
  }

  @RetryWithExponentialBackoff(
      maxRetries = "${SUBSCRIPTION_MAX_RETRY_ATTEMPTS:4}",
      delay = "${SUBSCRIPTION_BACK_OFF_INITIAL_INTERVAL:1000ms}",
      maxDelay = "${SUBSCRIPTION_BACK_OFF_MAX_INTERVAL:64s}",
      factor = "${SUBSCRIPTION_BACK_OFF_MULTIPLIER:2}")
  @Timed(
      description =
          "Time taken to lookup, via RHIT subscription service, a subscription by subscription number (including retries)",
      value = "swatch_get_subscriptions_by_subscription_number")
  public Subscription getSubscriptionBySubscriptionNumber(String subscriptionNumber) {
    try {
      List<Subscription> matchingSubscriptions =
          subscriptionApi.getSubscriptionBySubscriptionNumber(subscriptionNumber);
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
  }

  public List<Subscription> getSubscriptionsByOrgId(String orgId) {
    var index = 0;
    var pageSize = properties.getSubscriptionPageSize();
    int latestResultCount;

    Set<Subscription> total = new HashSet<>();
    do {
      List<Subscription> subscriptionsByOrgId = getSubscriptionsByOrgId(orgId, index, pageSize);
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
  @RetryWithExponentialBackoff(
      maxRetries = "${SUBSCRIPTION_MAX_RETRY_ATTEMPTS:4}",
      delay = "${SUBSCRIPTION_BACK_OFF_INITIAL_INTERVAL:1000ms}",
      maxDelay = "${SUBSCRIPTION_BACK_OFF_MAX_INTERVAL:64s}",
      factor = "${SUBSCRIPTION_BACK_OFF_MULTIPLIER:2}")
  public List<Subscription> getSubscriptionsByOrgId(String orgId, int index, int pageSize) {
    try {
      return subscriptionApi.searchSubscriptionsByOrgId(orgId, index, pageSize);
    } catch (ApiException e) {
      var response = e.getResponse().getEntity();
      log.error(API_EXCEPTION_FROM_SUBSCRIPTION_SERVICE, response);

      if (response != null && response.toString().contains("NumberFormatException")) {
        throw new ServiceException(
            ErrorCode.REQUEST_PROCESSING_ERROR,
            Response.Status.INTERNAL_SERVER_ERROR,
            ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
            null,
            e);
      }

      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          ERROR_DURING_ATTEMPT_TO_REQUEST_SUBSCRIPTION_INFO_MSG,
          e);
    }
  }
}
