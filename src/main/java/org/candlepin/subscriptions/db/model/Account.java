/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.db.model;

import java.util.Map;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapKeyJoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * Aggregate for an account.
 *
 * Using an aggregate simplifies modification, as we can leverage JPA to track persistence of hosts, etc.
 */
@Entity
@Table(name = "account_config") // NOTE: we're abusing account_config here, table needs refactor?
public class Account {
    @Id
    @Column(name = "account_number")
    private String accountNumber;

    @OneToMany(
        cascade = CascadeType.ALL,
        mappedBy = "accountNumber",
        fetch = FetchType.EAGER,
        orphanRemoval = true
    )
    @MapKeyJoinColumn(name="id")
    private Map<UUID, Host> hosts;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Map<UUID, Host> getHosts() {
        return hosts;
    }

    public void setHosts(Map<UUID, Host> hosts) {
        this.hosts = hosts;
    }
}
