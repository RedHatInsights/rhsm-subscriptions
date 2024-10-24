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
package org.candlepin.subscriptions.resteasy;

import static org.candlepin.subscriptions.resteasy.TraceResponseFilter.TRACE_RESPONSE_HEADER;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TraceResponseFilterTest {

  @Mock ContainerRequestContext requestContext;
  @Mock ContainerResponseContext responseContext;
  MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

  private final TraceResponseFilter filter = new TraceResponseFilter();

  @BeforeEach
  void setup() {
    headers.clear();
    when(responseContext.getHeaders()).thenReturn(headers);
  }

  @Test
  void testPopulateHeader() throws IOException {
    filter.filter(requestContext, responseContext);
    String traceResponse = (String) headers.getFirst(TRACE_RESPONSE_HEADER);
    assertNotNull(traceResponse);
    assertTrue(traceResponse.startsWith("00-"));
  }
}
