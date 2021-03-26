/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.marketplace;

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.marketplace.api.model.BatchStatus;
import org.candlepin.subscriptions.marketplace.api.model.StatusResponse;
import org.candlepin.subscriptions.marketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

/**
 * Component that is responsible for emitting usage info to Marketplace, including handling retries.
 */
@Service
public class MarketplaceProducer {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceProducer.class);
    private static final String[] SUCCESSFUL_SUBMISSION_STATUSES = new String[]{"accepted", "inprogress"};

    private final MarketplaceService marketplaceService;
    private final RetryTemplate retryTemplate;
    private final Counter acceptedCounter;
    private final Counter unverifiedCounter;
    private final Counter rejectedCounter;
    private final MarketplaceProperties properties;

    @Autowired
    MarketplaceProducer(MarketplaceService marketplaceService,
        @Qualifier("marketplaceRetryTemplate") RetryTemplate retryTemplate, MeterRegistry meterRegistry,
        MarketplaceProperties properties) {
        this.marketplaceService = marketplaceService;
        this.retryTemplate = retryTemplate;
        this.acceptedCounter = meterRegistry.counter("rhsm-subscriptions.marketplace.batch.accepted");
        this.unverifiedCounter = meterRegistry.counter("rhsm-subscriptions.marketplace.batch.unverified");
        this.rejectedCounter = meterRegistry.counter("rhsm-subscriptions.marketplace.batch.rejected");
        this.properties = properties;
    }

    @Timed("rhsm-subscriptions.marketplace.usage.submission")
    public StatusResponse submitUsageRequest(UsageRequest usageRequest) {
        try {
            StatusResponse status = retryTemplate.execute(context -> tryRequest(usageRequest));
            Set<String> batchIds = Optional.ofNullable(status.getData()).orElse(Collections.emptyList())
                .stream()
                .map(BatchStatus::getBatchId)
                .collect(Collectors.toSet());
            if (properties.isVerifyBatches()) {
                verifyBatchIds(batchIds);
            }
            return status;
        }
        catch (Exception e) {
            rejectedCounter.increment();
            throw e;
        }
    }

    private void verifyBatchIds(Set<String> batchIds) {
        batchIds.forEach(batchId -> {
            try {
                retryTemplate.execute(context -> verifyBatchId(batchId));
            }
            catch (Exception e) {
                log.error("Error verifying batchId {}", batchId, e);
                unverifiedCounter.increment();
            }
        });
    }

    private String verifyBatchId(String batchId) {
        try {
            StatusResponse response = marketplaceService.getUsageBatchStatus(batchId);
            String status = Objects.requireNonNull(response.getStatus());
            if ("inprogress".equals(status)) {
                // throw an exception so that retry logic re-checks the batch
                throw new MarketplaceUsageSubmissionException(response.getMessage(), status);
            }
            else if (!"accepted".equals(status)) {
                log.error("Marketplace rejected batch {} with status {} and message {}", batchId, status,
                    response.getMessage());
                rejectedCounter.increment();
            }
            else {
                acceptedCounter.increment();
            }
            return status;
        }
        catch (ApiException e) {
            throw new SubscriptionsException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                Response.Status.fromStatusCode(e.getCode()),
                "Exception checking usage batch in Marketplace",
                e
            );
        }
    }

    private StatusResponse tryRequest(UsageRequest usageRequest) {
        try {
            StatusResponse status = marketplaceService.submitUsageEvents(usageRequest);
            log.debug("Marketplace response: {}", status);
            if (!Arrays.asList(SUCCESSFUL_SUBMISSION_STATUSES).contains(status.getStatus())) {
                throw new MarketplaceUsageSubmissionException(status.getStatus(), status.getMessage());
            }
            if (status.getData() != null) {
                status.getData().forEach(batchStatus ->
                    log.info("Marketplace Batch: {} for Tally Snapshot IDs: {}", batchStatus.getBatchId(),
                    usageRequest.getData().stream()
                    .map(UsageEvent::getEventId).collect(Collectors.joining(","))));
            }
            return status;
        }
        // handle checked exceptions here, so that submitUsageRequest can be easily used in lambdas etc.
        catch (ApiException e) {
            throw new SubscriptionsException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                Response.Status.fromStatusCode(e.getCode()),
                "Exception submitting usage record to Marketplace",
                e
            );
        }
    }
}
