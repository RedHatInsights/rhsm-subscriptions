/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
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
