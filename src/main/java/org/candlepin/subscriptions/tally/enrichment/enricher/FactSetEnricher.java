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
package org.candlepin.subscriptions.tally.enrichment.enricher;

import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.subscriptions.tally.enrichment.EnrichedFacts;

/**
 * A FactSetEnricher processes an inventory FactSet and converts it to some form of enriched data.
 */
public interface FactSetEnricher {

    /**
     * Enriches the given inventory FactSet.
     *
     * @param enrichedFacts the existing set of enriched facts.
     * @param factSet the fact set to process.
     */
    void enrich(EnrichedFacts enrichedFacts, FactSet factSet);
}
