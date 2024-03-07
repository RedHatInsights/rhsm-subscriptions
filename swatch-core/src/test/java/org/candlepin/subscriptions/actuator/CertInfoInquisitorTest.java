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
package org.candlepin.subscriptions.actuator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileInputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

class CertInfoInquisitorTest {
  @Test
  void testLoadStoreInfo() throws Exception {
    var file = ResourceUtils.getFile("classpath:ca-bundle.p12");

    var results =
        CertInfoInquisitor.loadStoreInfo(new FileInputStream(file), "changeit".toCharArray());
    var expected = Set.of("test ca 1", "test ca 2", "test ca 3", "test ca 4");
    assertEquals(expected, results.keySet());
  }
}
