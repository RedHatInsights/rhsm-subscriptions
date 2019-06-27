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
package org.candlepin.subscriptions.resource;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.exception.SubscriptionsException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.Response;

public class TallyResourceTest {
    private static final byte[] TEST_ACCOUNT_HEADER =
        "{\"identity\":{\"account_number\":\"123456\"}}".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testAccountNumberMustMatchHeader() {
        TallyResource resource = new TallyResource(new ObjectMapper());
        SubscriptionsException e = assertThrows(SubscriptionsException.class, () -> resource.getTallyReport(
            TEST_ACCOUNT_HEADER,
            "42",
            "product1",
            "daily",
            null,
            null
        ));
        assertEquals(Response.Status.UNAUTHORIZED, e.getStatus());
    }

    @Test
    public void testBadAuthHeaderRespondsWithBadRequest() {
        TallyResource resource = new TallyResource(new ObjectMapper());
        SubscriptionsException e = assertThrows(SubscriptionsException.class, () -> resource.getTallyReport(
            "{".getBytes(StandardCharsets.UTF_8),
            "42",
            "product1",
            "daily",
            null,
            null
        ));
        assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
    }
}
