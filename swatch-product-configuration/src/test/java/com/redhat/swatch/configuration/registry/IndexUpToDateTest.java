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
package com.redhat.swatch.configuration.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class IndexUpToDateTest {

  @Test
  void testIndexUpToDate() throws IOException {
    try (var index = getClass().getClassLoader().getResourceAsStream("swatch_config_index.txt")) {
      assertNotNull(index);
      var lines =
          Arrays.stream(new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
              .filter(s -> !s.isEmpty())
              .toList();
      try (var files = Files.walk(Path.of("src", "main", "resources", "subscription_configs"))) {
        var allFiles =
            files
                .filter(f -> f.toFile().isFile())
                .map(p -> Path.of("src", "main", "resources").relativize(p).toString())
                .sorted()
                .toList();
        assertEquals(lines, allFiles);
      }
    }
  }
}
