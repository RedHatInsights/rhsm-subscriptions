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
package com.redhat.swatch.hbi.events.constants;

/** Constants used by HBI event processing and component tests. */
public final class HbiEventConstants {

  public static final String EVENT_SERVICE_TYPE = "HBI_HOST";
  public static final String EVENT_SOURCE = "HBI_EVENT";
  public static final String EVENTS_METRIC = "rhsm-subscriptions.metrics-hbi.events";

  private HbiEventConstants() {
    // Utility class - prevent instantiation
  }
}
