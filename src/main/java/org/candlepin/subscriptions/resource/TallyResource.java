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

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.model.TallyReportMeta;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;
import org.candlepin.subscriptions.utilization.api.resources.TallyApi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

/**
 * Tally API implementation.
 */
@Component
public class TallyResource implements TallyApi {

    private static final Integer DEFAULT_LIMIT = 50;

    @Context
    SecurityContext securityContext;

    @Context
    UriInfo uriInfo;

    private final TallySnapshotRepository repository;
    private final PageLinkCreator pageLinkCreator;

    public TallyResource(TallySnapshotRepository repository, PageLinkCreator pageLinkCreator) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
    }


    @Override
    public TallyReport getTallyReport(String productId, @NotNull String granularity,
        @NotNull OffsetDateTime beginning, @NotNull OffsetDateTime ending, Integer offset, Integer limit) {

        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }

        if (offset == null) {
            offset = 0;
        }

        if (offset % limit != 0) {
            throw new SubscriptionsException(
                ErrorCode.VALIDATION_FAILED_ERROR,
                Response.Status.BAD_REQUEST,
                "Offset must be divisible by limit",
                "Arbitrary offsets are not currently supported by this API"
            );
        }

        Pageable pageable = PageRequest.of(offset / limit, limit);

        String accountNumber = getAccountNumber();
        TallyGranularity granularityValue = TallyGranularity.valueOf(granularity.toUpperCase());
        Page<org.candlepin.subscriptions.db.model.TallySnapshot> snapshotPage = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(

            accountNumber,
            productId,
            granularityValue,
            beginning,
            ending,
            pageable);

        List<TallySnapshot> snapshots = snapshotPage
            .stream()
            .map(org.candlepin.subscriptions.db.model.TallySnapshot::asApiSnapshot)
            .collect(Collectors.toList());

        TallyReport report = new TallyReport();
        report.setData(snapshots);
        report.setLinks(pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage));
        report.setMeta(new TallyReportMeta());
        report.getMeta().setGranularity(granularity);
        report.getMeta().setProduct(productId);
        report.getMeta().setCount((int) snapshotPage.getTotalElements());

        return report;
    }

    private String getAccountNumber() {
        return securityContext.getUserPrincipal().getName();
    }

}
