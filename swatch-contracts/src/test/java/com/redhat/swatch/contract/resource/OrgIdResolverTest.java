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
package com.redhat.swatch.contract.resource;

import static com.redhat.swatch.common.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.common.security.PskPrincipal;
import com.redhat.swatch.common.security.RhIdentityPrincipalFactory;
import com.redhat.swatch.contract.security.RhIdentityUtils;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Field;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrgIdResolverTest {

  private static final String ORG_ID = "org123";
  private static final String SERVICE_ACCOUNT = "urn:console.redhat.com:service:swatch";

  private OrgIdResolver orgIdResolver;
  private SecurityContext securityContext;
  private HttpHeaders httpHeaders;

  @BeforeEach
  void setUp() throws Exception {
    var identityPrincipalFactory = new RhIdentityPrincipalFactory();
    Field mapperField = RhIdentityPrincipalFactory.class.getDeclaredField("mapper");
    mapperField.setAccessible(true);
    mapperField.set(identityPrincipalFactory, new ObjectMapper());

    orgIdResolver = new OrgIdResolver();
    orgIdResolver.identityPrincipalFactory = identityPrincipalFactory;
    securityContext = mock(SecurityContext.class);
    httpHeaders = mock(HttpHeaders.class);
  }

  @Test
  void shouldReturnOrgIdFromCustomerPrincipal() {
    var principal = mock(Principal.class);
    when(principal.getName()).thenReturn(ORG_ID);
    when(securityContext.getUserPrincipal()).thenReturn(principal);

    String orgId = orgIdResolver.getOrgId(securityContext, httpHeaders);

    assertEquals(ORG_ID, orgId);
  }

  @Test
  void shouldExtractOrgIdFromIdentityHeaderWhenPskPrincipal() {
    when(securityContext.getUserPrincipal()).thenReturn(new PskPrincipal());
    when(httpHeaders.getHeaderString(RH_IDENTITY_HEADER))
        .thenReturn(RhIdentityUtils.CUSTOMER_IDENTITY_HEADER);

    String orgId = orgIdResolver.getOrgId(securityContext, httpHeaders);

    assertEquals(ORG_ID, orgId);
  }

  @Test
  void shouldFallbackToServiceUrnWhenPskPrincipalAndNoIdentityHeader() {
    when(securityContext.getUserPrincipal()).thenReturn(new PskPrincipal());
    when(httpHeaders.getHeaderString(RH_IDENTITY_HEADER)).thenReturn(null);

    String orgId = orgIdResolver.getOrgId(securityContext, httpHeaders);

    assertEquals(SERVICE_ACCOUNT, orgId);
  }

  @Test
  void shouldFallbackToServiceUrnWhenPskPrincipalAndInvalidIdentityHeader() {
    when(securityContext.getUserPrincipal()).thenReturn(new PskPrincipal());
    when(httpHeaders.getHeaderString(RH_IDENTITY_HEADER)).thenReturn("not-valid-base64!!!");

    String orgId = orgIdResolver.getOrgId(securityContext, httpHeaders);

    assertEquals(SERVICE_ACCOUNT, orgId);
  }
}
