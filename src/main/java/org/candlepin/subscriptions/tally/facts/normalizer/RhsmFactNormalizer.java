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
package org.candlepin.subscriptions.tally.facts.normalizer;

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalizes the inventory host FactSet from the 'rhsm' (rhsm-conduit) namespace.
 */
public class RhsmFactNormalizer implements FactSetNormalizer {

    public static final String RH_PRODUCTS = "RH_PROD";
    public static final String CPU_CORES = "CPU_CORES";

    private final Set<String> configuredRhelProducts;

    public RhsmFactNormalizer(Set<String> configuredRhelProducts) {
        this.configuredRhelProducts = configuredRhelProducts;
    }

    @Override
    public void normalize(NormalizedFacts normalizedFacts, FactSet factSet) {
        if (!FactSetNamespace.RHSM.equalsIgnoreCase(factSet.getNamespace())) {
            throw new IllegalArgumentException("FactSet has an invalid namespace.");
        }

        Map<String, Object> rhsmFacts = (Map<String, Object>) factSet.getFacts();

        // Check if using RHEL
        if (isRhel(rhsmFacts)) {
            normalizedFacts.addProduct("RHEL");
        }

        // Check for cores. If not included, default to 0 cores.
        normalizedFacts.setCores(rhsmFacts.containsKey(CPU_CORES) ? (Integer) rhsmFacts.get(CPU_CORES) : 0);
    }

    private boolean isRhel(Map<String, Object> facts) {
        List<String> products = (List<String>) facts.get(RH_PRODUCTS);
        if (products == null) {
            return false;
        }

        return !products.stream()
            .filter(this.configuredRhelProducts::contains)
            .collect(Collectors.toList())
            .isEmpty();
    }

}
