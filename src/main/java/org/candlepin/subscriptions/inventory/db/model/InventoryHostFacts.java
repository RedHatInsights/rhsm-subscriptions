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
package org.candlepin.subscriptions.inventory.db.model;

import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an inventory host's facts.
 */
public class InventoryHostFacts {
    private String account;
    private String displayName;
    private String orgId;
    private Integer cores;
    private Integer sockets;
    private String syncTimestamp;
    private Set<String> products;
    private String systemProfileInfrastructureType;
    private Integer systemProfileCoresPerSocket;
    private Integer systemProfileSockets;
    private boolean isVirtual;
    private String hypervisorUuid;
    private String guestId;
    private String subscriptionManagerId;
    private Set<String> qpcProducts;
    private Set<String> qpcProductIds;
    private Set<String> systemProfileProductIds;
    private String syspurposeRole;
    private String cloudProvider;
    private OffsetDateTime staleTimestamp;

    public InventoryHostFacts() {
        // Used for testing
    }

    @SuppressWarnings("squid:S00107")
    public InventoryHostFacts(String account, String displayName, String orgId, String cores, String sockets,
        String products, String syncTimestamp, String systemProfileInfrastructureType,
        String systemProfileCores, String systemProfileSockets, String qpcProducts, String qpcProductIds,
        String systemProfileProductIds, String syspurposeRole, String isVirtual, String hypervisorUuid,
        String guestId, String subscriptionManagerId, String cloudProvider, OffsetDateTime staleTimestamp) {

        this.account = account;
        this.displayName = displayName;
        this.orgId = orgId;
        this.cores = asInt(cores);
        this.sockets = asInt(sockets);
        this.products = asStringSet(products);
        this.qpcProducts = asStringSet(qpcProducts);
        this.qpcProductIds = asStringSet(qpcProductIds);
        this.syncTimestamp = StringUtils.hasText(syncTimestamp) ? syncTimestamp : "";
        this.systemProfileInfrastructureType = systemProfileInfrastructureType;
        this.systemProfileCoresPerSocket = asInt(systemProfileCores);
        this.systemProfileSockets = asInt(systemProfileSockets);
        this.systemProfileProductIds = asStringSet(systemProfileProductIds);
        this.syspurposeRole = syspurposeRole;
        this.isVirtual = asBoolean(isVirtual);
        this.hypervisorUuid = hypervisorUuid;
        this.guestId = guestId;
        this.subscriptionManagerId = subscriptionManagerId;
        this.cloudProvider = cloudProvider;
        this.staleTimestamp = staleTimestamp;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Integer getCores() {
        return cores == null ? 0 : cores;
    }

    public void setCores(Integer cores) {
        this.cores = cores;
    }

    public Integer getSockets() {
        return sockets == null ? 0 : sockets;
    }

    public void setSockets(Integer sockets) {
        this.sockets = sockets;
    }

    public String getSyncTimestamp() {
        return syncTimestamp;
    }

    public void setSyncTimestamp(String syncTimestamp) {
        this.syncTimestamp = syncTimestamp;
    }

    public Set<String> getProducts() {
        return products;
    }

    public void setProducts(Set<String> products) {
        this.products = products;
    }

    public void setProducts(String products) {
        this.products = asStringSet(products);
    }

    private boolean asBoolean(String value) {
        return Boolean.parseBoolean(value);
    }

    private Integer asInt(String value) {
        try {
            return StringUtils.hasText(value) ? Integer.valueOf(value) : 0;
        }
        catch (NumberFormatException nfe) {
            return 0;
        }
    }

    private Set<String> asStringSet(String productJson) {
        if (!StringUtils.hasText(productJson)) {
            return new HashSet<>();
        }
        return StringUtils.commaDelimitedListToSet(productJson);
    }

    public String getSystemProfileInfrastructureType() {
        return systemProfileInfrastructureType;
    }

    public void setSystemProfileInfrastructureType(String systemProfileInfrastructureType) {
        this.systemProfileInfrastructureType = systemProfileInfrastructureType;
    }

    public Integer getSystemProfileCoresPerSocket() {
        return systemProfileCoresPerSocket == null ? 0 : systemProfileCoresPerSocket;
    }

    public void setSystemProfileCoresPerSocket(Integer coresPerSocket) {
        this.systemProfileCoresPerSocket = coresPerSocket;
    }

    public Integer getSystemProfileSockets() {
        return systemProfileSockets == null ? 0 : systemProfileSockets;
    }

    public void setSystemProfileSockets(Integer sockets) {
        this.systemProfileSockets = sockets;
    }

    public Set<String> getQpcProducts() {
        return qpcProducts;
    }

    public void setQpcProducts(String qpcProducts) {
        this.qpcProducts = asStringSet(qpcProducts);
    }

    public Set<String> getQpcProductIds() {
        return qpcProductIds;
    }

    public void setQpcProductIds(String qpcProductIds) {
        this.qpcProductIds = asStringSet(qpcProductIds);
    }

    public Set<String> getSystemProfileProductIds() {
        return systemProfileProductIds;
    }

    public void setSystemProfileProductIds(String productIds) {
        this.systemProfileProductIds = asStringSet(productIds);
    }

    public String getSyspurposeRole() {
        return syspurposeRole;
    }

    public void setSyspurposeRole(String syspurposeRole) {
        this.syspurposeRole = syspurposeRole;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public void setVirtual(boolean virtual) {
        isVirtual = virtual;
    }

    public String getHypervisorUuid() {
        return hypervisorUuid;
    }

    public void setHypervisorUuid(String hypervisorUuid) {
        this.hypervisorUuid = hypervisorUuid;
    }

    public String getGuestId() {
        return guestId;
    }

    public void setGuestId(String guestId) {
        this.guestId = guestId;
    }

    public String getSubscriptionManagerId() {
        return subscriptionManagerId;
    }

    public void setSubscriptionManagerId(String subscriptionManagerId) {
        this.subscriptionManagerId = subscriptionManagerId;
    }

    public String getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    public OffsetDateTime getStaleTimestamp() {
        return staleTimestamp;
    }

    public void setStaleTimestamp(OffsetDateTime staleTimestamp) {
        this.staleTimestamp = staleTimestamp;
    }
}
