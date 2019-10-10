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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

public class ProductUsageCalculationTest {

    @Test
    public void testDefaults() {
        ProductUsageCalculation calculation = new ProductUsageCalculation("Test Product");
        assertEquals("Test Product", calculation.getProductId());
        assertEquals(0, calculation.getTotalInstanceCount());
        assertEquals(0, calculation.getTotalSockets());
        assertEquals(0, calculation.getTotalCores());
        assertEquals(0, calculation.getTotalPhysicalInstanceCount());
        assertEquals(0, calculation.getTotalPhysicalSockets());
        assertEquals(0, calculation.getTotalPhysicalCores());
    }

    @Test
    public void testUncategorizedSystemTotal() {
        ProductUsageCalculation calculation = new ProductUsageCalculation("Product");
        int expected = 10;
        IntStream.rangeClosed(0, 4).forEach(i -> calculation.addToTotal(i + 2, i + 1, i));
        assertEquals(20, calculation.getTotalCores());
        assertEquals(15, calculation.getTotalSockets());
        assertEquals(10, calculation.getTotalInstanceCount());
    }

    @Test
    public void testPhysicalSystemTotal() {
        ProductUsageCalculation calculation = new ProductUsageCalculation("Product");
        int expected = 10;
        IntStream.rangeClosed(0, 4).forEach(i -> calculation.addPhysical(i + 2, i + 1, i));
        assertEquals(20, calculation.getTotalPhysicalCores());
        assertEquals(15, calculation.getTotalPhysicalSockets());
        assertEquals(10, calculation.getTotalPhysicalInstanceCount());
    }

}
