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

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionGranularity;
import com.redhat.swatch.configuration.registry.Variant;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.SnapshotTimeAdjuster;
import org.candlepin.subscriptions.utilization.api.model.CapacityReport;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportByMetricId;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportByMetricIdMeta;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportMeta;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshot;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshotByMetricId;
import org.candlepin.subscriptions.utilization.api.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.model.PageLinks;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.CapacityApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Capacity API implementation. */
@Component
@Slf4j
public class CapacityResource implements CapacityApi {

  public static final String SOCKETS = "Sockets";
  public static final String CORES = "Cores";
  public static final String PHYSICAL = HardwareMeasurementType.PHYSICAL.toString().toUpperCase();
  public static final String HYPERVISOR =
      HardwareMeasurementType.HYPERVISOR.toString().toUpperCase();
  private final SubscriptionRepository subscriptionRepository;
  private final PageLinkCreator pageLinkCreator;
  private final ApplicationClock clock;

  @Context UriInfo uriInfo;

  public CapacityResource(
      SubscriptionRepository subscriptionRepository,
      PageLinkCreator pageLinkCreator,
      ApplicationClock clock) {

    this.subscriptionRepository = subscriptionRepository;
    this.pageLinkCreator = pageLinkCreator;
    this.clock = clock;
  }

  /**
   * @deprecated for removal once <a
   *     href="https://issues.redhat.com/browse/SWATCH-218">SWATCH-218</a> is completed
   */
  @Override
  @Deprecated(since = "https://issues.redhat.com/browse/ENT-4384")
  @ReportingAccessRequired
  public CapacityReport getCapacityReport(
      String productIdValue,
      @NotNull GranularityType granularityType,
      @NotNull OffsetDateTime beginning,
      @NotNull OffsetDateTime ending,
      Integer offset,
      @Min(1) Integer limit,
      ServiceLevelType sla,
      UsageType usage) {
    ProductId productId;
    try {
      productId = ProductId.fromString(productIdValue);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e);
    }

    // capacity records do not include _ANY rows
    ServiceLevel sanitizedServiceLevel = ResourceUtils.sanitizeServiceLevel(sla);
    if (sanitizedServiceLevel == ServiceLevel._ANY) {
      sanitizedServiceLevel = null;
    }

    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
    if (sanitizedUsage == Usage._ANY) {
      sanitizedUsage = null;
    }

    Granularity granularityValue = Granularity.fromString(granularityType.toString());
    String orgId = ResourceUtils.getOrgId();
    List<CapacitySnapshot> capacities =
        getCapacities(
            orgId,
            productId,
            sanitizedServiceLevel,
            sanitizedUsage,
            granularityValue,
            beginning,
            ending);

    List<CapacitySnapshot> data;
    PageLinks links;
    if (offset != null || limit != null) {
      Pageable pageable = ResourceUtils.getPageable(offset, limit);
      data = paginate(capacities, pageable);
      Page<CapacitySnapshot> snapshotPage =
          new PageImpl<>(data, pageable, capacities.size()); // NOSONAR
      links = pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage);
    } else {
      data = capacities;
      links = null;
    }

    CapacityReport report = new CapacityReport();
    report.setData(data);
    report.setMeta(new CapacityReportMeta());
    report.getMeta().setGranularity(granularityType);
    report.getMeta().setProduct(productId.toString());
    report.getMeta().setCount(report.getData().size());

    if (sanitizedServiceLevel != null) {
      report.getMeta().setServiceLevel(sanitizedServiceLevel.asOpenApiEnum());
    }

    if (sanitizedUsage != null) {
      report.getMeta().setUsage(sanitizedUsage.asOpenApiEnum());
    }

    report.setLinks(links);

    return report;
  }

  @Override
  @ReportingAccessRequired
  public CapacityReportByMetricId getCapacityReportByMetricId(
      String productIdValue,
      String metricIdValue,
      @NotNull GranularityType granularityType,
      @NotNull OffsetDateTime beginning,
      @NotNull OffsetDateTime ending,
      Integer offset,
      @Min(1) Integer limit,
      ReportCategory reportCategory,
      ServiceLevelType sla,
      UsageType usage) {

    ProductId productId;
    MetricId metricId;
    try {
      productId = ProductId.fromString(productIdValue);
      metricId = MetricId.fromString(metricIdValue);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e);
    }
    log.debug(
        "Get capacity report for product {} by metric {} in range [{}, {}] for category {}",
        productId,
        metricId,
        beginning,
        ending,
        reportCategory);
    HypervisorReportCategory hypervisorReportCategory =
        HypervisorReportCategory.mapCategory(reportCategory);

    // capacity records do not include _ANY rows
    ServiceLevel sanitizedServiceLevel = ResourceUtils.sanitizeServiceLevel(sla);
    if (sanitizedServiceLevel == ServiceLevel._ANY) {
      sanitizedServiceLevel = null;
    }

    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
    if (sanitizedUsage == Usage._ANY) {
      sanitizedUsage = null;
    }

    Granularity granularityValue = Granularity.fromString(granularityType.toString());
    String orgId = ResourceUtils.getOrgId();
    List<CapacitySnapshotByMetricId> capacities =
        getCapacitiesByMetricId(
            orgId,
            productId,
            metricId,
            hypervisorReportCategory,
            sanitizedServiceLevel,
            sanitizedUsage,
            granularityValue,
            beginning,
            ending);

    List<CapacitySnapshotByMetricId> data;
    PageLinks links;
    if (offset != null || limit != null) {
      Pageable pageable = ResourceUtils.getPageable(offset, limit);
      data = paginate(capacities, pageable);
      Page<CapacitySnapshotByMetricId> snapshotPage =
          new PageImpl<>(data, pageable, capacities.size());
      links = pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage);
    } else {
      data = capacities;
      links = null;
    }

    CapacityReportByMetricId report = new CapacityReportByMetricId();
    report.setData(data);
    report.setMeta(new CapacityReportByMetricIdMeta());
    var meta = report.getMeta();
    meta.setGranularity(granularityType);
    meta.setProduct(productId.toString());
    meta.setMetricId(metricId.toString());
    meta.setCategory(reportCategory);
    meta.setCount(report.getData().size());

    if (sanitizedServiceLevel != null) {
      meta.setServiceLevel(sanitizedServiceLevel.asOpenApiEnum());
    }

    if (sanitizedUsage != null) {
      meta.setUsage(sanitizedUsage.asOpenApiEnum());
    }

    report.setLinks(links);

    return report;
  }

  protected List<CapacitySnapshot> getCapacities(
      String orgId,
      ProductId productId,
      ServiceLevel sla,
      Usage usage,
      Granularity granularity,
      @NotNull OffsetDateTime reportBegin,
      @NotNull OffsetDateTime reportEnd) {

    /* Throw an error if we are asked to generate capacity reports at a finer granularity than what is
     * supported by the product.  The reports created would be technically accurate, but would convey the
     * false impression that we have capacity information at that fine of a granularity.  This decision is
     * on of personal judgment and it may be appropriate to reverse it at a later date. */
    validateGranularity(productId, granularity);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId(orgId)
            .productId(productId.toString())
            .serviceLevel(sla)
            .usage(usage)
            .beginning(reportBegin)
            .ending(reportEnd)
            .build();

    List<Subscription> subscriptions =
        subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted());
    List<Subscription> unlimitedSubscriptions =
        subscriptionRepository.findUnlimited(dbReportCriteria);

    SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);

    OffsetDateTime start = timeAdjuster.adjustToPeriodStart(reportBegin);
    OffsetDateTime end = timeAdjuster.adjustToPeriodEnd(reportEnd);
    TemporalAmount offset = timeAdjuster.getSnapshotOffset();

    List<CapacitySnapshot> result = new ArrayList<>();
    OffsetDateTime next = OffsetDateTime.from(start);

    while (next.isBefore(end) || next.isEqual(end)) {
      CapacitySnapshot capacitySnapshot = createCapacitySnapshot(next, subscriptions);
      capacitySnapshot.setHasInfiniteQuantity(hasInfiniteQuantity(next, unlimitedSubscriptions));
      result.add(capacitySnapshot);
      next = timeAdjuster.adjustToPeriodStart(next.plus(offset));
    }

    return result;
  }

  private boolean hasInfiniteQuantity(
      OffsetDateTime date, List<Subscription> unlimitedSubscriptions) {
    for (Subscription subscription : unlimitedSubscriptions) {
      if (subscription.getStartDate().isBefore(date) && subscription.getEndDate().isAfter(date)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("java:S107")
  protected List<CapacitySnapshotByMetricId> getCapacitiesByMetricId(
      String orgId,
      ProductId productId,
      MetricId metricId,
      HypervisorReportCategory hypervisorReportCategory,
      ServiceLevel sla,
      Usage usage,
      Granularity granularity,
      @NotNull OffsetDateTime reportBegin,
      @NotNull OffsetDateTime reportEnd) {

    /* Throw an error if we are asked to generate capacity reports at a finer granularity than what is
     * supported by the product.  The reports created would be technically accurate, but would convey the
     * false impression that we have capacity information at that fine of a granularity.  This decision is
     * on of personal judgment and it may be appropriate to reverse it at a later date. */
    validateGranularity(productId, granularity);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId(orgId)
            .productId(productId.toString())
            .serviceLevel(sla)
            .usage(usage)
            .beginning(reportBegin)
            .ending(reportEnd)
            .hypervisorReportCategory(hypervisorReportCategory)
            .metricId(metricId == null ? null : metricId.toString())
            .build();

    var subscriptions = subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted());
    List<Subscription> unlimitedSubscriptions =
        subscriptionRepository.findUnlimited(dbReportCriteria);

    SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);

    OffsetDateTime start = timeAdjuster.adjustToPeriodStart(reportBegin);
    OffsetDateTime end = timeAdjuster.adjustToPeriodEnd(reportEnd);
    TemporalAmount offset = timeAdjuster.getSnapshotOffset();

    List<CapacitySnapshotByMetricId> result = new ArrayList<>();
    OffsetDateTime next = OffsetDateTime.from(start);

    while (next.isBefore(end) || next.isEqual(end)) {
      CapacitySnapshotByMetricId snapshot =
          createCapacitySnapshotWithMetricId(
              next, subscriptions, metricId, Optional.ofNullable(hypervisorReportCategory));
      snapshot.setHasInfiniteQuantity(hasInfiniteQuantity(next, unlimitedSubscriptions));
      result.add(snapshot);
      next = timeAdjuster.adjustToPeriodStart(next.plus(offset));
    }

    return result;
  }

  private <T> List<T> paginate(List<T> capacities, Pageable pageable) {
    if (pageable == null) {
      return capacities;
    }
    int offset = pageable.getPageNumber() * pageable.getPageSize();
    int lastIndex = Math.min(capacities.size(), offset + pageable.getPageSize());
    return capacities.subList(offset, lastIndex);
  }

  @SuppressWarnings("java:S3776")
  protected CapacitySnapshot createCapacitySnapshot(
      OffsetDateTime date, List<Subscription> subscriptions) {
    // NOTE there is room for future optimization here, as we're *generally* calculating the
    // same sum across a time range, also we might opt to do some of this in the DB query in the
    // future.
    int sockets = 0;
    int physicalSockets = 0;
    int hypervisorSockets = 0;
    int cores = 0;
    int physicalCores = 0;
    int hypervisorCores = 0;

    for (Subscription subscription : subscriptions) {
      var begin = subscription.getStartDate();
      var end = subscription.getEndDate();

      if ((begin == null || begin.isBefore(date)) && (end == null || end.isAfter(date))) {
        for (var entry : subscription.getSubscriptionMeasurements().entrySet()) {
          var measurementKey = entry.getKey();
          var measurementValue = entry.getValue();
          var measurementType = measurementKey.getMeasurementType();
          var isPhysical = PHYSICAL.equals(measurementType);
          var isHypervisor = HYPERVISOR.equals(measurementType);

          if (CORES.equals(measurementKey.getMetricId())) {
            var val = measurementValue.intValue();
            cores += val;
            if (isPhysical) {
              physicalCores += val;
            } else if (isHypervisor) {
              hypervisorCores += val;
            }
          } else if (SOCKETS.equals(measurementKey.getMetricId())) {
            var val = measurementValue.intValue();
            sockets += val;
            if (isPhysical) {
              physicalSockets += val;
            } else if (isHypervisor) {
              hypervisorSockets += val;
            }
          }
        }
      }
    }

    return new CapacitySnapshot()
        .date(date)
        .sockets(sockets)
        .physicalSockets(physicalSockets)
        .hypervisorSockets(hypervisorSockets)
        .cores(cores)
        .physicalCores(physicalCores)
        .hypervisorCores(hypervisorCores);
  }

  @SuppressWarnings("java:S3776")
  protected CapacitySnapshotByMetricId createCapacitySnapshotWithMetricId(
      OffsetDateTime date,
      List<Subscription> subscriptions,
      MetricId metricId,
      Optional<HypervisorReportCategory> hypervisorReportCategory) {
    int value = 0;
    boolean hasData = false;

    for (Subscription subscription : subscriptions) {
      var begin = subscription.getStartDate();
      var end = subscription.getEndDate();
      if (begin.isBefore(date) && (Objects.isNull(end) || end.isAfter(date))) {
        hasData = true;
        for (var entry : subscription.getSubscriptionMeasurements().entrySet()) {
          var measurementKey = entry.getKey();
          var measurementValue = entry.getValue();
          if (metricId.toString().equalsIgnoreCase(measurementKey.getMetricId())) {
            if (hypervisorReportCategory.isEmpty()) {
              value += measurementValue.intValue();
              continue;
            }

            var measurementType = measurementKey.getMeasurementType();
            var isNonHypervisorMeasurement = PHYSICAL.equals(measurementType);
            var isHypervisorMeasurement = HYPERVISOR.equals(measurementType);

            var category = hypervisorReportCategory.get();
            var isHypervisorCategory = category.equals(HypervisorReportCategory.HYPERVISOR);
            var isNonHypervisorCategory = category.equals(HypervisorReportCategory.NON_HYPERVISOR);

            if ((isHypervisorCategory && isHypervisorMeasurement)
                || (isNonHypervisorCategory && isNonHypervisorMeasurement)) {
              value += measurementValue.intValue();
            }
          }
        }
      }
    }

    return new CapacitySnapshotByMetricId().date(date).value(value).hasData(hasData);
  }

  private void validateGranularity(ProductId productId, Granularity granularity) {
    if (!Variant.isGranularityCompatible(
        productId.toString(), SubscriptionDefinitionGranularity.valueOf(granularity.name()))) {
      throw new BadRequestException(
          String.format(
              "%s does not support any granularity finer than %s",
              productId, granularity.getValue()));
    }
  }
}
