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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

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
public class RhsmFactNormalizerTest {

    private RhsmFactNormalizer normalizer;

    @BeforeAll
    public void setupTests() {
        HashSet<String> rhelProdList = new HashSet<>(Arrays.asList("P1", "P2"));
        normalizer = new RhsmFactNormalizer(rhelProdList);
    }

    @Test
    void testRhelFactSetNormalization() {
        FactSet rhsm = createRhsmFactSet(Arrays.asList("P1"), 4);
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, rhsm);
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertEquals(Integer.valueOf(4), normalized.getCores());
    }

    @Test
    void testInvalidNamespace() {
        FactSet factSet = createRhsmFactSet(Arrays.asList("69"), 4);
        factSet.setNamespace("unknown_namespace");
        NormalizedFacts normalized = new NormalizedFacts();
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(normalized, factSet));
    }

    @Test
    public void testNormalizeNonRhelProduct() {
        FactSet rhsm = createRhsmFactSet(Arrays.asList("NonRHEL"), 4);
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, rhsm);
        assertTrue(normalized.getProducts().isEmpty());
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFacts() {
        FactSet rhsm = createRhsmFactSet(4);
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, rhsm);
        assertNotNull(normalized.getProducts());
        assertTrue(normalized.getProducts().isEmpty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
    }

    @Test
    public void testNormalizeWhenCoresMissingFromFacts() {
        FactSet rhsm = createRhsmFactSet(Arrays.asList("P1"));
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, rhsm);
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
    }

    private FactSet createRhsmFactSet(List<String> products, Integer cores) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.RH_PRODUCTS, products);
        rhsmFacts.put(RhsmFactNormalizer.CPU_CORES, cores);
        return new FactSet().namespace(FactSetNamespace.RHSM).facts(rhsmFacts);
    }

    private FactSet createRhsmFactSet(List<String> products) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.RH_PRODUCTS, products);
        return new FactSet().namespace(FactSetNamespace.RHSM).facts(rhsmFacts);
    }

    private FactSet createRhsmFactSet(Integer cores) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.CPU_CORES, cores);
        return new FactSet().namespace(FactSetNamespace.RHSM).facts(rhsmFacts);
    }
}
