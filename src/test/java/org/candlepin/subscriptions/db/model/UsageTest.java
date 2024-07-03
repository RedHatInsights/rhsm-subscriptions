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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.utilization.api.v1.model.UsageType;
import org.junit.jupiter.api.Test;

class UsageTest {

  public static final String DEVELOPMENT_TEST_MIXED_CASE = "DevelOPment/TeSt";
  public static final String BAD_VALUE = "overused";

  @Test
  void testEachValueSurvivesStringConversion() {
    Usage.EnumConverter converter = new Usage.EnumConverter();
    for (Usage usage : Usage.values()) {
      assertEquals(converter.convertToDatabaseColumn(usage), usage.getValue());
      assertEquals(converter.convertToEntityAttribute(usage.getValue()), usage);
    }
  }

  @Test
  void testFromStringInvalidValueDefaultUnspecified() {
    assertEquals(Usage.EMPTY, Usage.fromString(BAD_VALUE));
  }

  @Test
  void testFromStringNullDefaultUnspecified() {
    assertEquals(Usage.EMPTY, Usage.fromString(null));
  }

  @Test
  void testFromStringEmptyString() {
    assertEquals(Usage.EMPTY, Usage.fromString(""));
  }

  @Test
  void testFromStringUpperCase() {
    assertEquals(
        Usage.DEVELOPMENT_TEST, Usage.fromString(DEVELOPMENT_TEST_MIXED_CASE.toUpperCase()));
  }

  @Test
  void testFromStringLowerCase() {
    assertEquals(
        Usage.DEVELOPMENT_TEST, Usage.fromString(DEVELOPMENT_TEST_MIXED_CASE.toLowerCase()));
  }

  @Test
  void testFromStringMixedCase() {
    assertEquals(Usage.DEVELOPMENT_TEST, Usage.fromString(DEVELOPMENT_TEST_MIXED_CASE));
  }

  @Test
  void testAsOpenApiEnumValuesMatch() {
    Set<UsageType> expected = Sets.newHashSet(UsageType.class.getEnumConstants());
    Set<UsageType> actual =
        Sets.newHashSet(Usage.class.getEnumConstants()).stream()
            .map(Usage::asOpenApiEnum)
            .collect(Collectors.toSet());

    assertEquals(expected, actual);
  }
}
