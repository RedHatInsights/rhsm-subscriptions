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

import java.util.HashSet;
import java.util.Set;

/**
 * Represents an inventory host's facts.
 */
public class InventoryHostFacts {
    private String account;
    private String displayName;
    private String orgId;
    private int cores;
    private int sockets;
    private boolean isRhel;
    private String syncTimestamp;
    private Set<String> products;

    public InventoryHostFacts(String account, String displayName, String orgId, String cores, String sockets,
        String isRhel, String products, String syncTimestamp) {
        this.account = account;
        this.displayName = displayName;
        this.orgId = orgId;
        this.cores = asInt(cores);
        this.sockets = asInt(sockets);
        this.isRhel = asBoolean(isRhel);
        this.products = asProducts(products);
        this.syncTimestamp = StringUtils.hasText(syncTimestamp) ? syncTimestamp : "";
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
        return cores;
    }

    public void setCores(Integer cores) {
        this.cores = cores;
    }

    public Integer getSockets() {
        return sockets;
    }

    public void setSockets(Integer sockets) {
        this.sockets = sockets;
    }

    public boolean isRhel() {
        return isRhel;
    }

    public void setRhel(boolean rhel) {
        isRhel = rhel;
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

    private int asInt(String value) {
        try {
            return StringUtils.hasText(value) ? Integer.valueOf(value) : 0;
        }
        catch (NumberFormatException nfe) {
            return 0;
        }
    }

    private boolean asBoolean(String value) {
        return StringUtils.hasText(value) && value.equalsIgnoreCase("true");
    }

    private Set<String> asProducts(String productJson) {
        if (!StringUtils.hasText(productJson)) {
            return new HashSet<>();
        }
        return StringUtils.commaDelimitedListToSet(productJson);
    }
}
