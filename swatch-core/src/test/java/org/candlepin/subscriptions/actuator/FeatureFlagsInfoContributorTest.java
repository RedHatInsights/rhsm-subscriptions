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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Map;
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

  @Mock private FeatureFlags featureFlags;

  private FeatureFlagsInfoContributor contributor;

  @BeforeEach
  void setUp() {
    contributor = new FeatureFlagsInfoContributor(featureFlags);
  }

  @Test
  void testAllFlagsEnabled() {
    when(featureFlags.isEnabled(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES)).thenReturn(true);
    when(featureFlags.isEnabled(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES)).thenReturn(true);

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertEquals(2, flagStatus.size());
    assertTrue(flagStatus.get(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES));
    assertTrue(flagStatus.get(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES));
  }

  @Test
  void testAllFlagsDisabled() {
    when(featureFlags.isEnabled(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES)).thenReturn(false);
    when(featureFlags.isEnabled(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES)).thenReturn(false);

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertEquals(2, flagStatus.size());
    assertFalse(flagStatus.get(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES));
    assertFalse(flagStatus.get(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES));
  }

  @Test
  void testMixedFlagStates() {
    when(featureFlags.isEnabled(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES)).thenReturn(true);
    when(featureFlags.isEnabled(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES)).thenReturn(false);

    Builder builder = new Builder();
    contributor.contribute(builder);
    Info info = builder.build();

    @SuppressWarnings("unchecked")
    Map<String, Boolean> flagStatus = (Map<String, Boolean>) info.getDetails().get("feature-flags");

    assertNotNull(flagStatus);
    assertEquals(2, flagStatus.size());
    assertTrue(flagStatus.get(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES));
    assertFalse(flagStatus.get(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES));
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
