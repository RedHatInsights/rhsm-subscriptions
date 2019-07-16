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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The calculated usage for an account.
 */
public class AccountUsageCalculation {

    private String account;
    private String owner;
    private Map<String, ProductUsageCalculation> productCalculations;


    public AccountUsageCalculation(String account) {
        this.account = account;
        this.productCalculations = new HashMap<>();
    }

    public String getAccount() {
        return account;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void addProductCalculation(ProductUsageCalculation calc) {
        this.productCalculations.put(calc.getProductId(), calc);
    }

    public boolean containsProductCalculation(String productId) {
        return this.productCalculations.containsKey(productId);
    }

    public Set<String> getProducts() {
        return this.productCalculations.keySet();
    }

    public ProductUsageCalculation getProductCalculation(String product) {
        return this.productCalculations.get(product);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("[Account: %s, Owner: %s, Calculations: [", account, owner));
        for (ProductUsageCalculation calc : this.productCalculations.values()) {
            builder.append(calc);
        }
        builder.append("]");
        return builder.toString();
    }

}
