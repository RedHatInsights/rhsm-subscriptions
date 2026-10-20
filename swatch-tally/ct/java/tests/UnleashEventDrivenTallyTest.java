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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UnleashEventDrivenTallyTest extends BaseTallyComponentTest {

  private static final String EVENT_DRIVEN_TALLY_FLAG =
      "swatch.swatch-tally.enable-event-driven-tally";

  @AfterEach
  void resetEventDrivenTallyFlag() {
    unleash.disableEventDrivenTally();
  }

  @Test
  @TestPlanName("unleash-event-driven-TC001")
  void shouldEnableEventDrivenTallyFlag() {
    // When
    unleash.enableEventDrivenTally();

    // Then
    assertTrue(
        unleash.isFlagEnabled(EVENT_DRIVEN_TALLY_FLAG),
        "Event-driven tally flag should be enabled");
  }

  @Test
  @TestPlanName("unleash-event-driven-TC002")
  void shouldDisableEventDrivenTallyFlag() {
    // Given - ensure flag is enabled first
    unleash.enableEventDrivenTally();
    assertTrue(
        unleash.isFlagEnabled(EVENT_DRIVEN_TALLY_FLAG),
        "Event-driven tally flag should be enabled initially");

    // When
    unleash.disableEventDrivenTally();

    // Then
    assertFalse(
        unleash.isFlagEnabled(EVENT_DRIVEN_TALLY_FLAG),
        "Event-driven tally flag should be disabled");
  }

  @Test
  @TestPlanName("unleash-event-driven-TC003")
  void shouldToggleEventDrivenTallyFlag() {
    // When - enable flag
    unleash.enableEventDrivenTally();

    // Then - verify enabled
    assertTrue(
        unleash.isFlagEnabled(EVENT_DRIVEN_TALLY_FLAG),
        "Event-driven tally flag should be enabled after enable call");

    // When - disable flag
    unleash.disableEventDrivenTally();

    // Then - verify disabled
    assertFalse(
        unleash.isFlagEnabled(EVENT_DRIVEN_TALLY_FLAG),
        "Event-driven tally flag should be disabled after disable call");

    // When - enable again
    unleash.enableEventDrivenTally();

    // Then - verify enabled again
    assertTrue(
        unleash.isFlagEnabled(EVENT_DRIVEN_TALLY_FLAG),
        "Event-driven tally flag should be enabled again after second enable call");
  }
}
