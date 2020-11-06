/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.subscriptions.FixedClockConfiguration;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;


public class ApplicationClockTest {

    private ApplicationClock clock;

    public ApplicationClockTest() {
        // 2019-5-24 12:35:00 UTC
        clock = new FixedClockConfiguration().fixedClock();
    }

    @Test
    public void testNow() {
        assertDate(2019, 5, 24, 12, 35, 0, 0, clock.now());
    }

    @Test
    public void testStartOfToday() {
        assertStartOfDay(2019, 5, 24, clock.startOfToday());
    }

    @Test
    public void testStartOfDay() {
        assertStartOfDay(2019, 5, 25, clock.startOfDay(clock.now().plusDays(1)));
    }

    @Test
    public void testEndOfToday() {
        assertEndOfDay(2019, 5, 24, clock.endOfToday());
    }

    @Test
    public void testEndOfDay() {
        assertEndOfDay(2019, 5, 25, clock.endOfDay(clock.now().plusDays(1)));
    }

    @Test
    public void testStartOfCurrentWeek() {
        assertStartOfDay(2019, 5, 19, clock.startOfCurrentWeek());
    }

    @Test
    public void testStartOfWeek() {
        assertStartOfDay(2019, 4, 28, clock.startOfWeek(clock.now().minusWeeks(3)));
    }

    @Test
    public void testStartOfWeekWhenDateIsTheStartOfTheWeek() {
        OffsetDateTime startOfWeek = clock.now().withYear(2019).withMonth(5).withDayOfMonth(12);
        assertStartOfDay(2019, 5, 12, clock.startOfWeek(startOfWeek));
    }

    @Test
    public void testEndOfCurrentWeek() {
        assertEndOfDay(2019, 5, 25, clock.endOfCurrentWeek());
    }

    @Test
    public void testEndOfWeek() {
        assertEndOfDay(2019, 5, 4, clock.endOfWeek(clock.now().minusWeeks(3)));
    }

    @Test
    public void testEndOfWeekWhenDateIsTheEndOfTheWeek() {
        OffsetDateTime endOfWeek = clock.now().withYear(2019).withMonth(5).withDayOfMonth(18);
        assertEndOfDay(2019, 5, 18, clock.endOfWeek(endOfWeek));
    }

    @Test
    public void testStartOfCurrentMonth() {
        assertStartOfDay(2019, 5, 1, clock.startOfCurrentMonth());
    }

    @Test
    public void testStartOfMonth() {
        assertStartOfDay(2019, 4, 1, clock.startOfMonth(clock.now().minusMonths(1)));
    }

    @Test
    public void testEndOfCurrentMonth() {
        assertEndOfDay(2019, 5, 31, clock.endOfCurrentMonth());
    }

    @Test
    public void testEndOfMonth() {
        assertEndOfDay(2019, 2, 28, clock.endOfMonth(clock.now().minusMonths(3)));
    }

    @Test
    public void testStartOfCurrentYear() {
        assertStartOfDay(2019, 1, 1, clock.startOfCurrentYear());
    }

    @Test
    public void testStartOfYear() {
        assertStartOfDay(2018, 1, 1, clock.startOfYear(clock.now().minusYears(1)));
    }

    @Test
    public void testEndOfCurrentYear() {
        assertEndOfDay(2019, 12, 31, clock.endOfCurrentYear());
    }

    @Test
    public void testEndOfYear() {
        assertEndOfDay(2018, 12, 31, clock.endOfYear(clock.now().minusYears(1)));
    }

    @Test
    public void startOfQuarter() {
        assertStartOfDay(2019, 1, 1, clock.startOfQuarter(clock.startOfCurrentYear()));
        assertStartOfDay(2019, 4, 1, clock.startOfQuarter(clock.startOfCurrentYear().plusMonths(3)));
        assertStartOfDay(2019, 7, 1, clock.startOfQuarter(clock.startOfCurrentYear().plusMonths(6)));
        assertStartOfDay(2019, 10, 1, clock.startOfQuarter(clock.startOfCurrentYear().plusMonths(9)));
    }

    @Test
    public void endOfQuarter() {
        assertEndOfDay(2019, 3, 31, clock.endOfQuarter(clock.startOfCurrentYear()));
        assertEndOfDay(2019, 6, 30, clock.endOfQuarter(clock.startOfCurrentYear().plusMonths(3)));
        assertEndOfDay(2019, 9, 30, clock.endOfQuarter(clock.startOfCurrentYear().plusMonths(6)));
        assertEndOfDay(2019, 12, 31, clock.endOfQuarter(clock.startOfCurrentYear().plusMonths(9)));
    }

    @Test
    public void testStartCurrentQuarter() {
        assertStartOfDay(2019, 4, 1, clock.startOfCurrentQuarter());
    }

    @Test
    public void testEndOfCurrentQuarter() {
        assertEndOfDay(2019, 6, 30, clock.endOfCurrentQuarter());
    }

    private void assertDate(int year, int month, int day, int hour, int minute, int seconds, int millis,
        OffsetDateTime date) {
        assertEquals(year, date.getYear());
        assertEquals(month, date.getMonthValue());
        assertEquals(day, date.getDayOfMonth());
        assertEquals(hour, date.getHour());
        assertEquals(minute, date.getMinute());
        assertEquals(seconds, date.getSecond());
        assertEquals(millis, date.getNano());
    }

    private void assertStartOfDay(int year, int month, int day, OffsetDateTime date) {
        assertDate(year, month, day, 0, 0, 0, 0, date);
    }

    private void assertEndOfDay(int year, int month, int day, OffsetDateTime date) {
        LocalDateTime max = LocalDateTime.MAX;
        assertDate(year, month, day, max.getHour(), max.getMinute(), max.getSecond(), max.getNano(), date);
    }
}
