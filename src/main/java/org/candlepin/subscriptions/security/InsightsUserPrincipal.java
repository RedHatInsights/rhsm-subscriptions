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
package org.candlepin.subscriptions.security;

import java.util.Objects;

/**
 * Represents the subset of data we depend on in the x-rh-identity header.
 */
public class InsightsUserPrincipal {

    private final String ownerId;
    private final String accountNumber;

    public InsightsUserPrincipal() {
        this(null, null);
    }

    public InsightsUserPrincipal(String ownerId, String accountNumber) {
        this.ownerId = ownerId;
        this.accountNumber = accountNumber;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String toString() {
        return String.format("[Account: %s, Owner: %s]", accountNumber, ownerId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InsightsUserPrincipal)) {
            return false;
        }
        InsightsUserPrincipal principal = (InsightsUserPrincipal) o;
        return Objects.equals(getOwnerId(), principal.getOwnerId()) &&
            Objects.equals(getAccountNumber(), principal.getAccountNumber());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOwnerId(), getAccountNumber());
    }

}
