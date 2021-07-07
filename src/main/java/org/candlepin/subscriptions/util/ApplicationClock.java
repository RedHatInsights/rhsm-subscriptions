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

import lombok.NoArgsConstructor;
import org.candlepin.subscriptions.db.model.Granularity;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * The single date and time source to be used by the application.
 *
 * <p>All start* methods return the time at midnight - 2019-04-19 00:00:00.0 All end* methods return
 * the max time of the day - 2019-04-19 23:59:59.999999999Z
 */
@NoArgsConstructor
public class ApplicationClock extends CoreApplicationClock {
    private static final String BAD_GRANULARITY_MESSAGE = "Unsupported granularity: %s";

    public ApplicationClock(Clock fixed) {
        super(fixed);
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

    public boolean isHourlyRange(DateRange dateRange) {
        return isHourlyRange(dateRange.getStartDate(), dateRange.getEndDate());
    }
}
