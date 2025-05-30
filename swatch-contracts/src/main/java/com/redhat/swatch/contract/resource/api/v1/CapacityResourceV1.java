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
package com.redhat.swatch.contract.resource.api.v1;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionGranularity;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.contract.openapi.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.openapi.model.CapacityReportByMetricIdMeta;
import com.redhat.swatch.contract.openapi.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.openapi.model.GranularityType;
import com.redhat.swatch.contract.openapi.model.PageLinks;
import com.redhat.swatch.contract.openapi.model.ReportCategory;
import com.redhat.swatch.contract.openapi.model.ServiceLevelType;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.openapi.resource.CapacityApi;
import com.redhat.swatch.contract.repository.DbReportCriteria;
import com.redhat.swatch.contract.repository.Granularity;
import com.redhat.swatch.contract.repository.HypervisorReportCategory;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.resource.InMemoryPager;
import com.redhat.swatch.contract.resource.ResourceUtils;
import com.redhat.swatch.contract.resteasy.Page;
import com.redhat.swatch.contract.resteasy.PageLinkCreator;
import com.redhat.swatch.contract.resteasy.Pageable;
import com.redhat.swatch.contract.utils.SnapshotTimeAdjuster;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;

/** Capacity API implementation. */
@ApplicationScoped
@Slf4j
public class CapacityResourceV1 implements CapacityApi {

  public static final String SOCKETS = "Sockets";
  public static final String CORES = "Cores";
  public static final String PHYSICAL = HardwareMeasurementType.PHYSICAL.toString().toUpperCase();
  public static final String HYPERVISOR =
      HardwareMeasurementType.HYPERVISOR.toString().toUpperCase();
  private final ApiModelMapperV1 mapper;
  private final SubscriptionRepository subscriptionRepository;
  private final PageLinkCreator pageLinkCreator;
  private final ApplicationClock clock;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  public CapacityResourceV1(
      ApiModelMapperV1 apiModelMapper,
      SubscriptionRepository subscriptionRepository,
      PageLinkCreator pageLinkCreator,
      ApplicationClock clock) {
    this.mapper = apiModelMapper;

    this.subscriptionRepository = subscriptionRepository;
    this.pageLinkCreator = pageLinkCreator;
    this.clock = clock;
  }

  @Override
  @RolesAllowed({"customer"})
  public CapacityReportByMetricId getCapacityReportByMetricId(
      ProductId productId,
      MetricId metricId,
      @NotNull GranularityType granularityType,
      @NotNull OffsetDateTime beginning,
      @NotNull OffsetDateTime ending,
      Integer offset,
      @Min(1) Integer limit,
      String billingAccountId,
      ReportCategory reportCategory,
      ServiceLevelType sla,
      UsageType usage) {

    log.debug(
        "Get capacity report for product {} by metric {} and billing account {} in range [{}, {}] for category {}",
        productId,
        metricId,
        billingAccountId,
        beginning,
        ending,
        reportCategory);
    HypervisorReportCategory hypervisorReportCategory =
        HypervisorReportCategory.mapCategory(mapper.map(reportCategory));

    // capacity records do not include _ANY rows
    ServiceLevel sanitizedServiceLevel = ResourceUtils.sanitizeServiceLevel(mapper.map(sla));
    if (sanitizedServiceLevel == ServiceLevel._ANY) {
      sanitizedServiceLevel = null;
    }

    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(mapper.map(usage));
    if (sanitizedUsage == Usage._ANY) {
      sanitizedUsage = null;
    }

    Granularity granularityValue = Granularity.fromString(granularityType.toString());
    String orgId = getOrgId();
    List<CapacitySnapshotByMetricId> capacities =
        getCapacitiesByMetricId(
            orgId,
            productId,
            metricId,
            billingAccountId,
            hypervisorReportCategory,
            sanitizedServiceLevel,
            sanitizedUsage,
            granularityValue,
            beginning,
            ending);

    PageLinks links;
    Pageable pageable = new Pageable(limit, offset, capacities.size());
    Page<CapacitySnapshotByMetricId> snapshotPage = null;
    if (offset != null || limit != null) {
      snapshotPage = InMemoryPager.paginate(capacities, pageable);
      links = mapper.map(pageLinkCreator.getPaginationLinks(uriInfo, snapshotPage));
    } else {
      snapshotPage = InMemoryPager.paginate(capacities, pageable);
      links = null;
    }

    CapacityReportByMetricId report = new CapacityReportByMetricId();
    report.setData(snapshotPage.getContent());
    report.setMeta(new CapacityReportByMetricIdMeta());
    var meta = report.getMeta();
    meta.setGranularity(granularityType);
    meta.setProduct(productId.toString());
    meta.setMetricId(metricId.toString());
    meta.setBillingAccountId(billingAccountId);
    meta.setCategory(reportCategory);
    meta.setCount(capacities.size());

    if (sanitizedServiceLevel != null) {
      meta.setServiceLevel(mapper.map(sanitizedServiceLevel));
    }

    if (sanitizedUsage != null) {
      meta.setUsage(mapper.map(sanitizedUsage));
    }

    report.setLinks(links);

    return report;
  }

  private boolean hasInfiniteQuantity(
      OffsetDateTime date, List<SubscriptionEntity> unlimitedSubscriptions) {
    for (SubscriptionEntity subscription : unlimitedSubscriptions) {
      if (subscription.getStartDate().isBefore(date) && subscription.getEndDate().isAfter(date)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("java:S107")
  protected List<CapacitySnapshotByMetricId> getCapacitiesByMetricId(
      String orgId,
      ProductId product,
      MetricId metricId,
      String billingAccountId,
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
    validateGranularity(product, granularity);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId(orgId)
            .productTag(product.toString())
            .serviceLevel(sla)
            .usage(usage)
            .beginning(reportBegin)
            .ending(reportEnd)
            .hypervisorReportCategory(hypervisorReportCategory)
            .metricId(metricId == null ? null : metricId.toString())
            .billingAccountId(billingAccountId)
            .build();

    var subscriptions = subscriptionRepository.findByCriteria(dbReportCriteria, Sort.empty());
    List<SubscriptionEntity> unlimitedSubscriptions =
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

  @SuppressWarnings("java:S3776")
  protected CapacitySnapshotByMetricId createCapacitySnapshotWithMetricId(
      OffsetDateTime date,
      List<SubscriptionEntity> subscriptions,
      MetricId metricId,
      Optional<HypervisorReportCategory> hypervisorReportCategory) {
    int value = 0;
    boolean hasData = false;

    for (SubscriptionEntity subscription : subscriptions) {
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
          String.format("%s does not support granularity %s", productId, granularity.getValue()));
    }
  }

  private String getOrgId() {
    return securityContext.getUserPrincipal().getName();
  }
}
