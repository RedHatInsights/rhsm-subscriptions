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

import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurement;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.tally.ProductUsageCalculation;

import java.util.ArrayList;
import java.util.List;

public class SnapshotRollerTestHelper {
    public static AccountUsageCalculation createAccountCalc(String account, String owner, String product,
        int totalCores, int totalSockets, int totalInstances) {
        ProductUsageCalculation productCalc = new ProductUsageCalculation(product);
        // Assume physical systems added
        productCalc.addPhysical(totalCores, totalSockets, totalInstances);

        AccountUsageCalculation calc = new AccountUsageCalculation(account);
        calc.setOwner(owner);
        calc.addProductCalculation(productCalc);

        return calc;
    }

    public static List<AccountUsageCalculation> createAccountProductCalcs(String account, String owner,
        String product, int totalCores, int totalSockets, int totalInstances) {
        List<AccountUsageCalculation> calcs = new ArrayList<>();
        calcs.add(createAccountCalc(account, owner, product, totalCores, totalSockets, totalInstances));
        return calcs;
    }

    public static void assertSnapshot(TallySnapshot snapshot, String expectedProduct,
        Granularity expectedGranularity, int expectedCores, int expectedSockets,
        int expectedInstances) {
        assertNotNull(snapshot);
        assertEquals(expectedGranularity, snapshot.getGranularity());
        assertEquals(expectedProduct, snapshot.getProductId());

        HardwareMeasurement total = snapshot.getHardwareMeasurement(HardwareMeasurementType.TOTAL);

        assertEquals(expectedCores, total.getCores());
        assertEquals(expectedSockets, total.getSockets());
        assertEquals(expectedInstances, total.getInstanceCount());
    }

    public static void assertSnapshotPhysicalTotals(TallySnapshot snapshot, String expectedProduct,
        Granularity expectedGranularity, int expectedPhysCores, int expectedPhysSockets,
        int expectedPhysInstances) {
        assertNotNull(snapshot);
        assertEquals(expectedGranularity, snapshot.getGranularity());
        assertEquals(expectedProduct, snapshot.getProductId());

        HardwareMeasurement physical = snapshot.getHardwareMeasurement(HardwareMeasurementType.PHYSICAL);

        assertEquals(expectedPhysCores, physical.getCores());
        assertEquals(expectedPhysSockets, physical.getSockets());
        assertEquals(expectedPhysInstances, physical.getInstanceCount());
    }

    private SnapshotRollerTestHelper() {
        throw new AssertionError();
    }
}
