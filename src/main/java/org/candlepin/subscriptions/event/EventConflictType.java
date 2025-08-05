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

/**
 * Enum defining the different types of event conflicts that can occur during event processing. Each
 * type represents a specific scenario when processing incoming events against existing data.
 */
public enum EventConflictType {

  /**
   * Original Event - First time an event is encountered for a given EventKey. No conflicts exist,
   * event is processed normally without amendments.
   */
  ORIGINAL("No conflicts, first occurrence of this event"),

  /**
   * Identical Event - Incoming event matches an existing event exactly. Same UsageConflictKey, same
   * usage descriptors, same measurement values. Event is ignored (idempotent behavior).
   */
  IDENTICAL("Event ignored - identical to existing event"),

  /**
   * Corrective Event - Incoming event has different measurement values but same descriptors. Same
   * UsageConflictKey, same usage descriptors, different measurement values. Triggers measurement
   * amendment (deduction + new event).
   */
  CORRECTIVE("Measurement correction - different values, same descriptors"),

  /**
   * Contextual Event - Incoming event has different usage descriptors but same measurements. Same
   * UsageConflictKey, different usage descriptors, same measurement values. Triggers descriptor
   * amendment (deduction + new event).
   */
  CONTEXTUAL("Context change - same values, different descriptors"),

  /**
   * Comprehensive Event - Incoming event differs in both measurements and descriptors. Same
   * UsageConflictKey, different usage descriptors, different measurement values. Triggers full
   * amendment (deduction + new event).
   */
  COMPREHENSIVE("Full amendment - different values and descriptors");

  private final String description;

  EventConflictType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  /**
   * Determines if this conflict type requires creating a deduction event.
   *
   * @return true if deduction event should be created
   */
  public boolean requiresDeduction() {
    return this == CORRECTIVE || this == CONTEXTUAL || this == COMPREHENSIVE;
  }

  /**
   * Determines if this conflict type results in the incoming event being saved.
   *
   * @return true if incoming event should be saved
   */
  public boolean saveIncomingEvent() {
    return this != IDENTICAL;
  }
}
