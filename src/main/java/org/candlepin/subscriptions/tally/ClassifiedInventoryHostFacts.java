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

import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;

import java.util.Set;

/**
 * An {@link InventoryHostFacts} instance enriched with classification information.
 */
public class ClassifiedInventoryHostFacts {
    private final InventoryHostFacts inventoryHostFacts;
    private boolean isHypervisor;
    private boolean isHypervisorUnknown;

    public ClassifiedInventoryHostFacts(InventoryHostFacts inventoryHostFacts) {
        this.inventoryHostFacts = inventoryHostFacts;
    }

    public boolean isHypervisor() {
        return isHypervisor;
    }

    public void setHypervisor(boolean hypervisor) {
        isHypervisor = hypervisor;
    }

    public boolean isHypervisorUnknown() {
        return isHypervisorUnknown;
    }

    public void setHypervisorUnknown(boolean hypervisorUnknown) {
        isHypervisorUnknown = hypervisorUnknown;
    }

    public boolean isVirtual() {
        return inventoryHostFacts.isVirtual();
    }

    public void setVirtual(boolean virtual) {
        inventoryHostFacts.setVirtual(virtual);
    }

    public String getAccount() {
        return inventoryHostFacts.getAccount();
    }

    public String getDisplayName() {
        return inventoryHostFacts.getDisplayName();
    }

    public String getOrgId() {
        return inventoryHostFacts.getOrgId();
    }

    public Integer getCores() {
        return inventoryHostFacts.getCores();
    }

    public Integer getSockets() {
        return inventoryHostFacts.getSockets();
    }

    public String getSyncTimestamp() {
        return inventoryHostFacts.getSyncTimestamp();
    }

    public Set<String> getProducts() {
        return inventoryHostFacts.getProducts();
    }

    public String getSystemProfileInfrastructureType() {
        return inventoryHostFacts.getSystemProfileInfrastructureType();
    }

    public Integer getSystemProfileCoresPerSocket() {
        return inventoryHostFacts.getSystemProfileCoresPerSocket();
    }

    public Integer getSystemProfileSockets() {
        return inventoryHostFacts.getSystemProfileSockets();
    }

    public Set<String> getQpcProducts() {
        return inventoryHostFacts.getQpcProducts();
    }

    public Set<String> getQpcProductIds() {
        return inventoryHostFacts.getQpcProductIds();
    }

    public Set<String> getSystemProfileProductIds() {
        return inventoryHostFacts.getSystemProfileProductIds();
    }

    public String getHypervisorUuid() {
        return inventoryHostFacts.getHypervisorUuid();
    }

    public String getGuestId() {
        return inventoryHostFacts.getGuestId();
    }

    public String getSubscriptionManagerId() {
        return inventoryHostFacts.getSubscriptionManagerId();
    }

    public String getSyspurposeRole() {
        return inventoryHostFacts.getSyspurposeRole();
    }
}
