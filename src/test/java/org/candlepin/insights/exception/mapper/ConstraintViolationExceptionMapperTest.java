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

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.insights.api.model.Error;
import org.candlepin.insights.api.model.Errors;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class ConstraintViolationExceptionMapperTest {

    @Test
    public void testMapsWebApplicationException() {
        String expectedDetail = "FORCED!";
        ConstraintViolationException exception = new ConstraintViolationException(expectedDetail,
            new HashSet<>());

        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();
        Response resp = mapper.toResponse(exception);
        Object entityObj = resp.getEntity();
        assertNotNull(entityObj);
        assertThat(entityObj, instanceOf(Errors.class));

        Errors errors = (Errors) entityObj;
        assertEquals(1, errors.getErrors().size());

        Error error = errors.getErrors().get(0);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), Integer.parseInt(error.getStatus()));
        assertEquals(ConstraintViolationExceptionMapper.ERROR_TITLE, error.getTitle());
        assertEquals(expectedDetail, error.getDetail());
    }

}
