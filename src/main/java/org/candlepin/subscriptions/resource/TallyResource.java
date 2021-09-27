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
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurement;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
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
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.model.TallyReportData;
import org.candlepin.subscriptions.utilization.api.model.TallyReportDataMeta;
import org.candlepin.subscriptions.utilization.api.model.TallyReportDataPoint;
import org.candlepin.subscriptions.utilization.api.model.TallyReportMeta;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.TallyApi;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Tally API implementation. */
@Component
public class TallyResource implements TallyApi {

  private static final Map<ReportCategory, Set<HardwareMeasurementType>> CATEGORY_MAP =
      Map.of(
          ReportCategory.PHYSICAL, Set.of(HardwareMeasurementType.PHYSICAL),
          ReportCategory.VIRTUAL,
              Set.of(HardwareMeasurementType.VIRTUAL, HardwareMeasurementType.HYPERVISOR),
          ReportCategory.CLOUD, new HashSet<>(HardwareMeasurementType.getCloudProviderTypes()));
  private final TallySnapshotRepository repository;
  private final PageLinkCreator pageLinkCreator;
  private final ApplicationClock clock;
  private final ProductProfileRegistry productProfileRegistry;

  @Context private UriInfo uriInfo;

  @Autowired
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

  @Override
  public TallyReportData getTallyReportData(
      ProductId productId,
      String metricId,
      GranularityType granularityType,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usageType,
      Integer offset,
      Integer limit) {
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
            offset,
            limit);

    Page<org.candlepin.subscriptions.db.model.TallySnapshot> snapshotPage =
        repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                reportCriteria.getAccountNumber(),
                reportCriteria.getProductId(),
                reportCriteria.getGranularity(),
                reportCriteria.getServiceLevel(),
                reportCriteria.getUsage(),
                reportCriteria.getBeginning(),
                reportCriteria.getEnding(),
                reportCriteria.getPageable());

    Uom uom = Uom.fromValue(metricId);

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
    report.getMeta().setServiceLevel(sla);
    report.getMeta().setUsage(usageType == null ? null : reportCriteria.getUsage().asOpenApiEnum());

    Page<org.candlepin.subscriptions.db.model.TallySnapshot> monthlySnaps =
        repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                reportCriteria.getAccountNumber(),
                reportCriteria.getProductId(),
                Granularity.MONTHLY,
                reportCriteria.getServiceLevel(),
                reportCriteria.getUsage(),
                reportCriteria.getBeginning(),
                reportCriteria.getEnding(),
                reportCriteria.getPageable());

    // there should be only one monthly retrieved, but just in case, we'll pick the last
    monthlySnaps.stream()
        .map(snapshot -> dataPointFromSnapshot(uom, category, snapshot))
        .forEachOrdered(report.getMeta()::setTotalMonthly);

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

  // NOTE(khowell): deprecated method to be removed by https://issues.redhat.com/browse/ENT-3545
  @SuppressWarnings("java:S5738")
  private boolean hasCloudigradeData(
      org.candlepin.subscriptions.db.model.TallySnapshot tallySnapshot, Uom uom) {
    if (tallySnapshot.getTallyMeasurements().isEmpty()) {
      HardwareMeasurement hardwareMeasurement =
          tallySnapshot.getHardwareMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE);
      return hardwareMeasurement != null && extractLegacyValue(hardwareMeasurement, uom) > 0.0;
    }
    Double measurement = tallySnapshot.getMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, uom);
    return measurement != null && measurement > 0.0;
  }

  // NOTE(khowell): deprecated method to be removed by https://issues.redhat.com/browse/ENT-3545
  @SuppressWarnings("java:S5738")
  private boolean hasCloudigradeMismatch(
      org.candlepin.subscriptions.db.model.TallySnapshot tallySnapshot, Uom uom) {
    if (tallySnapshot.getTallyMeasurements().isEmpty()) {
      HardwareMeasurement cloudigradeMeasurement =
          tallySnapshot.getHardwareMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE);
      HardwareMeasurement hbiMeasurement =
          tallySnapshot.getHardwareMeasurement(HardwareMeasurementType.AWS);
      return cloudigradeMeasurement != null
          && (hbiMeasurement == null
              || extractLegacyValue(cloudigradeMeasurement, uom)
                  != extractLegacyValue(hbiMeasurement, uom));
    }
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
      String metricId,
      GranularityType granularityType,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usageType,
      Integer offset,
      Integer limit) {
    // When limit and offset are not specified, we will fill the report with dummy
    // records from beginning to ending dates. Otherwise we page as usual.
    Pageable pageable = null;
    if (limit != null || offset != null) {
      pageable = ResourceUtils.getPageable(offset, limit);
    }

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
    return ReportCriteria.builder()
        .accountNumber(ResourceUtils.getAccountNumber())
        .productId(productId.toString())
        .metricId(metricId)
        .granularity(granularityFromValue)
        .reportCategory(category)
        .serviceLevel(serviceLevel)
        .usage(effectiveUsage)
        .pageable(pageable)
        .beginning(beginning)
        .ending(ending)
        .build();
  }

  private TallyReportDataPoint dataPointFromSnapshot(
      Uom uom,
      ReportCategory category,
      org.candlepin.subscriptions.db.model.TallySnapshot snapshot) {
    double value;
    if (snapshot.getTallyMeasurements().isEmpty()) {
      value = extractLegacyValue(uom, category, snapshot);
    } else {
      value = extractValue(uom, category, snapshot);
    }
    return new TallyReportDataPoint().date(snapshot.getSnapshotDate()).value(value).hasData(true);
  }

  // NOTE(khowell): deprecated method to be removed by https://issues.redhat.com/browse/ENT-3545
  @SuppressWarnings("java:S5738")
  private double extractLegacyValue(
      Uom uom,
      ReportCategory category,
      org.candlepin.subscriptions.db.model.TallySnapshot snapshot) {
    Set<HardwareMeasurementType> contributingTypes = getContributingTypes(category);
    return contributingTypes.stream()
        .map(snapshot::getHardwareMeasurement)
        .filter(Objects::nonNull)
        .mapToDouble(m -> extractLegacyValue(m, uom))
        .sum();
  }

  private double extractLegacyValue(HardwareMeasurement hardwareMeasurement, Uom uom) {
    switch (uom) {
      case CORES:
        return hardwareMeasurement.getCores();
      case SOCKETS:
        return hardwareMeasurement.getSockets();
      default:
        throw new IllegalArgumentException(uom + " cannot be extracted from HardwareMeasurement");
    }
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
            offset,
            limit);

    Page<org.candlepin.subscriptions.db.model.TallySnapshot> snapshotPage =
        repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                reportCriteria.getAccountNumber(),
                reportCriteria.getProductId(),
                reportCriteria.getGranularity(),
                reportCriteria.getServiceLevel(),
                reportCriteria.getUsage(),
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
}
