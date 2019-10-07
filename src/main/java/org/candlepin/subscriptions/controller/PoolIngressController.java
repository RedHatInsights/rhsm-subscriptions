/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.controller;

import org.candlepin.subscriptions.capacity.CandlepinPoolCapacityMapper;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.files.ProductWhitelist;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for ingesting subscription information from Candlepin pools.
 */
@Component
public class PoolIngressController {

    private static final Logger log = LoggerFactory.getLogger(PoolIngressController.class);

    private final SubscriptionCapacityRepository repository;
    private final CandlepinPoolCapacityMapper capacityMapper;
    private final ProductWhitelist productWhitelist;

    public PoolIngressController(SubscriptionCapacityRepository repository,
        CandlepinPoolCapacityMapper capacityMapper, ProductWhitelist productWhitelist) {

        this.repository = repository;
        this.capacityMapper = capacityMapper;
        this.productWhitelist = productWhitelist;
    }

    public void updateCapacityForOrg(String orgId, List<CandlepinPool> pools) {
        List<CandlepinPool> whitelistedPools = pools.stream()
            .filter(pool -> productWhitelist.productIdMatches(pool.getProductId()))
            .collect(Collectors.toList());

        List<SubscriptionCapacity> capacities = whitelistedPools.stream()
            .map(pool -> capacityMapper.mapPoolToSubscriptionCapacity(orgId, pool))
            .flatMap(Collection::stream).collect(Collectors.toList());

        capacities.forEach(repository::save);

        log.info(
            "Update for org {} processed {} of {} posted pools, resulting in {} capacity records.",
            orgId,
            whitelistedPools.size(),
            pools.size(),
            capacities.size()
        );
    }
}
