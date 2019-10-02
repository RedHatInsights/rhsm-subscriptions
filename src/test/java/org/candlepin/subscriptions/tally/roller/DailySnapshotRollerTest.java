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

import static org.candlepin.subscriptions.tally.roller.SnapshotRollerTestHelper.assertSnapshot;
import static org.candlepin.subscriptions.tally.roller.SnapshotRollerTestHelper.assertSnapshotPhysicalTotals;
import static org.candlepin.subscriptions.tally.roller.SnapshotRollerTestHelper.createAccountProductCalcs;
import static org.candlepin.subscriptions.tally.roller.SnapshotRollerTestHelper.createAccountCalc;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.ProductUsageCalculation;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
@TestInstance(Lifecycle.PER_CLASS)
public class DailySnapshotRollerTest {
    private static final String TEST_PRODUCT = "TEST_PROD";

    @Autowired
    private TallySnapshotRepository repository;

    private ApplicationClock clock;

    private DailySnapshotRoller roller;

    @BeforeAll
    public void setupAllTests() {
        this.clock = new FixedClockConfiguration().fixedClock();
        this.roller = new DailySnapshotRoller(repository, clock);
    }

    @SuppressWarnings("indentation")
    @Test
    public void testDailySnapshotProducer() {
        String account = "A1";
        List<AccountUsageCalculation> accountCalcs = createAccountProductCalcs(account, "O1",
            TEST_PRODUCT, 12, 24, 6);
        AccountUsageCalculation a1Calc = accountCalcs.get(0);
        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        roller.rollSnapshots(Arrays.asList(account), accountCalcs);

        List<TallySnapshot> dailySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
                TEST_PRODUCT, Granularity.DAILY, clock.startOfToday(), clock.endOfToday(),
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, dailySnaps.size());

        assertSnapshot(dailySnaps.get(0), TEST_PRODUCT, Granularity.DAILY, a1ProductCalc.getTotalCores(),
            a1ProductCalc.getTotalSockets(), a1ProductCalc.getTotalInstanceCount());
    }

    @SuppressWarnings("indentation")
    @Test
    public void testDailySnapIsUpdatedWhenItAlreadyExists() {
        String account = "A1";
        List<AccountUsageCalculation> accountCalcs = createAccountProductCalcs(account, "O1",
            TEST_PRODUCT, 12, 24, 6);
        AccountUsageCalculation a1Calc = accountCalcs.get(0);
        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        roller.rollSnapshots(Arrays.asList(account), accountCalcs);

        List<TallySnapshot> dailySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
                TEST_PRODUCT, Granularity.DAILY, clock.startOfToday(), clock.endOfToday(),
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, dailySnaps.size());

        TallySnapshot dailyToBeUpdated = dailySnaps.get(0);
        assertSnapshot(dailyToBeUpdated, TEST_PRODUCT, Granularity.DAILY, a1ProductCalc.getTotalCores(),
            a1ProductCalc.getTotalSockets(), a1ProductCalc.getTotalInstanceCount());

        a1ProductCalc.addPhysical(100, 200, 50);
        roller.rollSnapshots(Arrays.asList("A1"), accountCalcs);

        List<TallySnapshot> updatedDailySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
                TEST_PRODUCT, Granularity.DAILY, clock.startOfToday(), clock.endOfToday(),
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, updatedDailySnaps.size());

        TallySnapshot updated = updatedDailySnaps.get(0);
        assertEquals(dailyToBeUpdated.getId(), updated.getId());
        assertSnapshot(updated, TEST_PRODUCT, Granularity.DAILY, a1ProductCalc.getTotalCores(),
            a1ProductCalc.getTotalSockets(), a1ProductCalc.getTotalInstanceCount());
        assertSnapshotPhysicalTotals(updated, TEST_PRODUCT, Granularity.DAILY,
            a1ProductCalc.getTotalPhysicalCores(), a1ProductCalc.getTotalPhysicalSockets(),
            a1ProductCalc.getTotalPhysicalInstanceCount());
    }

    @Test
    public void ensureCurrentDailyUpdatedRegardlessOfWhetherIncomingCalculationsAreLessThanTheExisting() {
        String account = "A1";
        List<AccountUsageCalculation> accountCalcs = createAccountProductCalcs(account, "O1",
            TEST_PRODUCT, 100, 200, 10);
        AccountUsageCalculation a1Calc = accountCalcs.get(0);
        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        roller.rollSnapshots(Arrays.asList(account), accountCalcs);

        List<TallySnapshot> dailySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, Granularity.DAILY, clock.startOfToday(), clock.endOfToday(),
            PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, dailySnaps.size());

        TallySnapshot toUpdate = dailySnaps.get(0);
        assertSnapshot(toUpdate, TEST_PRODUCT, Granularity.DAILY, a1ProductCalc.getTotalCores(),
            a1ProductCalc.getTotalSockets(), a1ProductCalc.getTotalInstanceCount());

        // Update the values and run again
        accountCalcs.clear();
        accountCalcs.add(createAccountCalc(account, "O1", TEST_PRODUCT, 2, 3, 4));
        roller.rollSnapshots(Arrays.asList("A1"), accountCalcs);

        List<TallySnapshot> updatedDailySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, Granularity.DAILY, clock.startOfToday(), clock.endOfToday(),
            PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, updatedDailySnaps.size());

        TallySnapshot updated = updatedDailySnaps.get(0);
        assertEquals(toUpdate.getId(), updated.getId());
        assertSnapshot(updated, TEST_PRODUCT, Granularity.DAILY, 2, 3, 4);
    }
}
