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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.candlepin.subscriptions.configuration.FeatureFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.Info.Builder;

@ExtendWith(MockitoExtension.class)
class FeatureFlagsInfoContributorTest {

  private static final Set<String> UNIQUE_FLAGS =
      new LinkedHashSet<>(Arrays.asList(FeatureFlags.FLAG_LIST));

  @Mock private FeatureFlags featureFlags;

  private FeatureFlagsInfoContributor contributor;

  @BeforeEach
  void setUp() {
    contributor = new FeatureFlagsInfoContributor(featureFlags);
  }

  @Test
  void testAllFlagsEnabled() {
    for (String flag : FeatureFlags.FLAG_LIST) {
      when(featureFlags.isEnabled(flag)).thenReturn(true);
    }

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertEquals(UNIQUE_FLAGS.size(), flagStatus.size());
    for (String flag : UNIQUE_FLAGS) {
      assertEquals(Boolean.TRUE, flagStatus.get(flag));
    }
  }

  @Test
  void testAllFlagsDisabled() {
    for (String flag : FeatureFlags.FLAG_LIST) {
      when(featureFlags.isEnabled(flag)).thenReturn(false);
    }

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertEquals(UNIQUE_FLAGS.size(), flagStatus.size());
    for (String flag : UNIQUE_FLAGS) {
      assertEquals(Boolean.FALSE, flagStatus.get(flag));
    }
  }

  @Test
  void testContributeReportsAllFlagsFromFlagList() {
    for (String flag : FeatureFlags.FLAG_LIST) {
      when(featureFlags.isEnabled(flag)).thenReturn(false);
    }

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertEquals(UNIQUE_FLAGS, flagStatus.keySet());
  }

  @Test
  void testOnlyOneFlagEnabled() {
    String enabledFlag = UNIQUE_FLAGS.iterator().next();
    for (String flag : FeatureFlags.FLAG_LIST) {
      when(featureFlags.isEnabled(flag)).thenReturn(flag.equals(enabledFlag));
    }

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertEquals(UNIQUE_FLAGS.size(), flagStatus.size());
    for (String flag : UNIQUE_FLAGS) {
      assertEquals(flag.equals(enabledFlag), flagStatus.get(flag));
    }
  }

  @Test
  void testNullFeatureFlags() {
    contributor = new FeatureFlagsInfoContributor(null);

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertTrue(flagStatus.isEmpty());
  }
}
