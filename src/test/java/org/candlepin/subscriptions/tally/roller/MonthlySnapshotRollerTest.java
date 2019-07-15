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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;


@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
@TestInstance(Lifecycle.PER_CLASS)
public class MonthlySnapshotRollerTest {

    private static final String TEST_PRODUCT = "TEST_PROD";

    @Autowired
    private TallySnapshotRepository repository;

    private ApplicationClock clock;

    private MonthlySnapshotRoller roller;

    @BeforeAll
    public void setupAllTests() {
        this.clock = new FixedClockConfiguration().fixedClock();
        this.roller = new MonthlySnapshotRoller(TEST_PRODUCT, repository, clock);
    }

    @SuppressWarnings("indentation")
    @Test
    public void testMonthlySnapshotProducer() {
        List<TallySnapshot> forMonth = setupDailySnapsForMonth("A1");
        TallySnapshot max = forMonth.stream()
            .filter(t -> t.getCores() != null &&
                t.getSnapshotDate().getMonth().equals(clock.now().getMonth()) &&
                TallyGranularity.DAILY.equals(t.getGranularity()))
            .max(Comparator.comparingInt(TallySnapshot::getCores))
            .get();

        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> monthlySnaps =
            repository.findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetween("A1",
            TEST_PRODUCT, TallyGranularity.MONTHLY, clock.startOfCurrentMonth(), clock.endOfCurrentMonth());
        assertEquals(1, monthlySnaps.size());

        TallySnapshot result = monthlySnaps.get(0);
        assertEquals("A1", result.getAccountNumber());
        assertEquals(TallyGranularity.MONTHLY, result.getGranularity());
        assertEquals(TEST_PRODUCT, result.getProductId());
        // Cores and instance count should both come from the weekly snap with the largest cores count.
        assertEquals(max.getCores(), result.getCores());
        assertEquals(max.getInstanceCount(), result.getInstanceCount());
    }

    @SuppressWarnings("indentation")
    @Test
    public void testMonthlySnapIsUpdatedWhenItAlreadyExists() {
        List<TallySnapshot> forMonth = setupDailySnapsForMonth("A1");

        TallySnapshot max = forMonth.stream()
            .filter(t -> t.getCores() != null &&
                 t.getSnapshotDate().getMonth().equals(clock.now().getMonth()) &&
                 TallyGranularity.DAILY.equals(t.getGranularity()))
            .max(Comparator.comparingInt(TallySnapshot::getCores))
            .get();

        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> monthlySnaps =
            repository.findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetween("A1",
            TEST_PRODUCT, TallyGranularity.MONTHLY, clock.startOfCurrentMonth(), clock.endOfCurrentMonth());
        assertEquals(1, monthlySnaps.size());

        TallySnapshot firstUpdate = monthlySnaps.get(0);

        // Update the max so it is no longer the max. The new max should be 1 day behind
        // and will be one less than the max.
        Integer expectedUpdatedCoresAndInstance = max.getCores() - 1;
        max.setCores(12);
        max.setInstanceCount(12);
        repository.saveAndFlush(max);

        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> updatedMonthlySnaps =
            repository.findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetween("A1",
            TEST_PRODUCT, TallyGranularity.MONTHLY, clock.startOfCurrentMonth(), clock.endOfCurrentMonth());
        assertEquals(1, updatedMonthlySnaps.size());

        TallySnapshot updated = updatedMonthlySnaps.get(0);
        assertEquals(firstUpdate.getId(), updated.getId());
        assertEquals("A1", updated.getAccountNumber());
        assertEquals(TallyGranularity.MONTHLY, updated.getGranularity());
        assertEquals(TEST_PRODUCT, updated.getProductId());
        // Cores and instance count should both come from the weekly snap with the largest cores count.
        assertEquals(expectedUpdatedCoresAndInstance, updated.getCores());
        assertEquals(expectedUpdatedCoresAndInstance, updated.getInstanceCount());
    }

    private List<TallySnapshot> setupDailySnapsForMonth(String account) {
        OffsetDateTime firstDayOfFistWeekInMonth = clock.startOfWeek(clock.startOfCurrentMonth());
        OffsetDateTime lastDayOfLastWeekInMonth = clock.endOfWeek(clock.endOfCurrentMonth());

        int count = 0;
        OffsetDateTime next = firstDayOfFistWeekInMonth;
        List<TallySnapshot> dailies = new LinkedList<>();
        List<TallySnapshot> weeklies = new LinkedList<>();
        while (!next.isAfter(lastDayOfLastWeekInMonth)) {
            count++;
            dailies.add(createUnpersisted(account, TEST_PRODUCT, TallyGranularity.DAILY, count, count, next));

            OffsetDateTime peek = next.plusDays(1L);
            if (peek.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
                weeklies.add(createUnpersisted(account, TEST_PRODUCT, TallyGranularity.WEEKLY, count, count,
                    clock.startOfWeek(next)));
            }
            next = OffsetDateTime.from(peek);
        }

        List<TallySnapshot> all = new LinkedList<>(dailies);
        all.addAll(weeklies);

        return repository.saveAll(all);
    }

    private TallySnapshot createUnpersisted(String account, String product, TallyGranularity granularity,
        int cores, int instanceCount, OffsetDateTime date) {
        TallySnapshot tally = new TallySnapshot();
        tally.setAccountNumber(account);
        tally.setProductId(product);
        tally.setOwnerId("N/A");
        tally.setCores(cores);
        tally.setGranularity(granularity);
        tally.setInstanceCount(instanceCount);
        tally.setSnapshotDate(date);
        return tally;
    }

}
