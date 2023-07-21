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
package org.candlepin.subscriptions.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A ConstraintValidator that ensures that a MAC address is valid */
public class MacAddressValidator implements ConstraintValidator<MacAddress, String> {
  public static final Pattern MAC_REGEX =
      Pattern.compile(
          /* Start with a non-capturing group since the ^ operator normally takes precedence over the
           * | operator.  Then 2 hex characters followed by an optional colon or hyphen in a
           * capturing group.  That optional character will be treated as the delimiter.  Then look
           * for 2 more hex characters.  After that, reference the delimiter with the \1
           * backreference coupled with 2 hex characters and look for that three character unit 4 times.
           *
           * After the pipe character is the alternate format which is the comparatively simple 12
           * hex characters broken into groups of 4 and delimited with periods.
           *
           * Simplest of all is the last option which is just 12 hex digits.
           */
          "^(?:[0-9a-fA-F]{2}([-:]?)[0-9a-fA-F]{2}(\\1[0-9a-fA-F]{2}){4}|"
              + "([0-9a-fA-F]{4}\\.[0-9a-fA-F]{4}\\.[0-9a-fA-F]{4})|"
              + "[0-9a-fA-F]{12})$" // NOSONAR
          );

  @Override
  public void initialize(MacAddress constraintAnnotation) {
    /* intentionally empty */
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // Note that the Jakarta Bean Validation specification recommends to consider null values as
    // being valid. If null is not a valid value for an element, it should be annotated with
    // @NotNull explicitly
    if (value == null) {
      return true;
    }
    Matcher matcher = MAC_REGEX.matcher(value);
    return matcher.matches();
  }
}
