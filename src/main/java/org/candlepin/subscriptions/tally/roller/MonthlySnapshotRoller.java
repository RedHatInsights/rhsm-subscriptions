/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally.roller;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Produces monthly snapshots based on data stored in the inventory service. If a snapshot does not
 * exist for the account for the current month and an incoming calculation exists for the account, a
 * new snapshot will be created. A snapshot's cores, sockets, and instances will only be updated if
 * the incoming calculated values are greater than those existing for the current month.
 */
public class MonthlySnapshotRoller extends BaseSnapshotRoller {

  private static final Logger log = LoggerFactory.getLogger(MonthlySnapshotRoller.class);

  public MonthlySnapshotRoller(TallySnapshotRepository tallyRepo, ApplicationClock clock) {
    super(tallyRepo, clock);
  }

  @Override
  @Transactional
  public void rollSnapshots(
      Collection<String> accounts, Collection<AccountUsageCalculation> accountCalcs) {
    log.debug("Producing monthly snapshots for {} account(s).", accounts.size());

    Map<String, List<TallySnapshot>> currentMonthlySnaps =
        getCurrentSnapshotsByAccount(
            accounts,
            getApplicableProducts(accountCalcs),
            Granularity.MONTHLY,
            clock.startOfCurrentMonth(),
            clock.endOfCurrentMonth());

    updateSnapshots(accountCalcs, currentMonthlySnaps, Granularity.MONTHLY);
  }
}
