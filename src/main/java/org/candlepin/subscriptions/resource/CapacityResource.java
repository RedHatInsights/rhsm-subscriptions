/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.AdminOnly;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.SnapshotTimeAdjuster;
import org.candlepin.subscriptions.utilization.api.model.CapacityReport;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportMeta;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshot;
import org.candlepin.subscriptions.utilization.api.model.TallyReportLinks;
import org.candlepin.subscriptions.utilization.api.resources.CapacityApi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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

    @Context
    UriInfo uriInfo;

    private final SubscriptionCapacityRepository repository;
    private final PageLinkCreator pageLinkCreator;
    private final ApplicationClock clock;

    public CapacityResource(SubscriptionCapacityRepository repository, PageLinkCreator pageLinkCreator,
        ApplicationClock clock) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
        this.clock = clock;
    }

    @Override
    @AdminOnly
    public CapacityReport getCapacityReport(String productId, @NotNull String granularity,
        @NotNull OffsetDateTime beginning, @NotNull OffsetDateTime ending, Integer offset,
        @Min(1) Integer limit) {

        Granularity granularityValue = Granularity.valueOf(granularity.toUpperCase());

        String ownerId = ResourceUtils.getOwnerId();
        List<CapacitySnapshot> capacities = getCapacities(ownerId, productId, granularityValue,
            beginning, ending);

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
        report.getMeta().setGranularity(granularity);
        report.getMeta().setProduct(productId);
        report.getMeta().setCount(report.getData().size());
        report.setLinks(links);

        return report;
    }

    private List<CapacitySnapshot> paginate(List<CapacitySnapshot> capacities, Pageable pageable) {
        if (pageable == null) {
            return capacities;
        }
        int offset = pageable.getPageNumber() * pageable.getPageSize();
        int lastIndex = Math.min(capacities.size(), offset + pageable.getPageSize());
        return capacities.subList(offset, lastIndex);
    }

    private List<CapacitySnapshot> getCapacities(String ownerId, String productId,
        Granularity granularity, @NotNull OffsetDateTime beginning, @NotNull OffsetDateTime ending) {

        List<SubscriptionCapacity> matches =
            repository.findSubscriptionCapacitiesByOwnerIdAndProductIdAndEndDateAfterAndBeginDateBefore(
            ownerId,
            productId,
            beginning,
            ending);

        SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);

        OffsetDateTime start = timeAdjuster.adjustToPeriodStart(beginning);
        OffsetDateTime end = timeAdjuster.adjustToPeriodEnd(ending);
        TemporalAmount offset = timeAdjuster.getSnapshotOffset();

        List<CapacitySnapshot> result = new ArrayList<>();
        OffsetDateTime next = OffsetDateTime.from(start);
        while (next.isBefore(end) || next.isEqual(end)) {
            result.add(createCapacitySnapshot(clock.startOfDay(next), matches));
            next = clock.startOfDay(next.plus(offset));
        }
        return result;
    }

    private CapacitySnapshot createCapacitySnapshot(OffsetDateTime date, List<SubscriptionCapacity> matches) {
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
