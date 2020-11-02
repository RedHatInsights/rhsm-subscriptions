/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.files;

import org.candlepin.subscriptions.tally.AccountListSource;
import org.candlepin.subscriptions.tally.AccountListSourceException;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;


/**
 * An Account list source that uses a file as its source.
 */
public class FileAccountListSource implements AccountListSource, ResourceLoaderAware {

    private FileAccountSyncListSource syncListSource;
    private ReportingAccountWhitelist reportingAccountWhitelist;

    public FileAccountListSource(FileAccountSyncListSource syncListSource,
        ReportingAccountWhitelist reportingAccountWhitelist) {
        this.syncListSource = syncListSource;
        this.reportingAccountWhitelist = reportingAccountWhitelist;
    }

    @Override
    public Stream<String> syncableAccounts() throws AccountListSourceException {
        try {
            return syncListSource.list().stream();
        }
        catch (IOException ioe) {
            throw new AccountListSourceException("Unable to get account sync list!", ioe);
        }
    }

    @Override
    public boolean containsReportingAccount(String accountNumber) throws AccountListSourceException {
        try {
            return reportingAccountWhitelist.hasAccount(accountNumber);
        }
        catch (IOException ioe) {
            throw new AccountListSourceException("Unable to determine if account was in whitelist.", ioe);
        }
    }

    @Override
    public Stream<String> purgeReportAccounts() throws AccountListSourceException {
        try {
            return syncListSource.list().stream();
        }
        catch (IOException ioe) {
            throw new AccountListSourceException("Unable to get account purge list!", ioe);
        }
    }

    @PostConstruct
    public void init() {
        // @PostConstruct methods will not get called by these objects since
        // only the managed beans have this invoked.
        this.syncListSource.init();
        this.reportingAccountWhitelist.init();
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.syncListSource.setResourceLoader(resourceLoader);
        this.reportingAccountWhitelist.setResourceLoader(resourceLoader);
    }
}
