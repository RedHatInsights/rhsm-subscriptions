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
package org.candlepin.subscriptions.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AntiCsrfFilterTest {

  private static final String DOMAIN_SUFFIX = ".redhat.com";

  @Mock SecurityProperties properties;
  @Mock HttpServletRequest request;
  @Mock HttpServletResponse response;
  @Mock FilterChain filterChain;

  @Test
  void testWhenDisabledThenShouldDoFilter() throws ServletException, IOException {
    givenCorsDisabled();
    whenDoFilterInternal();
    thenDoFilterIsDone();
  }

  @Test
  void testWhenNoOriginHeaderThenShouldDoFilter() throws ServletException, IOException {
    givenCorsEnabledWithDomain();
    whenDoFilterInternal();
    thenDoFilterIsDone();
  }

  @Test
  void testWhenOriginHeaderDoesNotMatchThenShouldReturnUnauthorized()
      throws ServletException, IOException {
    givenCorsEnabledWithDomain();
    givenOrigin("wrong.com");
    whenDoFilterInternal();
    thenResponseIsUnauthorized();
  }

  @Test
  void testWhenOriginHeaderMatchesThenShouldDoFilter() throws ServletException, IOException {
    givenCorsEnabledWithDomain();
    givenOrigin("mytestservice" + DOMAIN_SUFFIX);
    whenDoFilterInternal();
    thenDoFilterIsDone();
  }

  private void givenOrigin(String origin) {
    when(request.getHeader("Origin")).thenReturn(origin);
  }

  private void givenCorsDisabled() {
    when(properties.isDevMode()).thenReturn(true);
  }

  private void givenCorsEnabledWithDomain() {
    when(properties.isDevMode()).thenReturn(false);
    when(properties.getAntiCsrfDomainSuffix()).thenReturn(DOMAIN_SUFFIX);
  }

  private void whenDoFilterInternal() throws ServletException, IOException {
    AntiCsrfFilter filter = new AntiCsrfFilter(properties);
    filter.doFilterInternal(request, response, filterChain);
  }

  private void thenDoFilterIsDone() throws ServletException, IOException {
    verify(filterChain).doFilter(any(), any());
  }

  private void thenResponseIsUnauthorized() throws IOException {
    verify(response).sendError(eq(403), any());
  }
}
