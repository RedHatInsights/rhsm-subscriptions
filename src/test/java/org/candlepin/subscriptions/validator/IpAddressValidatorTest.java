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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IpAddressValidatorTest {

  private IpAddressValidator validator = new IpAddressValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        // IPv4
        "192.168.2.1",
        // Standard notation
        "1762:0:0:0:0:B03:1:AF18",
        // Mixed notation
        "1762:0:0:0:0:B03:127.32.67.15",
        // Compressed notation
        "::1",
        "1762::B03:1:AF18",
        // Compressed with variable number 0s
        "1:0:0:0:0:6:0:0",
        "1::6:0:0",
        "1:0:0:0:0:6::",
        // Compressed mixed notation
        "1762::B03:127.32.67.15",
        // Case insensitive
        "2607:F380:A58:FFFF:0000:0000:0000:0001",
        "2607:f380:a58:ffff:0000:0000:0000:0001",
        "2607:f380:A58:FFfF:0000:0000:0000:0001"
      })
  void isValid(String ip) {
    assertTrue(validator.isValid(ip, null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a.b.c.d",
        "192.168.2.8.4",
        "",
        "129.2.2.z",
        "129.2.2.",
        ".129.2.2",
        "192.500.2.4",
        "redhat.com",
        "myhost",
        "999.3.3.3",
        // Can only use :: once
        "1200::AB00:1234::2552:7777:1313",
        // Can't use O in all zeros
        "1200:0000:AB00:1234:O000:2552:7777:1313",
        // A -> F are valid, G is not.
        "1762:0:0:0:0:G03:1:AF18",
        // Invalid character
        "2607:F380:A58:FFFF;0000:0000:0000:0001",
        "2607:redhat.com"
      })
  void isInvalid(String ip) {
    assertFalse(validator.isValid(ip, null));
  }
}
