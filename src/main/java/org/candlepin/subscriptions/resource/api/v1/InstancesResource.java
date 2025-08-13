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

import static java.util.Optional.ofNullable;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.InsightsUserPrincipal;
import org.candlepin.subscriptions.security.auth.ReportingAccessOrInternalRequired;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingAccountIdInfo;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingAccountIdResponse;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.v1.model.CloudProvider;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceData;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceGuestReport;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceMeta;
import org.candlepin.subscriptions.utilization.api.v1.model.InstanceResponse;
import org.candlepin.subscriptions.utilization.api.v1.model.MetaCount;
import org.candlepin.subscriptions.utilization.api.v1.model.PageLinks;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v1.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.v1.model.UsageType;
import org.candlepin.subscriptions.utilization.api.v1.resources.InstancesApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Instance API implementation. */
@Component
@Slf4j
public class InstancesResource implements InstancesApi {
  private static final Map<ReportCategory, List<HardwareMeasurementType>> CATEGORY_MAP =
      Map.of(
          ReportCategory.PHYSICAL, List.of(HardwareMeasurementType.PHYSICAL),
          ReportCategory.VIRTUAL, List.of(HardwareMeasurementType.VIRTUAL),
          ReportCategory.HYPERVISOR, List.of(HardwareMeasurementType.HYPERVISOR),
          ReportCategory.CLOUD, new ArrayList<>(HardwareMeasurementType.getCloudProviderTypes()));

  private final ApiModelMapperV1 mapper;
  private final TallyInstanceViewRepository repository;
  private final HostRepository hostRepository;
  private final PageLinkCreator pageLinkCreator;
  private final HostTallyBucketRepository hostTallyBucketRepository;

  @Context UriInfo uriInfo;

  public InstancesResource(
      ApiModelMapperV1 mapper,
      TallyInstanceViewRepository tallyInstanceViewRepository,
      HostRepository hostRepository,
      PageLinkCreator pageLinkCreator,
      HostTallyBucketRepository hostTallyBucketRepository) {
    this.mapper = mapper;
    this.repository = tallyInstanceViewRepository;
    this.hostRepository = hostRepository;
    this.pageLinkCreator = pageLinkCreator;
    this.hostTallyBucketRepository = hostTallyBucketRepository;
  }

  @Override
  @ReportingAccessRequired
  public InstanceGuestReport getInstanceGuests(String instanceId, Integer offset, Integer limit) {
    String orgId = ResourceUtils.getOrgId();
    Pageable page = ResourceUtils.getPageable(offset, limit);
    Page<Host> guests = hostRepository.getGuestHostsByHypervisorInstanceId(orgId, instanceId, page);
    PageLinks links;
    if (offset != null || limit != null) {
      links = mapper.map(pageLinkCreator.getPaginationLinks(uriInfo, guests));
    } else {
      links = null;
    }

    return new InstanceGuestReport()
        .links(links)
        .meta(new MetaCount().count((int) guests.getTotalElements()))
        .data(guests.getContent().stream().map(mapper::map).toList());
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
      String metricId,
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

    Optional<MetricId> metricIdOptional =
        Stream.of(metricId)
            .filter(Objects::nonNull)
            .map(
                m -> {
                  try {
                    return MetricId.fromString(m);
                  } catch (IllegalArgumentException ex) {
                    throw new BadRequestException(ex);
                  }
                })
            .findFirst();

    Integer minCores = null;
    Integer minSockets = null;
    if (metricIdOptional.map(m -> m.equals(MetricIdUtils.getCores())).orElse(false)) {
      minCores = 0;
    } else if (metricIdOptional.map(m -> m.equals(MetricIdUtils.getSockets())).orElse(false)) {
      minSockets = 0;
    }

    ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(mapper.map(sla));
    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(mapper.map(usage));
    BillingProvider sanitizedBillingProvider =
        ResourceUtils.sanitizeBillingProvider(mapper.map(billingProviderType));
    String sanitizedBillingAccountId = ResourceUtils.sanitizeBillingAccountId(billingAccountId);

    List<HardwareMeasurementType> hardwareMeasurementTypes =
        getHardwareMeasurementTypesFromCategory(reportCategory);

    List<InstanceData> payload;
    Page<? extends TallyInstanceView> instances;

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime start = ofNullable(beginning).orElse(now);
    OffsetDateTime end = ofNullable(ending).orElse(now);

    var variant = Variant.findByTag(productId.toString());
    var metricIdSet =
        MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null))
            .collect(Collectors.toSet());
    List<String> measurements = metricIdSet.stream().map(MetricId::toString).sorted().toList();

    validateBeginningAndEndingDates(productId, start, end);

    // Build the month parameter for PAYG products
    String month = productId.isPayg() ? InstanceMonthlyTotalKey.formatMonthId(start) : null;
    MetricId referenceMetricId = metricIdOptional.orElse(null);

    // Use the existing method - date filtering will be handled by the repository's search
    // specification
    instances =
        repository.findAllBy(
            orgId,
            productId,
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
            offset,
            limit,
            sort,
            dir);
    payload =
        instances.getContent().stream()
            .map(tallyInstanceView -> asTallyHostViewApiInstance(tallyInstanceView, measurements))
            .toList();

    PageLinks links;
    if (offset != null || limit != null) {
      links = mapper.map(pageLinkCreator.getPaginationLinks(uriInfo, instances));
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

  @jakarta.transaction.Transactional
  @ReportingAccessOrInternalRequired
  @Override
  public BillingAccountIdResponse fetchBillingAccountIdsForOrg(
      String orgId, String productTag, String billingProvider) {
    Object principal = ResourceUtils.getPrincipal();
    if (principal instanceof InsightsUserPrincipal userPrincipal
        && !userPrincipal.getOrgId().equals(orgId)) {
      throw new ForbiddenException("The user is not authorized to access this organization.");
    }

    List<BillingAccountIdInfo> billingAccountIds = new ArrayList<>();
    hostTallyBucketRepository
        .billingAccountIds(
            DbReportCriteria.builder()
                .orgId(orgId)
                .productTag(productTag)
                .billingProvider(BillingProvider.fromString(billingProvider))
                .build())
        .forEach(
            x ->
                billingAccountIds.add(
                    new BillingAccountIdInfo()
                        .orgId(orgId)
                        .productTag(x.productId())
                        .billingProvider(getBillingProviderString(x))
                        .billingAccountId(x.billingAccountId())));
    return new BillingAccountIdResponse().ids(billingAccountIds);
  }

  private String getBillingProviderString(
      HostTallyBucketRepository.BillingAccountIdRecord idRecord) {
    if (idRecord.billingProvider() == null) {
      return null;
    } else {
      return idRecord.billingProvider().getValue();
    }
  }

  protected void validateBeginningAndEndingDates(
      ProductId productId, OffsetDateTime beginning, OffsetDateTime ending) {
    boolean isDateRangePossible = beginning.isBefore(ending) || beginning.isEqual(ending);
    boolean isBothDatesFromSameMonth = Objects.equals(beginning.getMonth(), ending.getMonth());
    boolean isPAYG = productId.isPayg();

    // See SWATCH-745 for the reasoning on the same month restriction
    if (!isDateRangePossible || (isPAYG && !isBothDatesFromSameMonth)) {
      throw new BadRequestException(
          "Invalid date range (PAYG products must be within the same month");
    }
  }

  private InstanceData asTallyHostViewApiInstance(
      TallyInstanceView tallyInstanceView, List<String> measurements) {
    var instance = new InstanceData();
    instance.setId(tallyInstanceView.getId());
    instance.setInstanceId(tallyInstanceView.getKey().getInstanceId());
    instance.setDisplayName(tallyInstanceView.getDisplayName());
    if (Objects.nonNull(tallyInstanceView.getHostBillingProvider())) {
      instance.setBillingProvider(mapper.map(tallyInstanceView.getHostBillingProvider()));
    }
    instance.setCategory(
        mapper.measurementTypeToReportCategory(tallyInstanceView.getKey().getMeasurementType()));
    instance.setCloudProvider(
        getCloudProviderByMeasurementType(tallyInstanceView.getKey().getMeasurementType()));
    instance.setBillingAccountId(tallyInstanceView.getHostBillingAccountId());
    instance.setMeasurements(getInstanceMeasurements(tallyInstanceView, measurements));
    instance.setLastSeen(tallyInstanceView.getLastSeen());
    instance.setLastAppliedEventRecordDate(tallyInstanceView.getLastAppliedEventRecordDate());
    instance.setNumberOfGuests(tallyInstanceView.getNumOfGuests());
    instance.setSubscriptionManagerId(tallyInstanceView.getSubscriptionManagerId());
    instance.setInventoryId(tallyInstanceView.getInventoryId());
    return instance;
  }

  private static List<Double> getInstanceMeasurements(
      TallyInstanceView tallyInstanceView, List<String> measurements) {
    List<Double> measurementList = new ArrayList<>();
    for (String metric : measurements) {
      MetricId metricId = MetricId.fromString(metric);
      measurementList.add(ofNullable(tallyInstanceView.getMetricValue(metricId)).orElse(0.0));
    }
    return measurementList;
  }

  public static List<HardwareMeasurementType> getHardwareMeasurementTypesFromCategory(
      ReportCategory reportCategory) {
    if (Objects.isNull(reportCategory)) {
      return new ArrayList<>();
    } else {
      return CATEGORY_MAP.get(reportCategory);
    }
  }

  public static CloudProvider getCloudProviderByMeasurementType(
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
