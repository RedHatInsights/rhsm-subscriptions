/*
 * Copyright (c) 2019 Red Hat, Inc.
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
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.tally.roller.DailySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.HourlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.MonthlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.QuarterlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.WeeklySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.YearlySnapshotRoller;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * Produces usage snapshot for all configured accounts.
 */
@Component
public class UsageSnapshotProducer {

    private static final Logger log = LoggerFactory.getLogger(UsageSnapshotProducer.class);

    private final HourlySnapshotRoller hourlyRoller;
    private final DailySnapshotRoller dailyRoller;
    private final WeeklySnapshotRoller weeklyRoller;
    private final MonthlySnapshotRoller monthlyRoller;
    private final YearlySnapshotRoller yearlyRoller;
    private final QuarterlySnapshotRoller quarterlyRoller;

    @SuppressWarnings("squid:S00107")
    @Autowired
    public UsageSnapshotProducer(TallySnapshotRepository tallyRepo, ApplicationClock clock,
        ProductProfileRegistry registry) {
        hourlyRoller = new HourlySnapshotRoller(tallyRepo, clock, registry);
        dailyRoller = new DailySnapshotRoller(tallyRepo, clock, registry);
        weeklyRoller = new WeeklySnapshotRoller(tallyRepo, clock, registry);
        monthlyRoller = new MonthlySnapshotRoller(tallyRepo, clock, registry);
        yearlyRoller = new YearlySnapshotRoller(tallyRepo, clock, registry);
        quarterlyRoller = new QuarterlySnapshotRoller(tallyRepo, clock, registry);
    }

    @Transactional
    public void produceSnapshotsFromCalculations(Collection<String> accounts,
        Collection<AccountUsageCalculation> accountCalcs) {
        hourlyRoller.rollSnapshots(accounts, accountCalcs);
        dailyRoller.rollSnapshots(accounts, accountCalcs);
        weeklyRoller.rollSnapshots(accounts, accountCalcs);
        monthlyRoller.rollSnapshots(accounts, accountCalcs);
        yearlyRoller.rollSnapshots(accounts, accountCalcs);
        quarterlyRoller.rollSnapshots(accounts, accountCalcs);
        log.info("Finished producing snapshots for all accounts.");
    }
}
