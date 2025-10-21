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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class StringUtils {
  public static final String[] EMPTY_STRING_ARRAY = {};
  public static final String EMPTY = "";

  private StringUtils() {}

  public static boolean isEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }

  public static boolean isNotEmpty(String str) {
    return !isEmpty(str);
  }

  public static boolean startsWith(String str, String suffix) {
    return str != null && str.startsWith(suffix);
  }

  public static boolean hasLength(String str) {
    return (str != null && !str.isEmpty());
  }

  public static String deleteAny(String inString, String charsToDelete) {
    if (!hasLength(inString) || !hasLength(charsToDelete)) {
      return inString;
    }

    int lastCharIndex = 0;
    char[] result = new char[inString.length()];
    for (int i = 0; i < inString.length(); i++) {
      char c = inString.charAt(i);
      if (charsToDelete.indexOf(c) == -1) {
        result[lastCharIndex++] = c;
      }
    }
    if (lastCharIndex == inString.length()) {
      return inString;
    }
    return new String(result, 0, lastCharIndex);
  }

  public static String[] commaDelimitedListToStringArray(String str) {
    return delimitedListToStringArray(str, ",", null);
  }

  public static String[] delimitedListToStringArray(
      String str, String delimiter, String charsToDelete) {

    if (str == null) {
      return EMPTY_STRING_ARRAY;
    }
    if (delimiter == null) {
      return new String[] {str};
    }

    List<String> result = new ArrayList<>();
    if (delimiter.isEmpty()) {
      for (int i = 0; i < str.length(); i++) {
        result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
      }
    } else {
      int pos = 0;
      int delPos;
      while ((delPos = str.indexOf(delimiter, pos)) != -1) {
        result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
        pos = delPos + delimiter.length();
      }
      if (!str.isEmpty() && pos <= str.length()) {
        // Add rest of String, but not in case of empty input.
        result.add(deleteAny(str.substring(pos), charsToDelete));
      }
    }
    return toStringArray(result);
  }

  public static String[] toStringArray(Collection<String> collection) {
    return (!(collection == null || collection.isEmpty())
        ? collection.toArray(EMPTY_STRING_ARRAY)
        : EMPTY_STRING_ARRAY);
  }
}
