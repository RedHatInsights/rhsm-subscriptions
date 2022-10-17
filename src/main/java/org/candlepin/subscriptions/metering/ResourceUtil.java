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
package org.candlepin.subscriptions.metering;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Utility component for metering resources. */
@Component
public class ResourceUtil {

  protected ApplicationClock clock;

  public ResourceUtil(ApplicationClock clock) {
    this.clock = clock;
  }

  public OffsetDateTime getDate(String dateToParse) {
    Optional<OffsetDateTime> optionalDate;
    if (StringUtils.hasText(dateToParse)) {
      try {
        // 2018-03-20T09:00:00Z
        optionalDate = Optional.of(OffsetDateTime.parse(dateToParse));
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(
            String.format("Unable to parse date arg '%s'. Invalid format.", dateToParse));
      }
    } else {
      optionalDate = Optional.empty();
    }

    return getDate(optionalDate);
  }

  public OffsetDateTime getDate(Optional<OffsetDateTime> optionalDate) {
    if (optionalDate.isPresent()) {
      OffsetDateTime date = optionalDate.get();
      if (!date.isEqual(clock.startOfHour(date))) {
        throw new IllegalArgumentException(
            String.format("Date must start at top of the hour: %s", date));
      }
      return date;
    }
    // Default to the top of the current hour.
    return clock.startOfCurrentHour();
  }

  public OffsetDateTime getStartDate(OffsetDateTime endDate, Integer rangeInMinutes) {
    if (rangeInMinutes == null) {
      throw new IllegalArgumentException("Required argument: rangeInMinutes");
    }

    if (rangeInMinutes < 0) {
      throw new IllegalArgumentException("Invalid value specified (Must be >= 0): rangeInMinutes");
    }

    OffsetDateTime result = endDate.minusMinutes(rangeInMinutes);
    if (!result.isEqual(clock.startOfHour(result))) {
      throw new IllegalArgumentException(
          String.format(
              "endDate %s - range %s produces time not at top of the hour: %s",
              endDate, rangeInMinutes, result));
    }
    return result;
  }
}
