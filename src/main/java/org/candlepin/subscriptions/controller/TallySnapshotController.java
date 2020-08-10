/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.InventoryAccountUsageCollector;
import org.candlepin.subscriptions.tally.UsageSnapshotProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.annotation.Timed;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides the logic for updating Tally snapshots.
 */
@Component
public class TallySnapshotController {

    private static final Logger log = LoggerFactory.getLogger(TallySnapshotController.class);

    private final InventoryAccountUsageCollector usageCollector;
    private final UsageSnapshotProducer snapshotProducer;
    private final RetryTemplate retryTemplate;

    private final Set<String> applicableProducts;


    public TallySnapshotController(ProductIdToProductsMapSource productIdToProductsMapSource,
        RoleToProductsMapSource roleToProductsMapSource,
        InventoryAccountUsageCollector usageCollector,
        UsageSnapshotProducer snapshotProducer,
        @Qualifier("collectorRetryTemplate") RetryTemplate retryTemplate) throws IOException {

        this.applicableProducts = new HashSet<>();
        productIdToProductsMapSource.getValue().values().forEach(this.applicableProducts::addAll);
        roleToProductsMapSource.getValue().values().forEach(this.applicableProducts::addAll);

        this.usageCollector = usageCollector;
        this.snapshotProducer = snapshotProducer;
        this.retryTemplate = retryTemplate;
    }

    @Timed("rhsm-subscriptions.snapshots.single")
    public void produceSnapshotsForAccount(String account) {
        produceSnapshotsForAccounts(Collections.singletonList(account));
    }

    @Timed("rhsm-subscriptions.snapshots.collection")
    public void produceSnapshotsForAccounts(List<String> accounts) {
        log.info("Producing snapshots for {} accounts.", accounts.size());
        // Account list could be large. Only print them when debugging.
        if (log.isDebugEnabled()) {
            log.debug("Producing snapshots for accounts: {}", String.join(",", accounts));
        }

        Collection<AccountUsageCalculation> accountCalcs;
        try {
            accountCalcs = retryTemplate.execute(context ->
                usageCollector.collect(this.applicableProducts, accounts)
            );
        }
        catch (Exception e) {
            log.error("Could not collect existing usage snapshots for accounts {}", accounts, e);
            return;
        }

        snapshotProducer.produceSnapshotsFromCalculations(accounts, accountCalcs);
    }

}
