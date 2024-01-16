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

import com.google.common.collect.ImmutableMap;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.utilization.api.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.model.CloudProvider;
import org.candlepin.subscriptions.utilization.api.model.InstanceData;
import org.candlepin.subscriptions.utilization.api.model.InstanceGuestReport;
import org.candlepin.subscriptions.utilization.api.model.InstanceMeta;
import org.candlepin.subscriptions.utilization.api.model.InstanceResponse;
import org.candlepin.subscriptions.utilization.api.model.MetaCount;
import org.candlepin.subscriptions.utilization.api.model.PageLinks;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.InstancesApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Instance API implementation. */
@Component
@Slf4j
public class InstancesResource implements InstancesApi {

  public static final Map<String, String> FIELD_SORT_PARAM_MAPPING =
      ImmutableMap.<String, String>builder()
          .put("display_name", "displayName")
          .put("last_seen", "lastSeen")
          .put("billing_provider", "hostBillingProvider")
          .put("number_of_guests", "numOfGuests")
          .put("category", "key.measurementType")
          .put("Sockets", "sockets")
          .put("Cores", "cores")
          .build();

  public static final Set<String> METRICS_TO_SORT =
      MetricId.getAll().stream().map(MetricId::getValue).collect(Collectors.toUnmodifiableSet());
  public static final String METRICS_SORT_PARAM = "value";
  private static final Sort.Order IMPLICIT_ORDER_TO_SORT = Sort.Order.by("id");

  private static final Map<ReportCategory, List<HardwareMeasurementType>> CATEGORY_MAP =
      Map.of(
          ReportCategory.PHYSICAL, List.of(HardwareMeasurementType.PHYSICAL),
          ReportCategory.VIRTUAL, List.of(HardwareMeasurementType.VIRTUAL),
          ReportCategory.HYPERVISOR, List.of(HardwareMeasurementType.HYPERVISOR),
          ReportCategory.CLOUD, new ArrayList<>(HardwareMeasurementType.getCloudProviderTypes()));

  private final TallyInstanceViewRepository repository;
  private final HostRepository hostRepository;
  private final PageLinkCreator pageLinkCreator;

  @Context UriInfo uriInfo;

  public InstancesResource(
      TallyInstanceViewRepository tallyInstanceViewRepository,
      HostRepository hostRepository,
      PageLinkCreator pageLinkCreator) {
    this.repository = tallyInstanceViewRepository;
    this.hostRepository = hostRepository;
    this.pageLinkCreator = pageLinkCreator;
  }

  @Override
  @ReportingAccessRequired
  public InstanceGuestReport getInstanceGuests(String instanceId, Integer offset, Integer limit) {
    String orgId = ResourceUtils.getOrgId();
    Pageable page = ResourceUtils.getPageable(offset, limit);
    Page<Host> guests = hostRepository.getGuestHostsByHypervisorInstanceId(orgId, instanceId, page);
    PageLinks links;
    if (offset != null || limit != null) {
      links = pageLinkCreator.getPaginationLinks(uriInfo, guests);
    } else {
      links = null;
    }

    return new InstanceGuestReport()
        .links(links)
        .meta(new MetaCount().count((int) guests.getTotalElements()))
        .data(guests.getContent().stream().map(Host::asApiHost).toList());
  }

  @Override
  @ReportingAccessRequired
  @Transactional(readOnly = true)
  public InstanceResponse getInstancesByProduct(
      ProductId productId,
      Integer offset,
      Integer limit,
      ServiceLevelType sla,
      UsageType usage,
      String uom,
      BillingProviderType billingProviderType,
      String billingAccountId,
      String displayNameContains,
      ReportCategory reportCategory,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String sort,
      SortDirection dir) {

    String orgId = ResourceUtils.getOrgId();

    log.debug("Get instances api called for org_id: {} and product: {}", orgId, productId);

    Optional<MetricId> metricIdOptional = Optional.empty();
    if (Objects.nonNull(uom)) {
      try {
        metricIdOptional = Optional.of(MetricId.fromString(uom));
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException(ex);
      }
    }

    int minCores = 0;
    int minSockets = 0;
    if (metricIdOptional.map(metricId -> metricId.equals(MetricIdUtils.getCores())).orElse(false)) {
      minCores = 1;
    } else if (metricIdOptional
        .map(metricId -> metricId.equals(MetricIdUtils.getSockets()))
        .orElse(false)) {
      minSockets = 1;
    }

    ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
    BillingProvider sanitizedBillingProvider =
        ResourceUtils.sanitizeBillingProvider(billingProviderType);
    String sanitizedBillingAccountId = ResourceUtils.sanitizeBillingAccountId(billingAccountId);

    List<HardwareMeasurementType> hardwareMeasurementTypes =
        getHardwareMeasurementTypesFromCategory(reportCategory);

    List<InstanceData> payload;
    Page<TallyInstanceView> instances;

    Pageable page = ResourceUtils.getPageable(offset, limit, toSort(sort, dir));

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime start = Optional.ofNullable(beginning).orElse(now);
    OffsetDateTime end = Optional.ofNullable(ending).orElse(now);

    var variant = Variant.findByTag(productId.toString());
    var metricIdSet =
        MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null))
            .collect(Collectors.toSet());
    List<String> measurements = metricIdSet.stream().map(MetricId::toString).sorted().toList();

    validateBeginningAndEndingDates(productId, start, end);

    boolean isPAYG = isPayg(variant);
    String month = isPAYG ? InstanceMonthlyTotalKey.formatMonthId(start) : null;
    // We depend on a "reference MetricId" in order to filter out instances that were not active in
    // the selected month. This is also used for sorting purposes (same join). See
    // org.candlepin.subscriptions.db.TallyInstanceViewSpecification#toPredicate and
    // org.candlepin.subscriptions.db.TallyInstanceViewRepository#findAllBy.
    MetricId referenceMetricId = metricIdOptional.orElse(null);
    if (referenceMetricId == null && sort != null && METRICS_TO_SORT.contains(sort)) {
      referenceMetricId = MetricId.fromString(sort);
    }

    instances =
        repository.findAllBy(
            orgId,
            productId.toString(),
            sanitizedSla,
            sanitizedUsage,
            displayNameContains,
            minCores,
            minSockets,
            month,
            referenceMetricId,
            sanitizedBillingProvider,
            sanitizedBillingAccountId,
            hardwareMeasurementTypes,
            page);
    payload =
        instances.getContent().stream()
            .map(
                tallyInstanceView ->
                    asTallyHostViewApiInstance(tallyInstanceView, month, measurements, isPAYG))
            .toList();

    PageLinks links;
    if (offset != null || limit != null) {
      links = pageLinkCreator.getPaginationLinks(uriInfo, instances);
    } else {
      links = null;
    }

    return new InstanceResponse()
        .links(links)
        .meta(
            new InstanceMeta()
                .count((int) instances.getTotalElements())
                .product(productId.toString())
                .serviceLevel(sla)
                .usage(usage)
                .billingProvider(billingProviderType)
                .billingAccountId(billingAccountId)
                .measurements(measurements))
        .data(payload);
  }

  private static Sort toSort(String sort, SortDirection dir) {
    Sort.Direction dirValue =
        SortDirection.DESC.equals(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;

    Sort sortValue;

    if (sort != null) {
      String column =
          METRICS_TO_SORT.contains(sort) ? METRICS_SORT_PARAM : FIELD_SORT_PARAM_MAPPING.get(sort);
      Sort.Order userDefinedOrder = new Sort.Order(dirValue, column);
      sortValue = Sort.by(userDefinedOrder, IMPLICIT_ORDER_TO_SORT);
    } else {
      sortValue = Sort.by(IMPLICIT_ORDER_TO_SORT);
    }

    return sortValue;
  }

  private static Boolean isPayg(Optional<Variant> variant) {
    return variant
        .map(Variant::getSubscription)
        .map(SubscriptionDefinition::isPaygEligible)
        .orElse(false);
  }

  protected void validateBeginningAndEndingDates(
      ProductId productId, OffsetDateTime beginning, OffsetDateTime ending) {
    boolean isDateRangePossible = beginning.isBefore(ending) || beginning.isEqual(ending);
    boolean isBothDatesFromSameMonth = Objects.equals(beginning.getMonth(), ending.getMonth());
    boolean isPAYG = isPayg(Variant.findByTag(productId.toString()));

    // See SWATCH-745 for the reasoning on the same month restriction
    if (!isDateRangePossible || (isPAYG && !isBothDatesFromSameMonth)) {
      throw new BadRequestException(
          "Invalid date range (PAYG products must be within the same month");
    }
  }

  private InstanceData asTallyHostViewApiInstance(
      TallyInstanceView tallyInstanceView,
      String monthId,
      List<String> measurements,
      boolean isPAYG) {
    var instance = new InstanceData();
    instance.setId(tallyInstanceView.getId());
    instance.setInstanceId(tallyInstanceView.getKey().getInstanceId());
    instance.setDisplayName(tallyInstanceView.getDisplayName());
    if (Objects.nonNull(tallyInstanceView.getHostBillingProvider())) {
      instance.setBillingProvider(tallyInstanceView.getHostBillingProvider().asOpenApiEnum());
    }
    instance.setCategory(
        getCategoryByMeasurementType(tallyInstanceView.getKey().getMeasurementType()));
    instance.setCloudProvider(
        getCloudProviderByMeasurementType(tallyInstanceView.getKey().getMeasurementType()));
    instance.setBillingAccountId(tallyInstanceView.getHostBillingAccountId());
    instance.setMeasurements(
        getInstanceMeasurements(tallyInstanceView, monthId, measurements, isPAYG));
    instance.setLastSeen(tallyInstanceView.getLastSeen());
    instance.setLastAppliedEventRecordDate(tallyInstanceView.getLastAppliedEventRecordDate());
    instance.setNumberOfGuests(tallyInstanceView.getNumOfGuests());
    instance.setSubscriptionManagerId(tallyInstanceView.getSubscriptionManagerId());
    return instance;
  }

  private static List<Double> getInstanceMeasurements(
      TallyInstanceView tallyInstanceView,
      String monthId,
      List<String> measurements,
      boolean isPAYG) {
    List<Double> measurementList = new ArrayList<>();
    for (String metric : measurements) {
      if (MetricIdUtils.getSockets().equals(MetricId.fromString(metric))) {
        measurementList.add(Double.valueOf(tallyInstanceView.getSockets()));
      } else if (!isPAYG && tallyInstanceView.getKey().getMetricId().equalsIgnoreCase(metric)) {
        measurementList.add(Optional.ofNullable(tallyInstanceView.getValue()).orElse(0.0));
      } else {
        measurementList.add(
            Optional.ofNullable(
                    tallyInstanceView.getMonthlyTotal(monthId, MetricId.fromString(metric)))
                .orElse(0.0));
      }
    }
    return measurementList;
  }

  private static List<HardwareMeasurementType> getHardwareMeasurementTypesFromCategory(
      ReportCategory reportCategory) {
    if (Objects.isNull(reportCategory)) {
      return new ArrayList<>();
    } else {
      return CATEGORY_MAP.get(reportCategory);
    }
  }

  private static ReportCategory getCategoryByMeasurementType(
      HardwareMeasurementType measurementType) {
    if (HardwareMeasurementType.isSupportedCloudProvider(measurementType.name())) {
      return ReportCategory.CLOUD;
    }
    return switch (measurementType) {
      case VIRTUAL -> ReportCategory.VIRTUAL;
      case PHYSICAL -> ReportCategory.PHYSICAL;
      case HYPERVISOR -> ReportCategory.HYPERVISOR;
      default -> null;
    };
  }

  private static CloudProvider getCloudProviderByMeasurementType(
      HardwareMeasurementType measurementType) {
    return switch (measurementType) {
      case AWS -> CloudProvider.AWS;
      case GOOGLE -> CloudProvider.GCP;
      case ALIBABA -> CloudProvider.ALIBABA;
      case AZURE -> CloudProvider.AZURE;
      default -> null;
    };
  }
}
