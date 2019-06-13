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

import java.util.Map;

/**
 * Responsible for examining an inventory host FactSet and enriching the dataset's cores value
 * based on the host's RHSM facts.
 */
public class CoresEnricher implements FactSetEnricher {

    @Override
    public void enrich(EnrichedFacts enrichedFacts, FactSet factSet) {
        if (FactSetNamespace.RHSM.equalsIgnoreCase(factSet.getNamespace())) {
            Map<String, Object> rhsmFacts = (Map<String, Object>) factSet.getFacts();
            enrichedFacts.setCores(getCoresFromFact(rhsmFacts.get(FactSetProperties.RHSM_CPU_CORES)));
        }
    }

    private Integer getCoresFromFact(Object cores) {
        if (cores == null) {
            return null;
        }

        if (cores instanceof String) {
            return Integer.valueOf((String) cores);
        }

        return (Integer) cores;
    }

}
