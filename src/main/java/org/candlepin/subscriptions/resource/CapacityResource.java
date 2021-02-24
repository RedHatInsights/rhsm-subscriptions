/*
 * Copyright (c) 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.files.ProductProfile;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.SnapshotTimeAdjuster;
import org.candlepin.subscriptions.utilization.api.model.CapacityReport;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportMeta;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshot;
import org.candlepin.subscriptions.utilization.api.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.TallyReportLinks;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.CapacityApi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

/**
 * Capacity API implementation.
 */
@Component
public class CapacityResource implements CapacityApi {
    private final SubscriptionCapacityRepository repository;
    private final PageLinkCreator pageLinkCreator;
    private final ApplicationClock clock;
    private final ProductProfileRegistry productProfileRegistry;

    @Context
    UriInfo uriInfo;

    public CapacityResource(SubscriptionCapacityRepository repository, PageLinkCreator pageLinkCreator,
        ApplicationClock clock, ProductProfileRegistry productProfileRegistry) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
        this.clock = clock;
        this.productProfileRegistry = productProfileRegistry;
    }

    @Override
    @ReportingAccessRequired
    public CapacityReport getCapacityReport(ProductId productId, @NotNull GranularityType granularityType,
        @NotNull OffsetDateTime beginning, @NotNull OffsetDateTime ending, Integer offset,
        @Min(1) Integer limit, ServiceLevelType sla, UsageType usage) {

        // capacity records do not include _ANY rows
        ServiceLevel sanitizedServiceLevel = ResourceUtils.sanitizeServiceLevel(sla);
        if (sanitizedServiceLevel == ServiceLevel._ANY) {
            sanitizedServiceLevel = null;
        }

        Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
        if (sanitizedUsage == Usage._ANY) {
            sanitizedUsage = null;
        }

        Granularity granularityValue = Granularity.fromString(granularityType.toString());
        String ownerId = ResourceUtils.getOwnerId();
        List<CapacitySnapshot> capacities = getCapacities(ownerId,
            productId,
            sanitizedServiceLevel,
            sanitizedUsage,
            granularityValue,
            beginning,
            ending
        );

        List<CapacitySnapshot> data;
        TallyReportLinks links;
        if (offset != null || limit != null) {
            Pageable pageable = ResourceUtils.getPageable(offset, limit);
            data = paginate(capacities, pageable);
            Page<CapacitySnapshot> snapshotPage = new PageImpl<>(data, pageable, capacities.size());
            links = pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage);
        }
        else {
            data = capacities;
            links = null;
        }

        CapacityReport report = new CapacityReport();
        report.setData(data);
        report.setMeta(new CapacityReportMeta());
        report.getMeta().setGranularity(granularityType);
        report.getMeta().setProduct(productId);
        report.getMeta().setCount(report.getData().size());

        if (sanitizedServiceLevel != null) {
            report
                .getMeta()
                .setServiceLevel(sanitizedServiceLevel.asOpenApiEnum());
        }

        if (sanitizedUsage != null) {
            report.getMeta().setUsage(sanitizedUsage.asOpenApiEnum());
        }

        report.setLinks(links);

        return report;
    }

    protected List<CapacitySnapshot> getCapacities(String ownerId, ProductId productId, ServiceLevel sla,
        Usage usage, Granularity granularity, @NotNull OffsetDateTime reportBegin,
        @NotNull OffsetDateTime reportEnd) {

        /* Throw an error if we are asked to generate capacity reports at a finer granularity than what is
         * supported by the product.  The reports created would be technically accurate, but would convey the
         * false impression that we have capacity information at that fine of a granularity.  This decision is
         * on of personal judgment and it may be appropriate to reverse it at a later date. */
        validateGranularityCompatibility(productId, granularity);

        List<SubscriptionCapacity> matches = repository.findByOwnerAndProductId(
            ownerId,
            productId.toString(),
            sla,
            usage,
            reportBegin,
            reportEnd);

        SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);

        OffsetDateTime start = timeAdjuster.adjustToPeriodStart(reportBegin);
        OffsetDateTime end = timeAdjuster.adjustToPeriodEnd(reportEnd);
        TemporalAmount offset = timeAdjuster.getSnapshotOffset();

        List<CapacitySnapshot> result = new ArrayList<>();
        OffsetDateTime next = OffsetDateTime.from(start);

        while (next.isBefore(end) || next.isEqual(end)) {
            result.add(createCapacitySnapshot(next, matches));
            next = timeAdjuster.adjustToPeriodStart(next.plus(offset));
        }

        return result;
    }

    private List<CapacitySnapshot> paginate(List<CapacitySnapshot> capacities, Pageable pageable) {
        if (pageable == null) {
            return capacities;
        }
        int offset = pageable.getPageNumber() * pageable.getPageSize();
        int lastIndex = Math.min(capacities.size(), offset + pageable.getPageSize());
        return capacities.subList(offset, lastIndex);
    }

    /** Verify that the granularity requested is compatible with the finest granularity supported by the
     *  product.  For example, if the requester asks for HOURLY granularity but the product only supports
     *  DAILY granularity, we can't meaningfully fulfill that request.
     */
    protected void validateGranularityCompatibility(ProductId productId, Granularity requestedGranularity) {
        ProductProfile productProfile = productProfileRegistry.findProfileForSwatchProductId(productId);
        String msg = String.format("%s does not support any granularity finer than %s", productId.toString(),
            productProfile.getFinestGranularity());
        Assert.isTrue(productProfile.supportsGranularity(requestedGranularity), msg);
    }

    protected CapacitySnapshot createCapacitySnapshot(OffsetDateTime date,
        List<SubscriptionCapacity> matches) {
        // NOTE there is room for future optimization here, as the we're *generally* calculating the same sum
        // across a time range, also we might opt to do some of this in the DB query in the future.
        int sockets = 0;
        int physicalSockets = 0;
        int hypervisorSockets = 0;
        int cores = 0;
        int physicalCores = 0;
        int hypervisorCores = 0;

        for (SubscriptionCapacity capacity : matches) {
            if (capacity.getBeginDate().isBefore(date) && capacity.getEndDate().isAfter(date)) {
                int capacityVirtSockets = sanitize(capacity.getVirtualSockets());
                sockets += capacityVirtSockets;
                hypervisorSockets += capacityVirtSockets;

                int capacityPhysicalSockets = sanitize(capacity.getPhysicalSockets());
                sockets += capacityPhysicalSockets;
                physicalSockets += capacityPhysicalSockets;

                int capacityPhysCores = sanitize(capacity.getPhysicalCores());
                cores += capacityPhysCores;
                physicalCores += capacityPhysCores;

                int capacityVirtCores = sanitize(capacity.getVirtualCores());
                cores += capacityVirtCores;
                hypervisorCores += capacityVirtCores;
            }
        }

        return new CapacitySnapshot()
            .date(date)
            .sockets(sockets)
            .physicalSockets(physicalSockets)
            .hypervisorSockets(hypervisorSockets)
            .cores(cores)
            .physicalCores(physicalCores)
            .hypervisorCores(hypervisorCores)
            .hasInfiniteQuantity(false);
    }

    private int sanitize(Integer value) {
        return value != null ? value : 0;
    }

}
