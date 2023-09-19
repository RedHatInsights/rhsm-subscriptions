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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MetricIdTest {
  @Test
  void illegalArgumentExceptionThrownForBadValue() {
    assertThrows(IllegalArgumentException.class, () -> MetricId.fromString("badValue"));
  }

  @Test
  void validValueAcceptedAndAccessible() {
    assertEquals("Sockets", MetricId.fromString("SOCKETS").getValue());
  }

  @Test
  void validValueWithUnderscore() {
    assertEquals("Instance-hours", MetricId.fromString("INSTANCE_HOURS").getValue());
  }

  @Test
  void testToUpperCaseFormattedReplacesUnderscoreWithHyphen() {
    assertEquals("INSTANCE_HOURS", MetricId.fromString("Instance-hours").toUpperCaseFormatted());
  }

  @Test
  void testGetAll() {
    assertFalse(MetricId.getAll().isEmpty());
  }
}
