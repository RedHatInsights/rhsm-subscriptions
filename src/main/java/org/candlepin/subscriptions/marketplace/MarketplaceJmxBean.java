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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        List<UsageEvent> usageEvents = new ArrayList<>();

        for (TallySnapshot x : tallySummary.getTallySnapshots()) {
            /*
                Tally snapshot not having usage != '_ANY', sla != '_ANY',
                productId in ('OpenShift-metrics','OpenShift-dedicated-metrics') should be transformed (all others should be skipped).
            */
            if (!Objects.equals(x.getUsage(), TallySnapshot.Usage.ANY) &&
                !Objects.equals(x.getSla(), TallySnapshot.Sla.ANY)) {
                UsageEvent usageEvent = new UsageEvent();

                long end = x.getSnapshotDate().toEpochSecond();
                long start = x.getSnapshotDate().toEpochSecond();
                String resourceType = "resourceType";
                Object additionalAttributes = null;
                String subscriptionId = "subscriptionId";

                usageEvent.setEnd(end);
                usageEvent.setStart(start);
                usageEvent.setResourceType(resourceType);
                usageEvent.setAdditionalAttributes(additionalAttributes);
                usageEvent.setSubscriptionId(subscriptionId);

                //available fields
                x.getUsage();
                x.getSla();
                x.getGranularity();
                x.getId();
                x.getProductId();
                x.getSnapshotDate();

                List<UsageMeasurement> measurements = new ArrayList<>();

                x.getTallyMeasurements().forEach(y -> {
                    var value = y.getValue();

                    //TODO where does this come from
                    var chargeId = y.getUom();
                    y.getHardwareMeasurementType();

                    UsageMeasurement usageMeasurement = new UsageMeasurement();
                    usageMeasurement.setValue(value);
                    usageMeasurement.setChargeId(chargeId.toString());

                    measurements.add(usageMeasurement);

                });
                usageEvent.setMeasuredUsage(measurements);
                usageEvents.add(usageEvent);
            }
        }

        UsageRequest usageRequest = null;
        for (UsageEvent event : usageEvents) {
            usageRequest = new UsageRequest().addDataItem(event);
        }

        return marketplaceService.submitUsageEvents(usageRequest).toString();
    }

    @ManagedOperation(description = "Fetch a usage event status")
    public String getUsageEventStatus(String batchId) throws ApiException {
        return marketplaceService.getUsageBatchStatus(batchId).toString();
    }
}
