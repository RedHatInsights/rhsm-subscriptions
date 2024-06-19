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
import org.candlepin.subscriptions.utilization.api.v1.model.GranularityType;
import org.junit.jupiter.api.Test;

class GranularityTest {

  public static final String DAILY_MIXEDCASE = "DaIlY";
  public static final String BAD_VALUE = "bicentenially";

  @Test
  void testFromStringBadValue() {
    assertThrows(IllegalArgumentException.class, () -> Granularity.fromString(BAD_VALUE));
  }

  @Test
  void testFromStringEmpty() {
    assertThrows(IllegalArgumentException.class, () -> Granularity.fromString(""));
  }

  @Test
  void testFromStringNull() {
    assertThrows(IllegalArgumentException.class, () -> Granularity.fromString(null));
  }

  @Test
  void testFromStringUpperCase() {
    assertEquals(Granularity.DAILY, Granularity.fromString(DAILY_MIXEDCASE.toUpperCase()));
  }

  @Test
  void testFromStringLowerCase() {
    assertEquals(Granularity.DAILY, Granularity.fromString(DAILY_MIXEDCASE.toLowerCase()));
  }

  @Test
  void testFromStringMixedCase() {
    assertEquals(Granularity.DAILY, Granularity.fromString(DAILY_MIXEDCASE));
  }

  @Test
  void testAsOpenApiEnumValuesMatch() {
    Set<GranularityType> expected = Sets.newHashSet(GranularityType.class.getEnumConstants());
    Set<GranularityType> actual =
        Sets.newHashSet(Granularity.class.getEnumConstants()).stream()
            .map(Granularity::asOpenApiEnum)
            .collect(Collectors.toSet());

    assertEquals(expected, actual);
  }
}
