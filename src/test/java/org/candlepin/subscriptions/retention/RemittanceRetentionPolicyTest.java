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
package org.candlepin.subscriptions.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;

class RemittanceRetentionPolicyTest {
  public RemittanceRetentionPolicy createTestPolicy(RemittanceRetentionPolicyProperties config) {
    ApplicationClock clock = new FixedClockConfiguration().fixedClock();
    return new RemittanceRetentionPolicy(clock, config);
  }

  @Test
  void testHourlyCutoff() {
    RemittanceRetentionPolicyProperties config = new RemittanceRetentionPolicyProperties();
    config.setHourly(8);
    OffsetDateTime cutoff = createTestPolicy(config).getCutoffDate(Granularity.HOURLY);
    OffsetDateTime eightHoursAgo = OffsetDateTime.of(2019, 5, 24, 4, 00, 0, 0, ZoneOffset.UTC);
    assertEquals(eightHoursAgo, cutoff);
  }

  @Test
  void testDailyCutoffDate() {
    RemittanceRetentionPolicyProperties config = new RemittanceRetentionPolicyProperties();
    config.setDaily(15);
    OffsetDateTime cutoff = createTestPolicy(config).getCutoffDate(Granularity.DAILY);
    OffsetDateTime fifteenDaysAgo = OffsetDateTime.of(2019, 5, 9, 0, 0, 0, 0, ZoneOffset.UTC);
    assertEquals(fifteenDaysAgo, cutoff);
  }
}
