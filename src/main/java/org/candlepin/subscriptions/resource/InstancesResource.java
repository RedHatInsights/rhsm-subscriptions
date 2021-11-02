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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.candlepin.subscriptions.utilization.api.resources.InstancesApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Instance API implementation. */
@Component
public class InstancesResource implements InstancesApi {

  private final HostRepository repository;
  private final PageLinkCreator pageLinkCreator;
  private final TagProfile tagProfile;

  public static final Map<InstanceReportSort, String> INSTANCE_SORT_PARAM_MAPPING =
      ImmutableMap.<InstanceReportSort, String>builder()
          .put(InstanceReportSort.DISPLAY_NAME, "displayName")
          .put(InstanceReportSort.LAST_SEEN, "lastSeen")
          .putAll(getUomSorts())
          .build();

  public static final Map<InstanceReportSort, Measurement.Uom> SORT_TO_UOM_MAP =
      ImmutableMap.copyOf(getSortToUomMap());

  @Context UriInfo uriInfo;

  public InstancesResource(
      HostRepository instancesRepository, PageLinkCreator pageLinkCreator, TagProfile tagProfile) {
    this.repository = instancesRepository;
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
      String displayNameContains,
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

    String accountNumber = ResourceUtils.getAccountNumber();
    ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
    String sanitizedDisplayNameSubstring =
        Objects.nonNull(displayNameContains) ? displayNameContains : "";

    List<InstanceData> payload;
    Page<Host> hosts;
    if (sort != null) {
      Sort.Order userDefinedOrder = new Sort.Order(dirValue, INSTANCE_SORT_PARAM_MAPPING.get(sort));
      sortValue = Sort.by(userDefinedOrder, implicitOrder);
    }
    Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime start = Optional.ofNullable(beginning).orElse(now);
    OffsetDateTime end = Optional.ofNullable(ending).orElse(now);
    var uomSet = tagProfile.measurementsByTag(productId.toString());
    List<String> measurements =
        uomSet.stream().map(Measurement.Uom::toString).sorted().collect(Collectors.toList());

    validateBeginningAndEndingDates(start, end);

    String month = InstanceMonthlyTotalKey.formatMonthId(start);
    // We depend on a "reference UOM" in order to filter out instances that were not active in
    // the selected month. This is also used for sorting purposes (same join). See
    // org.candlepin.subscriptions.db.HostSpecification#toPredicate and
    // org.candlepin.subscriptions.db.HostRepository#findAllBy.
    Measurement.Uom referenceUom = SORT_TO_UOM_MAP.get(sort);
    hosts =
        repository.findAllBy(
            accountNumber,
            productId.toString(),
            sanitizedSla,
            sanitizedUsage,
            sanitizedDisplayNameSubstring,
            minCores,
            minSockets,
            month,
            referenceUom,
            page);
    payload =
        hosts.getContent().stream()
            .map(h -> asTallyHostViewApiInstance(h, month, measurements))
            .collect(Collectors.toList());

    PageLinks links;
    if (offset != null || limit != null) {
      links = pageLinkCreator.getPaginationLinks(uriInfo, hosts);
    } else {
      links = null;
    }

    return new InstanceResponse()
        .links(links)
        .meta(
            new InstanceMeta()
                .count((int) hosts.getTotalElements())
                .product(productId)
                .serviceLevel(sla)
                .usage(usage)
                .measurements(measurements))
        .data(payload);
  }

  protected void validateBeginningAndEndingDates(OffsetDateTime beginning, OffsetDateTime ending) {
    boolean isDateRangePossible = beginning.isBefore(ending) || beginning.isEqual(ending);
    boolean isBothDatesFromSameMonth = Objects.equals(beginning.getMonth(), ending.getMonth());

    if (!isDateRangePossible || !isBothDatesFromSameMonth) {
      throw new IllegalArgumentException("Invalid date range.");
    }
  }

  private InstanceData asTallyHostViewApiInstance(
      Host host, String monthId, List<String> measurements) {
    var instance = new InstanceData();
    List<Double> measurementList = new ArrayList<>();
    instance.setId(host.getInstanceId());
    instance.setDisplayName(host.getDisplayName());

    for (String uom : measurements) {
      measurementList.add(host.getMonthlyTotal(monthId, Measurement.Uom.fromValue(uom)));
    }
    instance.setMeasurements(measurementList);
    instance.setLastSeen(host.getLastSeen());

    return instance;
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
        .collect(Collectors.toMap(Function.identity(), key -> "monthlyTotals"));
  }
}
