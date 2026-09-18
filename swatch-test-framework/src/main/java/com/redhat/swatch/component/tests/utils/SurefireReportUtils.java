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
package com.redhat.swatch.component.tests.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class SurefireReportUtils {

  public static final String TESTSUITES = "testsuites";
  public static final String TESTSUITE = "testsuite";
  public static final String TESTCASE = "testcase";
  public static final String CLASSNAME = "classname";
  public static final String PROPERTIES = "properties";
  public static final String PROPERTY = "property";
  public static final String VALUE = "value";
  public static final String NAME = "name";
  public static final String KEY = "key";

  private SurefireReportUtils() {}

  public static boolean isSurefireReportFile(String filename) {
    return filename.startsWith("TEST-") && filename.endsWith(".xml");
  }

  public static List<Path> listSurefireReportFiles(Path reportsDir) throws IOException {
    if (!Files.isDirectory(reportsDir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(reportsDir)) {
      return files
          .filter(path -> isSurefireReportFile(path.getFileName().toString()))
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }
}
