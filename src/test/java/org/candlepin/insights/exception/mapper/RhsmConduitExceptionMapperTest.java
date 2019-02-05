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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.insights.api.model.Error;
import org.candlepin.insights.api.model.Errors;
import org.candlepin.insights.exception.ErrorCode;
import org.candlepin.insights.exception.RhsmConduitException;
import org.candlepin.insights.inventory.client.ApiException;

import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;


public class RhsmConduitExceptionMapperTest {

    @Test
    public void testMapsRhsmConduitException() {
        ErrorCode expectedCode = ErrorCode.UNHANDLED_EXCEPTION_ERROR;
        String expectedCodeString = expectedCode.getCode();
        Status expectedStatus = Status.INTERNAL_SERVER_ERROR;
        String expectedTitle = "An exception was thrown";
        String expectedDetail = "FORCED!";

        RhsmConduitExceptionMapper mapper = new RhsmConduitExceptionMapper();
        Response resp = mapper.toResponse(new RhsmConduitException(expectedCode, expectedStatus,
            expectedTitle, expectedDetail));

        Object entityObj = resp.getEntity();
        assertTrue(entityObj != null && entityObj instanceof Errors);

        Errors errors = (Errors) entityObj;
        assertEquals(1, errors.getErrors().size());

        Error error = errors.getErrors().get(0);
        assertEquals(expectedCodeString, error.getCode());
        assertEquals(String.valueOf(expectedStatus.getStatusCode()), error.getStatus());
        assertEquals(expectedTitle, error.getTitle());
        assertEquals(expectedDetail, error.getDetail());
    }

    @Test
    public void testMapsNestedException() {
        ErrorCode expectedCode = ErrorCode.UNHANDLED_EXCEPTION_ERROR;
        String expectedCodeString = expectedCode.getCode();
        Status expectedStatus = Status.INTERNAL_SERVER_ERROR;
        String expectedTitle = "An exception occurred from Inventory API.";
        String expectedDetail = "Forced API Exception";

        ApiException apie = new ApiException(Status.BAD_REQUEST.getStatusCode(), expectedDetail);
        RhsmConduitException rhsme = new RhsmConduitException(expectedCode, expectedStatus, expectedTitle,
            apie);

        RhsmConduitExceptionMapper mapper = new RhsmConduitExceptionMapper();
        Response resp = mapper.toResponse(rhsme);

        Object entityObj = resp.getEntity();
        assertTrue(entityObj != null && entityObj instanceof Errors);

        Errors errors = (Errors) entityObj;
        assertEquals(1, errors.getErrors().size());

        Error error = errors.getErrors().get(0);
        assertEquals(expectedCodeString, error.getCode());
        assertEquals(String.valueOf(expectedStatus.getStatusCode()), error.getStatus());
        assertEquals(expectedTitle, error.getTitle());
        assertEquals(expectedDetail, error.getDetail());
    }

}
