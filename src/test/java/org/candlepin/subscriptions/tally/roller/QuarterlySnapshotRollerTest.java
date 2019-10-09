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

import org.junit.jupiter.api.BeforeEach;
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
public class QuarterlySnapshotRollerTest {

    private static final String TEST_PRODUCT = "TEST_PROD";

    @Autowired
    private TallySnapshotRepository repository;

    private ApplicationClock clock;
    private QuarterlySnapshotRoller roller;

    @BeforeEach
    public void setupTest() {
        this.clock = new FixedClockConfiguration().fixedClock();
        this.roller = new QuarterlySnapshotRoller(repository, clock);
    }

    @Test
    public void testQuarterlySnapshotProduction() {
        String account = "A1";
        List<AccountUsageCalculation> accountCalcs = createAccountProductCalcs(account, "O1",
            TEST_PRODUCT, 12, 24, 6);
        AccountUsageCalculation a1Calc = accountCalcs.get(0);
        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        roller.rollSnapshots(Arrays.asList(account), accountCalcs);

        List<TallySnapshot> quarterlySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, Granularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, quarterlySnaps.size());

        assertSnapshot(quarterlySnaps.get(0), TEST_PRODUCT, Granularity.QUARTERLY,
            a1ProductCalc.getTotalCores(), a1ProductCalc.getTotalSockets(),
            a1ProductCalc.getTotalInstanceCount());
    }

    @Test
    public void testQuarterlySnapIsUpdatedWhenItAlreadyExists() {
        String account = "A1";
        List<AccountUsageCalculation> accountCalcs = createAccountProductCalcs(account, "O1",
            TEST_PRODUCT, 12, 24, 6);
        AccountUsageCalculation a1Calc = accountCalcs.get(0);
        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        roller.rollSnapshots(Arrays.asList(account), accountCalcs);

        List<TallySnapshot> originalSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, Granularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, originalSnaps.size());

        TallySnapshot toUpdate = originalSnaps.get(0);
        assertSnapshot(toUpdate, TEST_PRODUCT, Granularity.QUARTERLY, a1ProductCalc.getTotalCores(),
            a1ProductCalc.getTotalSockets(), a1ProductCalc.getTotalInstanceCount());

        a1ProductCalc.addPhysical(400, 200, 100);
        roller.rollSnapshots(Arrays.asList("A1"), accountCalcs);

        // Check the yearly again. Should still be a single instance, but have updated values.
        List<TallySnapshot> updatedSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, Granularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, originalSnaps.size());

        TallySnapshot updated = updatedSnaps.get(0);
        assertEquals(toUpdate.getId(), updated.getId());
        assertSnapshot(updated, TEST_PRODUCT, Granularity.QUARTERLY, a1ProductCalc.getTotalCores(),
            a1ProductCalc.getTotalSockets(), a1ProductCalc.getTotalInstanceCount());
        assertSnapshotPhysicalTotals(updated, TEST_PRODUCT, Granularity.QUARTERLY,
            a1ProductCalc.getTotalPhysicalCores(), a1ProductCalc.getTotalPhysicalSockets(),
            a1ProductCalc.getTotalPhysicalInstanceCount());
    }

    @Test
    public void ensureCurrentQuarterlyIsNotUpdatedWhenIncomingCalculationsAreLessThanTheExisting() {
        int expectedCores = 100;
        int expectedSockets = 200;
        int expectedInstances = 10;

        String account = "A1";
        List<AccountUsageCalculation> accountCalcs = createAccountProductCalcs(account, "O1",
            TEST_PRODUCT, expectedCores, expectedSockets, expectedInstances);
        AccountUsageCalculation a1Calc = accountCalcs.get(0);
        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        roller.rollSnapshots(Arrays.asList(account), accountCalcs);

        List<TallySnapshot> quarterlySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, Granularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, quarterlySnaps.size());

        TallySnapshot toUpdate = quarterlySnaps.get(0);
        assertSnapshot(toUpdate, TEST_PRODUCT, Granularity.QUARTERLY, a1ProductCalc.getTotalCores(),
            a1ProductCalc.getTotalSockets(), a1ProductCalc.getTotalInstanceCount());

        // Update the values and run again
        accountCalcs.clear();
        accountCalcs.add(createAccountCalc(account, "O1", TEST_PRODUCT, 2, 2, 2));
        roller.rollSnapshots(Arrays.asList("A1"), accountCalcs);

        List<TallySnapshot> updatedQuarterlySnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
            TEST_PRODUCT, Granularity.QUARTERLY, clock.startOfCurrentQuarter(),
            clock.endOfCurrentQuarter(), PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, updatedQuarterlySnaps.size());

        TallySnapshot updated = updatedQuarterlySnaps.get(0);
        assertEquals(toUpdate.getId(), updated.getId());
        assertSnapshot(updated, TEST_PRODUCT, Granularity.QUARTERLY, expectedCores, expectedSockets,
            expectedInstances);
    }

}
