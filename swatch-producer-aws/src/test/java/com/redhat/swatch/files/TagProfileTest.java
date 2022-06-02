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
package com.redhat.swatch.files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.exception.AwsDimensionNotConfiguredException;
import com.redhat.swatch.openapi.model.BillableUsage.UomEnum;
import org.junit.jupiter.api.Test;

class TagProfileTest {
  @Test
  void testCanLoadRhosakInstanceHoursDimension() {
    TagProfile tagProfile = new TagProfile();
    assertEquals(
        "cluster_hour", tagProfile.getAwsDimension("rhosak", UomEnum.INSTANCE_HOURS.name()));
  }

  @Test
  void testUnconfiguredDimensionThrowsException() {
    TagProfile tagProfile = new TagProfile();
    AwsDimensionNotConfiguredException exception =
        assertThrows(
            AwsDimensionNotConfiguredException.class,
            () -> {
              tagProfile.getAwsDimension("rhosak", "dummy-uom");
            });
    assertEquals("rhosak", exception.getProductId());
    assertEquals("dummy-uom", exception.getUom());
    assertThat(exception.getMessage(), containsString("rhosak"));
    assertThat(exception.getMessage(), containsString("dummy-uom"));
  }

  @Test
  void testCanLoadRhosakStorageGibMonthsDimension() {
    TagProfile tagProfile = new TagProfile();
    assertEquals(
        "storage_gb", tagProfile.getAwsDimension("rhosak", UomEnum.STORAGE_GIBIBYTE_MONTHS.name()));
  }
}
