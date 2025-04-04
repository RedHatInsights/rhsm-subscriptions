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
import static org.mockito.Mockito.*;

import io.getunleash.Unleash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeatureFlagsTest {

  private Unleash unleash;
  private FeatureFlags featureFlags;

  @BeforeEach
  void setup() {
    unleash = mock(Unleash.class);
    featureFlags = new FeatureFlags(unleash);
  }

  @Test
  void emitEvents_shouldReturnTrue_whenFlagEnabled() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    assertTrue(featureFlags.emitEvents());
    verify(unleash).isEnabled(FeatureFlags.EMIT_EVENTS);
  }

  @Test
  void emitEvents_shouldReturnFalse_whenFlagDisabled() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(false);
    assertFalse(featureFlags.emitEvents());
    verify(unleash).isEnabled(FeatureFlags.EMIT_EVENTS);
  }

  @Test
  void isEnabled_shouldDelegateToUnleash() {
    String testFlag = "some.custom.flag";
    when(unleash.isEnabled(testFlag)).thenReturn(true);

    assertTrue(featureFlags.isEnabled(testFlag));
    verify(unleash).isEnabled(testFlag);
  }
}
