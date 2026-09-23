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

import com.redhat.swatch.component.tests.logging.Log;
import java.util.BitSet;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomUtils {
  private static final int MIN_RANGE = 10000;
  private static final int MAX_RANGE = 99999;
  private static final int RANGE_SIZE = MAX_RANGE - MIN_RANGE + 1;

  private static final BitSet USED_NUMBERS = new BitSet(RANGE_SIZE);
  private static int COUNT = 0;

  private RandomUtils() {}

  /**
   * Returns a random five-digit number unique within the current cycle.
   *
   * <p>After all numbers are used, logs a warning and starts a new cycle. Uniqueness applies to
   * threads sharing this class loader, not separate JVMs or different cycles.
   *
   * @return a unique number as a string, within the specified range
   */
  public static synchronized String generateRandom() {
    if (COUNT >= RANGE_SIZE) {
      Log.warn("All random numbers used; resetting pool.");
      USED_NUMBERS.clear();
      COUNT = 0;
    }

    int offset;
    do {
      offset = ThreadLocalRandom.current().nextInt(0, RANGE_SIZE);
    } while (USED_NUMBERS.get(offset));

    USED_NUMBERS.set(offset);
    COUNT++;

    return String.valueOf(MIN_RANGE + offset);
  }
}
