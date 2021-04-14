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

import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.json.TallyMeasurement.Uom;
import org.candlepin.subscriptions.json.TallySnapshot;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.marketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.marketplace.api.model.UsageMeasurement;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;
import org.candlepin.subscriptions.tally.UsageCalculation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maps TallySummary to payload contents to be sent to RHM apis
 */
@Service
public class MarketplacePayloadMapper {
    private static final Logger log = LoggerFactory.getLogger(MarketplacePayloadMapper.class);

    public static final String OPENSHIFT_DEDICATED_4_CPU_HOUR = "redhat.com:openshift_dedicated:4cpu_hour";

    private final ProductProfileRegistry profileRegistry;
    private final MarketplaceProperties marketplaceProperties;
    private final MarketplaceSubscriptionIdProvider idProvider;

    @Autowired
    public MarketplacePayloadMapper(ProductProfileRegistry profileRegistry,
        MarketplaceSubscriptionIdProvider idProvider, MarketplaceProperties marketplaceProperties) {
        this.profileRegistry = profileRegistry;
        this.marketplaceProperties = marketplaceProperties;
        this.idProvider = idProvider;
    }

    /**
     * Create UsageRequest pojo to send to Marketplace
     *
     * @param tallySummary TallySummary
     * @return UsageRequest
     */
    public UsageRequest createUsageRequest(TallySummary tallySummary) {

        UsageRequest usageRequest = new UsageRequest();

        var usageEvents = produceUsageEvents(tallySummary);
        usageEvents.forEach(usageRequest::addDataItem);

        log.debug("UsageRequest {}", usageRequest);

        return usageRequest;
    }

    /**
     * We only want to send snapshot information for OpenShift-metrics, OpenShift-dedicated-metrics product
     * ids.  To prevent duplicate data, we don't want to send snapshots with the Usage or ServiceLevel of
     * "_ANY".  We only want to report on hourly metrics, so the Granularity should be HOURLY.
     *
     * @param snapshot tally snapshot
     * @return eligibility status
     */
    protected boolean isSnapshotPAYGEligible(TallySnapshot snapshot) {
        String productId = snapshot.getProductId();

        var applicableProducts = marketplaceProperties.getEligibleSwatchProductIds();
        boolean isApplicableProduct = applicableProducts.contains(productId);

        boolean isHourlyGranularity = Objects
            .equals(TallySnapshot.Granularity.HOURLY, snapshot.getGranularity());

        boolean isSpecificUsage = !List.of(TallySnapshot.Usage.ANY, TallySnapshot.Usage.__EMPTY__)
            .contains(snapshot.getUsage());

        boolean isSpecificServiceLevel = !List.of(TallySnapshot.Sla.ANY, TallySnapshot.Sla.__EMPTY__)
            .contains(snapshot.getSla());

        boolean isSnapshotPAYGEligible =
            isHourlyGranularity && isApplicableProduct && isSpecificUsage && isSpecificServiceLevel;

        if (!isSnapshotPAYGEligible) {
            log.debug("Snapshot not eligible for sending to RHM {}", snapshot);
        }
        return isSnapshotPAYGEligible;
    }

    /**
     * UsageRequest objects are made up of a list of UsageEvents.
     *
     * @param tallySummary TallySummary
     * @return List<UsageEvent>
     */
    protected List<UsageEvent> produceUsageEvents(TallySummary tallySummary) {
        if (Objects.isNull(tallySummary.getTallySnapshots())) {
            tallySummary.setTallySnapshots(new ArrayList<>());
        }

        var eligibleSnapshots = tallySummary.getTallySnapshots().stream()
            .filter(this::isSnapshotPAYGEligible).collect(Collectors.toList());

        List<UsageEvent> events = new ArrayList<>();
        for (TallySnapshot snapshot : eligibleSnapshots) {
            String productId = snapshot.getProductId();

            // call MarketplaceIdProvider.findSubscriptionId once available
            UsageCalculation.Key usageKey = new UsageCalculation.Key(productId,
                ServiceLevel.fromString(snapshot.getSla().toString()),
                Usage.fromString(snapshot.getUsage().toString()));

            OffsetDateTime snapshotDate = snapshot.getSnapshotDate();
            String eventId = snapshot.getId().toString();

            /*
            This will need to be updated if we expand the criteria defined in the
            isSnapshotPAYGEligible method to allow for Granularities other than HOURLY
             */
            OffsetDateTime startDate = snapshotDate.minus(Duration.of(1, ChronoUnit.HOURS));

            long start = startDate.toInstant().toEpochMilli();
            long end = snapshotDate.toInstant().toEpochMilli();

            var subscriptionIdOpt = idProvider.findSubscriptionId(
                tallySummary.getAccountNumber(), usageKey, startDate, snapshotDate);

            if (subscriptionIdOpt.isEmpty()) {
                log.error("{}", ErrorCode.SUBSCRIPTION_SERVICE_MARKETPLACE_ID_LOOKUP_ERROR);
                continue;
            }

            var usageMeasurements = produceUsageMeasurements(snapshot, productId);

            UsageEvent event = new UsageEvent().measuredUsage(usageMeasurements).end(end).start(start)
                .subscriptionId(subscriptionIdOpt.get()).eventId(eventId)
                .additionalAttributes(Collections.emptyMap());

            events.add(event);

        }
        return events;
    }

    /**
     * UsageEvents include a list of usage measurements.  This data includes unit of measure (UOM), the
     * value for the uom, and the metricId (RHM terminology) which is a configuration value of the product
     * the uom is for.
     *
     * @param snapshot TallySnapshot
     * @param productId swatch product id
     * @return List<UsageMeasurement>
     */
    protected List<UsageMeasurement> produceUsageMeasurements(TallySnapshot snapshot, String productId) {
        var productProfile = profileRegistry.findProfileForSwatchProductId(productId);

        List<UsageMeasurement> usageMeasurements = new ArrayList<>();

        if (Objects.isNull(snapshot.getTallyMeasurements())) {
            snapshot.setTallyMeasurements(new ArrayList<>());
        }

        //Filter out any HardwareMeasurementType.TOTAL measurments to prevent duplicates
        snapshot.getTallyMeasurements().stream().filter(measurement -> !Objects
            .equals(HardwareMeasurementType.TOTAL.toString(), measurement.getHardwareMeasurementType()))
            .forEach(measurement -> {
                String metricId = productProfile.metricByProductAndUom(productId, measurement.getUom());
                Double value = measurement.getValue();

                // RHM is expecting counts of 4 vCPU-hour blocks, but currently does not have a way
                // to do this automatically. If we detect this case, divide the cores value by 4.
                //
                // We need a longer term process to get that information onto the SKU/product definition
                // itself so that we are not hard coding this type of value in our code. This will do
                // for now.
                if (OPENSHIFT_DEDICATED_4_CPU_HOUR.equalsIgnoreCase(metricId) && !Objects.isNull(value) &&
                    Uom.CORES.equals(measurement.getUom())) {
                    value = value / 4;
                    log.debug("Found cores measurement for metric ID {}. Dividing cores by 4: {}",
                        OPENSHIFT_DEDICATED_4_CPU_HOUR, value);
                }

                UsageMeasurement usageMeasurement = new UsageMeasurement();
                usageMeasurement.setValue(value);
                usageMeasurement.setMetricId(metricId);
                usageMeasurements.add(usageMeasurement);
            });
        return usageMeasurements;
    }

}
