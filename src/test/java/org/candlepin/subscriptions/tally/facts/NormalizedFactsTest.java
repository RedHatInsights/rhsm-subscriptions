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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;


public class NormalizedFactsTest {

    @Test
    public void testToInventoryPayload() {
        NormalizedFacts facts = new NormalizedFacts();
        facts.addProduct("RHEL");
        facts.setCores(12);

        Map<String, Object> payload = facts.toInventoryPayload();
        assertTrue(payload.containsKey(NormalizedFacts.PRODUCTS_KEY));
        Set<String> products = (Set<String>) payload.get(NormalizedFacts.PRODUCTS_KEY);
        assertTrue(products.contains("RHEL"));

        assertTrue(payload.containsKey(NormalizedFacts.CORES_KEY));
        assertNotNull(payload.get(NormalizedFacts.CORES_KEY));
        assertEquals(12, payload.get(NormalizedFacts.CORES_KEY));
    }

    @Test
    public void testEmptyInventoryPayload() {
        NormalizedFacts facts = new NormalizedFacts();

        Map<String, Object> payload = facts.toInventoryPayload();
        assertTrue(payload.containsKey(NormalizedFacts.PRODUCTS_KEY));
        Set<String> products = (Set<String>) payload.get(NormalizedFacts.PRODUCTS_KEY);
        assertTrue(products.isEmpty());

        assertTrue(payload.containsKey(NormalizedFacts.CORES_KEY));
        assertNull(payload.get(NormalizedFacts.CORES_KEY));
    }
}
