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
package org.candlepin.subscriptions.db;


import org.candlepin.subscriptions.tally.AccountListSourceException;

import java.util.stream.Stream;

/**
 * Gathers all accounts in the host inventory DB as the list to tally.
 */
public class DatabaseAccountListSource implements AccountListSource {

    private final AccountConfigRepository repository;

    public DatabaseAccountListSource(AccountConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public Stream<String> syncableAccounts() throws AccountListSourceException {
        try {
            return repository.findSyncEnabledAccounts();
        }
        catch (Exception e) {
            throw new AccountListSourceException("Unable to get account sync list!", e);
        }
    }

    @Override
    public boolean containsReportingAccount(String accountNumber) throws AccountListSourceException {
        try {
            return repository.isReportingEnabled(accountNumber);
        }
        catch (Exception e) {
            throw new AccountListSourceException("Unable to determine if account was in whitelist.", e);
        }
    }

    @Override
    public Stream<String> purgeReportAccounts() throws AccountListSourceException {
        try {
            return repository.findSyncEnabledAccounts();
        }
        catch (Exception e) {
            throw new AccountListSourceException("Unable to get account purge list!", e);
        }
    }
}
