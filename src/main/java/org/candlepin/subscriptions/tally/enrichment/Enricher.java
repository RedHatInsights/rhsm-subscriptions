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

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.insights.inventory.client.model.HostOut;
import org.candlepin.subscriptions.tally.enrichment.enricher.CoresEnricher;
import org.candlepin.subscriptions.tally.enrichment.enricher.FactSetEnricher;
import org.candlepin.subscriptions.tally.enrichment.enricher.ProductEnricher;

import java.util.LinkedList;
import java.util.List;

// Impl Note: I decided not to make this an injectable component so that
// we are forced to create an Enricher manually, forcing the RHEL product
// list to be reloaded without requiring an application restart. I didn't
// want to read the file list inside of the ProductEnricher each time since
// it would happen for each FactSet that it processes which is wasteful.

/**
 * Responsible for examining an inventory host and producing an enriched
 * and condensed data model based on the host's facts.
 */
public class Enricher {

    private List<FactSetEnricher> enrichers;

    public Enricher(RhelProductListSource rhelProductListSource) {
        enrichers = new LinkedList<>();
        enrichers.add(new ProductEnricher(rhelProductListSource.getProductIds()));
        enrichers.add(new CoresEnricher());
    }

    public EnrichedFacts enrich(HostOut host) {
        EnrichedFacts facts = new EnrichedFacts();
        for (FactSet factSet : host.getFacts()) {
            enrichers.forEach(enricher -> {
                enricher.enrich(facts, factSet);
            });
        }
        return facts;
    }

}
