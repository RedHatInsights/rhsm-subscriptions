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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;


@TestInstance(Lifecycle.PER_CLASS)
public class QpcFactNormalizerTest {

    private QpcFactNormalizer normalizer = new QpcFactNormalizer();
    private ApplicationClock clock = new ApplicationClock();

    @Test
    void testRhelFromQpcFacts() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, createQpcFactSet(true));
        assertTrue(normalized.getProducts().contains("RHEL"));
    }

    @Test
    public void testEmptyProductListWhenIsRhelIsFalse() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, createQpcFactSet(false));
        assertTrue(normalized.getProducts().isEmpty());
    }

    @Test
    public void testEmptyProductListWhenIsRhelNotSet() {
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, createQpcFactSet(null));
        assertTrue(normalized.getProducts().isEmpty());
    }

    private InventoryHostFacts createQpcFactSet(Boolean isRhel) {
        return new InventoryHostFacts("Account", "Test System", "test_org", null,
            null, isRhel == null ? null :isRhel.toString(),
            null, clock.now().toString());
    }
}
