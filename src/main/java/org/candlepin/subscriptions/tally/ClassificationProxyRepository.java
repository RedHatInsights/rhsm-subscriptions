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

package org.candlepin.subscriptions.tally;

import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads results from the {@link InventoryRepository}, classifies them, and then makes the results available
 * for further use in tally calculations.
 */
@Component
public class ClassificationProxyRepository {
    private final InventoryRepository inventoryRepository;

    @Autowired
    public ClassificationProxyRepository(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Stream<ClassifiedInventoryHostFacts> getFacts(Collection<String> accounts) {
        List<InventoryHostFacts> hostFactsList =
            inventoryRepository.getFacts(accounts).collect(Collectors.toList());

        Set<String> hypervisorUuids = new HashSet<>();
        Set<String> subscriptionManagerIds = new HashSet<>();

        // Create a listing of all the hypervisors in this org and record the sub man id while we're at it.
        for (InventoryHostFacts hostFacts : hostFactsList) {
            if (StringUtils.hasText(hostFacts.getSubscriptionManagerId())) {
                subscriptionManagerIds.add(hostFacts.getSubscriptionManagerId());
            }

            if (StringUtils.hasText(hostFacts.getHypervisorUuid())) {
                hypervisorUuids.add(hostFacts.getHypervisorUuid());
            }
        }

        // Duplicate the list of hypervisors and remove all the hypervisor IDs we know of to find
        // unknown/missing hypervisors
        Set<String> missingHypervisors = new HashSet<>(hypervisorUuids);
        missingHypervisors.removeAll(subscriptionManagerIds);

        List<ClassifiedInventoryHostFacts> classifiedFactsList = new ArrayList<>(hostFactsList.size());
        for (InventoryHostFacts hostFacts : hostFactsList) {
            ClassifiedInventoryHostFacts enhancedFacts = new ClassifiedInventoryHostFacts(hostFacts);

            // Determine whether a reported system has an unknown hypervisor. The hypervisor is
            // considered unknown if the system is virtual (a guest) and either has a null
            // hypervisor UUID OR the guest's hypervisor UUID was not reported by conduit.
            String hypervisorUuid = enhancedFacts.getHypervisorUuid();
            boolean isHypervisorUnknown =
                (enhancedFacts.isVirtual() && !StringUtils.hasText(hypervisorUuid)) ||
                missingHypervisors.contains(hypervisorUuid);
            enhancedFacts.setHypervisorUnknown(isHypervisorUnknown);

            // Determine whether a reported system is a hypervisor based on whether it is on our list
            String subManId = enhancedFacts.getSubscriptionManagerId();
            boolean isHypervisor = StringUtils.hasText(subManId) && hypervisorUuids.contains(subManId);
            enhancedFacts.setHypervisor(isHypervisor);

            classifiedFactsList.add(enhancedFacts);
        }

        return classifiedFactsList.stream();
    }
}
