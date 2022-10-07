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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MacAddressValidatorTest {
  private MacAddressValidator validator = new MacAddressValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "01-23-45-67-89-ab",
        "01-23-45-67-89-AB",
        "01-23-45-67-89-aB",
        "01:23:45:67:89:ab",
        "01:23:45:67:89:AB",
        "01:23:45:67:89:aB",
        "0123.4567.89ab", // See https://en.wikipedia.org/wiki/MAC_address#Notational_conventions
        "0123456789ab"
      })
  void isValid(String mac) {
    assertTrue(validator.isValid(mac, null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "01:23:45-67:89:aB", // Mixed delimiters
        "xx:23:45:67:89:aB",
        "I am not a mac address"
      })
  void isInvalid(String mac) {
    assertFalse(validator.isValid(mac, null));
  }
}
