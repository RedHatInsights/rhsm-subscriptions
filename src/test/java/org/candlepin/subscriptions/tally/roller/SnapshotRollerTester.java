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
package org.candlepin.subscriptions.tally.roller;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurement;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Totals;

import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Since the roller tests are very similar, this class provides some common test
 * scenarios.
 */
@SuppressWarnings("linelength")
public class SnapshotRollerTester<R extends BaseSnapshotRoller> {
    private static final String TEST_PRODUCT = "TEST_PROD";

    private TallySnapshotRepository repository;
    private R roller;

    public SnapshotRollerTester(TallySnapshotRepository tallySnapshotRepository, R roller) {
        this.repository = tallySnapshotRepository;
        this.roller = roller;
    }

    @SuppressWarnings("indentation")
    public void performBasicSnapshotRollerTest(Granularity granularity,
        OffsetDateTime startOfGranularPeriod, OffsetDateTime endOfGranularPeriod) {
        AccountUsageCalculation a1Calc = createTestData();
        String account = a1Calc.getAccount();

        UsageCalculation a1ProductCalc = a1Calc.getCalculation(createUsageKey(TEST_PRODUCT));
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1Calc));

        List<TallySnapshot> currentSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(account,
                TEST_PRODUCT, granularity, ServiceLevel.EMPTY, Usage.EMPTY,
                startOfGranularPeriod, endOfGranularPeriod,
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, currentSnaps.size());
        assertSnapshot(currentSnaps.get(0), a1ProductCalc, granularity);
    }

    @SuppressWarnings("indentation")
    public void performSnapshotUpdateTest(Granularity granularity,
        OffsetDateTime startOfGranularPeriod, OffsetDateTime endOfGranularPeriod) {
        AccountUsageCalculation a1Calc = createTestData();
        String account = a1Calc.getAccount();
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1Calc));

        List<TallySnapshot> currentSnaps = repository
             .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(account,
                 TEST_PRODUCT, granularity, ServiceLevel.EMPTY, Usage.EMPTY,
                 startOfGranularPeriod, endOfGranularPeriod,
                 PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, currentSnaps.size());
        TallySnapshot toBeUpdated = currentSnaps.get(0);

        UsageCalculation a1ProductCalc = a1Calc.getCalculation(createUsageKey(TEST_PRODUCT));
        assertNotNull(a1ProductCalc);
        assertSnapshot(toBeUpdated, a1ProductCalc, granularity);

        a1ProductCalc.addPhysical(100, 200, 50);
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1Calc));

        List<TallySnapshot> updatedSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(account,
                TEST_PRODUCT, granularity, ServiceLevel.EMPTY, Usage.EMPTY,
                startOfGranularPeriod, endOfGranularPeriod,
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, updatedSnaps.size());

        TallySnapshot updated = updatedSnaps.get(0);
        assertEquals(toBeUpdated.getId(), updated.getId());
        assertSnapshot(updated, a1ProductCalc, granularity);
    }

    @SuppressWarnings("indentation")
    public void performUpdateWithLesserValueTest(Granularity granularity,
        OffsetDateTime startOfGranularPeriod, OffsetDateTime endOfGranularPeriod, boolean expectMaxAccepted) {
        int lowCores = 2;
        int lowSockets = 2;
        int lowInstances = 2;

        int highCores = 100;
        int highSockets = 200;
        int highInstances = 10;

        String account = "A1";
        AccountUsageCalculation a1HighCalc = createAccountCalc(account, "O1", TEST_PRODUCT,
            highCores, highSockets, highInstances);
        AccountUsageCalculation a1LowCalc = createAccountCalc(account, "O1", TEST_PRODUCT,
            lowCores, lowSockets, lowInstances);

        AccountUsageCalculation expectedCalc = expectMaxAccepted ? a1HighCalc : a1LowCalc;

        // Roll to the initial high values
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1HighCalc));

        List<TallySnapshot> currentSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate("A1",
                TEST_PRODUCT, granularity, ServiceLevel.EMPTY,  Usage.EMPTY,
                startOfGranularPeriod, endOfGranularPeriod,
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, currentSnaps.size());

        TallySnapshot toUpdate = currentSnaps.get(0);
        assertSnapshot(toUpdate, a1HighCalc.getCalculation(createUsageKey(TEST_PRODUCT)), granularity);


        // Roll again with the low values
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1LowCalc));

        List<TallySnapshot> updatedSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(account,
                TEST_PRODUCT, granularity, ServiceLevel.EMPTY,  Usage.EMPTY,
                startOfGranularPeriod, endOfGranularPeriod,
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, updatedSnaps.size());

        TallySnapshot updated = updatedSnaps.get(0);
        assertEquals(toUpdate.getId(), updated.getId());

        // Use the calculation with the expected
        assertSnapshot(updated, expectedCalc.getCalculation(createUsageKey(TEST_PRODUCT)), granularity);
    }

    @SuppressWarnings("indentation")
    public void performDoesNotPersistEmptySnapshots(Granularity granularity,
        OffsetDateTime startOfGranularPeriod, OffsetDateTime endOfGranularPeriod) {

        AccountUsageCalculation calc = createAccountCalc("12345678", "O1", TEST_PRODUCT,
            0, 0, 0);
        roller.rollSnapshots(Collections.singletonList("12345678"), Collections.singletonList(calc));

        List<TallySnapshot> currentSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate("A1",
                TEST_PRODUCT, granularity, ServiceLevel.EMPTY,  Usage.EMPTY,
                startOfGranularPeriod, endOfGranularPeriod,
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(0, currentSnaps.size());
    }

    private UsageCalculation.Key createUsageKey(String product) {
        return new UsageCalculation.Key(product, ServiceLevel.EMPTY, Usage.EMPTY);
    }

    private AccountUsageCalculation createTestData() {
        return createAccountCalc("my_account", "O1", TEST_PRODUCT, 12, 24, 6);
    }

    private AccountUsageCalculation createAccountCalc(String account, String owner, String product,
        int totalCores, int totalSockets, int totalInstances) {
        UsageCalculation productCalc = new UsageCalculation(createUsageKey(product));
        productCalc.addPhysical(totalCores, totalSockets, totalInstances);
        productCalc.addHypervisor(totalCores, totalSockets, totalInstances);
        productCalc.addCloudProvider(HardwareMeasurementType.AWS, totalCores, totalSockets,
            totalInstances);

        AccountUsageCalculation calc = new AccountUsageCalculation(account);
        calc.setOwner(owner);
        calc.addCalculation(productCalc);

        return calc;
    }

    private void assertSnapshot(TallySnapshot snapshot, UsageCalculation expectedVals,
        Granularity expectedGranularity) {
        assertNotNull(snapshot);
        assertEquals(expectedGranularity, snapshot.getGranularity());
        assertEquals(expectedVals.getProductId(), snapshot.getProductId());

        for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
            HardwareMeasurement measurement = snapshot.getHardwareMeasurement(type);
            Totals expectedTotal = expectedVals.getTotals(type);
            if (measurement == null) {
                assertNull(expectedTotal);
                continue;
            }

            assertNotNull(expectedTotal);
            assertEquals(expectedTotal.getCores(), measurement.getCores());
            assertEquals(expectedTotal.getSockets(), measurement.getSockets());
            assertEquals(expectedTotal.getInstances(), measurement.getInstanceCount());
        }
    }

}
