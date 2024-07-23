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
package org.candlepin.subscriptions.resource.api.v1;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionGranularity;
import com.redhat.swatch.configuration.registry.Variant;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.ReportCriteria;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.tally.filler.ReportFiller;
import org.candlepin.subscriptions.tally.filler.ReportFillerFactory;
import org.candlepin.subscriptions.tally.filler.UnroundedTallyReportDataPoint;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingCategory;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.v1.model.CapacitySnapshotByMetricId;
import org.candlepin.subscriptions.utilization.api.v1.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v1.model.TallyReportData;
import org.candlepin.subscriptions.utilization.api.v1.model.TallyReportDataMeta;
import org.candlepin.subscriptions.utilization.api.v1.model.TallyReportTotalMonthly;
import org.candlepin.subscriptions.utilization.api.v1.model.UsageType;
import org.candlepin.subscriptions.utilization.api.v1.resources.TallyApi;
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
  private final ApiModelMapperV1 mapper;
  private final TallySnapshotRepository repository;
  private final PageLinkCreator pageLinkCreator;
  private final ApplicationClock clock;
  private final CapacityResource capacityResource;

  @Context private UriInfo uriInfo;

  @Autowired
  public TallyResource(
      ApiModelMapperV1 mapper,
      TallySnapshotRepository repository,
      PageLinkCreator pageLinkCreator,
      ApplicationClock clock,
      CapacityResource capacityResource) {
    this.mapper = mapper;
    this.repository = repository;
    this.pageLinkCreator = pageLinkCreator;
    this.clock = clock;
    this.capacityResource = capacityResource;
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
      Boolean useRunningTotalsFormat,
      BillingCategory billingCategory) {

    if (Objects.nonNull(billingCategory) && !Boolean.TRUE.equals(useRunningTotalsFormat)) {
      throw new BadRequestException(
          "When `billing_category` is specified, `use_running_totals_format` must be `true`.");
    }

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
            billingCategory,
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

    Map<OffsetDateTime, Integer> capacityByDate =
        Objects.nonNull(billingCategory)
            ? getCapacityReport(
                productId,
                metricId,
                granularityType,
                beginning,
                ending,
                offset,
                limit,
                category,
                sla,
                usageType)
            : Collections.emptyMap();

    List<org.candlepin.subscriptions.db.model.TallySnapshot> snapshots =
        snapshotPage.stream().toList();

    List<UnroundedTallyReportDataPoint> snaps =
        snapshots.stream()
            .map(snapshot -> unroundedDataPointFromSnapshot(metricId, category, snapshot))
            .collect(Collectors.toList());

    TallyReportData report = new TallyReportData();
    report.setMeta(new TallyReportDataMeta());
    report.getMeta().setGranularity(mapper.map(reportCriteria.getGranularity()));
    report.getMeta().setProduct(productId.toString());
    report.getMeta().setMetricId(metricId.toString());
    report.getMeta().setServiceLevel(sla);
    report.getMeta().setUsage(usageType == null ? null : mapper.map(reportCriteria.getUsage()));
    report
        .getMeta()
        .setBillingProvider(
            billingProviderType == null ? null : mapper.map(reportCriteria.getBillingProvider()));
    report.getMeta().setBillingAcountId(billingAcctId);

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
      OffsetDateTime latestSnapshotDate = null;
      var totalMonthlyValueRaw = 0.0;
      for (var snapshot : snapshots) {
        totalMonthlyValueRaw += extractRawValue(metricId, category, snapshot);
        latestSnapshotDate = snapshot.getSnapshotDate();
      }
      var totalMonthlyValue = (int) Math.ceil(totalMonthlyValueRaw);
      TallyReportTotalMonthly totalMonthly =
          new TallyReportTotalMonthly()
              .hasData(!snapshots.isEmpty())
              .value(totalMonthlyValue)
              .date(latestSnapshotDate);
      report.getMeta().setTotalMonthly(totalMonthly);
    } else {
      log.warn(
          "Tally API called for a range more or less than a full month. Not populating totalMonthly");
    }

    // Only set page links if we are paging (not filling).
    if (reportCriteria.getPageable() != null) {
      report.setLinks(mapper.map(pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage)));
    }

    if (Boolean.TRUE.equals(useRunningTotalsFormat)) {
      snaps = transformToRunningTotalFormat(snaps, metricId, billingCategory, capacityByDate);
    }

    // Fill the report gaps if no paging was requested.
    List<UnroundedTallyReportDataPoint> dataPointsToConvert;
    if (reportCriteria.getPageable() == null) {
      ReportFiller<UnroundedTallyReportDataPoint> reportFiller =
          ReportFillerFactory.getDataPointReportFiller(clock, reportCriteria.getGranularity());
      dataPointsToConvert =
          reportFiller.fillGaps(
              snaps, beginning, ending, Objects.requireNonNullElse(useRunningTotalsFormat, false));
    } else {
      dataPointsToConvert = snaps;
    }
    report.setData(
        dataPointsToConvert.stream()
            .map(UnroundedTallyReportDataPoint::toRoundedDataPoint)
            .toList());

    // Set the count last since the report may have gotten filled.
    report.getMeta().setCount(report.getData().size());
    return report;
  }

  @SuppressWarnings("java:S107")
  private Map<OffsetDateTime, Integer> getCapacityReport(
      ProductId productId,
      MetricId metricId,
      GranularityType granularityType,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      Integer offset,
      Integer limit,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usageType) {

    var capacityReportByMetricId =
        capacityResource.getCapacityReportByMetricId(
            productId,
            metricId,
            GranularityType.valueOf(granularityType.name()),
            beginning,
            ending,
            offset,
            limit,
            Optional.ofNullable(category).map(c -> ReportCategory.valueOf(c.name())).orElse(null),
            Optional.ofNullable(sla).map(s -> ServiceLevelType.valueOf(s.name())).orElse(null),
            Optional.ofNullable(usageType).map(ut -> UsageType.valueOf(ut.name())).orElse(null));
    return capacityReportByMetricId.getData().stream()
        .collect(
            Collectors.toMap(
                CapacitySnapshotByMetricId::getDate, CapacitySnapshotByMetricId::getValue));
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
      BillingCategory billingCategory,
      Integer offset,
      Integer limit) {
    // When limit and offset are not specified, we will fill the report with placeholder
    // records from beginning to ending dates. Otherwise we page as usual.
    Pageable pageable = null;
    if (limit != null || offset != null) {
      pageable = ResourceUtils.getPageable(offset, limit);
    }

    // Sanitize null value as _ANY for optional fields to filter through snapshot table.
    ServiceLevel serviceLevel = ResourceUtils.sanitizeServiceLevel(mapper.map(sla));
    Usage effectiveUsage = ResourceUtils.sanitizeUsage(mapper.map(usageType));
    Granularity granularityFromValue = Granularity.fromString(granularityType.toString());
    BillingProvider providerType =
        ResourceUtils.sanitizeBillingProvider(mapper.map(billingProviderType));
    String sanitizedBillingAcctId = ResourceUtils.sanitizeBillingAccountId(billingAccountId);

    /* Throw an error if we are asked to return reports at a finer grain than what is supported by
     * product.  Ideally, those reports should not even exist, but we want to inform the user that
     * their request is a non sequitur. */
    if (!Variant.isGranularityCompatible(
        productId.toString(), SubscriptionDefinitionGranularity.valueOf(granularityType.name()))) {
      // Combined with our logging configuration, this tells the OnMdcEvaluator class to suppress
      // the stacktrace
      MDC.put("INVALID_GRANULARITY", Boolean.TRUE.toString());
      throw new BadRequestException(
          String.format(
              "%s does not support any granularity finer than %s",
              productId, granularityFromValue.getValue()));
    }
    return ReportCriteria.builder()
        .orgId(ResourceUtils.getOrgId())
        .productId(productId.toString())
        .metricId(Optional.ofNullable(metricId).map(MetricId::toString).orElse(null))
        .granularity(granularityFromValue)
        .reportCategory(mapper.map(category))
        .serviceLevel(serviceLevel)
        .usage(effectiveUsage)
        .billingProvider(providerType)
        .billingAccountId(sanitizedBillingAcctId)
        .billingCategory(mapper.map(billingCategory))
        .pageable(pageable)
        .beginning(beginning)
        .ending(ending)
        .build();
  }

  private UnroundedTallyReportDataPoint unroundedDataPointFromSnapshot(
      MetricId metricId,
      ReportCategory category,
      org.candlepin.subscriptions.db.model.TallySnapshot snapshot) {
    return new UnroundedTallyReportDataPoint(
        snapshot.getSnapshotDate(), extractRawValue(metricId, category, snapshot), true);
  }

  private Double extractRawValue(
      MetricId metricId,
      ReportCategory category,
      org.candlepin.subscriptions.db.model.TallySnapshot snapshot) {
    Set<HardwareMeasurementType> contributingTypes = getContributingTypes(category);
    return contributingTypes.stream()
        .mapToDouble(
            type -> Optional.ofNullable(snapshot.getMeasurement(type, metricId)).orElse(0.0))
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

  private List<UnroundedTallyReportDataPoint> transformToRunningTotalFormat(
      List<UnroundedTallyReportDataPoint> snaps,
      MetricId metricId,
      BillingCategory billingCategory,
      Map<OffsetDateTime, Integer> capacityByDate) {
    Map<MetricId, Double> runningTotals = new HashMap<>();
    List<UnroundedTallyReportDataPoint> runningTotalSnaps = new ArrayList<>();
    snaps.forEach(
        snapshot -> {
          double snapshotTotal = Optional.ofNullable(snapshot.value()).orElse(0.0);
          double newValue = runningTotals.getOrDefault(metricId, 0.0) + snapshotTotal;

          double snapshotValue = newValue;
          double capacity = capacityByDate.getOrDefault(clock.startOfDay(snapshot.date()), 0);
          if (BillingCategory.PREPAID.equals(billingCategory)) {
            snapshotValue = Math.min(newValue, capacity);
          } else if (BillingCategory.ON_DEMAND.equals(billingCategory)) {
            snapshotValue = Math.max(newValue - capacity, 0);
          }

          runningTotalSnaps.add(
              new UnroundedTallyReportDataPoint(
                  snapshot.date(), snapshotValue, snapshot.hasData()));
          runningTotals.put(metricId, newValue);
        });
    return runningTotalSnaps;
  }
}
