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

import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

/**
 * The default product usage collection rules.
 */
public class DefaultProductUsageCollector implements ProductUsageCollector {

    @Override
    public void collect(UsageCalculation prodCalc, NormalizedFacts normalizedFacts) {
        int cores = normalizedFacts.getCores() != null ? normalizedFacts.getCores() : 0;
        int sockets = normalizedFacts.getSockets() != null ? normalizedFacts.getSockets() : 0;

        // Cloud provider hosts only account for a single socket.
        if (normalizedFacts.getCloudProviderType() != null) {
            prodCalc.addCloudProvider(normalizedFacts.getCloudProviderType(), cores, 1, 1);
        }
        // Accumulate for physical systems.
        else if (!normalizedFacts.isVirtual()) {
            prodCalc.addPhysical(cores, sockets, 1);
        }
        // Any other system is simply added to the overall total
        else {
            prodCalc.addToTotal(cores, sockets, 1);
        }
    }

    @Override
    public void collectForHypervisor(String account, UsageCalculation prodCalc,
        NormalizedFacts hypervisorFacts) {

        /* do nothing for hypervisor-guest mappings by default */
    }

}
