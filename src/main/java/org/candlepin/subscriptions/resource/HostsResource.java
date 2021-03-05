/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.utilization.api.model.HostReport;
import org.candlepin.subscriptions.utilization.api.model.HostReportMeta;
import org.candlepin.subscriptions.utilization.api.model.HostReportSort;
import org.candlepin.subscriptions.utilization.api.model.HypervisorGuestReport;
import org.candlepin.subscriptions.utilization.api.model.HypervisorGuestReportMeta;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.TallyReportLinks;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.HostsApi;

import com.google.common.collect.ImmutableMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

/**
 * Hosts API implementation.
 */
@Component
public class HostsResource implements HostsApi {

    @SuppressWarnings("linelength")
    public static final Map<HostReportSort, String> HOST_SORT_PARAM_MAPPING = ImmutableMap.<HostReportSort, String>builderWithExpectedSize(
        5)
        .put(HostReportSort.DISPLAY_NAME, "host.displayName")
        .put(HostReportSort.CORES, "cores")
        .put(HostReportSort.HARDWARE_TYPE, "host.hardwareType")
        .put(HostReportSort.SOCKETS, "sockets")
        .put(HostReportSort.LAST_SEEN, "host.lastSeen")
        .put(HostReportSort.MEASUREMENT_TYPE, "measurementType")
        .build();

    @SuppressWarnings("linelength")
    public static final Map<HostReportSort, String> INSTANCE_SORT_PARAM_MAPPING = ImmutableMap.<HostReportSort, String>builder()
        .put(HostReportSort.DISPLAY_NAME, "displayName")
        .put(HostReportSort.CORE_HOURS, "monthlyTotals")
        .put(HostReportSort.LAST_SEEN, "lastSeen")
        .build();

    private final HostRepository repository;
    private final PageLinkCreator pageLinkCreator;
    @Context
    UriInfo uriInfo;

    public HostsResource(HostRepository repository, PageLinkCreator pageLinkCreator) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
    }

    @SuppressWarnings("java:S3776")
    @Transactional
    @ReportingAccessRequired
    @Override
    public HostReport getHosts(ProductId productId, Integer offset, @Min(1) @Max(100) Integer limit,
        ServiceLevelType sla, UsageType usage, Uom uom, String displayNameContains, OffsetDateTime beginning,
        OffsetDateTime ending, HostReportSort sort, SortDirection dir) {

        Sort.Direction dirValue = Sort.Direction.ASC;
        if (dir == SortDirection.DESC) {
            dirValue = Sort.Direction.DESC;
        }
        Sort.Order implicitOrder = Sort.Order.by("id");
        Sort sortValue = Sort.by(implicitOrder);

        int minCores = 0;
        int minSockets = 0;
        if (uom == Uom.CORES) {
            minCores = 1;
        }
        else if (uom == Uom.SOCKETS) {
            minSockets = 1;
        }

        String accountNumber = ResourceUtils.getAccountNumber();
        ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
        Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
        String sanitizedDisplayNameSubstring = Objects.nonNull(displayNameContains) ?
            displayNameContains :
            "";

        boolean isSpecial = Objects.equals(productId, ProductId.OPENSHIFT_DEDICATED_METRICS) ||
            Objects.equals(productId, ProductId.OPENSHIFT_METRICS);

        List<org.candlepin.subscriptions.utilization.api.model.Host> payload;
        Page<?> hosts;
        if (isSpecial) {
            if (sort != null) {
                Sort.Order userDefinedOrder = new Sort.Order(dirValue, INSTANCE_SORT_PARAM_MAPPING.get(sort));
                sortValue = Sort.by(userDefinedOrder, implicitOrder);
            }
            Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime start = Optional.ofNullable(beginning).orElse(now);
            OffsetDateTime end = Optional.ofNullable(ending).orElse(now);

            validateBeginningAndEndingDates(start, end);

            String month = InstanceMonthlyTotalKey.formatMonthId(start);

            hosts = repository.findAllBy(accountNumber, productId.toString(), sanitizedSla, sanitizedUsage,
                sanitizedDisplayNameSubstring, minCores, minSockets, month, page);
            payload = ((Page<Host>) hosts).getContent().stream().map(Host::asTallyHostViewApiHost)
                .collect(Collectors.toList());
        }
        else {
            if (sort != null) {
                Sort.Order userDefinedOrder = new Sort.Order(dirValue,
                    HOST_SORT_PARAM_MAPPING.get(sort));
                sortValue = Sort.by(userDefinedOrder, implicitOrder);
            }
            Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);
            hosts = repository
                .getTallyHostViews(accountNumber, productId.toString(), sanitizedSla, sanitizedUsage,
                    sanitizedDisplayNameSubstring, minCores, minSockets, page);

            payload = ((Page<TallyHostView>) hosts).getContent().stream().map(TallyHostView::asApiHost)
                .collect(Collectors.toList());
        }

        TallyReportLinks links;
        if (offset != null || limit != null) {
            links = pageLinkCreator.getPaginationLinks(uriInfo, hosts);
        }
        else {
            links = null;
        }

        return new HostReport()
            .links(links)
            .meta(new HostReportMeta()
                .count((int) hosts.getTotalElements())
                .product(productId)
                .serviceLevel(sla)
                .usage(usage)
                .uom(uom))
            .data(payload);
    }

    protected void validateBeginningAndEndingDates(OffsetDateTime beginning, OffsetDateTime ending) {
        boolean isDateRangePossible = beginning.isBefore(ending) || beginning.isEqual(ending);
        boolean isBothDatesFromSameMonth = Objects.equals(beginning.getMonth(), ending.getMonth());

        if (!isDateRangePossible || !isBothDatesFromSameMonth) {
            throw new IllegalArgumentException("Invalid date range.");
        }
    }

    @Override
    @ReportingAccessRequired
    public HypervisorGuestReport getHypervisorGuests(String hypervisorUuid, Integer offset, Integer limit) {
        String accountNumber = ResourceUtils.getAccountNumber();
        Pageable page = ResourceUtils.getPageable(offset, limit);
        Page<Host> guests = repository.getHostsByHypervisor(accountNumber, hypervisorUuid, page);
        TallyReportLinks links;
        if (offset != null || limit != null) {
            links = pageLinkCreator.getPaginationLinks(uriInfo, guests);
        }
        else {
            links = null;
        }

        return new HypervisorGuestReport()
            .links(links)
            .meta(new HypervisorGuestReportMeta().count((int) guests.getTotalElements()))
            .data(guests.getContent().stream().map(Host::asApiHost).collect(Collectors.toList()));
    }
}
