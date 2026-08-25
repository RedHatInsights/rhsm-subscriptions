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
package com.redhat.swatch.aws.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.redhat.swatch.aws.exception.DefaultApiException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class DefaultApiExceptionMapperTest {

  private final DefaultApiExceptionMapper mapper = new DefaultApiExceptionMapper();

  @Test
  void shouldParseContractsErrorFromJsonBody() {
    String json =
        "{\"code\":\"CONTRACTS1005\",\"status\":\"404\",\"title\":\"Subscription recently terminated\"}";
    Response response =
        Response.status(Response.Status.NOT_FOUND)
            .entity(json)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();

    DefaultApiException exception = mapper.toThrowable(response);

    assertEquals("CONTRACTS1005", exception.getError().getCode());
    assertEquals("404", exception.getError().getStatus());
    assertEquals("Subscription recently terminated", exception.getError().getTitle());
  }

  @Test
  void shouldReturnNullErrorForEmptyResponseBody() {
    Response response = Response.status(Response.Status.NOT_FOUND).build();

    DefaultApiException exception = mapper.toThrowable(response);

    assertNull(exception.getError());
  }

  @Test
  void shouldParseErrorWhenContentTypeIsMissing() {
    String json =
        "{\"code\":\"CONTRACTS1006\",\"status\":\"404\",\"title\":\"Missing billing account\"}";
    Response response = Response.status(Response.Status.NOT_FOUND).entity(json).build();

    DefaultApiException exception = mapper.toThrowable(response);

    assertEquals("CONTRACTS1006", exception.getError().getCode());
  }
}
