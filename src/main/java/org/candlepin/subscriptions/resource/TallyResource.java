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
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.tally.filler.ReportFiller;
import org.candlepin.subscriptions.tally.filler.ReportFillerFactory;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;
import org.candlepin.subscriptions.utilization.api.resources.TallyApi;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Tally API implementation. */
@Component
@Slf4j
public class TallyResource implements TallyApi {

  private static final Map<ReportCategory, Set<HardwareMeasurementType>> CATEGORY_MAP =
      Map.of(
          ReportCategory.PHYSICAL, Set.of(HardwareMeasurementType.PHYSICAL),
          ReportCategory.VIRTUAL, Set.of(HardwareMeasurementType.VIRTUAL),
          ReportCategory.HYPERVISOR, Set.of(HardwareMeasurementType.HYPERVISOR),
          ReportCategory.CLOUD, new HashSet<>(HardwareMeasurementType.getCloudProviderTypes()));
  private final TallySnapshotRepository repository;
  private final PageLinkCreator pageLinkCreator;
  private final ApplicationClock clock;
  private final TagProfile tagProfile;

  @Context private UriInfo uriInfo;

  @Autowired
  public TallyResource(
      TallySnapshotRepository repository,
      PageLinkCreator pageLinkCreator,
      ApplicationClock clock,
      TagProfile tagProfile) {
    this.repository = repository;
    this.pageLinkCreator = pageLinkCreator;
    this.clock = clock;
    this.tagProfile = tagProfile;
  }

  @Override
  @ReportingAccessRequired
  public TallyReportData getTallyReportData(
      ProductId productId,
      MetricId metricId,
      GranularityType granularityType,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usageType,
      BillingProviderType billingProviderType,
      String billingAcctId,
      Integer offset,
      Integer limit,
      Boolean useRunningTotalsFormat) {
    ReportCriteria reportCriteria =
        extractReportCriteria(
            productId,
            metricId,
            granularityType,
            beginning,
            ending,
            category,
            sla,
            usageType,
            billingProviderType,
            billingAcctId,
            offset,
            limit);

    Page<org.candlepin.subscriptions.db.model.TallySnapshot> snapshotPage =
        repository.findSnapshot(
            reportCriteria.getOrgId(),
            reportCriteria.getProductId(),
            reportCriteria.getGranularity(),
            reportCriteria.getServiceLevel(),
            reportCriteria.getUsage(),
            reportCriteria.getBillingProvider(),
            reportCriteria.getBillingAccountId(),
            reportCriteria.getBeginning(),
            reportCriteria.getEnding(),
            reportCriteria.getPageable());

    Uom uom = Uom.fromValue(metricId.toString());

    List<org.candlepin.subscriptions.db.model.TallySnapshot> snapshots =
        snapshotPage.stream().collect(Collectors.toList());

    List<TallyReportDataPoint> snaps =
        snapshots.stream()
            .map(snapshot -> dataPointFromSnapshot(uom, category, snapshot))
            .collect(Collectors.toList());

    TallyReportData report = new TallyReportData();
    report.setData(snaps);
    report.setMeta(new TallyReportDataMeta());
    report.getMeta().setGranularity(reportCriteria.getGranularity().asOpenApiEnum());
    report.getMeta().setProduct(productId);
    report.getMeta().setMetricId(metricId.toString());
    report.getMeta().setServiceLevel(sla);
    report.getMeta().setUsage(usageType == null ? null : reportCriteria.getUsage().asOpenApiEnum());
    report
        .getMeta()
        .setBillingProvider(
            billingProviderType == null
                ? null
                : reportCriteria.getBillingProvider().asOpenApiEnum());
    report.getMeta().setBillingAcountId(billingAcctId);

    if (Boolean.TRUE.equals(useRunningTotalsFormat)) {
      transformToRunningTotalFormat(report, uom);
    }

    // NOTE: rather than keep a separate monthly rollup, in order to avoid unnecessary storage and
    // DB round-trips, deserialization, etc., simply aggregate in-memory the monthly totals here.
    // NOTE: In order to avoid incorrect aggregations, if there is not exactly a full month (e.g.
    // custom API usage), we'll log a warning and omit totalMonthly if the range doesn't match
    // expected UI usage, or if paging is requested.
    // NOTE: the UI's precision for end of month is less than the backends. By truncating to
    // seconds, we relax the comparison.
    if (clock.startOfMonth(reportCriteria.getBeginning()).equals(reportCriteria.getBeginning())
        && clock
            .endOfMonth(reportCriteria.getBeginning())
            .truncatedTo(ChronoUnit.SECONDS)
            .equals(reportCriteria.getEnding().truncatedTo(ChronoUnit.SECONDS))
        && reportCriteria.getPageable() == null) {
      // gather monthly totals for the month
      TallyReportDataPoint totalMonthly =
          report.getData().stream()
              .collect(
                  () ->
                      new TallyReportDataPoint()
                          .value(0.0) // set value to avoid NPE
                          .hasData(false), // indicate in API there is no data
                  this::combineDataPointsForTotal,
                  this::combineDataPointsForTotal);
      report.getMeta().setTotalMonthly(totalMonthly);
    } else {
      log.warn(
          "Tally API called for a range more or less than a full month. Not populating totalMonthly");
    }

    // Only set page links if we are paging (not filling).
    if (reportCriteria.getPageable() != null) {
      report.setLinks(pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage));
    }

    // Fill the report gaps if no paging was requested.
    if (reportCriteria.getPageable() == null) {
      ReportFiller<TallyReportDataPoint> reportFiller =
          ReportFillerFactory.getDataPointReportFiller(clock, reportCriteria.getGranularity());
      report.setData(reportFiller.fillGaps(report.getData(), beginning, ending, false));
    }

    // Set the count last since the report may have gotten filled.
    report.getMeta().setCount(report.getData().size());
    report
        .getMeta()
        .setHasCloudigradeData(
            snapshots.stream().anyMatch(snapshot -> hasCloudigradeData(snapshot, uom)));
    report
        .getMeta()
        .setHasCloudigradeMismatch(
            snapshots.stream().anyMatch(snapshot -> hasCloudigradeMismatch(snapshot, uom)));

    return report;
  }

  private void combineDataPointsForTotal(
      TallyReportDataPoint result, TallyReportDataPoint newDataPoint) {
    if (!Boolean.TRUE.equals(newDataPoint.getHasData())) {
      return;
    }
    if (newDataPoint
        .getDate()
        .isAfter(Optional.ofNullable(result.getDate()).orElse(OffsetDateTime.MIN))) {
      result.setDate(newDataPoint.getDate());
    }
    result.setHasData(true);
    result.setValue(result.getValue() + newDataPoint.getValue());
  }

  @SuppressWarnings("java:S5738")
  private boolean hasCloudigradeData(
      org.candlepin.subscriptions.db.model.TallySnapshot tallySnapshot, Uom uom) {
    Double measurement = tallySnapshot.getMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, uom);
    return measurement != null && measurement > 0.0;
  }

  @SuppressWarnings("java:S5738")
  private boolean hasCloudigradeMismatch(
      org.candlepin.subscriptions.db.model.TallySnapshot tallySnapshot, Uom uom) {
    Double cloudigradeMeasurement =
        tallySnapshot.getMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, uom);
    Double hbiMeasurement = tallySnapshot.getMeasurement(HardwareMeasurementType.AWS, uom);
    return cloudigradeMeasurement != null
        && !Objects.equals(cloudigradeMeasurement, hbiMeasurement);
  }

  /** Validate and extract report criteria */
  @SuppressWarnings("java:S107")
  private ReportCriteria extractReportCriteria(
      ProductId productId,
      MetricId metricId,
      GranularityType granularityType,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usageType,
      BillingProviderType billingProviderType,
      String billingAccountId,
      Integer offset,
      Integer limit) {
    // When limit and offset are not specified, we will fill the report with placeholder
    // records from beginning to ending dates. Otherwise we page as usual.
    Pageable pageable = null;
    if (limit != null || offset != null) {
      pageable = ResourceUtils.getPageable(offset, limit);
    }

    // Sanitize null value as _ANY for optional fields to filter through snapshot table.
    ServiceLevel serviceLevel = ResourceUtils.sanitizeServiceLevel(sla);
    Usage effectiveUsage = ResourceUtils.sanitizeUsage(usageType);
    Granularity granularityFromValue = Granularity.fromString(granularityType.toString());
    BillingProvider providerType = ResourceUtils.sanitizeBillingProvider(billingProviderType);
    String sanitizedBillingAcctId = ResourceUtils.sanitizeBillingAccountId(billingAccountId);

    try {
      /* Throw an error if we are asked to return reports at a finer grain than what is supported by
       * product.  Ideally, those reports should not even exist, but we want to inform the user that
       * their request is a non sequitur. */
      tagProfile.validateGranularityCompatibility(productId, granularityFromValue);
    } catch (IllegalStateException e) {
      // Combined with our logging configuration, this tells the OnMdcEvaluator class to suppress
      // the stacktrace
      MDC.put("INVALID_GRANULARITY", Boolean.TRUE.toString());
      throw new BadRequestException(e.getMessage());
    }
    return ReportCriteria.builder()
        .orgId(ResourceUtils.getOrgId())
        .productId(productId.toString())
        .metricId(Optional.ofNullable(metricId).map(MetricId::toString).orElse(null))
        .granularity(granularityFromValue)
        .reportCategory(category)
        .serviceLevel(serviceLevel)
        .usage(effectiveUsage)
        .billingProvider(providerType)
        .billingAccountId(sanitizedBillingAcctId)
        .pageable(pageable)
        .beginning(beginning)
        .ending(ending)
        .build();
  }

  private TallyReportDataPoint dataPointFromSnapshot(
      Uom uom,
      ReportCategory category,
      org.candlepin.subscriptions.db.model.TallySnapshot snapshot) {
    double value = extractValue(uom, category, snapshot);
    return new TallyReportDataPoint().date(snapshot.getSnapshotDate()).value(value).hasData(true);
  }

  private double extractValue(
      Uom uom,
      ReportCategory category,
      org.candlepin.subscriptions.db.model.TallySnapshot snapshot) {
    Set<HardwareMeasurementType> contributingTypes = getContributingTypes(category);
    return contributingTypes.stream()
        .mapToDouble(type -> Optional.ofNullable(snapshot.getMeasurement(type, uom)).orElse(0.0))
        .sum();
  }

  private Set<HardwareMeasurementType> getContributingTypes(ReportCategory category) {
    Set<HardwareMeasurementType> contributingTypes;
    if (category == null) {
      contributingTypes = Set.of(HardwareMeasurementType.TOTAL);
    } else {
      contributingTypes = CATEGORY_MAP.get(category);
    }
    return contributingTypes;
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
    ReportCriteria reportCriteria =
        extractReportCriteria(
            productId,
            null,
            granularityType,
            beginning,
            ending,
            null,
            sla,
            usageType,
            null,
            null,
            offset,
            limit);

    Page<org.candlepin.subscriptions.db.model.TallySnapshot> snapshotPage =
        repository.findSnapshot(
            reportCriteria.getOrgId(),
            reportCriteria.getProductId(),
            reportCriteria.getGranularity(),
            reportCriteria.getServiceLevel(),
            reportCriteria.getUsage(),
            reportCriteria.getBillingProvider(),
            reportCriteria.getBillingAccountId(),
            reportCriteria.getBeginning(),
            reportCriteria.getEnding(),
            reportCriteria.getPageable());

    List<TallySnapshot> snaps =
        snapshotPage.stream()
            .map(org.candlepin.subscriptions.db.model.TallySnapshot::asApiSnapshot)
            .collect(Collectors.toList());

    TallyReport report = new TallyReport();
    report.setData(snaps);
    report.setMeta(new TallyReportMeta());
    report.getMeta().setGranularity(reportCriteria.getGranularity().asOpenApiEnum());
    report.getMeta().setProduct(productId);
    report.getMeta().setServiceLevel(sla);
    report.getMeta().setUsage(usageType == null ? null : reportCriteria.getUsage().asOpenApiEnum());
    report.getMeta().setTotalCoreHours(getTotalCoreHours(report));
    report.getMeta().setTotalInstanceHours(getTotalInstanceHours(report));

    if (Boolean.TRUE.equals(useRunningTotalsFormat)) {
      transformToRunningTotalFormat(report);
    }

    // Only set page links if we are paging (not filling).
    if (reportCriteria.getPageable() != null) {
      report.setLinks(pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage));
    }

    // Fill the report gaps if no paging was requested.
    if (reportCriteria.getPageable() == null) {
      ReportFiller<TallySnapshot> reportFiller =
          ReportFillerFactory.getInstance(clock, reportCriteria.getGranularity());
      report.setData(
          reportFiller.fillGaps(report.getData(), beginning, ending, useRunningTotalsFormat));
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

  private void transformToRunningTotalFormat(TallyReportData report, Uom uom) {
    Map<Uom, Double> runningTotals = new EnumMap<>(Measurement.Uom.class);
    report
        .getData()
        .forEach(
            snapshot -> {
              double snapshotTotal = Optional.ofNullable(snapshot.getValue()).orElse(0.0);
              Double newValue = runningTotals.getOrDefault(uom, 0.0) + snapshotTotal;
              snapshot.setValue(newValue);
              runningTotals.put(uom, newValue);
            });
  }
}
