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
package org.candlepin.insights.exception.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.insights.api.model.Error;
import org.candlepin.insights.api.model.Errors;

import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;


public class DefaultExceptionMapperTest {

    @Test
    public void testExceptionMapping() {
        String expectedDetail = "FORCED!";
        String expectedTitle =
            "An internal server error has occurred. Check the server logs for further details.";

        DefaultExceptionMapper mapper = new DefaultExceptionMapper();
        Response resp = mapper.toResponse(new RuntimeException(expectedDetail));

        Object entityObj = resp.getEntity();
        assertTrue(entityObj != null && entityObj instanceof Errors);

        Errors errors = (Errors) entityObj;
        assertEquals(1, errors.getErrors().size());

        Error error = errors.getErrors().get(0);
        assertEquals("500", error.getStatus());
        assertEquals(expectedTitle, error.getTitle());
        assertEquals(expectedDetail, error.getDetail());
    }

}
