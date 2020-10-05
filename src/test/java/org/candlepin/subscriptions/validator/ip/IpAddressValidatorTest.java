/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.validator.ip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpAddressValidatorTest {

    private IpAddressValidator validator = new IpAddressValidator();

    @Test
    void testIpV4Validation() {
        assertTrue(validator.isValid("192.168.2.1", null));
    }

    @Test
    void testInvalidIpV4Ip() {
        assertFalse(validator.isValid("a.b.c.d", null));
        assertFalse(validator.isValid("192.168.2.8.4", null));
        assertFalse(validator.isValid("", null));
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("129.2.2.z", null));
        assertFalse(validator.isValid("129.2.2.", null));
        assertFalse(validator.isValid(".129.2.2", null));
        assertFalse(validator.isValid("192.500.2.4", null));
        assertFalse(validator.isValid("redhat.com", null));
        assertFalse(validator.isValid("myhost", null));
        assertFalse(validator.isValid("999.3.3.3", null));
    }

    @Test
    void testIpV6Validation() {
        // Standard notation
        assertTrue(validator.isValid("1762:0:0:0:0:B03:1:AF18", null));

        // Mixed notation
        assertTrue(validator.isValid("1762:0:0:0:0:B03:127.32.67.15", null));

        // Compressed notation
        assertTrue(validator.isValid("::1", null));
        assertTrue(validator.isValid("1762::B03:1:AF18", null));

        // Compressed with variable number 0s
        assertTrue(validator.isValid("1:0:0:0:0:6:0:0", null));
        assertTrue(validator.isValid("1::6:0:0", null));
        assertTrue(validator.isValid("1:0:0:0:0:6::", null));

        // Compressed mixed notation
        assertTrue(validator.isValid("1762::B03:127.32.67.15", null));
    }

    @Test
    void testInvalidIpV6() {
        // Can only use :: once
        assertFalse(validator.isValid("1200::AB00:1234::2552:7777:1313", null));

        // Can't use O in all zeros
        assertFalse(validator.isValid("1200:0000:AB00:1234:O000:2552:7777:1313", null));

        // A -> F are valid, G is not.
        assertFalse(validator.isValid("1762:0:0:0:0:G03:1:AF18", null));

        // Invalid character
        assertFalse(validator.isValid("2607:F380:A58:FFFF;0000:0000:0000:0001", null));
        assertFalse(validator.isValid("2607:redhat.com", null));
    }

    @Test
    void caseInsensitiveIpV6() {
        assertTrue(validator.isValid("2607:F380:A58:FFFF:0000:0000:0000:0001", null));
        assertTrue(validator.isValid("2607:f380:a58:ffff:0000:0000:0000:0001", null));
        assertTrue(validator.isValid("2607:f380:A58:FFfF:0000:0000:0000:0001", null));
    }

}
