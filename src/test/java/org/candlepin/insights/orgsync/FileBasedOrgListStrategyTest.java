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

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;

import java.util.List;


public class FileBasedOrgListStrategyTest {

    @Test
    public void ensureOneOrgPerLine() throws Exception {
        assertOrgListFile("file_based_org_list.csv");
    }

    @Test
    public void ensureEmptyLinesAreIgnored() throws Exception {
        assertOrgListFile("file_based_org_list_with_empty_lines.csv");
    }

    @Test
    public void ensureEmptyOrgIdsAreIgnored() throws Exception {
        assertOrgListFile("file_based_org_list_with_empty_orgid.csv");
    }

    @Test
    public void ensureAccountNumberIsMapped() throws Exception {
        FileBasedOrgListStrategy strategy = createStrategy("file_based_org_list.csv");
        assertNull(strategy.getAccountNumberForOrg("org3"));
        assertEquals("account1", strategy.getAccountNumberForOrg("org1"));
        assertEquals("account2", strategy.getAccountNumberForOrg("org2"));
    }

    private void assertOrgListFile(String orgListFileLocation) throws Exception {
        FileBasedOrgListStrategy strategy = createStrategy(orgListFileLocation);
        List<String> orgs = strategy.getOrgsToSync();
        assertEquals(3, orgs.size());

        assertThat(orgs, Matchers.contains("org1", "org2", "org3"));
    }

    private FileBasedOrgListStrategy createStrategy(String orgListFileLocation) {
        FileBasedOrgListStrategyProperties props = new FileBasedOrgListStrategyProperties();
        props.setOrgResourceLocation(String.format("classpath:%s", orgListFileLocation));

        FileBasedOrgListStrategy strategy = new FileBasedOrgListStrategy(props);
        strategy.setResourceLoader(new FileSystemResourceLoader());
        strategy.init();
        return strategy;
    }
}
