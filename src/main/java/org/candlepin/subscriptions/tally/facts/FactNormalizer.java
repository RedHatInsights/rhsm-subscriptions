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

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.RhelProductListSource;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.util.ApplicationClock;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Responsible for examining an inventory host and producing normalized
 * and condensed facts based on the host's facts.
 */
public class FactNormalizer {
    private final List<String> configuredRhelProducts;
    private final ApplicationClock clock;
    private final int hostSyncThresholdHours;

    public FactNormalizer(ApplicationProperties props, RhelProductListSource rhelProductListSource,
        ApplicationClock clock) throws IOException {
        this.clock = clock;
        this.hostSyncThresholdHours = props.getHostLastSyncThresholdHours();
        this.configuredRhelProducts = rhelProductListSource.list();
    }

    /**
     * Normalize the FactSets of the given host.
     *
     * @param hostFacts the collection of facts to normalize.
     * @return a normalized version of the host's facts.
     */
    public NormalizedFacts normalize(InventoryHostFacts hostFacts) {
        NormalizedFacts normalizedFacts = new NormalizedFacts();
        normalizeSystemProfileFacts(normalizedFacts, hostFacts);
        normalizeRhsmFacts(normalizedFacts, hostFacts);
        normalizeQpcFacts(normalizedFacts, hostFacts);
        return normalizedFacts;
    }

    private void normalizeSystemProfileFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
        if (hostFacts.getSystemProfileSockets() != 0) {
            normalizedFacts.setSockets(hostFacts.getSystemProfileSockets());
        }
        if (hostFacts.getSystemProfileSockets() != 0 && hostFacts.getSystemProfileCoresPerSocket() != 0) {
            normalizedFacts.setCores(
                hostFacts.getSystemProfileCoresPerSocket() * hostFacts.getSystemProfileSockets());
        }
        if (isRhel(hostFacts.getSystemProfileProductIds())) {
            normalizedFacts.addProduct("RHEL");
        }
    }

    private void normalizeRhsmFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
        // If the host hasn't been seen by rhsm-conduit, consider the host as unregistered, and do not
        // apply this host's facts.
        //
        // NOTE: This logic is applied since currently the inventory service does not prune inventory
        //       records once a host no longer exists.
        String syncTimestamp = hostFacts.getSyncTimestamp();
        boolean skipRhsmFacts =
            !syncTimestamp.isEmpty() && hostUnregistered(OffsetDateTime.parse(syncTimestamp));
        if (!skipRhsmFacts) {
            // Check if using RHEL
            if (isRhel(hostFacts.getProducts())) {
                normalizedFacts.addProduct("RHEL");
            }

            // Check for cores and sockets. If not included, default to 0.
            if (normalizedFacts.getCores() == null || hostFacts.getCores() != 0) {
                normalizedFacts.setCores(hostFacts.getCores());
            }
            if (normalizedFacts.getSockets() == null || hostFacts.getSockets() != 0) {
                normalizedFacts.setSockets(hostFacts.getSockets());
            }
            normalizedFacts.setOwner(hostFacts.getOrgId());
        }
    }

    private void normalizeQpcFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
        // Check if this is a RHEL host and set product.
        if (hostFacts.getQpcProducts().contains("RHEL")) {
            normalizedFacts.addProduct("RHEL");
        }
    }

    private boolean isRhel(Collection<String> productIds) {
        return !productIds.stream()
                    .filter(this.configuredRhelProducts::contains)
                    .collect(Collectors.toList())
                    .isEmpty();
    }

    /**
     * A host is considered unregistered if the last time it was synced passes the configured number
     * of hours.
     *
     * NOTE: If the passed lastSync date is null, it is considered to be registered.
     *
     * @param lastSync the last known time that a host sync occured from pinhead to conduit.
     * @return true if the host is considered unregistered, false otherwise.
     */
    private boolean hostUnregistered(OffsetDateTime lastSync) {
        // If last sync is not present, consider it a registered host.
        if (lastSync == null) {
            return false;
        }
        return lastSync.isBefore(clock.now().minusHours(hostSyncThresholdHours));
    }

}
