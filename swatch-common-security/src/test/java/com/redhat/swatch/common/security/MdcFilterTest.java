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
package com.redhat.swatch.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcFilterTest {

  private MdcFilter mdcFilter;
  private ContainerRequestContext requestContext;
  private ContainerResponseContext responseContext;
  private SecurityContext securityContext;
  private Principal principal;

  @BeforeEach
  void setUp() {
    mdcFilter = new MdcFilter();
    requestContext = mock(ContainerRequestContext.class);
    responseContext = mock(ContainerResponseContext.class);
    securityContext = mock(SecurityContext.class);
    principal = mock(Principal.class);

    // Use reflection to set the security context (since it's @Context injected)
    try {
      var field = MdcFilter.class.getDeclaredField("securityContext");
      field.setAccessible(true);
      field.set(mdcFilter, securityContext);
    } catch (Exception e) {
      fail("Failed to inject mock security context: " + e.getMessage());
    }
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void testFilterWithAuthenticatedUser() throws Exception {
    // Given
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("testuser@redhat.com");

    // When
    mdcFilter.filter(requestContext);

    // Then
    assertEquals("testuser@redhat.com", MDC.get("user"));
  }

  @Test
  void testFilterWithNoAuthenticatedUser() throws Exception {
    // Given
    when(securityContext.getUserPrincipal()).thenReturn(null);

    // When
    mdcFilter.filter(requestContext);

    // Then
    assertNull(MDC.get("user"));
  }

  @Test
  void testResponseFilterClearsMdc() throws Exception {
    // Given - Set up MDC with user
    MDC.put("user", "testuser@redhat.com");
    assertEquals("testuser@redhat.com", MDC.get("user"));

    // When
    mdcFilter.filter(requestContext, responseContext);

    // Then
    assertNull(MDC.get("user"));
  }

  @Test
  void testCompleteRequestResponseCycle() throws Exception {
    // Given
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("serviceaccount@redhat.com");

    // When - Request filter
    mdcFilter.filter(requestContext);

    // Then - User should be in MDC
    assertEquals("serviceaccount@redhat.com", MDC.get("user"));

    // When - Response filter
    mdcFilter.filter(requestContext, responseContext);

    // Then - User should be cleared from MDC
    assertNull(MDC.get("user"));
  }
}
