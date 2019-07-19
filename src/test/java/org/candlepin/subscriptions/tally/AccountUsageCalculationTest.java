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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class AccountUsageCalculationTest {

    @Test
    public void testGetProductCalculation() {
        String p1 = "Product1";
        AccountUsageCalculation calc = new AccountUsageCalculation("Account1");
        ProductUsageCalculation prodCalc = new ProductUsageCalculation(p1);
        calc.addProductCalculation(prodCalc);

        assertEquals(1, calc.getProducts().size());
        assertEquals(prodCalc, calc.getProductCalculation(p1));
    }

    @Test
    public void testContainsProductCalculation() {
        String p1 = "Product1";
        AccountUsageCalculation calc = new AccountUsageCalculation("Account1");
        ProductUsageCalculation prodCalc = new ProductUsageCalculation(p1);
        calc.addProductCalculation(prodCalc);

        assertEquals(1, calc.getProducts().size());
        assertTrue(calc.containsProductCalculation(p1));
        assertFalse(calc.containsProductCalculation("NOT_THERE"));
    }

    @Test
    public void testGetProducts() {
        String p1 = "Product1";
        String p2 = "Product2";
        String p3 = "Product3";
        List<String> expectedProducts = Arrays.asList(p1, p2, p3);

        AccountUsageCalculation calc = new AccountUsageCalculation("Account1");
        calc.addProductCalculation(new ProductUsageCalculation(p1));
        calc.addProductCalculation(new ProductUsageCalculation(p2));
        calc.addProductCalculation(new ProductUsageCalculation(p3));

        assertEquals(3, calc.getProducts().size());
        assertTrue(calc.getProducts().containsAll(expectedProducts));
    }
}
