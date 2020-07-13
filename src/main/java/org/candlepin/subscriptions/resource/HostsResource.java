/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
import org.candlepin.subscriptions.db.model.AppliedHost;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.utilization.api.model.HostReport;
import org.candlepin.subscriptions.utilization.api.model.HostReportMeta;
import org.candlepin.subscriptions.utilization.api.model.HypervisorGuestReport;
import org.candlepin.subscriptions.utilization.api.model.HypervisorGuestReportMeta;
import org.candlepin.subscriptions.utilization.api.model.TallyReportLinks;
import org.candlepin.subscriptions.utilization.api.resources.HostsApi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import javax.validation.constraints.Min;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

/**
 * Hosts API implementation.
 */
@Component
public class HostsResource implements HostsApi {

    @Context
    UriInfo uriInfo;

    private final HostRepository repository;
    private final PageLinkCreator pageLinkCreator;

    public HostsResource(HostRepository repository, PageLinkCreator pageLinkCreator) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
    }


    @Override
    @ReportingAccessRequired
    public HostReport getHosts(String productId, Integer offset,
        @Min(1) Integer limit, String sla, String usage) {
        String accountNumber = ResourceUtils.getAccountNumber();
        ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
        Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
        Pageable page = ResourceUtils.getPageable(offset, limit);
        Page<AppliedHost> hosts = repository.getAppliedHosts(
            accountNumber,
            productId,
            sanitizedSla,
            sanitizedUsage,
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
            )
            .data(hosts.getContent().stream().map(AppliedHost::asApiHost).collect(Collectors.toList()));
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
