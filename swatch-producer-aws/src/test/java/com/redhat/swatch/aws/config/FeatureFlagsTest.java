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
package com.redhat.swatch.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.redhat.swatch.info.model.InfoFeatureFlag;
import com.redhat.swatch.info.model.InfoFeatureFlags;
import io.getunleash.Unleash;
import io.getunleash.variant.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureFlagsTest {

  @Mock(strictness = Strictness.LENIENT)
  Unleash unleash;

  @InjectMocks FeatureFlags featureFlags;

  @Test
  void useLicenseMapsToToggleName() {
    when(unleash.isEnabled(FeatureFlags.USE_LICENSE)).thenReturn(true);
    assertTrue(featureFlags.useLicense());

    when(unleash.isEnabled(FeatureFlags.USE_LICENSE)).thenReturn(false);
    assertFalse(featureFlags.useLicense());
  }

  @Test
  void infoContributorExposesUseLicenseFlag() {
    when(unleash.isEnabled(FeatureFlags.USE_LICENSE)).thenReturn(true);
    when(unleash.getVariant(FeatureFlags.USE_LICENSE)).thenReturn(Variant.DISABLED_VARIANT);

    InfoFeatureFlags info = featureFlags.getFeatureFlags();
    InfoFeatureFlag flag =
        info.getFlags().stream()
            .filter(entry -> FeatureFlags.USE_LICENSE.equals(entry.getName()))
            .findFirst()
            .orElseThrow();

    assertEquals(FeatureFlags.USE_LICENSE, flag.getName());
    assertTrue(flag.getEnabled());
  }
}
