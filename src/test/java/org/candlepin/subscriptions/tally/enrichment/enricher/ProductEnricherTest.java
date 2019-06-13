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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.enrichment.EnrichedFacts;
import org.candlepin.subscriptions.tally.enrichment.FactSetNamespace;
import org.candlepin.subscriptions.tally.enrichment.FactSetProperties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


@TestInstance(Lifecycle.PER_CLASS)
public class ProductEnricherTest {

    private ProductEnricher productEnricher;

    @BeforeAll
    public void setupTest() {
        HashSet<String> rhelProdList = new HashSet<>(Arrays.asList("P1", "P2"));
        this.productEnricher = new ProductEnricher(rhelProdList);
    }

    @Test
    void testRhelFromRhsmFacts() {
        FactSet rhsm = createRhsmFactSet(Arrays.asList("P1"), "4");
        EnrichedFacts enriched = new EnrichedFacts();
        productEnricher.enrich(enriched, rhsm);
        assertTrue(enriched.getProducts().contains("RHEL"));
    }

    @Test
    void testRhelFromYupanaFacts() {
        FactSet yupana = createYupanaFactSet(true);
        EnrichedFacts enriched = new EnrichedFacts();
        productEnricher.enrich(enriched, yupana);
        assertTrue(enriched.getProducts().contains("RHEL"));
    }

    @Test
    void testEmptyProductListWithInvalidFactSetNamespace() {
        Map<String, Object> facts = new HashMap<>();
        facts.put(FactSetProperties.RHSM_RH_PRODUCTS, Arrays.asList("P1"));
        FactSet invalidNamespace = createFactSet("not_processed_by_enricher", facts);

        EnrichedFacts enriched = new EnrichedFacts();
        productEnricher.enrich(enriched, invalidNamespace);

        // Even though this FactSet has the correct product property, it should not get
        // processed because it is not in an expected namespace.
        assertTrue(enriched.getProducts().isEmpty());
    }

    private FactSet createRhsmFactSet(List<String> products, String cores) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(FactSetProperties.RHSM_RH_PRODUCTS, products);
        rhsmFacts.put(FactSetProperties.RHSM_CPU_CORES, cores);
        return createFactSet(FactSetNamespace.RHSM, rhsmFacts);
    }

    private FactSet createYupanaFactSet(boolean isRhel) {
        Map<String, Object> yupanaFacts = new HashMap<>();
        yupanaFacts.put(FactSetProperties.YUPANA_IS_RHEL, Boolean.toString(isRhel));
        return createFactSet(FactSetNamespace.YUPANA, yupanaFacts);
    }

    private FactSet createFactSet(String namespace, Map<String, Object> facts) {
        return new FactSet().namespace(namespace).facts(facts);
    }

}
