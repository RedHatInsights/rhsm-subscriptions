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
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.ComponentTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@ComponentTest
@Tag("component")
@Tag("contracts")
public class SimpleContractsComponentTest extends BaseContractComponentTest {

  @Test
  public void testContractComponentShouldPass() {
    // Placeholder smoke test; Should be replaced with tests of the swatch-contracts service
    assertEquals(1, 1);
  }
}
