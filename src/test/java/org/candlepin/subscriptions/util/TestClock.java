/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Clock for testing that allows manipulating the time */
public class TestClock extends Clock {

  private Instant instant;
  private final ZoneId zone;

  public TestClock(Instant instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  public void setInstant(Instant instant) {
    this.instant = instant;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zoneId) {
    return new TestClock(instant, zoneId);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
