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
package org.candlepin.subscriptions.tally;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.tally.roller.DailySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.MonthlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.QuarterlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.WeeklySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.YearlySnapshotRoller;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.annotation.Timed;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Produces usage snapshot for all configured accounts.
 */
@Component
public class UsageSnapshotProducer {

    private static final Logger log = LoggerFactory.getLogger(UsageSnapshotProducer.class);

    private final Set<String> applicableProducts;
    private final RetryTemplate retryTemplate;

    private final InventoryAccountUsageCollector accountUsageCollector;
    private final DailySnapshotRoller dailyRoller;
    private final WeeklySnapshotRoller weeklyRoller;
    private final MonthlySnapshotRoller monthlyRoller;
    private final YearlySnapshotRoller yearlyRoller;
    private final QuarterlySnapshotRoller quarterlyRoller;

    @SuppressWarnings("squid:S00107")
    @Autowired
    public UsageSnapshotProducer(ProductIdToProductsMapSource productIdToProductsMapSource,
        RoleToProductsMapSource roleToProductsMapSource, InventoryAccountUsageCollector accountUsageCollector,
        TallySnapshotRepository tallyRepo, ApplicationClock clock,
        @Qualifier("collectorRetryTemplate") RetryTemplate retryTemplate) throws IOException {

        this.applicableProducts = new HashSet<>();
        this.retryTemplate = retryTemplate;
        productIdToProductsMapSource.getValue().values().forEach(this.applicableProducts::addAll);
        roleToProductsMapSource.getValue().values().forEach(this.applicableProducts::addAll);

        this.accountUsageCollector = accountUsageCollector;
        dailyRoller = new DailySnapshotRoller(tallyRepo, clock);
        weeklyRoller = new WeeklySnapshotRoller(tallyRepo, clock);
        monthlyRoller = new MonthlySnapshotRoller(tallyRepo, clock);
        yearlyRoller = new YearlySnapshotRoller(tallyRepo, clock);
        quarterlyRoller = new QuarterlySnapshotRoller(tallyRepo, clock);
    }

    @Transactional
    @Timed("rhsm-subscriptions.snapshots.single")
    public void produceSnapshotsForAccount(String account) {
        produceSnapshotsForAccounts(Collections.singletonList(account));
    }

    @Transactional
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
                accountUsageCollector.collect(this.applicableProducts, accounts)
            );
        }
        catch (Exception e) {
            log.error("Could not collect existing usage snapshots for accounts {}", accounts, e);
            return;
        }

        dailyRoller.rollSnapshots(accounts, accountCalcs);
        weeklyRoller.rollSnapshots(accounts, accountCalcs);
        monthlyRoller.rollSnapshots(accounts, accountCalcs);
        yearlyRoller.rollSnapshots(accounts, accountCalcs);
        quarterlyRoller.rollSnapshots(accounts, accountCalcs);
        log.info("Finished producing snapshots for all accounts.");
    }
}
