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
import static org.junit.jupiter.api.Assertions.*;

import jakarta.ws.rs.core.Response;
import org.candlepin.subscriptions.utilization.api.model.Error;
import org.candlepin.subscriptions.utilization.api.model.Errors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class DefaultExceptionMapperTest {

  @Test
  void testExceptionMapping() {
    String expectedDetail = "FORCED!";
    String expectedTitle =
        "An internal server error has occurred. Check the server logs for further details.";

    DefaultExceptionMapper mapper = new DefaultExceptionMapper();
    Response resp = mapper.toResponse(new RuntimeException(expectedDetail));

    Object entityObj = resp.getEntity();
    assertNotNull(entityObj);
    assertThat(entityObj, Matchers.instanceOf(Errors.class));

    Errors errors = (Errors) entityObj;
    assertEquals(1, errors.getErrors().size());

    Error error = errors.getErrors().get(0);
    assertEquals("500", error.getStatus());
    assertEquals(expectedTitle, error.getTitle());
    assertEquals(expectedDetail, error.getDetail());
  }
}
