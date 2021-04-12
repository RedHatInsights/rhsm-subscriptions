/*
 * Copyright (c) 2020 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;


class DateRangeTest {

    @Test
    void testCreateFromCollection() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expectedStart = now.minusMonths(4);
        OffsetDateTime expectedEnd = now.plusMonths(4);

        List<OffsetDateTime> dates = List.of(now, expectedStart, expectedEnd, expectedEnd, expectedStart);
        DateRange range = DateRange.from(dates);
        assertEquals(expectedStart, range.getStartDate());
        assertEquals(expectedEnd, range.getEndDate());
    }

    @Test
    void testValidateStartDateIsBeforeOrOnEndDate() {
        OffsetDateTime now = OffsetDateTime.now();
        new DateRange(now, now.plusHours(1)); // Verify starts before end date case.
        new DateRange(now, now); // Verify on end date case.

        // Start can't be after the end date.
        OffsetDateTime start = now;
        OffsetDateTime end = now.minusHours(1);
        assertThrows(IllegalArgumentException.class, () -> new DateRange(start, end));
    }
}
