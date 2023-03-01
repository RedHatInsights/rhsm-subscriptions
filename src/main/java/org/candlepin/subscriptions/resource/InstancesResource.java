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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.utilization.api.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.model.CloudProvider;
import org.candlepin.subscriptions.utilization.api.model.InstanceData;
import org.candlepin.subscriptions.utilization.api.model.InstanceMeta;
import org.candlepin.subscriptions.utilization.api.model.InstanceReportSort;
import org.candlepin.subscriptions.utilization.api.model.InstanceResponse;
import org.candlepin.subscriptions.utilization.api.model.MetricId;
import org.candlepin.subscriptions.utilization.api.model.PageLinks;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
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
public class InstancesResource implements InstancesApi {

  private final TallyInstanceViewRepository repository;
  private final PageLinkCreator pageLinkCreator;
  private final TagProfile tagProfile;

  public static final Map<InstanceReportSort, String> INSTANCE_SORT_PARAM_MAPPING =
      ImmutableMap.<InstanceReportSort, String>builder()
          .put(InstanceReportSort.DISPLAY_NAME, "displayName")
          .put(InstanceReportSort.LAST_SEEN, "lastSeen")
          .put(InstanceReportSort.BILLING_PROVIDER, "hostBillingProvider")
          .put(InstanceReportSort.NUMBER_OF_GUESTS, "numOfGuests")
          .put(InstanceReportSort.CATEGORY, "key.measurementType")
          .putAll(getUomSorts())
          .build();

  public static final Map<InstanceReportSort, Measurement.Uom> SORT_TO_UOM_MAP =
      ImmutableMap.copyOf(getSortToUomMap());

  private static final Map<ReportCategory, List<HardwareMeasurementType>> CATEGORY_MAP =
      Map.of(
          ReportCategory.PHYSICAL, List.of(HardwareMeasurementType.PHYSICAL),
          ReportCategory.VIRTUAL, List.of(HardwareMeasurementType.VIRTUAL),
          ReportCategory.HYPERVISOR, List.of(HardwareMeasurementType.HYPERVISOR),
          ReportCategory.CLOUD, new ArrayList<>(HardwareMeasurementType.getCloudProviderTypes()));

  @Context UriInfo uriInfo;

  public InstancesResource(
      TallyInstanceViewRepository tallyInstanceViewRepository,
      PageLinkCreator pageLinkCreator,
      TagProfile tagProfile) {
    this.repository = tallyInstanceViewRepository;
    this.pageLinkCreator = pageLinkCreator;
    this.tagProfile = tagProfile;
  }

  @Override
  @Transactional(readOnly = true)
  public InstanceResponse getInstancesByProduct(
      ProductId productId,
      Integer offset,
      Integer limit,
      ServiceLevelType sla,
      UsageType usage,
      MetricId uom,
      BillingProviderType billingProviderType,
      String billingAccountId,
      String displayNameContains,
      ReportCategory reportCategory,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      InstanceReportSort sort,
      SortDirection dir) {
    Sort.Direction dirValue = Sort.Direction.ASC;
    if (dir == SortDirection.DESC) {
      dirValue = Sort.Direction.DESC;
    }
    Sort.Order implicitOrder = Sort.Order.by("id");
    Sort sortValue = Sort.by(implicitOrder);

    int minCores = 0;
    int minSockets = 0;

    String orgId = ResourceUtils.getOrgId();
    ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
    BillingProvider sanitizedBillingProvider =
        ResourceUtils.sanitizeBillingProvider(billingProviderType);
    String sanitizedBillingAccountId = ResourceUtils.sanitizeBillingAccountId(billingAccountId);

    String sanitizedDisplayNameSubstring =
        Objects.nonNull(displayNameContains) ? displayNameContains : "";

    List<HardwareMeasurementType> hardwareMeasurementTypes =
        getHardwareMeasurementTypesFromCategory(reportCategory);

    List<InstanceData> payload;
    Page<TallyInstanceView> instances;
    if (sort != null) {
      Sort.Order userDefinedOrder = new Sort.Order(dirValue, INSTANCE_SORT_PARAM_MAPPING.get(sort));
      sortValue = Sort.by(userDefinedOrder, implicitOrder);
    }
    Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime start = Optional.ofNullable(beginning).orElse(now);
    OffsetDateTime end = Optional.ofNullable(ending).orElse(now);
    var uomSet = tagProfile.measurementsByTag(productId.toString());
    List<String> measurements = uomSet.stream().map(Measurement.Uom::toString).sorted().toList();

    validateBeginningAndEndingDates(productId, start, end);

    boolean isPAYG = tagProfile.isProductPAYGEligible(productId.toString());
    String month = isPAYG ? InstanceMonthlyTotalKey.formatMonthId(start) : null;
    // We depend on a "reference UOM" in order to filter out instances that were not active in
    // the selected month. This is also used for sorting purposes (same join). See
    // org.candlepin.subscriptions.db.TallyInstanceViewSpecification#toPredicate and
    // org.candlepin.subscriptions.db.TallyInstanceViewRepository#findAllBy.
    Measurement.Uom referenceUom = getMeasurementUom(uom, sort);

    instances =
        repository.findAllBy(
            orgId,
            productId.toString(),
            sanitizedSla,
            sanitizedUsage,
            sanitizedDisplayNameSubstring,
            minCores,
            minSockets,
            month,
            referenceUom,
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
                .product(productId)
                .serviceLevel(sla)
                .usage(usage)
                .billingProvider(billingProviderType)
                .billingAccountId(billingAccountId)
                .measurements(measurements))
        .data(payload);
  }

  protected void validateBeginningAndEndingDates(
      ProductId productId, OffsetDateTime beginning, OffsetDateTime ending) {
    boolean isDateRangePossible = beginning.isBefore(ending) || beginning.isEqual(ending);
    boolean isBothDatesFromSameMonth = Objects.equals(beginning.getMonth(), ending.getMonth());
    boolean isPAYG = tagProfile.isProductPAYGEligible(productId.toString());

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
    for (String uom : measurements) {
      if (Measurement.Uom.SOCKETS.equals(Measurement.Uom.fromValue(uom))) {
        measurementList.add(Double.valueOf(tallyInstanceView.getSockets()));
      } else if (!isPAYG
          && tallyInstanceView.getKey().getUom().equals(Measurement.Uom.fromValue(uom))) {
        measurementList.add(Optional.ofNullable(tallyInstanceView.getValue()).orElse(0.0));
      } else {
        measurementList.add(
            Optional.ofNullable(
                    tallyInstanceView.getMonthlyTotal(monthId, Measurement.Uom.fromValue(uom)))
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
      case AWS, AWS_CLOUDIGRADE -> CloudProvider.AWS;
      case GOOGLE -> CloudProvider.GCP;
      case ALIBABA -> CloudProvider.ALIBABA;
      case AZURE -> CloudProvider.AZURE;
      default -> null;
    };
  }

  private static Map<InstanceReportSort, Measurement.Uom> getSortToUomMap() {
    return Arrays.stream(Measurement.Uom.values())
        .filter(
            uom ->
                Arrays.stream(InstanceReportSort.values())
                    .map(InstanceReportSort::toString)
                    .collect(Collectors.toSet())
                    .contains(uom.value()))
        .collect(
            Collectors.toMap(
                uom -> InstanceReportSort.fromValue(uom.value()), Function.identity()));
  }

  private static Map<InstanceReportSort, String> getUomSorts() {
    return getSortToUomMap().keySet().stream()
        .collect(Collectors.toMap(Function.identity(), key -> "value"));
  }

  private static Measurement.Uom getMeasurementUom(MetricId uom, InstanceReportSort sort) {
    if (uom != null) {
      return Measurement.Uom.fromValue(uom.toString());
    }
    return SORT_TO_UOM_MAP.get(sort);
  }
}
