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
package com.redhat.swatch.kafka;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.kafka.ReadOnlyModeEnabledQuarkusTest.ReadOnlyModeEnabledProfile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Startup coverage for read-only mode enabled with the Unleash feature flag off.. */
@QuarkusTest
@TestProfile(ReadOnlyModeEnabledProfile.class)
class ReadOnlyModeEnabledQuarkusTest {

  @SuppressWarnings("unused")
  @InjectMock
  ReadOnlyModeEnabled readOnlyModeEnabled;

  @Inject ChannelRegistry registry;

  @Test
  void shouldResumeConsumersAtStartupWhenFeatureFlagIsOff() {
    PausableChannel channel = registry.getPausable("test");
    assertNotNull(channel);
    assertFalse(channel.isPaused());
  }

  public static class ReadOnlyModeEnabledProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("READ_ONLY_MODE_ENABLED", "true");
    }
  }

  @ApplicationScoped
  public static class ReadOnlyModeEnabled {
    public boolean isEnabled() {
      return false;
    }
  }
}
