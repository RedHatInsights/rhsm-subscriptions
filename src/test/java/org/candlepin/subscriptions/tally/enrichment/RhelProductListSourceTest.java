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
package org.candlepin.subscriptions.tally.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.ApplicationProperties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Set;


public class RhelProductListSourceTest {

    @Test
    public void ensureOneProductPerLine() throws Exception {
        assertRhelProductListFile("rhel_prod_list.txt");
    }

    @Test
    public void ensureEmptyLinesAreIgnored() throws Exception {
        assertRhelProductListFile("rhel_prod_list_with_empty_lines.txt");
    }

    private void assertRhelProductListFile(String orgListFileLocation) throws Exception {
        RhelProductListSource source = createProductSource(orgListFileLocation);
        Set<String> prodList = source.getProductIds();
        assertEquals(3, prodList.size());

        List<String> expectedProducts = Arrays.asList("P1", "P2", "P3");
        assertTrue(prodList.containsAll(expectedProducts));
    }

    private RhelProductListSource createProductSource(String filename) {
        ApplicationProperties props = new ApplicationProperties();
        props.setRhelProductListResourceLocation(String.format("classpath:%s", filename));

        RhelProductListSource source = new RhelProductListSource(props);
        source.setResourceLoader(new FileSystemResourceLoader());
        source.init();
        return source;
    }
}
