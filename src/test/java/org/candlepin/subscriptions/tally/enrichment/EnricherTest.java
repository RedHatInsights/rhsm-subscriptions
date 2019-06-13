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
package org.candlepin.subscriptions.tally.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.insights.inventory.client.model.HostOut;
import org.candlepin.subscriptions.ApplicationProperties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.core.io.FileSystemResourceLoader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@TestInstance(Lifecycle.PER_CLASS)
public class EnricherTest {

    private Enricher enricher;

    @BeforeAll
    public void setup() {
        ApplicationProperties props = new ApplicationProperties();
        props.setRhelProductListResourceLocation("classpath:rhel_prod_list.txt");

        RhelProductListSource source = new RhelProductListSource(props);
        source.setResourceLoader(new FileSystemResourceLoader());
        source.init();

        enricher = new Enricher(source);
    }

    @Test
    public void testRhsmEnrichment() {
        HostOut host = createRhsmHost(Arrays.asList("P1"), "12");
        EnrichedFacts enriched = enricher.enrich(host);
        assertTrue(enriched.getProducts().contains("RHEL"));
        assertNotNull(enriched.getCores());
        assertEquals(Integer.valueOf(12), enriched.getCores());
    }

    @Test
    public void testYupanaEnrichment() {
        HostOut host = createYupanaHost(true);
        EnrichedFacts enriched = enricher.enrich(host);
        assertTrue(enriched.getProducts().contains("RHEL"));
        assertNull(enriched.getCores());
    }

    @Test
    public void testCombinedNamespaces() {
        HostOut host = createRhsmHost(Arrays.asList("P1"), "12");
        host.getFacts().addAll(createYupanaHost(false).getFacts());
        assertEquals(2, host.getFacts().size());

        EnrichedFacts enriched = enricher.enrich(host);
        assertTrue(enriched.getProducts().contains("RHEL"));
        assertEquals(Integer.valueOf(12), enriched.getCores());
    }

    private HostOut createRhsmHost(List<String> products, String cores) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(FactSetProperties.RHSM_RH_PRODUCTS, products);
        rhsmFacts.put(FactSetProperties.RHSM_CPU_CORES, cores);

        FactSet rhsmFactSet = new FactSet().namespace(FactSetNamespace.RHSM).facts(rhsmFacts);
        return new HostOut().addFactsItem(rhsmFactSet);
    }

    private HostOut createYupanaHost(boolean isRhel) {
        Map<String, Object> yupanaFacts = new HashMap<>();
        yupanaFacts.put(FactSetProperties.YUPANA_IS_RHEL, Boolean.toString(isRhel));

        FactSet yupanaFactSet = new FactSet().namespace(FactSetNamespace.YUPANA).facts(yupanaFacts);
        HostOut host = new HostOut().addFactsItem(yupanaFactSet);
        return host;
    }

}
