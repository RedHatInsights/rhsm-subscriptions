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
package org.candlepin.subscriptions.tally.facts;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.RhelProductListSource;
import org.candlepin.subscriptions.inventory.db.model.InventoryHost;
import org.candlepin.subscriptions.tally.facts.normalizer.QpcFactNormalizer;
import org.candlepin.subscriptions.tally.facts.normalizer.RhsmFactNormalizer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.core.io.FileSystemResourceLoader;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@TestInstance(Lifecycle.PER_CLASS)
public class FactNormalizerTest {

    private FactNormalizer normalizer;

    @BeforeAll
    public void setup() throws IOException {
        ApplicationProperties props = new ApplicationProperties();
        props.setRhelProductListResourceLocation("classpath:product_list.txt");

        RhelProductListSource source = new RhelProductListSource(props);
        source.setResourceLoader(new FileSystemResourceLoader());
        source.init();

        normalizer = new FactNormalizer(new ApplicationProperties(), source, Clock.systemUTC());
    }

    @Test
    public void testRhsmNormalization() {
        InventoryHost host = createRhsmHost(Arrays.asList("P1"), 12);
        NormalizedFacts normalized = normalizer.normalize(host);
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertNotNull(normalized.getCores());
        assertEquals(Integer.valueOf(12), normalized.getCores());
    }

    @Test
    public void testQpcNormalization() {
        InventoryHost host = createQpcHost(true);
        NormalizedFacts normalized = normalizer.normalize(host);
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertNull(normalized.getCores());
    }

    @Test
    public void testCombinedNamespaces() {
        InventoryHost host = createRhsmHost(Arrays.asList("P1"), 12);
        host.getFacts().putAll(createQpcHost(false).getFacts());
        assertEquals(2, host.getFacts().size());

        NormalizedFacts normalized = normalizer.normalize(host);
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
    }

    private InventoryHost createRhsmHost(List<String> products, Integer cores) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.RH_PRODUCTS, products);
        rhsmFacts.put(RhsmFactNormalizer.CPU_CORES, cores);

        Map<String, Map<String, Object>> factNamespaces = new HashMap<>();
        factNamespaces.put(FactSetNamespace.RHSM, rhsmFacts);

        InventoryHost host = new InventoryHost();
        host.setFacts(factNamespaces);
        return host;
    }

    private InventoryHost createQpcHost(boolean isRhel) {
        Map<String, Object> qpcFacts = new HashMap<>();
        qpcFacts.put(QpcFactNormalizer.IS_RHEL, Boolean.toString(isRhel));

        Map<String, Map<String, Object>> factNamespaces = new HashMap<>();
        factNamespaces.put(FactSetNamespace.QPC, qpcFacts);

        InventoryHost host = new InventoryHost();
        host.setFacts(factNamespaces);
        return host;
    }

}
