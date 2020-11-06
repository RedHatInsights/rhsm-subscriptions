/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A normalized version of an inventory host's facts.
 */
public class NormalizedFacts {

    public static final String PRODUCTS_KEY = "products";
    public static final String CORES_KEY = "cores";
    public static final String SOCKETS_KEY = "sockets";
    public static final String OWNER_KEY = "owner";

    private Set<String> products;
    private ServiceLevel sla = ServiceLevel.UNSPECIFIED;
    private Usage usage = Usage.UNSPECIFIED;
    private Integer cores;
    private Integer sockets;
    private String owner;
    /** Subscription-manager ID (UUID) of the hypervisor for this system */
    private String hypervisorUuid;
    private boolean isVirtual;
    private boolean isHypervisor;
    private boolean isHypervisorUnknown;
    private HostHardwareType hardwareType;
    private HardwareMeasurementType cloudProviderType;

    public NormalizedFacts() {
        products = new HashSet<>();
    }

    public Set<String> getProducts() {
        return products;
    }

    public void setProducts(Set<String> products) {
        this.products = products;
    }

    public void addProduct(String product) {
        products.add(product);
    }

    public Integer getCores() {
        return cores;
    }

    public void setCores(Integer cores) {
        this.cores = cores;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * Get the Subscription-manager ID (UUID) of the hypervisor for this system, if it's a guest and
     * its hypervisor is known.
     *
     * @return hypervisor UUID if known; otherwise, null
     */
    public String getHypervisorUuid() {
        return hypervisorUuid;
    }

    /**
     * Set the Subscription-manager ID (UUID) of the hypervisor for this system, if it's a guest and
     * its hypervisor is known.
     */
    public void setHypervisorUuid(String hypervisorUuid) {
        this.hypervisorUuid = hypervisorUuid;
    }

    public Integer getSockets() {
        return sockets;
    }

    public void setSockets(Integer sockets) {
        this.sockets = sockets;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public void setVirtual(boolean virtual) {
        isVirtual = virtual;
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

    public HardwareMeasurementType getCloudProviderType() {
        return cloudProviderType;
    }

    public void setCloudProviderType(HardwareMeasurementType cloudProviderType) {
        this.cloudProviderType = cloudProviderType;
    }

    public ServiceLevel getSla() {
        return sla;
    }

    public void setSla(ServiceLevel sla) {
        this.sla = sla;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public HostHardwareType getHardwareType() {
        return hardwareType;
    }

    public void setHardwareType(HostHardwareType hardwareType) {
        this.hardwareType = hardwareType;
    }

    public Map<String, Object> toInventoryPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(PRODUCTS_KEY, this.products);
        payload.put(CORES_KEY, this.cores);
        payload.put(SOCKETS_KEY, this.sockets);
        payload.put(OWNER_KEY, this.owner);
        return payload;
    }
}
