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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.enrichment.EnrichedFacts;
import org.candlepin.subscriptions.tally.enrichment.FactSetNamespace;
import org.candlepin.subscriptions.tally.enrichment.FactSetProperties;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CoresEnricherTest {

    private CoresEnricher enricher = new CoresEnricher();

    @Test
    void testCoresAddedWhenSpecified() {
        FactSet factSet = createRhsmFactSet(Arrays.asList("69"), "4");
        EnrichedFacts enriched = new EnrichedFacts();
        enricher.enrich(enriched, factSet);

        assertNotNull(enriched.getCores());
        assertEquals(Integer.valueOf(4), enriched.getCores());
    }

    @Test
    void testCoresNotAddedFromUnknowFactSet() {
        FactSet factSet = createRhsmFactSet(Arrays.asList("69"), "4");
        factSet.setNamespace("unknown_namespace");
        EnrichedFacts enriched = new EnrichedFacts();
        enricher.enrich(enriched, factSet);

        assertNull(enriched.getCores());
    }

    private FactSet createRhsmFactSet(List<String> products, String cores) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(FactSetProperties.RHSM_RH_PRODUCTS, products);
        rhsmFacts.put(FactSetProperties.RHSM_CPU_CORES, cores);
        return new FactSet().namespace(FactSetNamespace.RHSM).facts(rhsmFacts);
    }

}
