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

import java.util.concurrent.ThreadLocalRandom;

public final class RandomUtils {

  private static final int MIN_RANGE = 10000;
  private static final int MAX_RANGE = 99999;

  private RandomUtils() {}

  /**
   * Generates a random five-digit number within a predefined range and returns it as a string.
   *
   * @return a random number as a string, generated within the specified range
   */
  public static String generateRandom() {
    int randomNumber = ThreadLocalRandom.current().nextInt(MIN_RANGE, MAX_RANGE + 1);
    return String.valueOf(randomNumber);
  }
}
