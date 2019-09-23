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
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Responsible for examining an inventory host and producing normalized
 * and condensed facts based on the host's facts.
 */
public class FactNormalizer {
    private static final Logger log = LoggerFactory.getLogger(FactNormalizer.class);

    private final ApplicationClock clock;
    private final int hostSyncThresholdHours;
    private final Map<Integer, List<String>> productIdToProductsMap;
    private final Map<String, List<String>> roleToProductsMap;

    public FactNormalizer(ApplicationProperties props,
        ProductIdToProductsMapSource productIdToProductsMapSource,
        RoleToProductsMapSource roleToProductsMapSource,
        ApplicationClock clock) throws IOException {
        this.clock = clock;
        this.hostSyncThresholdHours = props.getHostLastSyncThresholdHours();
        this.productIdToProductsMap = productIdToProductsMapSource.getValue();
        this.roleToProductsMap = roleToProductsMapSource.getValue();
    }

    static boolean isRhelVariant(String product) {
        return product.startsWith("RHEL ") && !product.startsWith("RHEL for ");
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
        normalizeSocketCount(normalizedFacts);
        normalizeConflictingOrMissingRhelVariants(normalizedFacts);
        return normalizedFacts;
    }

    private void normalizeSocketCount(NormalizedFacts normalizedFacts) {
        Integer sockets = normalizedFacts.getSockets();
        if (sockets != null && (sockets % 2) == 1) {
            normalizedFacts.setSockets(sockets + 1);
        }
    }

    private void normalizeConflictingOrMissingRhelVariants(NormalizedFacts normalizedFacts) {
        long variantCount = normalizedFacts.getProducts().stream().filter(FactNormalizer::isRhelVariant)
            .count();

        boolean hasRhel = normalizedFacts.getProducts().contains("RHEL");

        if ((variantCount == 0 && hasRhel) || variantCount > 1) {
            normalizedFacts.addProduct("RHEL Ungrouped");
        }
    }

    private void normalizeSystemProfileFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
        if (hostFacts.getSystemProfileSockets() != 0) {
            normalizedFacts.setSockets(hostFacts.getSystemProfileSockets());
        }
        if (hostFacts.getSystemProfileSockets() != 0 && hostFacts.getSystemProfileCoresPerSocket() != 0) {
            normalizedFacts.setCores(
                hostFacts.getSystemProfileCoresPerSocket() * hostFacts.getSystemProfileSockets());
        }
        getProductsFromProductIds(normalizedFacts, hostFacts.getSystemProfileProductIds());
    }

    private void getProductsFromProductIds(NormalizedFacts normalizedFacts, Collection<String> productIds) {
        for (String productId : productIds) {
            try {
                Integer numericProductId = Integer.parseInt(productId);
                normalizedFacts.getProducts().addAll(
                    productIdToProductsMap.getOrDefault(numericProductId, Collections.emptyList()));
            }
            catch (NumberFormatException e) {
                log.debug("Skipping non-numeric productId: {}", productId);
            }
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
            getProductsFromProductIds(normalizedFacts, hostFacts.getProducts());

            // Check for cores and sockets. If not included, default to 0.
            if (normalizedFacts.getCores() == null || hostFacts.getCores() != 0) {
                normalizedFacts.setCores(hostFacts.getCores());
            }
            if (normalizedFacts.getSockets() == null || hostFacts.getSockets() != 0) {
                normalizedFacts.setSockets(hostFacts.getSockets());
            }
            normalizedFacts.setOwner(hostFacts.getOrgId());
            if (hostFacts.getSyspurposeRole() != null) {
                normalizedFacts.getProducts().removeIf(FactNormalizer::isRhelVariant);
                normalizedFacts.getProducts().addAll(
                    roleToProductsMap.getOrDefault(hostFacts.getSyspurposeRole(), Collections.emptyList()));
            }
        }
    }

    private void normalizeQpcFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
        // Check if this is a RHEL host and set product.
        if (hostFacts.getQpcProducts().contains("RHEL")) {
            normalizedFacts.addProduct("RHEL");
        }
        getProductsFromProductIds(normalizedFacts, hostFacts.getQpcProductIds());
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
