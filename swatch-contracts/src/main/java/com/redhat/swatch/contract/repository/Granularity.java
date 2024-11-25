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
package com.redhat.swatch.contract.repository;

import com.redhat.swatch.common.model.StringValueEnum;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Granularity of a given snapshot.
 *
 * <p>Granularity defines the scope of max concurrent usage. For example, max concurrent usage
 * across a week represents the maximum tally totals across all days in that week. For example,
 * given a week where daily tallies were 2, 3, 4, 5, 6, 2, 4, the weekly tally snapshot would be 6.
 */
@Getter
@AllArgsConstructor
public enum Granularity implements StringValueEnum {
  // NB: These need to be in descending order of length so that comparisons between granularities
  // will work
  YEARLY("Yearly"),
  QUARTERLY("Quarterly"),
  MONTHLY("Monthly"),
  WEEKLY("Weekly"),
  DAILY("Daily"),
  HOURLY("Hourly");

  private static final Map<String, Granularity> VALUE_ENUM_MAP =
      StringValueEnum.initializeImmutableMap(Granularity.class);

  private final String value;

  public static Granularity fromString(String value) {
    return StringValueEnum.getValueOf(Granularity.class, VALUE_ENUM_MAP, value, null);
  }
}
