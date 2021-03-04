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

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.json.TallySnapshot;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.marketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.marketplace.api.model.UsageMeasurement;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;
import org.candlepin.subscriptions.resource.ResourceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.JmxException;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Exposes admin functions for Marketplace integration.
 */
@Component
@ManagedResource
public class MarketplaceJmxBean {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceJmxBean.class);

    private final ApplicationProperties properties;
    private final MarketplaceService marketplaceService;
    private final ObjectMapper mapper;

    MarketplaceJmxBean(ApplicationProperties properties, MarketplaceService marketplaceService,
        ObjectMapper mapper) {

        this.properties = properties;
        this.marketplaceService = marketplaceService;
        this.mapper = mapper;
    }

    @ManagedOperation(description = "Force a refresh of the access token")
    public void refreshAccessToken() throws ApiException {
        Object principal = ResourceUtils.getPrincipal();
        log.info("Marketplace access token forcibly refreshed by {}", principal);
        marketplaceService.forceRefreshAccessToken();
    }

    @ManagedOperation(description = "Submit usage event JSON (dev-mode only)")
    public String submitUsageEvent(String payloadJson) throws JsonProcessingException, ApiException {
        if (!properties.isDevMode()) {
            throw new JmxException("Unsupported outside dev-mode");
        }

        TallySummary tallySummary = mapper.readValue(payloadJson, TallySummary.class);

        var accountNumber = tallySummary.getAccountNumber();

        UsageEvent usageEvent = null;
        for (TallySnapshot x : tallySummary.getTallySnapshots()) {
            usageEvent = new UsageEvent();

            UsageMeasurement usageMeasurement = new UsageMeasurement();
            usageMeasurement.setValue(0.0);
            usageMeasurement.setChargeId("chargeId");
            List<UsageMeasurement> usageMeasurement1 = List.of(usageMeasurement);
            long end = x.getSnapshotDate().toEpochSecond();
            long start = x.getSnapshotDate().toEpochSecond();
            String resourceType = "resourceType";
            Object additionalAttributes = null;
            String subscriptionId = "subscriptionId";

            usageEvent.addMeasuredUsageItem(usageMeasurement);
            usageEvent.setMeasuredUsage(usageMeasurement1);
            usageEvent.setEnd(end);
            usageEvent.setStart(start);
            usageEvent.setResourceType(resourceType);
            usageEvent.setAdditionalAttributes(additionalAttributes);
            usageEvent.setSubscriptionId(subscriptionId);

            x.getGranularity();
            x.getId();
            x.getUsage();
            x.getProductId();
            x.getSla();
            x.getSnapshotDate();
            x.getTallyMeasurements().forEach(y -> {
                y.getValue();
                y.getUom();
                y.getHardwareMeasurementType();
            });
        }

        UsageRequest usageRequest = new UsageRequest().addDataItem(usageEvent);
        return marketplaceService.submitUsageEvents(usageRequest).toString();
    }

    @ManagedOperation(description = "Fetch a usage event status")
    public String getUsageEventStatus(String batchId) throws ApiException {
        return marketplaceService.getUsageBatchStatus(batchId).toString();
    }
}
