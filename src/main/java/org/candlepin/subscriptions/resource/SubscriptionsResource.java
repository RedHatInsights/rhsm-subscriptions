/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.subscription.ApiException;
import org.candlepin.subscriptions.subscription.SubscriptionService;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.utilization.api.model.ReportLinks;
import org.candlepin.subscriptions.utilization.api.model.ReportMetadata;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.Subscription;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionReport;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionReportSort;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.candlepin.subscriptions.utilization.api.model.UpcomingEventType;
import org.candlepin.subscriptions.utilization.api.resources.SubscriptionsApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hosts API implementation.
 */
@Component
public class SubscriptionsResource implements SubscriptionsApi {

    public static final String PHYSICAL_SORTER = "physical";
    public static final String VIRTUAL_SORTER = "virtual";
    public static final String TOTAL_SORTER = "total";

    public static final Map<SubscriptionReportSort, String> SORT_PARAM_MAPPING =
        ImmutableMap.<SubscriptionReportSort, String>builderWithExpectedSize(7)
        .put(SubscriptionReportSort.SKU, "sku")
        .put(SubscriptionReportSort.SERVICE_LEVEL, "sla")
        .put(SubscriptionReportSort.USAGE, "usage")
        .put(SubscriptionReportSort.UPCOMING_EVENT_DATE, "end_date")
        .put(SubscriptionReportSort.PHYSICAL_CAPACITY, PHYSICAL_SORTER)
        .put(SubscriptionReportSort.VIRTUAL_CAPACITY, VIRTUAL_SORTER)
        .put(SubscriptionReportSort.TOTAL_CAPACITY, TOTAL_SORTER)
        .build();

    @Context
    UriInfo uriInfo;

    private final SubscriptionCapacityRepository repository;
    private final PageLinkCreator pageLinkCreator;
    private final SubscriptionService subscriptionService;

    public SubscriptionsResource(SubscriptionCapacityRepository repository, PageLinkCreator pageLinkCreator,
                                 SubscriptionService subscriptionService) {
        this.repository = repository;
        this.pageLinkCreator = pageLinkCreator;
        this.subscriptionService = subscriptionService;
    }

    @Override
    @ReportingAccessRequired
    public SubscriptionReport getSubscriptions(String productId, Integer offset, @Min(1) @Max(100) Integer limit,
                                               String sla, String usage, Uom uom, SubscriptionReportSort sort,
                                               SortDirection dir) {
        Sort.Direction dirValue = Sort.Direction.ASC;
        if (dir == SortDirection.DESC) {
            dirValue = Sort.Direction.DESC;
        }
        Sort.Order implicitOrder = Sort.Order.by("id");
        Sort sortValue = Sort.by(implicitOrder);

        if (sort != null) {
            Sort.Order userDefinedOrder = new Sort.Order(dirValue, SORT_PARAM_MAPPING.get(sort));
            sortValue = Sort.by(userDefinedOrder, implicitOrder);
        }

        String accountNumber = ResourceUtils.getAccountNumber();
        ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
        Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);
        Pageable page = ResourceUtils.getPageable(offset, limit, sortValue);
        Page<SubscriptionView> subscriptionViews = repository.getSubscriptionViews(
                accountNumber,
                productId,
                sanitizedSla,
                sanitizedUsage,
                page
        );

        ReportLinks links;
        if (offset != null || limit != null) {
            links = pageLinkCreator.getPaginationLinks(uriInfo, subscriptionViews);
        } else {
            links = null;
        }

        // get the subscription numbers and the assoc skus
        // then build the report
        try {
            final int ownerId = Integer.parseInt(ResourceUtils.getOwnerId());
            final List<org.candlepin.subscriptions.subscription.api.model.Subscription> subscriptionList =
                    subscriptionService.getSubscriptions(ownerId);
            // change the structure from subscription number -> skus to sku -> subscription numbers
            final Map<String, Set<String>> skusToSubNums = getSkusToSubNumbers(subscriptionList);
            return new SubscriptionReport()
                    .links(links)
                    .meta(
                            new ReportMetadata()
                                    .count((int) subscriptionViews.getTotalElements())
                                    .product(productId)
                                    .serviceLevel(sla)
                                    .usage(usage)
                                    .uom(uom)
                    )
                    .data(subscriptionViews.getContent().stream().map(subscriptionView -> {
                        final Subscription subscription = new Subscription();
                        subscription.setSku(subscriptionView.getSku());
                        subscription.setUsage(org.candlepin.subscriptions.utilization.api.model.Usage.fromValue(usage));
                        subscription.setUom(uom);
                        if (Uom.CORES.equals(uom)) {
                            subscription.setPhysicalCapacity(subscriptionView.getPhysicalCores());
                            subscription.setVirtualCapacity(subscriptionView.getVirtualCores());
                            subscription.setTotalCapacity(
                                    subscriptionView.getPhysicalCores() + subscriptionView.getVirtualCores());
                        } else if (Uom.SOCKETS.equals(uom)) {
                            subscription.setPhysicalCapacity(subscriptionView.getPhysicalSockets());
                            subscription.setVirtualCapacity(subscriptionView.getVirtualSockets());
                            subscription.setTotalCapacity(
                                    subscriptionView.getPhysicalSockets() + subscriptionView.getVirtualSockets());
                        }
                        subscription.setUom(uom);
                        if (subscriptionView.getBeginDate().isBefore(subscriptionView.getEndDate())) {
                            subscription.setUpcomingEventDate(subscriptionView.getBeginDate().toString());
                            subscription.setUpcomingEventType(UpcomingEventType.SUBSCRIPTIONBEGIN);
                        } else {
                            subscription.setUpcomingEventDate(subscriptionView.getEndDate().toString());
                            subscription.setUpcomingEventType(UpcomingEventType.SUBSCRIPTIONEND);
                        }
                        final Set<String> subNumbers = skusToSubNums.get(subscription.getSku());
                        if (subNumbers != null && !subNumbers.isEmpty()) {
                            subscription.setSubscriptionNumbers(new ArrayList<>(subNumbers));
                        }
                        return subscription;
                    }).collect(Collectors.toList()));
        } catch (ApiException e) {

        }
        return null;
    }

    private static Map<String, Set<String>> getSkusToSubNumbers(
            List<org.candlepin.subscriptions.subscription.api.model.Subscription> subscriptions) {
        final Map<String, Set<String>> map = new HashMap<>();
        subscriptions.forEach(subscription -> {
            subscription.getSubscriptionProducts()
                    .stream()
                    .map(SubscriptionProduct::getSku)
                    .forEach(sku -> {
                        if (map.containsKey(sku)) {
                            final Set<String> subNumbers = map.get(sku);
                            subNumbers.add(subscription.getSubscriptionNumber());
                        } else {
                            final Set<String> subNumbers = new HashSet<>();
                            subNumbers.add(subscription.getSubscriptionNumber());
                            map.put(sku, subNumbers);
                        }
                    });
        });
        return map;
    }
}
