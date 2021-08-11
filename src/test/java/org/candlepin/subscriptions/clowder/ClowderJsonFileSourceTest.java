/*
 * Copyright Red Hat, Inc.
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
package org.candlepin.subscriptions.clowder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;

class ClowderJsonFileSourceTest {
  @Test
  void deserializesClowderJson() throws Exception {
    ClowderJsonFileSource source = initClowderJsonSource("classpath:test-clowder-config.json");
    ClowderJson clowderJson = source.getValue();

    String actual = clowderJson.getString(JsonPointer.compile("/database/adminPassword"));
    assertEquals("SECRET", actual);
  }

  private ClowderJsonFileSource initClowderJsonSource(String resourceLocation) {
    var props = new ClowderProperties();
    props.setJsonResourceLocation(resourceLocation);
    ClowderJsonFileSource source =
        new ClowderJsonFileSource(props, new ApplicationClock(), new ObjectMapper());
    source.setResourceLoader(new FileSystemResourceLoader());
    source.init();
    return source;
  }
}
