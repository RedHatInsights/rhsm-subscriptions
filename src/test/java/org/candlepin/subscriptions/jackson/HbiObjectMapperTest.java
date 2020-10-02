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
package org.candlepin.subscriptions.jackson;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Calendar;
import java.util.TimeZone;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
class HbiObjectMapperTest {

    @Autowired
    @Qualifier("hbiObjectMapper")
    private ObjectMapper mapper;

    /**
     * Ensure that dates are in ISO-8601 format.
     */
    @Test
    void ensureDatesAreSerializedToISO8601Format() throws Exception {

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

    @Test
    void serialization() throws Exception {
        String expectedVal1 = "foo";
        String expectedVal2 = "bar";

        TestPojo pojo = new TestPojo(expectedVal1, expectedVal2);
        String data = mapper.writeValueAsString(pojo);
        assertContainsProperty(data, "value1", expectedVal1);
        assertContainsProperty(data, "value2", expectedVal2);
    }

    @Test
    void ensureSerializedObjectsDoNotIncludePropsWithNullValues() throws Exception {
        String v2 = "bar";
        TestPojo pojo = new TestPojo(null, v2);
        String data = mapper.writeValueAsString(pojo);
        assertDoesNotContainProperty(data, "value1");
        assertContainsProperty(data, "value2", v2);
    }

    @Test
    void ensureSerializedObjectsDoNotIncludePropsWithEmptyValues() throws Exception {
        String v2 = "bar";
        TestPojo pojo = new TestPojo("", v2);
        String data = mapper.writeValueAsString(pojo);
        assertDoesNotContainProperty(data, "value1");
        assertContainsProperty(data, "value2", v2);
    }

    @Test
    void testDeserialization() throws Exception {
        String pojoJson = "{\"value1\":\"value1\",\"value2\":\"value2\"}";
        TestPojo pojo = mapper.readValue(pojoJson, TestPojo.class);
        assertNotNull(pojo);
        assertEquals("value1", pojo.getValue1());
        assertEquals("value2", pojo.getValue2());
    }

    @Test
    void testDeserializationDoesNotFailOnUnknownProperties() throws Exception {
        String pojoJson = "{\"value1\":\"value1\",\"value2\":\"value2\",\"value3\":\"value3\"}";
        TestPojo pojo = mapper.readValue(pojoJson, TestPojo.class);
        assertNotNull(pojo);
        assertEquals("value1", pojo.getValue1());
        assertEquals("value2", pojo.getValue2());
    }

    private void assertContainsProperty(String data, String key, String value)  throws Exception {
        String toFind = String.format("\"%s\":\"%s\"", key, value);
        assertThat(data, Matchers.containsString(toFind));
    }

    private void assertDoesNotContainProperty(String data, String property) throws Exception {
        assertThat(data, Matchers.not(Matchers.containsString((property))));
    }

}
