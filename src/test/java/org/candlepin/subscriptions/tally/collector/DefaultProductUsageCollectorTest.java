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

import static org.candlepin.subscriptions.tally.collector.Assertions.*;
import static org.candlepin.subscriptions.tally.collector.TestHelper.*;

import org.candlepin.subscriptions.tally.ProductUsageCalculation;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import org.junit.jupiter.api.Test;

public class DefaultProductUsageCollectorTest {

    private DefaultProductUsageCollector collector;

    public DefaultProductUsageCollectorTest() {
        collector = new DefaultProductUsageCollector();
    }

    @Test
    public void testCountsForHypervisor() {
        NormalizedFacts facts = hypervisorFacts(4, 12);

        ProductUsageCalculation calc = new ProductUsageCalculation("RHEL");
        collector.collect(calc, facts);
        assertTotalsCalculation(calc, 4, 12, 1);
        assertHypervisorTotalsCalculation(calc, 0, 0, 0);
        assertPhysicalTotalsCalculation(calc, 4, 12, 1);
    }

    @Test
    public void testCountsForGuestWithUnknownHypervisor() {
        NormalizedFacts facts = guestFacts(3, 12, false);

        ProductUsageCalculation calc = new ProductUsageCalculation("RHEL");
        collector.collect(calc, facts);

        // A guest with a known hypervisor contributes to the overall totals,
        // but does not contribute to the hypervisor or physical totals.
        assertTotalsCalculation(calc, 3, 12, 1);
        assertHypervisorTotalsCalculation(calc, 0, 0, 0);
        assertPhysicalTotalsCalculation(calc, 0, 0, 0);
    }

    @Test
    public void testCountsForGuestWithKnownHypervisor() {
        NormalizedFacts facts = guestFacts(3, 12, true);

        ProductUsageCalculation calc = new ProductUsageCalculation("RHEL");
        collector.collect(calc, facts);

        // A guest with an unknown hypervisor contributes to the overall totals
        // It is considered as having its own unique hypervisor and therefore
        // contributes its own values to the hypervisor counts.
        assertTotalsCalculation(calc, 3, 12, 1);
        assertHypervisorTotalsCalculation(calc, 0, 0, 0);
        assertPhysicalTotalsCalculation(calc, 0, 0, 0);
    }

    @Test
    public void testCountsForPhysicalSystem() {
        NormalizedFacts facts = physicalNonHypervisor(4, 12);

        ProductUsageCalculation calc = new ProductUsageCalculation("RHEL");
        collector.collect(calc, facts);

        assertTotalsCalculation(calc, 4, 12, 1);
        assertHypervisorTotalsCalculation(calc, 0, 0, 0);
        assertPhysicalTotalsCalculation(calc, 4, 12, 1);
    }
}
