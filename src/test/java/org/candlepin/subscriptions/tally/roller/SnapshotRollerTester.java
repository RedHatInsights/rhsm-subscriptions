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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurement;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.ProductUsageCalculation;
import org.candlepin.subscriptions.tally.ProductUsageCalculation.Totals;

import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Since the roller tests are very similar, this class provides some common test
 * scenarios.
 */
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

        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1Calc));

        List<TallySnapshot> currentSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(account,
                TEST_PRODUCT, granularity, startOfGranularPeriod, endOfGranularPeriod,
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
             .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(account,
                 TEST_PRODUCT, granularity, startOfGranularPeriod, endOfGranularPeriod,
                 PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, currentSnaps.size());
        TallySnapshot toBeUpdated = currentSnaps.get(0);

        ProductUsageCalculation a1ProductCalc = a1Calc.getProductCalculation(TEST_PRODUCT);
        assertNotNull(a1ProductCalc);
        assertSnapshot(toBeUpdated, a1ProductCalc, granularity);

        a1ProductCalc.addPhysical(100, 200, 50);
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1Calc));

        List<TallySnapshot> updatedSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(account,
                TEST_PRODUCT, granularity, startOfGranularPeriod, endOfGranularPeriod,
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
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate("A1",
                TEST_PRODUCT, granularity, startOfGranularPeriod, endOfGranularPeriod,
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, currentSnaps.size());

        TallySnapshot toUpdate = currentSnaps.get(0);
        assertSnapshot(toUpdate, a1HighCalc.getProductCalculation(TEST_PRODUCT), granularity);


        // Roll again with the low values
        roller.rollSnapshots(Arrays.asList(account), Arrays.asList(a1LowCalc));

        List<TallySnapshot> updatedSnaps = repository
            .findByAccountNumberAndProductIdAndGranularityAndSnapshotDateBetweenOrderBySnapshotDate(account,
                TEST_PRODUCT, granularity, startOfGranularPeriod, endOfGranularPeriod,
                PageRequest.of(0, 100)).stream().collect(Collectors.toList());
        assertEquals(1, updatedSnaps.size());

        TallySnapshot updated = updatedSnaps.get(0);
        assertEquals(toUpdate.getId(), updated.getId());

        // Use the calculation with the expected
        assertSnapshot(updated, expectedCalc.getProductCalculation(TEST_PRODUCT), granularity);
    }

    private AccountUsageCalculation createTestData() {
        AccountUsageCalculation calc = createAccountCalc("my_account", "O1", TEST_PRODUCT, 12, 24, 6);

        return calc;
    }

    private AccountUsageCalculation createAccountCalc(String account, String owner, String product,
        int totalCores, int totalSockets, int totalInstances) {
        ProductUsageCalculation productCalc = new ProductUsageCalculation(product);
        productCalc.addPhysical(totalCores, totalSockets, totalInstances);
        productCalc.addHypervisor(totalCores, totalSockets, totalInstances);
        productCalc.addCloudProvider(HardwareMeasurementType.AWS, totalCores, totalSockets,
            totalInstances);

        AccountUsageCalculation calc = new AccountUsageCalculation(account);
        calc.setOwner(owner);
        calc.addProductCalculation(productCalc);

        return calc;
    }

    private void assertSnapshot(TallySnapshot snapshot, ProductUsageCalculation expectedVals,
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
