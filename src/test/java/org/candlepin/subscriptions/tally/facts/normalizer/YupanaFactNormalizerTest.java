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

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.HashMap;
import java.util.Map;


@TestInstance(Lifecycle.PER_CLASS)
public class YupanaFactNormalizerTest {

    private YupanaFactNormalizer normalizer = new YupanaFactNormalizer();

    @Test
    void testRhelFromYupanaFacts() {
        FactSet yupana = createYupanaFactSet(true);
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, yupana);
        assertTrue(normalized.getProducts().contains("RHEL"));
    }

    @Test
    public void testEmptyProductListWhenIsRhelIsFalse() {
        FactSet yupana = createYupanaFactSet(false);
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, yupana);
        assertTrue(normalized.getProducts().isEmpty());
    }

    @Test
    public void testEmptyProductListWhenIsRhelNotSet() {
        FactSet yupana = createYupanaFactSet(null);
        NormalizedFacts normalized = new NormalizedFacts();
        normalizer.normalize(normalized, yupana);
        assertTrue(normalized.getProducts().isEmpty());
    }

    @Test
    void testInvalidNamespace() {
        FactSet factSet = createYupanaFactSet(true);
        factSet.setNamespace("unknown_namespace");
        NormalizedFacts normalized = new NormalizedFacts();
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(normalized, factSet));
    }

    private FactSet createYupanaFactSet(Boolean isRhel) {
        Map<String, Object> yupanaFacts = new HashMap<>();
        if (isRhel != null) {
            yupanaFacts.put(YupanaFactNormalizer.IS_RHEL, Boolean.toString(isRhel));
        }
        return new FactSet().namespace(FactSetNamespace.YUPANA).facts(yupanaFacts);
    }
}
