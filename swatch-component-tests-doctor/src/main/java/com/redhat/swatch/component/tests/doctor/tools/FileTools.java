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
package com.redhat.swatch.component.tests.doctor.tools;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class FileTools {

  /**
   * Reads the entire content of a file.
   *
   * @param filePath Absolute path to the file to read
   * @return The file content, or an error message
   */
  @Tool(
      "Read the entire content of a file. "
          + "Use this to read source files. "
          + "MUST use absolute paths. "
          + "Example: readFile('/Users/jcarvaja/sources/RedHatInsights/rhsm-subscriptions/swatch-utilization/src/main/java/com/redhat/swatch/utilization/service/UtilizationSummaryConsumer.java')")
  public String readFile(String filePath) {
    Log.debugf("Reading file: %s", filePath);
    try {
      Path path = Path.of(filePath);
      if (!Files.exists(path)) {
        return "Error: File not found at " + filePath;
      }
      return Files.readString(path);
    } catch (IOException e) {
      Log.error("Error reading file: " + filePath, e);
      return "Error reading file: " + e.getMessage();
    }
  }

  /**
   * Searches for text in a file and returns matching lines with context.
   *
   * @param filePath Absolute path to the file to search
   * @param searchText Text to search for
   * @param contextLines Number of lines before and after each match to include
   * @return Matching lines with context, or an error message
   */
  @Tool(
      "Search for text in a file and return matching lines with context. "
          + "Use this to find specific code patterns like '@Incoming', '// @', method names, etc. "
          + "MUST use absolute paths. "
          + "Example: searchInFile('/path/to/file.java', '@Incoming', 3)")
  public String searchInFile(String filePath, String searchText, int contextLines) {
    Log.debugf("Searching for '%s' in %s", searchText, filePath);
    try {
      Path path = Path.of(filePath);
      if (!Files.exists(path)) {
        return "Error: File not found at " + filePath;
      }

      List<String> lines = Files.readAllLines(path);
      StringBuilder result = new StringBuilder();
      boolean foundAny = false;

      for (int i = 0; i < lines.size(); i++) {
        if (lines.get(i).contains(searchText)) {
          foundAny = true;
          int start = Math.max(0, i - contextLines);
          int end = Math.min(lines.size() - 1, i + contextLines);

          result.append("Found at line ").append(i + 1).append(":\n");
          for (int j = start; j <= end; j++) {
            result.append(String.format("%4d: %s\n", j + 1, lines.get(j)));
          }
          result.append("\n");
        }
      }

      if (!foundAny) {
        return "No matches found for '" + searchText + "' in " + filePath;
      }

      return result.toString();
    } catch (IOException e) {
      Log.error("Error searching file: " + filePath, e);
      return "Error searching file: " + e.getMessage();
    }
  }
}
