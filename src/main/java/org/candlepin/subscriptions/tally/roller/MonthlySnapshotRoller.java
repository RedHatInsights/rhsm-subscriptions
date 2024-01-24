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

import io.micrometer.core.annotation.Timed;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces monthly snapshots based on data stored in the inventory service. If a snapshot does not
 * exist for the account for the current month and an incoming calculation exists for the account, a
 * new snapshot will be created. A snapshot's cores, sockets, and instances will only be updated if
 * the incoming calculated values are greater than those existing for the current month.
 */
@Slf4j
@Component
public class MonthlySnapshotRoller extends BaseSnapshotRoller {

  @Autowired
  public MonthlySnapshotRoller(TallySnapshotRepository tallyRepo, ApplicationClock clock) {
    super(tallyRepo, clock);
  }

  @Timed("rhsm-subscriptions.tally.snapshots.roller.monthly")
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
