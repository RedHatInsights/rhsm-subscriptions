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
package com.redhat.swatch.hbi.config;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApplicationPropertiesIT {

  @Inject ApplicationProperties props;

  @Test
  void shouldInjectFromConfig() {
    assertNotNull(props.getCullingOffset(), "cullingOffset should be configured");
    assertNotNull(props.getHostLastSyncThreshold(), "hostLastSyncThreshold should be configured");

    // You can be more strict if test config is known
    Duration expectedOffset = Duration.ofHours(1);
    Duration expectedSyncThreshold = Duration.ofDays(1);

    assertEquals(expectedOffset, props.getCullingOffset(), "cullingOffset should match config");
    assertEquals(
        expectedSyncThreshold, props.getHostLastSyncThreshold(), "sync threshold mismatch");
    assertFalse(
        props.isUseCpuSystemFactsForAllProducts(), "feature flag should be false by default");
  }
}
