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
package org.candlepin.subscriptions.tally.roller;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.AccountMaxValues;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for all usage snapshot rollers. A snapshot roller is responsible compressing
 * finer granularity snapshots into more compressed snapshots. For example, rolling daily
 * snapshots into weekly snapshots or rolling weekly snapshots into monthly snapshots.
 */
public abstract class BaseSnapshotRoller {

    private static final Logger log = LoggerFactory.getLogger(BaseSnapshotRoller.class);

    protected  TallySnapshotRepository tallyRepo;
    protected ApplicationClock clock;
    protected String product;

    public BaseSnapshotRoller(String product, TallySnapshotRepository tallyRepo,
        ApplicationClock clock) {
        this.tallyRepo = tallyRepo;
        this.clock = clock;
        this.product = product;
    }

    /**
     * Roll the snapshots for the given account.
     *
     * @param accounts the accounts of the snapshots to roll.
     */
    public abstract void rollSnapshots(List<String> accounts);

    protected TallySnapshot createSnapshotFromMaxValues(AccountMaxValues maxValues,
        TallyGranularity granularity) {
        TallySnapshot snapshot = new TallySnapshot();
        snapshot.setProductId(this.product);
        snapshot.setGranularity(granularity);
        updateWithMax(snapshot, maxValues);
        return snapshot;
    }

    protected void updateWithMax(TallySnapshot snapshot, AccountMaxValues maxValues) {
        snapshot.setInstanceCount(maxValues.getMaxInstances());
        snapshot.setCores(maxValues.getMaxCores());
        snapshot.setOwnerId(maxValues.getOwnerId());
        snapshot.setAccountNumber(maxValues.getAccountNumber());
        snapshot.setSnapshotDate(getSnapshotDate(snapshot.getGranularity()));
    }

    protected void updateSnapshots(Map<String, TallySnapshot> existingSnapsByAccount,
        Map<String, AccountMaxValues> maxValuesByAccount, TallyGranularity targetGranularity) {
        List<TallySnapshot> snapshots = new LinkedList<>();
        for (Entry<String, AccountMaxValues> next : maxValuesByAccount.entrySet()) {
            TallySnapshot snap = existingSnapsByAccount.get(next.getKey());
            if (snap == null) {
                snap = createSnapshotFromMaxValues(next.getValue(), targetGranularity);
            }
            else {
                updateWithMax(snap, next.getValue());
            }
            snapshots.add(snap);
        }
        log.debug("Persisting {} {} snapshots.", snapshots.size(), targetGranularity);
        tallyRepo.saveAll(snapshots);
    }

    protected OffsetDateTime getSnapshotDate(TallyGranularity granularity) {
        switch (granularity) {
            case QUARTERLY:
                return clock.startOfCurrentQuarter();
            case WEEKLY:
                return clock.startOfCurrentWeek();
            case MONTHLY:
                return clock.startOfCurrentMonth();
            case YEARLY:
                return clock.startOfCurrentYear();
            default:
                return clock.now();
        }
    }

    protected void updateSnapshots(Collection<String> accounts, TallyGranularity maxValueGranularity,
        TallyGranularity targetGranularity, OffsetDateTime begin, OffsetDateTime end) {
        Map<String, AccountMaxValues> maxValues = getMaxValuesByAccount(accounts, maxValueGranularity, begin,
            end);

        Map<String, TallySnapshot> existingTargetSnaps = getCurrentSnapshotsByAccount(accounts,
            targetGranularity, begin, end);

        updateSnapshots(existingTargetSnaps, maxValues, targetGranularity);
    }

    @SuppressWarnings("indentation")
    protected Map<String, AccountMaxValues> getMaxValuesByAccount(Collection<String> accounts,
        TallyGranularity granularity, OffsetDateTime begin, OffsetDateTime end) {
        try (Stream<AccountMaxValues> maxValueStream =
            tallyRepo.getMaxValuesForAccounts(accounts, this.product, granularity, begin, end).stream()) {
            return maxValueStream.collect(Collectors.toMap(AccountMaxValues::getAccountNumber,
                Function.identity()));
        }
    }

    @SuppressWarnings("indentation")
    protected Map<String, TallySnapshot> getCurrentSnapshotsByAccount(Collection<String> accounts,
        TallyGranularity granularity, OffsetDateTime begin, OffsetDateTime end) {
        try (Stream<TallySnapshot> snapStream =
            tallyRepo.findByAccountNumberInAndProductIdAndGranularityAndSnapshotDateBetween(
                accounts, this.product, granularity, begin, end)) {
            return snapStream.collect(Collectors.toMap(TallySnapshot::getAccountNumber, Function.identity()));
        }
    }

}
