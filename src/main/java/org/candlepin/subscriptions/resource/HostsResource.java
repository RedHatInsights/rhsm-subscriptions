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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.utilization.api.model.HostReport;
import org.candlepin.subscriptions.utilization.api.model.HostReportMeta;
import org.candlepin.subscriptions.utilization.api.model.HostReportSort;
import org.candlepin.subscriptions.utilization.api.model.HypervisorGuestReport;
import org.candlepin.subscriptions.utilization.api.model.MetaCount;
import org.candlepin.subscriptions.utilization.api.model.PageLinks;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.HostsApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Hosts API implementation. */
@Component
public class HostsResource implements HostsApi {

  @SuppressWarnings("linelength")
  public static final Map<HostReportSort, String> HOST_SORT_PARAM_MAPPING =
      ImmutableMap.<HostReportSort, String>builderWithExpectedSize(5)
          .put(HostReportSort.DISPLAY_NAME, "host.displayName")
          .put(HostReportSort.CORES, "cores")
          .put(HostReportSort.HARDWARE_TYPE, "host.hardwareType")
          .put(HostReportSort.SOCKETS, "sockets")
          .put(HostReportSort.LAST_SEEN, "host.lastSeen")
          .put(HostReportSort.MEASUREMENT_TYPE, "measurementType")
          .build();

  @SuppressWarnings("linelength")
  public static final Map<HostReportSort, String> INSTANCE_SORT_PARAM_MAPPING =
      ImmutableMap.<HostReportSort, String>builder()
          .put(HostReportSort.DISPLAY_NAME, "displayName")
          .put(HostReportSort.LAST_SEEN, "lastSeen")
          .put(HostReportSort.CORE_HOURS, "monthlyTotals")
          .putAll(getUomSorts())
          .build();

  public static final Map<HostReportSort, Measurement.Uom> SORT_TO_UOM_MAP =
      ImmutableMap.copyOf(getSortToUomMap());
  public static final String BILLING_ACCOUNT_ID = "_ANY";

  private static Map<HostReportSort, Measurement.Uom> getSortToUomMap() {
    return Arrays.stream(Measurement.Uom.values())
        .filter(
            uom ->
                Arrays.stream(HostReportSort.values())
                    .map(HostReportSort::toString)
                    .collect(Collectors.toSet())
                    .contains(uom.value().toLowerCase().replace('-', '_')))
        .collect(
            Collectors.toMap(
                uom -> HostReportSort.fromValue(uom.value().toLowerCase().replace('-', '_')),
                Function.identity()));
  }

  private static Map<HostReportSort, String> getUomSorts() {
    return getSortToUomMap().keySet().stream()
        .collect(Collectors.toMap(Function.identity(), key -> "monthlyTotals"));
  }

  private final HostRepository repository;
  private final AccountConfigRepository accountConfigRepo;
  private final PageLinkCreator pageLinkCreator;
  private final TagProfile tagProfile;
  @Context UriInfo uriInfo;

  public HostsResource(
      HostRepository repository,
      AccountConfigRepository accountConfigRepo,
      PageLinkCreator pageLinkCreator,
      TagProfile tagProfile) {
    this.repository = repository;
    this.accountConfigRepo = accountConfigRepo;
    this.pageLinkCreator = pageLinkCreator;
    this.tagProfile = tagProfile;
  }

  @SuppressWarnings("java:S3776")
  @Transactional
  @ReportingAccessRequired
  @Override
  public HostReport getHosts(
      ProductId productId,
      Integer offset,
      @Min(1) @Max(100) Integer limit,
      ServiceLevelType sla,
      UsageType usage,
      Uom uom,
      String displayNameContains,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      HostReportSort sort,
      SortDirection dir) {

    Sort.Direction dirValue = Sort.Direction.ASC;
    if (dir == SortDirection.DESC) {
      dirValue = Sort.Direction.DESC;
    }
    Sort.Order implicitOrder = Sort.Order.by("id");
    Sort sortValue = Sort.by(implicitOrder);

    int minCores = 0;
    int minSockets = 0;
    if (uom == Uom.CORES) {
      minCores = 1;
    } else if (uom == Uom.SOCKETS) {
      minSockets = 1;
    }

    String accountNumber = ResourceUtils.getAccountNumber();
    // This should be removed as part of https://issues.redhat.com/browse/SWATCH-268
    String orgId = accountConfigRepo.findOrgByAccountNumber(accountNumber);

    ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
    String sanitizedDisplayNameSubstring =
        Objects.nonNull(displayNameContains) ? displayNameContains : "";

    List<org.candlepin.subscriptions.utilization.api.model.Host> payload;
    Page<?> hosts;
    if (tagProfile.isProductPAYGEligible(productId.toString())) {
      if (sort != null) {
        Sort.Order userDefinedOrder =
            new Sort.Order(dirValue, INSTANCE_SORT_PARAM_MAPPING.get(sort));
        sortValue = Sort.by(userDefinedOrder, implicitOrder);
      }
      Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);

      OffsetDateTime now = OffsetDateTime.now();
      OffsetDateTime start = Optional.ofNullable(beginning).orElse(now);
      OffsetDateTime end = Optional.ofNullable(ending).orElse(now);

      validateBeginningAndEndingDates(start, end);

      String month = InstanceMonthlyTotalKey.formatMonthId(start);
      // We depend on a "reference UOM" in order to filter out instances that were not active in
      // the selected month. This is also used for sorting purposes (same join). See
      // org.candlepin.subscriptions.db.HostSpecification#toPredicate and
      // org.candlepin.subscriptions.db.HostRepository#findAllBy.
      Measurement.Uom referenceUom = SORT_TO_UOM_MAP.get(sort);
      hosts =
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
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID,
              page);
      payload =
          ((Page<Host>) hosts)
              .getContent().stream()
                  .map(h -> h.asTallyHostViewApiHost(month))
                  .collect(Collectors.toList());
    } else {
      if (sort != null) {
        Sort.Order userDefinedOrder = new Sort.Order(dirValue, HOST_SORT_PARAM_MAPPING.get(sort));
        sortValue = Sort.by(userDefinedOrder, implicitOrder);
      }
      Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);
      hosts =
          repository.getTallyHostViews(
              accountNumber,
              productId.toString(),
              sanitizedSla,
              sanitizedUsage,
              BillingProvider._ANY,
              BILLING_ACCOUNT_ID,
              sanitizedDisplayNameSubstring,
              minCores,
              minSockets,
              page);

      payload =
          ((Page<TallyHostView>) hosts)
              .getContent().stream().map(TallyHostView::asApiHost).collect(Collectors.toList());
    }

    PageLinks links;
    if (offset != null || limit != null) {
      links = pageLinkCreator.getPaginationLinks(uriInfo, hosts);
    } else {
      links = null;
    }

    return new HostReport()
        .links(links)
        .meta(
            new HostReportMeta()
                .count((int) hosts.getTotalElements())
                .product(productId)
                .serviceLevel(sla)
                .usage(usage)
                .uom(uom))
        .data(payload);
  }

  protected void validateBeginningAndEndingDates(OffsetDateTime beginning, OffsetDateTime ending) {
    boolean isDateRangePossible = beginning.isBefore(ending) || beginning.isEqual(ending);
    boolean isBothDatesFromSameMonth = Objects.equals(beginning.getMonth(), ending.getMonth());

    if (!isDateRangePossible || !isBothDatesFromSameMonth) {
      throw new IllegalArgumentException("Invalid date range.");
    }
  }

  @Override
  @ReportingAccessRequired
  public HypervisorGuestReport getHypervisorGuests(
      String hypervisorUuid, Integer offset, Integer limit) {
    String accountNumber = ResourceUtils.getAccountNumber();
    Pageable page = ResourceUtils.getPageable(offset, limit);
    Page<Host> guests = repository.getHostsByHypervisor(accountNumber, hypervisorUuid, page);
    PageLinks links;
    if (offset != null || limit != null) {
      links = pageLinkCreator.getPaginationLinks(uriInfo, guests);
    } else {
      links = null;
    }

    return new HypervisorGuestReport()
        .links(links)
        .meta(new MetaCount().count((int) guests.getTotalElements()))
        .data(guests.getContent().stream().map(Host::asApiHost).collect(Collectors.toList()));
  }
}
