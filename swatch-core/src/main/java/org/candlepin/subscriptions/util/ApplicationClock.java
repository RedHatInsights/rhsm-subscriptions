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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import org.candlepin.subscriptions.db.model.Granularity;
import org.springframework.stereotype.Component;

/**
 * The single date and time source to be used by the application.
 *
 * <p>All start* methods return the time at midnight - 2019-04-19 00:00:00.0 All end* methods return
 * the max time of the day - 2019-04-19 23:59:59.999999999Z
 */
@Component
public class ApplicationClock {
  private static final String BAD_GRANULARITY_MESSAGE = "Unsupported granularity: %s";

  private Clock clock;

  // Ensure the week starts with Sunday.
  private TemporalField week = WeekFields.of(DayOfWeek.SUNDAY, 1).dayOfWeek();

  public ApplicationClock() {
    this.clock = Clock.systemUTC();
  }

  public ApplicationClock(Clock clock) {
    this.clock = clock;
  }

  public Clock getClock() {
    return this.clock;
  }

  public OffsetDateTime now() {
    return OffsetDateTime.now(getClock());
  }

  public OffsetDateTime startOfToday() {
    return startOfDay(now());
  }

  public OffsetDateTime startOfDay(OffsetDateTime anyDay) {
    return OffsetDateTime.from(anyDay.with(LocalTime.MIDNIGHT));
  }

  public OffsetDateTime endOfToday() {
    return endOfDay(now());
  }

  public OffsetDateTime endOfDay(OffsetDateTime anyDay) {
    return OffsetDateTime.from(anyDay.with(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS));
  }

  public OffsetDateTime endOfCurrentWeek() {
    return endOfWeek(now());
  }

  public OffsetDateTime endOfWeek(OffsetDateTime anyDayInWeek) {
    return endOfDay(anyDayInWeek.with(week, 7));
  }

  public OffsetDateTime startOfCurrentWeek() {
    return startOfWeek(now());
  }

  public OffsetDateTime startOfWeek(OffsetDateTime anyDayOfWeek) {
    return startOfDay(anyDayOfWeek.with(week, 1));
  }

  public OffsetDateTime endOfCurrentMonth() {
    return endOfMonth(now());
  }

  public OffsetDateTime endOfMonth(OffsetDateTime anyDayOfMonth) {
    return OffsetDateTime.from(anyDayOfMonth.with(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS))
        .with(TemporalAdjusters.lastDayOfMonth());
  }

  public OffsetDateTime startOfCurrentMonth() {
    return startOfMonth(now());
  }

  public OffsetDateTime startOfMonth(OffsetDateTime anyDayOfMonth) {
    return OffsetDateTime.from(anyDayOfMonth.with(LocalTime.MIDNIGHT))
        .with(TemporalAdjusters.firstDayOfMonth());
  }

  public OffsetDateTime startOfCurrentYear() {
    return startOfYear(now());
  }

  public OffsetDateTime startOfYear(OffsetDateTime anyDay) {
    return startOfDay(anyDay).with(TemporalAdjusters.firstDayOfYear());
  }

  public OffsetDateTime endOfCurrentYear() {
    return endOfYear(now());
  }

  public OffsetDateTime endOfYear(OffsetDateTime anyDay) {
    return endOfDay(anyDay).with(TemporalAdjusters.lastDayOfYear());
  }

  public OffsetDateTime startOfCurrentQuarter() {
    return startOfQuarter(now());
  }

  public OffsetDateTime endOfCurrentQuarter() {
    return endOfQuarter(now());
  }

  public OffsetDateTime startOfQuarter(OffsetDateTime anyDay) {
    OffsetDateTime startOfDay = startOfDay(anyDay);
    return startOfDay
        .with(startOfDay.getMonth().firstMonthOfQuarter())
        .with(TemporalAdjusters.firstDayOfMonth());
  }

  public OffsetDateTime endOfQuarter(OffsetDateTime anyDay) {
    OffsetDateTime endOfDay = endOfDay(anyDay);
    return endOfDay
        .with(endOfDay.getMonth().firstMonthOfQuarter())
        .plusMonths(2)
        .with(TemporalAdjusters.lastDayOfMonth());
  }

  public OffsetDateTime startOfCurrentHour() {
    return startOfHour(now());
  }

  public OffsetDateTime endOfCurrentHour() {
    return endOfHour(now());
  }

  /**
   * Takes an OffsetDateTime and returns the last nanosecond of the hour in that OffsetDateTime
   *
   * @param toAdjust an OffsetDateTime to round up to the last nanosecond in the hour
   * @return an OffsetDateTime representing the final nanosecond in toAdjust's hour.
   */
  public OffsetDateTime endOfHour(OffsetDateTime toAdjust) {
    return toAdjust.with(
        temporal ->
            temporal
                .with(
                    ChronoField.MINUTE_OF_HOUR,
                    temporal.range(ChronoField.MINUTE_OF_HOUR).getMaximum())
                .with(
                    ChronoField.SECOND_OF_MINUTE,
                    temporal.range(ChronoField.SECOND_OF_MINUTE).getMaximum())
                .with(
                    ChronoField.NANO_OF_SECOND,
                    temporal.range(ChronoField.NANO_OF_SECOND).getMaximum()));
  }

  public OffsetDateTime startOfHour(OffsetDateTime toAdjust) {
    return toAdjust.truncatedTo(ChronoUnit.HOURS);
  }

  public OffsetDateTime dateFromUnix(BigDecimal time) {
    return dateFromUnix(time.longValue());
  }

  public OffsetDateTime dateFromUnix(Long time) {
    ZoneId zone = this.clock.getZone();
    ZonedDateTime zonedDateTime = Instant.ofEpochSecond(time).atZone(zone);
    return zonedDateTime.toOffsetDateTime();
  }

  public OffsetDateTime dateFromMilliseconds(Long time) {
    ZoneId zoneId = this.clock.getZone();
    ZonedDateTime zonedDateTime = Instant.ofEpochMilli(time).atZone(zoneId);
    return zonedDateTime.toOffsetDateTime();
  }

  public OffsetDateTime calculateStartOfRange(OffsetDateTime toAdjust, Granularity granularity) {
    switch (granularity) {
      case HOURLY:
        return startOfHour(toAdjust);
      case DAILY:
        return startOfDay(toAdjust);
      case WEEKLY:
        return startOfWeek(toAdjust);
      case MONTHLY:
        return startOfMonth(toAdjust);
      case QUARTERLY:
        return startOfQuarter(toAdjust);
      case YEARLY:
        return startOfYear(toAdjust);
      default:
        throw new IllegalArgumentException(String.format(BAD_GRANULARITY_MESSAGE, granularity));
    }
  }

  public OffsetDateTime calculateEndOfRange(OffsetDateTime toAdjust, Granularity granularity) {
    switch (granularity) {
      case HOURLY:
        return endOfHour(toAdjust);
      case DAILY:
        return endOfDay(toAdjust);
      case WEEKLY:
        return endOfWeek(toAdjust);
      case MONTHLY:
        return endOfMonth(toAdjust);
      case QUARTERLY:
        return endOfQuarter(toAdjust);
      case YEARLY:
        return endOfYear(toAdjust);
      default:
        throw new IllegalArgumentException(String.format(BAD_GRANULARITY_MESSAGE, granularity));
    }
  }

  public boolean isHourlyRange(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
    return startDateTime.isEqual(startOfHour(startDateTime))
        && endDateTime.isEqual(startOfHour(endDateTime));
  }

  public boolean isHourlyRange(DateRange dateRange) {
    return isHourlyRange(dateRange.getStartDate(), dateRange.getEndDate());
  }
}
