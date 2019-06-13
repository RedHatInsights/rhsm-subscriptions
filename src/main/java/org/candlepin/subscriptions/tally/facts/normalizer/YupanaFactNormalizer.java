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

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import java.util.Map;

/**
 * Normalizes the inventory host FactSet from the 'yupana' (QPC) namespace.
 */
public class YupanaFactNormalizer implements FactSetNormalizer {

    public static final String IS_RHEL = "IS_RHEL";

    @Override
    public void normalize(NormalizedFacts normalizedFacts, FactSet factSet) {
        if (!FactSetNamespace.YUPANA.equalsIgnoreCase(factSet.getNamespace())) {
            throw new IllegalArgumentException("FactSet has an invalid namespace.");
        }

        // Check if this is a RHEL host and set product.
        Map<String, Object> yupanaFacts = (Map<String, Object>) factSet.getFacts();
        if (checkIsRhelFact(yupanaFacts.get(IS_RHEL))) {
            normalizedFacts.addProduct("RHEL");
        }
    }

    private boolean checkIsRhelFact(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof String) {
            return ((String) value).equalsIgnoreCase("true");
        }

        return false;
    }
}
