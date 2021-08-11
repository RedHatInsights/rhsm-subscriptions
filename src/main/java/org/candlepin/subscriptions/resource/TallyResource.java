/*
 * Copyright Red Hat, Inc.
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

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.ProductProfileRegistry;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.tally.filler.ReportFiller;
import org.candlepin.subscriptions.tally.filler.ReportFillerFactory;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.model.TallyReportMeta;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.TallyApi;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Tally API implementation. */
@Component
public class TallyResource implements TallyApi {

  private final TallySnapshotRepository repository;
  private final PageLinkCreator pageLinkCreator;
  private final ApplicationClock clock;
  private final ProductProfileRegistry productProfileRegistry;

  @Context private UriInfo uriInfo;

  public TallyResource(
      TallySnapshotRepository repository,
      PageLinkCreator pageLinkCreator,
      ApplicationClock clock,
      ProductProfileRegistry productProfileRegistry) {
    this.repository = repository;
    this.pageLinkCreator = pageLinkCreator;
    this.clock = clock;
    this.productProfileRegistry = productProfileRegistry;
  }

  @SuppressWarnings("linelength")
  @Override
  @ReportingAccessRequired
  public TallyReport getTallyReport(
      ProductId productId,
      @NotNull GranularityType granularityType,
      @NotNull OffsetDateTime beginning,
      @NotNull OffsetDateTime ending,
      Integer offset,
      @Min(1) Integer limit,
      ServiceLevelType sla,
      UsageType usageType,
      Boolean useRunningTotalsFormat) {
    // When limit and offset are not specified, we will fill the report with dummy
    // records from beginning to ending dates. Otherwise we page as usual.
    Pageable pageable = null;
    boolean fill = limit == null && offset == null;
    if (!fill) {
      pageable = ResourceUtils.getPageable(offset, limit);
    }

    String accountNumber = ResourceUtils.getAccountNumber();
    ServiceLevel serviceLevel = ResourceUtils.sanitizeServiceLevel(sla);
    Usage effectiveUsage = ResourceUtils.sanitizeUsage(usageType);
    Granularity granularityFromValue = Granularity.fromString(granularityType.toString());

    try {
      /* Throw an error if we are asked to return reports at a finer grain than what is supported by
       * product.  Ideally, those reports should not even exist, but we want to inform the user that
       * their request is a non sequitur. */
      productProfileRegistry.validateGranularityCompatibility(productId, granularityFromValue);
    } catch (IllegalStateException e) {
      // Combined with our logging configuration, this tells the OnMdcEvaluator class to suppress
      // the stacktrace
      MDC.put("INVALID_GRANULARITY", Boolean.TRUE.toString());
      throw new BadRequestException(e.getMessage());
    }

    Page<org.candlepin.subscriptions.db.model.TallySnapshot> snapshotPage =
        repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                accountNumber,
                productId.toString(),
                granularityFromValue,
                serviceLevel,
                effectiveUsage,
                beginning,
                ending,
                pageable);

    List<TallySnapshot> snaps =
        snapshotPage.stream()
            .map(org.candlepin.subscriptions.db.model.TallySnapshot::asApiSnapshot)
            .collect(Collectors.toList());

    TallyReport report = new TallyReport();
    report.setData(snaps);
    report.setMeta(new TallyReportMeta());
    report.getMeta().setGranularity(granularityFromValue.asOpenApiEnum());
    report.getMeta().setProduct(productId);
    report.getMeta().setServiceLevel(sla);
    report.getMeta().setUsage(usageType == null ? null : effectiveUsage.asOpenApiEnum());
    report.getMeta().setTotalCoreHours(getTotalCoreHours(report));
    report.getMeta().setTotalInstanceHours(getTotalInstanceHours(report));

    if (Boolean.TRUE.equals(useRunningTotalsFormat)) {
      transformToRunningTotalFormat(report);
    }

    // Only set page links if we are paging (not filling).
    if (pageable != null) {
      report.setLinks(pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage));
    }

    // Fill the report gaps if no paging was requested.
    if (fill) {
      ReportFiller reportFiller = ReportFillerFactory.getInstance(clock, granularityFromValue);
      reportFiller.fillGaps(report, beginning, ending, useRunningTotalsFormat);
    }

    // Set the count last since the report may have gotten filled.
    report.getMeta().setCount(report.getData().size());

    return report;
  }

  private Double getTotalCoreHours(TallyReport report) {
    return report.getData().stream()
        .mapToDouble(snapshot -> Optional.ofNullable(snapshot.getCoreHours()).orElse(0.0))
        .sum();
  }

  private Double getTotalInstanceHours(TallyReport report) {
    return report.getData().stream()
        .mapToDouble(snapshot -> Optional.ofNullable(snapshot.getInstanceHours()).orElse(0.0))
        .sum();
  }

  private void transformToRunningTotalFormat(TallyReport report) {
    Map<Uom, Double> runningTotals = new EnumMap<>(Measurement.Uom.class);
    report
        .getData()
        .forEach(
            snapshot -> {
              double snapshotHours = Optional.ofNullable(snapshot.getCoreHours()).orElse(0.0);
              Double newValue =
                  runningTotals.getOrDefault(Measurement.Uom.CORES, 0.0) + snapshotHours;
              snapshot.setCoreHours(newValue);
              runningTotals.put(Measurement.Uom.CORES, newValue);
            });
  }
}
