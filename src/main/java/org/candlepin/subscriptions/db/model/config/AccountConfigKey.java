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
package org.candlepin.subscriptions.db.model.config;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;


/**
 * Represents a the ReportingConfig key. Each entity should never be made up of the same
 * account_number/org_id combination.
 */
public class AccountConfigKey implements Serializable {

    public AccountConfigKey() {
    }

    public AccountConfigKey(String accountNumber, String orgId) {
        this.accountNumber = accountNumber;
        this.orgId = orgId;
    }

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "org_id")
    private String orgId;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AccountConfigKey)) {
            return false;
        }

        AccountConfigKey that = (AccountConfigKey) o;
        return accountNumber.equals(that.accountNumber) &&
                   orgId.equals(that.orgId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber, orgId);
    }
}
