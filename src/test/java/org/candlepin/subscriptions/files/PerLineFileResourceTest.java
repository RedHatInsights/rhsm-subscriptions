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
package org.candlepin.subscriptions.files;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class PerLineFileResourceTest {

    @Test
    public void ensureOneProductPerLine() throws Exception {
        assertListFile("item_per_line.txt", Arrays.asList("I1", "I2", "I3"));
    }

    @Test
    public void ensureEmptyLinesAreIgnored() throws Exception {
        assertListFile("item_per_line_with_empty_lines.txt", Arrays.asList("I10", "I20", "I30"));
    }

    @Test
    public void ensureExceptionWhenResourceNotFound() {
        RuntimeException rte = assertThrows(RuntimeException.class, () -> {
            assertListFile("bogus", Arrays.asList());
        });
        assertEquals("Resource not found: class path resource [bogus]", rte.getMessage());
    }

    private void assertListFile(String orgListFileLocation, List<String> expectedLines) throws Exception {
        PerLineFileSource source = createSource(orgListFileLocation);
        List<String> read = source.list();
        assertEquals(3, read.size());
        assertThat(read, Matchers.contains(expectedLines.toArray()));
    }

    private PerLineFileSource createSource(String filename) {
        PerLineFileSource source = new PerLineFileSource(String.format("classpath:%s", filename),
            Clock.systemUTC(), Duration.ofMinutes(5));
        source.setResourceLoader(new FileSystemResourceLoader());
        source.init();
        return source;
    }
}
