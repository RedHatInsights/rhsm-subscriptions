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
package com.redhat.swatch.hbi.events.ct;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventTemplateValidator {

  /**
   * Ensure that there are no template variables in the format of $VARIABLE in the given text. It
   * uses the regex "\$\w+" to find a literal '$' followed by one or more word characters (letters,
   * digits, or underscore). If a template variable is found, throw a RuntimeExecption detailing the
   * unfilled variables.
   *
   * @param template The template String to search within.
   * @exception RuntimeException when the template still contains unreplaced template variables.
   */
  public static void verifyNoTemplateVariablesExist(String template) throws RuntimeException {
    Set<String> dollarWords = new HashSet<>();

    if (template == null || template.isEmpty()) {
      return;
    }

    String regex = "\\$\\w+";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(template);

    while (matcher.find()) {
      dollarWords.add(matcher.group());
    }

    if (!dollarWords.isEmpty()) {
      throw new RuntimeException(
          "The following template variables were not replaced: " + dollarWords);
    }
  }
}
