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
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelGenerated;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.TallyReportLinks;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.candlepin.subscriptions.utilization.api.model.UsageGenerated;
import org.candlepin.subscriptions.utilization.api.resources.HostsApi;

import com.google.common.collect.ImmutableMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Map;
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

    public static final Map<HostReportSort, String> SORT_PARAM_MAPPING =
        ImmutableMap.<HostReportSort, String>builderWithExpectedSize(5)
        .put(HostReportSort.DISPLAY_NAME, "key.host.displayName")
        .put(HostReportSort.CORES, "cores")
        .put(HostReportSort.HARDWARE_TYPE, "key.host.hardwareType")
        .put(HostReportSort.SOCKETS, "sockets")
        .put(HostReportSort.LAST_SEEN, "key.host.lastSeen")
        .put(HostReportSort.MEASUREMENT_TYPE, "measurementType")
        .build();

    @Context
    UriInfo uriInfo;

    private final HostRepository repository;
    private final PageLinkCreator pageLinkCreator;

    public HostsResource(HostRepository repository, PageLinkCreator pageLinkCreator) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
    }

    @ReportingAccessRequired
    @Override
    public HostReport getHosts(String productId, Integer offset, @Min(1) @Max(100) Integer limit,
        ServiceLevelGenerated sla, UsageGenerated usage, Uom uom, HostReportSort sort, SortDirection dir) {

        Sort.Direction dirValue = Sort.Direction.ASC;
        if (dir == SortDirection.DESC) {
            dirValue = Sort.Direction.DESC;
        }
        Sort.Order implicitOrder = Sort.Order.by("id");
        Sort sortValue = Sort.by(implicitOrder);

        if (sort != null) {
            Sort.Order userDefinedOrder = new Sort.Order(dirValue, SORT_PARAM_MAPPING.get(sort));
            sortValue = Sort.by(userDefinedOrder, implicitOrder);
        }

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
        Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);
        Page<TallyHostView> hosts = repository.getTallyHostViews(
            accountNumber,
            productId,
            sanitizedSla,
            sanitizedUsage,
            minCores,
            minSockets,
            page
        );

        TallyReportLinks links;
        if (offset != null || limit != null) {
            links = pageLinkCreator.getPaginationLinks(uriInfo, hosts);
        }
        else {
            links = null;
        }

        return new HostReport()
            .links(links)
            .meta(
                new HostReportMeta()
                    .count((int) hosts.getTotalElements())
                    .product(productId)
                    .serviceLevel(sla)
                    .usage(usage)
                    .uom(uom)
            )
            .data(hosts.getContent().stream().map(TallyHostView::asApiHost).collect(Collectors.toList()));
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
            .meta(
                new HypervisorGuestReportMeta()
                    .count((int) guests.getTotalElements())
            )
            .data(guests.getContent().stream().map(Host::asApiHost).collect(Collectors.toList()));
    }
}
