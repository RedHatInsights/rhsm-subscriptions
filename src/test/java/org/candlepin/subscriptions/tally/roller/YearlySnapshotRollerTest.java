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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
@TestInstance(Lifecycle.PER_CLASS)
public class YearlySnapshotRollerTest {

    private static final String TEST_PRODUCT = "TEST_PROD";

    @Autowired
    private TallySnapshotRepository repository;

    private ApplicationClock clock;
    private YearlySnapshotRoller roller;

    @BeforeEach
    public void setupTest() {
        this.clock = new FixedClockConfiguration().fixedClock();
        this.roller = new YearlySnapshotRoller(TEST_PRODUCT, repository, clock);
    }

    @Test
    public void testYearlySnapshotProduction() {
        setupYearsWorthOfMonthlySnaps("A1", clock.now().minusYears(1));
        setupYearsWorthOfMonthlySnaps("A1", clock.now());
        setupYearsWorthOfMonthlySnaps("A1", clock.now().plusYears(1));
        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> yearlySnaps =
            repository.findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetween("A1",
            TEST_PRODUCT, TallyGranularity.YEARLY, clock.startOfCurrentYear(), clock.endOfCurrentYear());
        assertEquals(1, yearlySnaps.size());

        TallySnapshot result = yearlySnaps.get(0);
        assertEquals(TallyGranularity.YEARLY, result.getGranularity());
        assertEquals(TEST_PRODUCT, result.getProductId());
        assertEquals(Integer.valueOf(11), result.getCores());
        assertEquals(Integer.valueOf(11), result.getInstanceCount());
    }

    @Test
    public void testYearlySnapIsUpdatedWhenItAlreadyExists() {
        setupYearsWorthOfMonthlySnaps("A1", clock.now().minusYears(1));
        List<TallySnapshot> monthlies = setupYearsWorthOfMonthlySnaps("A1", clock.now());
        setupYearsWorthOfMonthlySnaps("A1", clock.now().plusYears(1));
        roller.rollSnapshots(Arrays.asList("A1"));

        List<TallySnapshot> originalSnaps =
            repository.findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetween("A1",
            TEST_PRODUCT, TallyGranularity.YEARLY, clock.startOfCurrentYear(), clock.endOfCurrentYear());
        assertEquals(1, originalSnaps.size());

        TallySnapshot toUpdate = originalSnaps.get(0);
        assertEquals(TallyGranularity.YEARLY, toUpdate.getGranularity());
        assertEquals(TEST_PRODUCT, toUpdate.getProductId());
        assertEquals(Integer.valueOf(11), toUpdate.getCores());
        assertEquals(Integer.valueOf(11), toUpdate.getInstanceCount());

        TallySnapshot monthlyToUpdate = monthlies.get(0);
        monthlyToUpdate.setCores(100);
        monthlyToUpdate.setInstanceCount(50);
        repository.saveAndFlush(monthlyToUpdate);

        roller.rollSnapshots(Arrays.asList("A1"));

        // Check the yearly again. Should still be a single instance, but have updated values.
        List<TallySnapshot> updatedSnaps =
            repository.findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetween("A1",
            TEST_PRODUCT, TallyGranularity.YEARLY, clock.startOfCurrentYear(), clock.endOfCurrentYear());
        assertEquals(1, originalSnaps.size());

        TallySnapshot updated = updatedSnaps.get(0);
        assertEquals(toUpdate.getId(), updated.getId());
        assertEquals("A1", toUpdate.getAccountNumber());
        assertEquals(TallyGranularity.YEARLY, updated.getGranularity());
        assertEquals(monthlyToUpdate.getProductId(), updated.getProductId());
        assertEquals(monthlyToUpdate.getCores(), updated.getCores());
        assertEquals(monthlyToUpdate.getInstanceCount(), updated.getInstanceCount());
    }

    private List<TallySnapshot> setupYearsWorthOfMonthlySnaps(String account, OffsetDateTime anyDayOfWeek) {
        OffsetDateTime startOfYear = clock.startOfYear(anyDayOfWeek);

        List<TallySnapshot> monthlyForYear = new LinkedList<>();
        IntStream.rangeClosed(0, 11).forEach(i -> {
            monthlyForYear.add(createUnpersisted(account, TEST_PRODUCT, TallyGranularity.MONTHLY, i,
                i, startOfYear.plusMonths(i)));
        });

        repository.saveAll(monthlyForYear);
        repository.flush();

        return monthlyForYear;
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
