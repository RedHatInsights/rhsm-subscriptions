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
package org.candlepin.subscriptions.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventConflictTypeTest {

  @Test
  void testRequiresDeduction() {
    // These types should require deduction events
    assertTrue(EventConflictType.CORRECTIVE.requiresDeduction());
    assertTrue(EventConflictType.CONTEXTUAL.requiresDeduction());
    assertTrue(EventConflictType.COMPREHENSIVE.requiresDeduction());

    // These types should not require deduction events
    assertFalse(EventConflictType.ORIGINAL.requiresDeduction());
    assertFalse(EventConflictType.IDENTICAL.requiresDeduction());
  }

  @Test
  void testSaveIncomingEvent() {
    // All types should save incoming event except IDENTICAL
    assertTrue(EventConflictType.ORIGINAL.saveIncomingEvent());
    assertTrue(EventConflictType.CORRECTIVE.saveIncomingEvent());
    assertTrue(EventConflictType.CONTEXTUAL.saveIncomingEvent());
    assertTrue(EventConflictType.COMPREHENSIVE.saveIncomingEvent());

    // IDENTICAL events should not be saved
    assertFalse(EventConflictType.IDENTICAL.saveIncomingEvent());
  }

  @Test
  void testDescriptions() {
    // Verify all types have descriptions
    for (EventConflictType type : EventConflictType.values()) {
      assertNotNull(type.getDescription());
      assertFalse(type.getDescription().isEmpty());
    }
  }

  @Test
  void testEnumValues() {
    // Verify all expected enum values exist
    EventConflictType[] expectedTypes = {
      EventConflictType.ORIGINAL,
      EventConflictType.IDENTICAL,
      EventConflictType.CORRECTIVE,
      EventConflictType.CONTEXTUAL,
      EventConflictType.COMPREHENSIVE
    };

    EventConflictType[] actualTypes = EventConflictType.values();
    assertEquals(expectedTypes.length, actualTypes.length);

    for (EventConflictType expected : expectedTypes) {
      assertTrue(java.util.Arrays.asList(actualTypes).contains(expected));
    }
  }
}
