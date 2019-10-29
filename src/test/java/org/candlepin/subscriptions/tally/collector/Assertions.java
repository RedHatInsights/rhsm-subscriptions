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
package org.candlepin.subscriptions.tally.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.subscriptions.tally.ProductUsageCalculation;

public class Assertions {

    public static void assertTotalsCalculation(ProductUsageCalculation calc, int sockets, int cores,
        int instances) {
        assertEquals(cores, calc.getTotalCores());
        assertEquals(sockets, calc.getTotalSockets());
        assertEquals(instances, calc.getTotalInstanceCount());
    }

    public static void assertPhysicalTotalsCalculation(ProductUsageCalculation calc, int physicalSockets,
        int physicalCores, int physicalInstances) {
        assertEquals(physicalCores, calc.getTotalPhysicalCores());
        assertEquals(physicalSockets, calc.getTotalPhysicalSockets());
        assertEquals(physicalInstances, calc.getTotalPhysicalInstanceCount());
    }

    public static void assertHypervisorTotalsCalculation(ProductUsageCalculation calc, int hypSockets,
        int hypCores, int hypInstances) {
        assertEquals(hypCores, calc.getTotalHypervisorCores());
        assertEquals(hypSockets, calc.getTotalHypervisorSockets());
        assertEquals(hypInstances, calc.getTotalHypervisorInstanceCount());
    }

    private Assertions() {
        throw new IllegalStateException("Utility class; should never be instantiated!");
    }
}
