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
package org.candlepin.subscriptions.db.model.config;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;


/**
 * Represents the configuration properties for an account.
 */
@Entity
@Table(name = "account_config")
public class AccountConfig extends BaseConfig {

    @Id
    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "reporting_enabled")
    private Boolean reportingEnabled;

    public AccountConfig() {
    }

    public AccountConfig(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Boolean getReportingEnabled() {
        return reportingEnabled;
    }

    public void setReportingEnabled(Boolean reportingEnabled) {
        this.reportingEnabled = reportingEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AccountConfig)) {
            return false;
        }

        if (!super.equals(o)) {
            return false;
        }

        AccountConfig that = (AccountConfig) o;
        return Objects.equals(accountNumber, that.accountNumber) &&
                   Objects.equals(reportingEnabled, that.reportingEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), accountNumber, reportingEnabled);
    }

}
