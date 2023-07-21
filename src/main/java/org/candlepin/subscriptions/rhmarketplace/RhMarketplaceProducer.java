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
package org.candlepin.subscriptions.rhmarketplace;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.rhmarketplace.api.model.BatchStatus;
import org.candlepin.subscriptions.rhmarketplace.api.model.StatusResponse;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Component that is responsible for emitting usage info to Marketplace, including handling retries.
 */
@Service
public class RhMarketplaceProducer {

  private static final Logger log = LoggerFactory.getLogger(RhMarketplaceProducer.class);
  public static final String ACCEPTED_STATUS = "accepted";
  public static final String IN_PROGRESS_STATUS = "inprogress";
  public static final String FAILED_STATUS = "failed";
  private static final List<String> SUCCESSFUL_SUBMISSION_STATUSES =
      List.of(ACCEPTED_STATUS, IN_PROGRESS_STATUS);

  private final RhMarketplaceService rhMarketplaceService;
  private final RetryTemplate retryTemplate;
  private final Counter acceptedCounter;
  private final Counter unverifiedCounter;
  private final Counter rejectedCounter;
  private final RhMarketplaceProperties properties;

  @Autowired
  RhMarketplaceProducer(
      RhMarketplaceService rhMarketplaceService,
      @Qualifier("rhMarketplaceRetryTemplate") RetryTemplate retryTemplate,
      MeterRegistry meterRegistry,
      RhMarketplaceProperties properties) {
    this.rhMarketplaceService = rhMarketplaceService;
    this.retryTemplate = retryTemplate;
    this.acceptedCounter =
        meterRegistry.counter("rhsm-subscriptions.rh-marketplace.batch.accepted");
    this.unverifiedCounter =
        meterRegistry.counter("rhsm-subscriptions.rh-marketplace.batch.unverified");
    this.rejectedCounter =
        meterRegistry.counter("rhsm-subscriptions.rh-marketplace.batch.rejected");
    this.properties = properties;
  }

  @Timed("rhsm-subscriptions.marketplace.usage.submission")
  public void submitUsageRequest(UsageRequest usageRequest) {
    try {
      StatusResponse status = retryTemplate.execute(context -> tryRequest(usageRequest));
      // tryRequest will return a failed status if it's due to amendments being not yet supported
      if (FAILED_STATUS.equals(status.getStatus())) {
        if (log.isWarnEnabled()) {
          log.warn(
              "Amendment attempted (but not supported) w/ eventIds={}. Skipping verification",
              usageRequest.getData().stream()
                  .map(UsageEvent::getEventId)
                  .collect(Collectors.joining(",")));
        }
        return;
      }
      Set<String> batchIds =
          Optional.ofNullable(status.getData()).orElse(Collections.emptyList()).stream()
              .map(BatchStatus::getBatchId)
              .collect(Collectors.toSet());
      if (properties.isVerifyBatches()) {
        verifyBatchIds(batchIds);
      }
    } catch (Exception e) {
      rejectedCounter.increment();
      String snapshotIds =
          usageRequest.getData().stream()
              .map(UsageEvent::getEventId)
              .collect(Collectors.joining(","));
      log.error("Error submitting usage for snapshot IDs: {}", snapshotIds, e);
    }
  }

  private void verifyBatchIds(Set<String> batchIds) {
    batchIds.forEach(
        batchId -> {
          try {
            retryTemplate.execute(context -> verifyBatchId(batchId));
          } catch (Exception e) {
            log.error("Error verifying batchId {}", batchId, e);
            unverifiedCounter.increment();
          }
        });
  }

  private String verifyBatchId(String batchId) {
    try {
      StatusResponse response = rhMarketplaceService.getUsageBatchStatus(batchId);
      String status = Objects.requireNonNull(response.getStatus());
      if (IN_PROGRESS_STATUS.equals(status)) {
        // throw an exception so that retry logic re-checks the batch
        throw new RhMarketplaceUsageSubmissionException(response);
      } else if (!ACCEPTED_STATUS.equals(status)) {
        log.error(
            "RH Marketplace rejected batch {} with status {} and message {}",
            batchId,
            status,
            response.getMessage());
        log.debug("RH Marketplace response: {}", response);
        rejectedCounter.increment();
      } else {
        acceptedCounter.increment();
      }
      return status;
    } catch (ApiException e) {
      throw new SubscriptionsException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          Response.Status.fromStatusCode(e.getCode()),
          "Exception checking usage batch in Marketplace",
          e);
    }
  }

  private StatusResponse tryRequest(UsageRequest usageRequest) {
    try {
      StatusResponse status = rhMarketplaceService.submitUsageEvents(usageRequest);
      log.debug("RH Marketplace response: {}", status);
      if (!isSuccessfulResponse(status)) {
        throw new RhMarketplaceUsageSubmissionException(status);
      }
      if (status.getData() != null) {
        status
            .getData()
            .forEach(
                batchStatus ->
                    log.info(
                        "RH Marketplace Batch: {} for Tally Snapshot IDs: {}",
                        batchStatus.getBatchId(),
                        usageRequest.getData().stream()
                            .map(UsageEvent::getEventId)
                            .collect(Collectors.joining(","))));
      }
      return status;
    }
    // handle checked exceptions here, so that submitUsageRequest can be easily used in lambdas etc.
    catch (ApiException e) {
      throw new SubscriptionsException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          Response.Status.fromStatusCode(e.getCode()),
          "Exception submitting usage record to Marketplace",
          e);
    }
  }

  private boolean isSuccessfulResponse(StatusResponse status) {
    return SUCCESSFUL_SUBMISSION_STATUSES.contains(status.getStatus())
        || failedOnlyDueToAmendmentUnsupported(status);
  }

  private boolean failedOnlyDueToAmendmentUnsupported(StatusResponse status) {
    if (!StringUtils.hasText(properties.getAmendmentNotSupportedMarker())) {
      return false;
    }
    return Optional.ofNullable(status.getData()).orElse(Collections.emptyList()).stream()
        .map(BatchStatus::getMessage)
        .allMatch(m -> m != null && m.contains(properties.getAmendmentNotSupportedMarker()));
  }
}
