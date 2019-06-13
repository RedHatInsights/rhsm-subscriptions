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
package org.candlepin.subscriptions.tally.enrichment.enricher;

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.enrichment.EnrichedFacts;
import org.candlepin.subscriptions.tally.enrichment.FactSetNamespace;
import org.candlepin.subscriptions.tally.enrichment.FactSetProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Responsible for examining an inventory host FactSet and enriching the dataset's product list
 * based on the host's facts.
 */
public class ProductEnricher implements FactSetEnricher {

    private final Set<String> rhelProductList;

    public ProductEnricher(Set<String> rhelProductList) {
        this.rhelProductList = rhelProductList;
    }

    @Override
    public void enrich(EnrichedFacts enrichedFacts, FactSet factSet) {
        // Check if using RHEL
        if (isRhel(factSet)) {
            enrichedFacts.addProduct("RHEL");
        }
    }

    private boolean isRhel(FactSet factSet) {
        // Process rhsm namespace
        if (FactSetNamespace.RHSM.equalsIgnoreCase(factSet.getNamespace())) {
            Map<String, Object> rhsmFacts = (Map<String, Object>) factSet.getFacts();
            List<String> hostProds = (List<String>) rhsmFacts.get(FactSetProperties.RHSM_RH_PRODUCTS);
            if (containsRhelProduct(hostProds)) {
                return true;
            }
        }

        // Process yupana namespace
        if (FactSetNamespace.YUPANA.equalsIgnoreCase(factSet.getNamespace())) {
            Map<String, Object> yupanaFacts = (Map<String, Object>) factSet.getFacts();
            return checkIsRhelFact(yupanaFacts.get(FactSetProperties.YUPANA_IS_RHEL));
        }

        return false;
    }

    private boolean containsRhelProduct(List<String> products) {
        if (products == null) {
            return false;
        }

        return !products.stream()
            .filter(prod -> this.rhelProductList.contains(prod))
            .collect(Collectors.toList())
            .isEmpty();
    }

    private boolean checkIsRhelFact(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof String) {
            return ((String) value).equalsIgnoreCase("true");
        }

        return false;
    }

}
