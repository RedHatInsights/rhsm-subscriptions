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

import com.redhat.swatch.hbi.infra.FakeUnleashProducer;
import io.getunleash.FakeUnleash;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FeatureFlagsIT {

  @Inject FeatureFlags featureFlags;

  FakeUnleash unleash;

  @BeforeEach
  void setup() {
    unleash = FakeUnleashProducer.getInstance();
    unleash.resetAll(); // clean state before each test
  }

  @Test
  void emitEvents_returnsTrue_whenEnabled() {
    unleash.enable(FeatureFlags.EMIT_EVENTS);
    assertTrue(featureFlags.emitEvents());
  }

  @Test
  void emitEvents_returnsFalse_whenDisabled() {
    unleash.disable(FeatureFlags.EMIT_EVENTS);
    assertFalse(featureFlags.emitEvents());
  }

  @Test
  void isEnabled_returnsCorrectFlagState() {
    unleash.enable("custom.flag");
    assertTrue(featureFlags.isEnabled("custom.flag"));

    unleash.disable("custom.flag");
    assertFalse(featureFlags.isEnabled("custom.flag"));
  }
}
