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

import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.candlepin.subscriptions.util.ApplicationClock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalizes the inventory host FactSet from the 'rhsm' (rhsm-conduit) namespace.
 */
public class RhsmFactNormalizer implements FactSetNormalizer {

    public static final String RH_PRODUCTS = "RH_PROD";
    public static final String CPU_CORES = "CPU_CORES";
    public static final String CPU_SOCKETS = "CPU_SOCKETS";

    // TODO: This should be uppercase in conduit.
    public static final String ORG_ID = "orgId";
    public static final String SYNC_TIMESTAMP = "SYNC_TIMESTAMP";

    private final List<String> configuredRhelProducts;
    private final ApplicationClock clock;
    private final int hostSyncThresholdHours;

    public RhsmFactNormalizer(int hostSyncThresholdHours, List<String> configuredRhelProducts,
        ApplicationClock clock) {
        this.hostSyncThresholdHours = hostSyncThresholdHours;
        this.configuredRhelProducts = configuredRhelProducts;
        this.clock = clock;
    }

    @Override
    public void normalize(NormalizedFacts normalizedFacts, String namespace, Map<String, Object> rhsmFacts) {
        if (!FactSetNamespace.RHSM.equalsIgnoreCase(namespace)) {
            throw new IllegalArgumentException("Attempted to process an invalid namespace.");
        }

        // If the host hasn't been seen by rhsm-conduit, consider the host as unregistered, and do not
        // apply this host's facts.
        //
        // NOTE: This logic is applied since currently the inventory service does not prune inventory
        //       records once a host no longer exists.
        String syncTimestamp = (String) rhsmFacts.getOrDefault(SYNC_TIMESTAMP, "");
        if (!syncTimestamp.isEmpty() && hostUnregistered(OffsetDateTime.parse(syncTimestamp))) {
            return;
        }

        // Check if using RHEL
        if (isRhel(rhsmFacts)) {
            normalizedFacts.addProduct("RHEL");
        }

        // Check for cores and sockets. If not included, default to 0.
        normalizedFacts.setCores(rhsmFacts.containsKey(CPU_CORES) ? (Integer) rhsmFacts.get(CPU_CORES) : 0);
        normalizedFacts.setSockets(rhsmFacts.containsKey(CPU_SOCKETS) ?
            (Integer) rhsmFacts.get(CPU_SOCKETS) : 0);
        normalizedFacts.setOwner((String) rhsmFacts.get(ORG_ID));
    }

    private boolean isRhel(Map<String, Object> facts) {
        List<String> products = (List<String>) facts.get(RH_PRODUCTS);
        if (products == null) {
            return false;
        }

        return !products.stream()
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
