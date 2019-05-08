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
package org.candlepin.insights.orgsync;

import org.candlepin.insights.exception.ErrorCode;
import org.candlepin.insights.exception.RhsmConduitException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.Response;

/**
 * An implementation of OrgListStrategy that reads org ids from a file
 */
public class FileBasedOrgListStrategy implements OrgListStrategy, ResourceLoaderAware {
    private static final String CANDLEPIN_ORG_ID = "Candlepin Org ID";
    private static final String ACCOUNT_NUMBER = "Account Number";

    private ResourceLoader resourceLoader;
    private String orgResourceLocation;
    private Resource orgResource;

    public FileBasedOrgListStrategy(FileBasedOrgListStrategyProperties props) {
        this.orgResourceLocation = props.getOrgResourceLocation();
    }

    private Stream<CSVRecord> getCSVRecordStream(InputStream s) throws IOException {
        Iterator<CSVRecord> recordIterator = CSVFormat.DEFAULT.withFirstRecordAsHeader()
            .parse(new InputStreamReader(s, Charset.defaultCharset())).iterator();
        Iterable<CSVRecord> iterable = () -> recordIterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    @Override
    public List<String> getOrgsToSync() throws IOException {
        // Re-read the file every time.  It shouldn't be a massive file and doing so allows us to update the
        // org list without restarting the app.
        try (InputStream s = orgResource.getInputStream()) {
            return getCSVRecordStream(s)
                .map(record -> record.get(CANDLEPIN_ORG_ID))
                .filter(orgId -> !orgId.isEmpty())
                .collect(Collectors.toList());
        }
    }

    public String getAccountNumberForOrg(String orgId) {
        try {
            // re-read the csv to get an updated lookup. Inefficient, but this is a stop-gap.
            try (InputStream s = orgResource.getInputStream()) {
                return getCSVRecordStream(s)
                    .filter(record -> record.get(CANDLEPIN_ORG_ID).trim().equals(orgId))
                    .map(record -> record.get(ACCOUNT_NUMBER))
                    .filter(accountNumber -> !accountNumber.isEmpty())
                    .findFirst().orElse(null);
            }
        }
        catch (IOException e) {
            throw new RhsmConduitException(
                ErrorCode.UNHANDLED_EXCEPTION_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                "Error extracting account number mapping from CSV.",
                e
            );
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            orgResource = resourceLoader.getResource(orgResourceLocation);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The orgResourceLocation property is unset. Please set in " +
                "the application configuration files.");
        }

        if (!orgResource.exists()) {
            throw new IllegalStateException(
                "Cannot find the resource " + orgResource.getDescription()
            );
        }
    }
}
