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

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.insights.inventory.client.model.HostOut;
import org.candlepin.subscriptions.tally.facts.normalizer.FactSetNormalizer;
import org.candlepin.subscriptions.tally.facts.normalizer.RhsmFactNormalizer;
import org.candlepin.subscriptions.tally.facts.normalizer.YupanaFactNormalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * Responsible for examining an inventory host and producing normalized
 * and condensed facts based on the host's facts.
 */
@Component
public class FactNormalizer {

    private static final Logger log = LoggerFactory.getLogger(FactNormalizer.class);

    private Map<String, FactSetNormalizer> normalizers;

    public FactNormalizer(RhelProductListSource rhelProductListSource) throws IOException {
        normalizers = new HashMap<>();
        normalizers.put(FactSetNamespace.RHSM, new RhsmFactNormalizer(rhelProductListSource.getProductIds()));
        normalizers.put(FactSetNamespace.YUPANA, new YupanaFactNormalizer());
    }

    /**
     * Normalize the FactSets of the given host.
     *
     * @param host the target inventory host.
     * @return a normalized version of the host's facts.
     */
    public NormalizedFacts normalize(HostOut host) {
        NormalizedFacts facts = new NormalizedFacts();
        for (FactSet factSet : host.getFacts()) {
            if (normalizers.containsKey(factSet.getNamespace())) {
                log.debug("Normalizing facts for host/namespace: {}/{}", host.getDisplayName(),
                    factSet.getNamespace());
                normalizers.get(factSet.getNamespace()).normalize(facts, factSet);
            }
        }
        return facts;
    }

}
