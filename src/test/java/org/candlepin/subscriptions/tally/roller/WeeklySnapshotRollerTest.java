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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
@TestInstance(Lifecycle.PER_CLASS)
public class WeeklySnapshotRollerTest {

    private static final String TEST_PRODUCT = "TEST_PROD";

    @Autowired
    private TallySnapshotRepository repository;

    private ApplicationClock clock;

    private WeeklySnapshotRoller roller;

    @BeforeAll
    public void setupAllTests() {
        this.clock = new FixedClockConfiguration().fixedClock();
        this.roller = new WeeklySnapshotRoller(TEST_PRODUCT, repository, clock);
    }

    @Test
    public void testWeeklySnapshotProduction() {
        setupWeeksWorthOfDailySnaps("A1", clock.now());

        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> weeklySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, TallyGranularity.WEEKLY, clock.startOfCurrentWeek(), clock.endOfCurrentWeek(),
            PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, weeklySnaps.size());

        TallySnapshot result = weeklySnaps.get(0);
        assertEquals(TallyGranularity.WEEKLY, result.getGranularity());
        assertEquals(TEST_PRODUCT, result.getProductId());
        assertEquals(Integer.valueOf(6), result.getCores());
        assertEquals(Integer.valueOf(7), result.getSockets());
        assertEquals(Integer.valueOf(6), result.getInstanceCount());
    }

    @Test
    public void testWeeklySnapIsUpdatedWhenItAlreadyExists() {
        // Set up three weeks of daily snaps
        OffsetDateTime now = clock.now();
        List<TallySnapshot> dailies = setupWeeksWorthOfDailySnaps("A1", now);
        setupWeeksWorthOfDailySnaps("A1", now.minusDays(7));
        setupWeeksWorthOfDailySnaps("A1", now.minusDays(14));

        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> weeklySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, TallyGranularity.WEEKLY, clock.startOfCurrentWeek(), clock.endOfCurrentWeek(),
            PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, weeklySnaps.size());
        TallySnapshot result = weeklySnaps.get(0);
        assertEquals("A1", result.getAccountNumber());
        assertEquals(TallyGranularity.WEEKLY, result.getGranularity());
        assertEquals(TEST_PRODUCT, result.getProductId());
        assertEquals(Integer.valueOf(6), result.getCores());
        assertEquals(Integer.valueOf(7), result.getSockets());
        assertEquals(Integer.valueOf(6), result.getInstanceCount());

        // Update the daily and run again
        TallySnapshot dailyToUpdate = dailies.get(0);
        dailyToUpdate.setCores(124);
        dailyToUpdate.setSockets(248);
        dailyToUpdate.setInstanceCount(20);
        repository.saveAndFlush(dailyToUpdate);

        roller.rollSnapshots(Arrays.asList("A1"));

        // Check the weekly again. Should still be a single instance, but have updated values.
        List<TallySnapshot> updatedWeeklySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, TallyGranularity.WEEKLY, clock.startOfCurrentWeek(), clock.endOfCurrentWeek(),
            PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, updatedWeeklySnaps.size());

        TallySnapshot updated = updatedWeeklySnaps.get(0);
        assertEquals(result.getId(), updated.getId());
        assertEquals("A1", result.getAccountNumber());
        assertEquals(TallyGranularity.WEEKLY, updated.getGranularity());
        assertEquals(dailyToUpdate.getProductId(), updated.getProductId());
        assertEquals(dailyToUpdate.getCores(), updated.getCores());
        assertEquals(dailyToUpdate.getSockets(), updated.getSockets());
        assertEquals(dailyToUpdate.getInstanceCount(), updated.getInstanceCount());
    }

    private List<TallySnapshot> setupWeeksWorthOfDailySnaps(String account, OffsetDateTime anyDayOfWeek) {
        OffsetDateTime endOfWeek = clock.endOfWeek(anyDayOfWeek);
        OffsetDateTime startOfWeek = clock.startOfWeek(anyDayOfWeek);
        System.out.println(String.format("WEEK: %s -> %s", startOfWeek, endOfWeek));

        List<TallySnapshot> dailyForWeek = new LinkedList<>();
        IntStream.rangeClosed(0, 6).forEach(i -> {
            dailyForWeek.add(createUnpersisted(account, TEST_PRODUCT, TallyGranularity.DAILY, i,
                i + 1, i, startOfWeek.plusDays(i)));
        });

        repository.saveAll(dailyForWeek);
        repository.flush();

        return dailyForWeek;
    }

    private TallySnapshot createUnpersisted(String account, String product, TallyGranularity granularity,
        int cores, int sockets, int instanceCount, OffsetDateTime date) {
        TallySnapshot tally = new TallySnapshot();
        tally.setAccountNumber(account);
        tally.setProductId(product);
        tally.setOwnerId("N/A");
        tally.setCores(cores);
        tally.setSockets(sockets);
        tally.setGranularity(granularity);
        tally.setInstanceCount(instanceCount);
        tally.setSnapshotDate(date);
        return tally;
    }

}
