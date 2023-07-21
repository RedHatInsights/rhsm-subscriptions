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
package org.candlepin.subscriptions.exception.mapper;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.HashSet;
import org.candlepin.subscriptions.utilization.api.model.Error;
import org.candlepin.subscriptions.utilization.api.model.Errors;
import org.junit.jupiter.api.Test;

class ConstraintViolationExceptionMapperTest {

  @Test
  void testMapsWebApplicationException() {
    String expectedDetail = "FORCED!";
    ConstraintViolationException exception =
        new ConstraintViolationException(expectedDetail, new HashSet<>());

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
