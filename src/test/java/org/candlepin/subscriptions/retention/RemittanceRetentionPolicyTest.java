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

import java.time.Duration;
import java.time.OffsetDateTime;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;

class RemittanceRetentionPolicyTest {
  private ApplicationClock clock = new TestClockConfiguration().adjustableClock();

  public RemittanceRetentionPolicy createTestPolicy(RemittanceRetentionPolicyProperties config) {
    return new RemittanceRetentionPolicy(clock, config);
  }

  @Test
  void testHourlyCutoff() {
    RemittanceRetentionPolicyProperties config = new RemittanceRetentionPolicyProperties();
    config.setDuration(Duration.ofHours(8));
    OffsetDateTime cutoff = createTestPolicy(config).getCutoffDate();
    OffsetDateTime eightHoursAgo = clock.now().minusHours(8);
    assertEquals(eightHoursAgo, cutoff);
  }

  @Test
  void testDailyCutoffDate() {
    RemittanceRetentionPolicyProperties config = new RemittanceRetentionPolicyProperties();
    config.setDuration(Duration.ofDays(15));
    OffsetDateTime cutoff = createTestPolicy(config).getCutoffDate();
    OffsetDateTime fifteenDaysAgo = clock.now().minusDays(15);
    assertEquals(fifteenDaysAgo, cutoff);
  }
}
