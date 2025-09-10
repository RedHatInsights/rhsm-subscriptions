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

import com.redhat.swatch.component.tests.api.Service;
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class LogsVerifier {

  private final Service service;

  public LogsVerifier(Service service) {
    this.service = service;
  }

  public void assertContains(String expectedLog) {
    assertContains(expectedLog, AwaitilitySettings.defaults());
  }

  public void assertContains(String expectedLog, AwaitilitySettings settings) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<String> actualLogs = service.getLogs();
          Assertions.assertTrue(
              actualLogs.stream().anyMatch(line -> line.contains(expectedLog)),
              "Log does not contain " + expectedLog + ". Full logs: " + actualLogs);
        },
        settings);
  }

  public void assertDoesNotContain(String unexpectedLog) {
    List<String> actualLogs = service.getLogs();
    Assertions.assertTrue(
        actualLogs.stream().noneMatch(line -> line.contains(unexpectedLog)),
        "Log does contain " + unexpectedLog + ". Full logs: " + actualLogs);
  }
}
