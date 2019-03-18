/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.insights.ApplicationProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.TimeZone;


public class ObjectMapperContextResolverTest {

    /**
     * Ensure that dates are in ISO-8601 format.
     */
    @Test
    public void ensureDatesAreSerializedToISO8601Format() throws Exception {
        ApplicationProperties props = new ApplicationProperties();
        ObjectMapperContextResolver resolver = new ObjectMapperContextResolver(props);
        ObjectMapper mapper = resolver.getContext(Void.class);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DATE, 12);
        cal.set(Calendar.YEAR, 2019);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 30);
        cal.set(Calendar.SECOND, 15);
        cal.set(Calendar.MILLISECOND, 222);

        String formatted = mapper.writeValueAsString(cal.getTime());
        // NOTE: The mapper will wrap the string in quotes.
        assertEquals("\"2019-01-12T08:30:15.222+00:00\"", formatted);

    }
}
