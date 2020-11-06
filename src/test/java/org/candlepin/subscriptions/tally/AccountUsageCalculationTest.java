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
package org.candlepin.subscriptions.tally;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class AccountUsageCalculationTest {

    @Test
    public void testGetCalculation() {
        String p1 = "Product1";
        AccountUsageCalculation calc = new AccountUsageCalculation("Account1");
        UsageCalculation prodCalc = new UsageCalculation(createUsageKey(p1));
        calc.addCalculation(prodCalc);

        assertEquals(1, calc.getProducts().size());
        assertEquals(prodCalc, calc.getCalculation(createUsageKey(p1)));
    }

    @Test
    public void testContainsCalculation() {
        String p1 = "Product1";
        AccountUsageCalculation calc = new AccountUsageCalculation("Account1");
        UsageCalculation prodCalc = new UsageCalculation(createUsageKey(p1));
        calc.addCalculation(prodCalc);

        assertEquals(1, calc.getProducts().size());
        assertTrue(calc.containsCalculation(createUsageKey(p1)));
        assertFalse(calc.containsCalculation(createUsageKey("NOT_THERE")
        ));
    }

    @Test
    public void testGetProducts() {
        String p1 = "Product1";
        String p2 = "Product2";
        String p3 = "Product3";

        AccountUsageCalculation calc = new AccountUsageCalculation("Account1");
        calc.addCalculation(new UsageCalculation(createUsageKey(p1)));
        calc.addCalculation(new UsageCalculation(createUsageKey(p2)));
        calc.addCalculation(new UsageCalculation(createUsageKey(p3)));

        assertEquals(3, calc.getProducts().size());
        assertThat(calc.getProducts(), Matchers.containsInAnyOrder(p1, p2, p3));
    }

    private UsageCalculation.Key createUsageKey(String productId) {
        return new UsageCalculation.Key(productId, ServiceLevel.UNSPECIFIED, Usage.UNSPECIFIED);
    }
}
