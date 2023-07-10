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
package org.candlepin.subscriptions.util;

import static org.candlepin.subscriptions.FixedClockConfiguration.SPRING_EPOCH_EDT;
import static org.candlepin.subscriptions.FixedClockConfiguration.SPRING_EPOCH_UTC;
import static org.candlepin.subscriptions.FixedClockConfiguration.SPRING_TIME_EDT;
import static org.candlepin.subscriptions.FixedClockConfiguration.SPRING_TIME_UTC;
import static org.candlepin.subscriptions.FixedClockConfiguration.WINTER_EPOCH_EST;
import static org.candlepin.subscriptions.FixedClockConfiguration.WINTER_EPOCH_UTC;
import static org.candlepin.subscriptions.FixedClockConfiguration.WINTER_TIME_EST;
import static org.candlepin.subscriptions.FixedClockConfiguration.WINTER_TIME_UTC;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = FixedClockConfiguration.class)
class ApplicationClockTest {

  @Autowired
  // 2019-5-24 12:35:00 UTC or 1558701300 seconds since the epoch
  private ApplicationClock clock;

  @Autowired
  @Qualifier("EDTSpringClock")
  // Clock set to SPRING_TIME with America/New York zone.  Will be in Daylight Saving Time.
  private ApplicationClock dstClock;

  @Autowired
  @Qualifier("ESTWinterClock")
  // Clock set to WINTER_TIME with America/New York zone.  Will be in Standard Time.
  private ApplicationClock standardClock;

  @Autowired
  @Qualifier("ZuluWinterClock")
  // Clock set to WINTER_TIME in UTC
  private ApplicationClock zuluWinterClock;

  @Test
  void testConvertToUnixTime() {
    assertEquals(OffsetDateTime.from(SPRING_TIME_UTC), clock.dateFromUnix(SPRING_EPOCH_UTC));
    assertEquals(OffsetDateTime.from(WINTER_TIME_UTC), clock.dateFromUnix(WINTER_EPOCH_UTC));

    assertEquals(
        OffsetDateTime.from(SPRING_TIME_UTC), zuluWinterClock.dateFromUnix(SPRING_EPOCH_UTC));
    assertEquals(
        OffsetDateTime.from(WINTER_TIME_UTC), zuluWinterClock.dateFromUnix(WINTER_EPOCH_UTC));

    // Neither of the America/New York clocks should equal the UTC epoch values due to their
    // offsets.
    assertNotEquals(OffsetDateTime.from(SPRING_TIME_EDT), dstClock.dateFromUnix(SPRING_EPOCH_UTC));
    assertNotEquals(
        OffsetDateTime.from(WINTER_TIME_EST), standardClock.dateFromUnix(WINTER_EPOCH_UTC));

    // Clocks should convert times accurately according to their zone's offset rules.  Since both
    // standardClock and dstClock use the same zone, America/New_York, they should both correctly
    // handle
    // the SPRING_TIME and WINTER_TIME.
    assertEquals(
        OffsetDateTime.from(WINTER_TIME_EST), standardClock.dateFromUnix(WINTER_EPOCH_EST));
    assertEquals(OffsetDateTime.from(WINTER_TIME_EST), dstClock.dateFromUnix(WINTER_EPOCH_EST));

    assertEquals(
        OffsetDateTime.from(SPRING_TIME_EDT), standardClock.dateFromUnix(SPRING_EPOCH_EDT));
    assertEquals(OffsetDateTime.from(SPRING_TIME_EDT), dstClock.dateFromUnix(SPRING_EPOCH_EDT));
  }

  @Test
  void testNow() {
    assertDate(2019, 5, 24, 12, 35, 0, 0, clock.now());
  }

  @Test
  void testStartOfToday() {
    assertStartOfDay(2019, 5, 24, clock.startOfToday());
  }

  @Test
  void testStartOfDay() {
    assertStartOfDay(2019, 5, 25, clock.startOfDay(clock.now().plusDays(1)));
  }

  @Test
  void testEndOfToday() {
    assertEndOfDay(2019, 5, 24, clock.endOfToday());
  }

  @Test
  void testEndOfDay() {
    assertEndOfDay(2019, 5, 25, clock.endOfDay(clock.now().plusDays(1)));
  }

  @Test
  void testStartOfCurrentWeek() {
    assertStartOfDay(2019, 5, 19, clock.startOfCurrentWeek());
  }

  @Test
  void testStartOfWeek() {
    assertStartOfDay(2019, 4, 28, clock.startOfWeek(clock.now().minusWeeks(3)));
  }

  @Test
  void testStartOfWeekWhenDateIsTheStartOfTheWeek() {
    OffsetDateTime startOfWeek = clock.now().withYear(2019).withMonth(5).withDayOfMonth(12);
    assertStartOfDay(2019, 5, 12, clock.startOfWeek(startOfWeek));
  }

  @Test
  void testEndOfCurrentWeek() {
    assertEndOfDay(2019, 5, 25, clock.endOfCurrentWeek());
  }

  @Test
  void testEndOfWeek() {
    assertEndOfDay(2019, 5, 4, clock.endOfWeek(clock.now().minusWeeks(3)));
  }

  @Test
  void testEndOfWeekWhenDateIsTheEndOfTheWeek() {
    OffsetDateTime endOfWeek = clock.now().withYear(2019).withMonth(5).withDayOfMonth(18);
    assertEndOfDay(2019, 5, 18, clock.endOfWeek(endOfWeek));
  }

  @Test
  void testStartOfCurrentMonth() {
    assertStartOfDay(2019, 5, 1, clock.startOfCurrentMonth());
  }

  @Test
  void testStartOfMonth() {
    assertStartOfDay(2019, 4, 1, clock.startOfMonth(clock.now().minusMonths(1)));
  }

  @Test
  void testEndOfCurrentMonth() {
    assertEndOfDay(2019, 5, 31, clock.endOfCurrentMonth());
  }

  @Test
  void testEndOfMonth() {
    assertEndOfDay(2019, 2, 28, clock.endOfMonth(clock.now().minusMonths(3)));
  }

  @Test
  void testStartOfCurrentYear() {
    assertStartOfDay(2019, 1, 1, clock.startOfCurrentYear());
  }

  @Test
  void testStartOfYear() {
    assertStartOfDay(2018, 1, 1, clock.startOfYear(clock.now().minusYears(1)));
  }

  @Test
  void testEndOfCurrentYear() {
    assertEndOfDay(2019, 12, 31, clock.endOfCurrentYear());
  }

  @Test
  void testEndOfYear() {
    assertEndOfDay(2018, 12, 31, clock.endOfYear(clock.now().minusYears(1)));
  }

  @Test
  void startOfQuarter() {
    assertStartOfDay(2019, 1, 1, clock.startOfQuarter(clock.startOfCurrentYear()));
    assertStartOfDay(2019, 4, 1, clock.startOfQuarter(clock.startOfCurrentYear().plusMonths(3)));
    assertStartOfDay(2019, 7, 1, clock.startOfQuarter(clock.startOfCurrentYear().plusMonths(6)));
    assertStartOfDay(2019, 10, 1, clock.startOfQuarter(clock.startOfCurrentYear().plusMonths(9)));
  }

  @Test
  void endOfQuarter() {
    assertEndOfDay(2019, 3, 31, clock.endOfQuarter(clock.startOfCurrentYear()));
    assertEndOfDay(2019, 6, 30, clock.endOfQuarter(clock.startOfCurrentYear().plusMonths(3)));
    assertEndOfDay(2019, 9, 30, clock.endOfQuarter(clock.startOfCurrentYear().plusMonths(6)));
    assertEndOfDay(2019, 12, 31, clock.endOfQuarter(clock.startOfCurrentYear().plusMonths(9)));
  }

  @Test
  void testStartCurrentQuarter() {
    assertStartOfDay(2019, 4, 1, clock.startOfCurrentQuarter());
  }

  @Test
  void testEndOfCurrentQuarter() {
    assertEndOfDay(2019, 6, 30, clock.endOfCurrentQuarter());
  }

  @Test
  void testStartOfCurrentHour() {
    // 2019-5-24 12:35:00 UTC
    assertDate(2019, 5, 24, 12, 0, 0, 0, clock.startOfCurrentHour());
  }

  @Test
  void testEndOfCurrentHour() {
    // 2019-5-24 12:35:00 UTC
    assertDate(2019, 5, 24, 12, 59, 59, 999_999_999, clock.endOfCurrentHour());
  }

  @Test
  void testIsHourlyRange() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(1);
    assertTrue(clock.isHourlyRange(start, end));
    assertFalse(clock.isHourlyRange(start.minusMinutes(1), end));
    assertFalse(clock.isHourlyRange(start, end.minusMinutes(1)));
  }

  private void assertDate(
      int year,
      int month,
      int day,
      int hour,
      int minute,
      int seconds,
      int nanos,
      OffsetDateTime date) {
    assertEquals(year, date.getYear());
    assertEquals(month, date.getMonthValue());
    assertEquals(day, date.getDayOfMonth());
    assertEquals(hour, date.getHour());
    assertEquals(minute, date.getMinute());
    assertEquals(seconds, date.getSecond());
    assertEquals(nanos, date.getNano());
  }

  private void assertStartOfDay(int year, int month, int day, OffsetDateTime date) {
    assertDate(year, month, day, 0, 0, 0, 0, date);
  }

  private void assertEndOfDay(int year, int month, int day, OffsetDateTime date) {
    OffsetDateTime max =
        OffsetDateTime.of(LocalDateTime.MAX, ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

    assertDate(
        year, month, day, max.getHour(), max.getMinute(), max.getSecond(), max.getNano(), date);
  }
}
