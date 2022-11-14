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
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
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
import org.candlepin.subscriptions.utilization.api.model.MetricId;
import org.candlepin.subscriptions.utilization.api.model.PageLinks;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.CapacityApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Capacity API implementation. */
@Component
public class CapacityResource implements CapacityApi {

  private final SubscriptionCapacityRepository repository;
  private final PageLinkCreator pageLinkCreator;
  private final ApplicationClock clock;
  private final TagProfile tagProfile;

  @Context UriInfo uriInfo;

  public CapacityResource(
      SubscriptionCapacityRepository repository,
      PageLinkCreator pageLinkCreator,
      ApplicationClock clock,
      TagProfile tagProfile) {
    this.repository = repository;
    this.pageLinkCreator = pageLinkCreator;
    this.clock = clock;
    this.tagProfile = tagProfile;
  }

  /**
   * @deprecated: for removal once https://issues.redhat.com/browse/ENT-4209 is completed
   */
  @Override
  @Deprecated(since = "https://issues.redhat.com/browse/ENT-4384")
  @ReportingAccessRequired
  public CapacityReport getCapacityReport(
      ProductId productId,
      @NotNull GranularityType granularityType,
      @NotNull OffsetDateTime beginning,
      @NotNull OffsetDateTime ending,
      Integer offset,
      @Min(1) Integer limit,
      ServiceLevelType sla,
      UsageType usage) {

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
      Page<CapacitySnapshot> snapshotPage = new PageImpl<>(data, pageable, capacities.size());
      links = pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage);
    } else {
      data = capacities;
      links = null;
    }

    CapacityReport report = new CapacityReport();
    report.setData(data);
    report.setMeta(new CapacityReportMeta());
    report.getMeta().setGranularity(granularityType);
    report.getMeta().setProduct(productId);
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
      ProductId productId,
      MetricId metricId,
      @NotNull GranularityType granularityType,
      @NotNull OffsetDateTime beginning,
      @NotNull OffsetDateTime ending,
      Integer offset,
      @Min(1) Integer limit,
      ReportCategory reportCategory,
      ServiceLevelType sla,
      UsageType usage) {

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
            reportCategory,
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
    meta.setProduct(productId);
    meta.setMetricId(metricId);
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
    try {
      tagProfile.validateGranularityCompatibility(productId, granularity);
    } catch (IllegalStateException e) {
      throw new BadRequestException(e.getMessage());
    }

    List<SubscriptionCapacity> matches;
    matches = repository.findAllBy(orgId, productId.toString(), sla, usage, reportBegin, reportEnd);

    SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);

    OffsetDateTime start = timeAdjuster.adjustToPeriodStart(reportBegin);
    OffsetDateTime end = timeAdjuster.adjustToPeriodEnd(reportEnd);
    TemporalAmount offset = timeAdjuster.getSnapshotOffset();

    List<CapacitySnapshot> result = new ArrayList<>();
    OffsetDateTime next = OffsetDateTime.from(start);

    while (next.isBefore(end) || next.isEqual(end)) {
      result.add(createCapacitySnapshot(next, matches));
      next = timeAdjuster.adjustToPeriodStart(next.plus(offset));
    }

    return result;
  }

  @SuppressWarnings("java:S107")
  protected List<CapacitySnapshotByMetricId> getCapacitiesByMetricId(
      String orgId,
      ProductId productId,
      MetricId metricId,
      ReportCategory reportCategory,
      ServiceLevel sla,
      Usage usage,
      Granularity granularity,
      @NotNull OffsetDateTime reportBegin,
      @NotNull OffsetDateTime reportEnd) {

    /* Throw an error if we are asked to generate capacity reports at a finer granularity than what is
     * supported by the product.  The reports created would be technically accurate, but would convey the
     * false impression that we have capacity information at that fine of a granularity.  This decision is
     * on of personal judgment and it may be appropriate to reverse it at a later date. */
    try {
      tagProfile.validateGranularityCompatibility(productId, granularity);
    } catch (IllegalStateException e) {
      throw new BadRequestException(e.getMessage());
    }

    HypervisorReportCategory hypervisorReportCategory =
        HypervisorReportCategory.mapCategory(reportCategory);
    List<SubscriptionCapacity> matches =
        repository.findAllBy(
            orgId,
            productId.toString(),
            metricId,
            hypervisorReportCategory,
            sla,
            usage,
            reportBegin,
            reportEnd);

    SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);

    OffsetDateTime start = timeAdjuster.adjustToPeriodStart(reportBegin);
    OffsetDateTime end = timeAdjuster.adjustToPeriodEnd(reportEnd);
    TemporalAmount offset = timeAdjuster.getSnapshotOffset();

    List<CapacitySnapshotByMetricId> result = new ArrayList<>();
    OffsetDateTime next = OffsetDateTime.from(start);

    while (next.isBefore(end) || next.isEqual(end)) {
      result.add(
          createCapacitySnapshotWithMetricId(
              next, matches, metricId, Optional.ofNullable(reportCategory)));
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

  protected CapacitySnapshot createCapacitySnapshot(
      OffsetDateTime date, List<SubscriptionCapacity> matches) {
    // NOTE there is room for future optimization here, as the we're *generally* calculating the
    // same sum
    // across a time range, also we might opt to do some of this in the DB query in the future.
    int sockets = 0;
    int physicalSockets = 0;
    int hypervisorSockets = 0;
    int cores = 0;
    int physicalCores = 0;
    int hypervisorCores = 0;
    boolean hasInfiniteQuantity = false;

    for (SubscriptionCapacity capacity : matches) {
      if (capacity.getBeginDate().isBefore(date) && capacity.getEndDate().isAfter(date)) {
        int capacityVirtSockets = sanitize(capacity.getVirtualSockets());
        sockets += capacityVirtSockets;
        hypervisorSockets += capacityVirtSockets;

        int capacityPhysicalSockets = sanitize(capacity.getPhysicalSockets());
        sockets += capacityPhysicalSockets;
        physicalSockets += capacityPhysicalSockets;
        int capacityPhysCores = sanitize(capacity.getPhysicalCores());
        cores += capacityPhysCores;
        physicalCores += capacityPhysCores;

        int capacityVirtCores = sanitize(capacity.getVirtualCores());
        cores += capacityVirtCores;
        hypervisorCores += capacityVirtCores;

        hasInfiniteQuantity |= Optional.ofNullable(capacity.getHasUnlimitedUsage()).orElse(false);
      }
    }

    return new CapacitySnapshot()
        .date(date)
        .sockets(sockets)
        .physicalSockets(physicalSockets)
        .hypervisorSockets(hypervisorSockets)
        .cores(cores)
        .physicalCores(physicalCores)
        .hypervisorCores(hypervisorCores)
        .hasInfiniteQuantity(hasInfiniteQuantity);
  }

  protected CapacitySnapshotByMetricId createCapacitySnapshotWithMetricId(
      OffsetDateTime date,
      List<SubscriptionCapacity> matches,
      MetricId metricId,
      Optional<ReportCategory> reportCategory) {
    int value = 0;
    boolean hasData = false;
    boolean hasInfiniteQuantity = false;

    for (SubscriptionCapacity capacity : matches) {
      if (capacity.getBeginDate().isBefore(date) && capacity.getEndDate().isAfter(date)) {
        hasData = true;
        if (metricId.equals(MetricId.SOCKETS)) {
          value += calculateSocketsCapacity(reportCategory, capacity);
        } else if (metricId.equals(MetricId.CORES)) {
          value += calculateCoresCapacity(reportCategory, capacity);
        }
        hasInfiniteQuantity |= Optional.ofNullable(capacity.getHasUnlimitedUsage()).orElse(false);
      }
    }

    return new CapacitySnapshotByMetricId()
        .date(date)
        .value(value)
        .hasData(hasData)
        .hasInfiniteQuantity(hasInfiniteQuantity);
  }

  private int calculateSocketsCapacity(
      Optional<ReportCategory> reportCategory, SubscriptionCapacity capacity) {
    int value = 0;
    if (reportCategory.isPresent()) {
      if (reportCategory.get().equals(ReportCategory.PHYSICAL)) {
        value += sanitize(capacity.getPhysicalSockets());
      } else if (reportCategory.get().equals(ReportCategory.VIRTUAL)) {
        value += sanitize(capacity.getVirtualSockets());
      }
    } else {
      value += sanitize(capacity.getVirtualSockets()) + sanitize(capacity.getPhysicalSockets());
    }
    return value;
  }

  private int calculateCoresCapacity(
      Optional<ReportCategory> reportCategory, SubscriptionCapacity capacity) {
    int value = 0;
    if (reportCategory.isPresent()) {
      if (reportCategory.get().equals(ReportCategory.PHYSICAL)) {
        value += sanitize(capacity.getPhysicalCores());
      } else if (reportCategory.get().equals(ReportCategory.VIRTUAL)) {
        value += sanitize(capacity.getVirtualCores());
      }
    } else {
      value += sanitize(capacity.getVirtualCores()) + sanitize(capacity.getPhysicalCores());
    }
    return value;
  }

  private int sanitize(Integer value) {
    return value != null ? value : 0;
  }
}
