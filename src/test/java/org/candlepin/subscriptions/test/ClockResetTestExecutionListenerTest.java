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
package org.candlepin.subscriptions.test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@TestMethodOrder(OrderAnnotation.class)
class ClockResetTestExecutionListenerTest {
  public static final LocalDateTime POLLUTE_TIME = LocalDateTime.of(2001, 10, 1, 17, 25, 0, 0);
  public static final ZonedDateTime POLLUTE_TIME_JAPAN =
      POLLUTE_TIME.atZone(ZoneId.of("Asia/Tokyo"));
  public static final OffsetDateTime DEFAULT_TEST_TIME =
      TestClockConfiguration.SPRING_TIME_UTC.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();

  @Autowired ApplicationClock applicationClock;

  @Test
  @Order(1)
  void testSetClockAndAttemptToPolluteContext() {
    var testClock = (TestClock) applicationClock.getClock();
    assertEquals(DEFAULT_TEST_TIME, applicationClock.now());
    testClock.setInstant(POLLUTE_TIME_JAPAN.toInstant());
    testClock.setZone(ZoneId.of("Asia/Tokyo"));
    assertEquals("Asia/Tokyo", testClock.getZone().getId());
    assertEquals("2001-10-01T17:25+09:00", applicationClock.now().toString());
  }

  @Test
  @Order(2)
  void testClockShouldBeReset() {
    assertEquals(DEFAULT_TEST_TIME, applicationClock.now());
  }
}
