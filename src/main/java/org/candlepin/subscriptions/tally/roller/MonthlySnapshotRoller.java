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
package org.candlepin.subscriptions.tally.roller;

import static org.candlepin.subscriptions.db.model.Granularity.MONTHLY;

import java.util.Collection;
import java.util.List;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

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
  public Collection<TallySnapshot> rollSnapshots(AccountUsageCalculation accountCalc) {
    var orgId = accountCalc.getOrgId();
    log.debug("Producing monthly snapshots for orgId={}.", orgId);

    List<TallySnapshot> currentMonthlySnaps =
        getCurrentSnapshotsByOrgId(
            orgId,
            getApplicableProducts(accountCalc, MONTHLY),
            MONTHLY,
            clock.startOfCurrentMonth(),
            clock.endOfCurrentMonth());

    return updateSnapshots(accountCalc, currentMonthlySnaps, MONTHLY);
  }
}
