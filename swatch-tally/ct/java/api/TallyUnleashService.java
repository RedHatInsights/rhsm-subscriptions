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
package api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.UnleashService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import java.time.Duration;
import java.util.Map;

public class TallyUnleashService extends UnleashService {
  private static final String ENABLE_PRIMARY_ROW_SEARCHES =
      "swatch.swatch-tally.enable-primary-row-searches";
  private TallySwatchService tallyService;

  public TallyUnleashService(TallySwatchService service) {
    super();
    this.tallyService = service;
  }

  public void enablePrimaryRowSearches() {
    enableFlag(ENABLE_PRIMARY_ROW_SEARCHES);
    waitForFlagToBe(ENABLE_PRIMARY_ROW_SEARCHES, true);
  }

  public void disablePrimaryRowSearches() {
    disableFlag(ENABLE_PRIMARY_ROW_SEARCHES);
    waitForFlagToBe(ENABLE_PRIMARY_ROW_SEARCHES, false);
  }

  private void waitForFlagToBe(String flag, boolean expectedState) {
    // Wait for the feature flag to be reflected in the /info endpoint
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofMillis(100), Duration.ofSeconds(20))
            .withService(tallyService)
            .timeoutMessage(
                "Feature flag '%s' did not reach expected state: %s", flag, expectedState);

    AwaitilityUtils.untilAsserted(
        () -> {
          @SuppressWarnings("unchecked")
          Map<String, Object> info = tallyService.getInfo();

          @SuppressWarnings("unchecked")
          Map<String, Boolean> featureFlags = (Map<String, Boolean>) info.get("feature-flags");

          assertNotNull(featureFlags, "feature-flags section should be present in /info response");

          Boolean actualValue = featureFlags.get(flag);

          assertNotNull(actualValue, "Feature flag '" + flag + "' should be present");

          assertEquals(expectedState, actualValue, "Feature flag should match configured value");
        },
        settings);
  }
}
