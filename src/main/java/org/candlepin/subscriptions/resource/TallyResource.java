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
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallySnapshotSummation;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.AdminOnly;
import org.candlepin.subscriptions.tally.filler.ReportFiller;
import org.candlepin.subscriptions.tally.filler.ReportFillerFactory;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.model.TallyReportMeta;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;
import org.candlepin.subscriptions.utilization.api.resources.TallyApi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

/**
 * Tally API implementation.
 */
@Component
@ConditionalOnProperty(prefix = "rhsm-subscriptions", name = "enableJobProcessing", havingValue = "false",
    matchIfMissing = true)
public class TallyResource implements TallyApi {

    @Context UriInfo uriInfo;

    private final TallySnapshotRepository repository;
    private final PageLinkCreator pageLinkCreator;
    private final ApplicationClock clock;

    public TallyResource(TallySnapshotRepository repository, PageLinkCreator pageLinkCreator,
        ApplicationClock clock) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
        this.clock = clock;
    }

    @Override
    @AdminOnly
    @SuppressWarnings("linelength")
    public TallyReport getTallyReport(String productId, @NotNull String granularity,
        @NotNull OffsetDateTime beginning, @NotNull OffsetDateTime ending, Integer offset,
        @Min(1) Integer limit, String sla) {
        // When limit and offset are not specified, we will fill the report with dummy
        // records from beginning to ending dates. Otherwise we page as usual.
        Pageable pageable = null;
        boolean fill = limit == null && offset == null;
        if (!fill) {
            pageable = ResourceUtils.getPageable(offset, limit);
        }

        boolean matchEmptySla = sla != null && sla.equalsIgnoreCase("unset");
        String accountNumber = ResourceUtils.getAccountNumber();
        Granularity granularityValue = Granularity.valueOf(granularity.toUpperCase());
        Page<TallySnapshotSummation> snapshotPage = repository.sumSnapshotMeasurements(
            accountNumber,
            productId,
            granularityValue,
            sla,
            beginning,
            ending,
            matchEmptySla,
            pageable
        );

        List<TallySnapshot> snaps = buildSnapshotsFromSums(snapshotPage);
        TallyReport report = new TallyReport();
        report.setData(snaps);
        report.setMeta(new TallyReportMeta());
        report.getMeta().setGranularity(granularity);
        report.getMeta().setProduct(productId);
        report.getMeta().setServiceLevel(sla);

        // Only set page links if we are paging (not filling).
        if (pageable != null) {
            report.setLinks(pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage));
        }

        // Fill the report gaps if no paging was requested.
        if (fill) {
            ReportFiller reportFiller = ReportFillerFactory.getInstance(clock, granularityValue);
            reportFiller.fillGaps(report, beginning, ending);
        }

        // Set the count last since the report may have gotten filled.
        report.getMeta().setCount(report.getData().size());

        return report;
    }

    private List<TallySnapshot> buildSnapshotsFromSums(Page<TallySnapshotSummation> summations) {
        List<TallySnapshot> data = new LinkedList<>();

        Map<OffsetDateTime, List<TallySnapshotSummation>> summationByDate =
            summations.stream().collect(Collectors.groupingBy(TallySnapshotSummation::getSnapshotDate));
        summationByDate.entrySet().forEach(entry -> data.add(asApiSnapshot(entry.getValue())));
        return data;
    }

    public TallySnapshot asApiSnapshot(List<TallySnapshotSummation> summations) {

        OffsetDateTime snapDate = null;

        Integer cloudInstances = 0;
        Integer cloudCores = 0;
        Integer cloudSockets = 0;

        TallySnapshot snapshot = new TallySnapshot();
        for (TallySnapshotSummation sum : summations) {

            if (snapDate != null && !snapDate.equals(sum.getSnapshotDate())) {
                throw new IllegalArgumentException("Invalid sum specified for API snapshot! " +
                    "Dates must all match!");
            }
            snapDate = sum.getSnapshotDate();

            if (HardwareMeasurementType.TOTAL.equals(sum.getType())) {
                snapshot.setCores(sum.getCores());
                snapshot.setSockets(sum.getSockets());
                snapshot.setInstanceCount(sum.getInstances());
            }
            else if (HardwareMeasurementType.PHYSICAL.equals(sum.getType())) {
                snapshot.setPhysicalCores(sum.getCores());
                snapshot.setPhysicalSockets(sum.getSockets());
                snapshot.setPhysicalInstanceCount(sum.getInstances());
            }
            else if (HardwareMeasurementType.HYPERVISOR.equals(sum.getType())) {
                snapshot.setHypervisorCores(sum.getCores());
                snapshot.setHypervisorSockets(sum.getSockets());
                snapshot.setHypervisorInstanceCount(sum.getInstances());
            }
            else if (HardwareMeasurementType.getCloudProviderTypes().contains(sum.getType())) {
                // Tally up all the cloud providers that we support. We count/store them separately in the DB
                // so that we can report on each provider if required in the future.
                cloudInstances += sum.getInstances();
                cloudCores += sum.getCores();
                cloudSockets += sum.getSockets();
            }
        }

        snapshot.setDate(snapDate);
        snapshot.setCloudInstanceCount(cloudInstances);
        snapshot.setCloudCores(cloudCores);
        snapshot.setCloudSockets(cloudSockets);
        snapshot.setHasData(true);
        return snapshot;
    }

}
