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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@TestInstance(Lifecycle.PER_CLASS)
public class RhsmFactNormalizerTest {

    private RhsmFactNormalizer normalizer;
    private ApplicationClock clock;

    @BeforeAll
    public void setupTests() {
        this.clock = new FixedClockConfiguration().fixedClock();

        List<String> rhelProdList = Arrays.asList("P1", "P2");
        int threshold = 24; // hours
        normalizer = new RhsmFactNormalizer(threshold, rhelProdList, clock);
    }

    @Test
    void testRhelFactSetNormalization() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, FactSetNamespace.RHSM, createRhsmFactSet(Arrays.asList("P1"), 4, 8));
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    void testInvalidNamespace() {
        NormalizedFacts normalized = new NormalizedFacts();
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(normalized,
            "unknown_namespace", createRhsmFactSet(Arrays.asList("69"), 4, 8)));
    }

    @Test
    public void testNormalizeNonRhelProduct() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, FactSetNamespace.RHSM,
            createRhsmFactSet(Arrays.asList("NonRHEL"), 4, 8));
        assertTrue(normalized.getProducts().isEmpty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlyCoresAreSet() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, FactSetNamespace.RHSM,
            createRhsmFactSet(RhsmFactNormalizer.CPU_CORES, 4));
        assertNotNull(normalized.getProducts());
        assertTrue(normalized.getProducts().isEmpty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlySocketsAreSet() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, FactSetNamespace.RHSM,
            createRhsmFactSet(RhsmFactNormalizer.CPU_SOCKETS, 8));
        assertNotNull(normalized.getProducts());
        assertTrue(normalized.getProducts().isEmpty());
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenCoresAndSocketsMissingFromFacts() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, FactSetNamespace.RHSM, createRhsmFactSet(Arrays.asList("P1")));
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testIgnoresHostWhenLastSyncIsOutOfConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(2);
        Map<String, Object> facts = createRhsmFactSet(Arrays.asList("P1"), 4, 8);
        facts.put(RhsmFactNormalizer.SYNC_TIMESTAMP, lastSynced.toString());

        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, FactSetNamespace.RHSM, facts);
        assertTrue(normalized.getProducts().isEmpty());
        assertNull(normalized.getCores());
    }

    @Test
    public void testIncludesHostWhenLastSyncIsWithinTheConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(1);
        Map<String, Object> facts = createRhsmFactSet(Arrays.asList("P1"), 4, 8);
        facts.put(RhsmFactNormalizer.SYNC_TIMESTAMP, lastSynced.toString());

        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, FactSetNamespace.RHSM, facts);
        assertTrue(normalized.getProducts().contains("RHEL"));
        assertEquals(Integer.valueOf(4), normalized.getCores());
    }

    private Map<String, Object> createRhsmFactSet(List<String> products, Integer cores, Integer sockets) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.RH_PRODUCTS, products);
        rhsmFacts.put(RhsmFactNormalizer.CPU_CORES, cores);
        rhsmFacts.put(RhsmFactNormalizer.CPU_SOCKETS, sockets);
        return rhsmFacts;
    }

    private Map<String, Object> createRhsmFactSet(List<String> products) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.RH_PRODUCTS, products);
        return rhsmFacts;
    }

    private Map<String, Object> createRhsmFactSet(String fact, Integer value) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(fact, value);
        return rhsmFacts;
    }

}
