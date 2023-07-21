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

import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER;
import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_PSK_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityHeaderAuthenticationFilterTest {

  private ObjectMapper mapper = new ObjectMapper();

  @Mock private HttpServletRequest request;

  @Test
  void testEmptyHeaderReturnsNullPrincipal() {
    doReturn(null).when(request).getHeader(RH_IDENTITY_HEADER);

    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    assertNull(filter.getPreAuthenticatedPrincipal(request));
  }

  @Test
  void defaultEmptyPrincipalReturnedWhenExceptionOccursWhileProcessingHeader() {
    doReturn("arandomheadervalue").when(request).getHeader(RH_IDENTITY_HEADER);

    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    assertPrincipal(filter.getPreAuthenticatedPrincipal(request), null, null);
  }

  @Test
  void missingIdentityResultsInNullOrgAndAccount() {
    // {}
    String emptyJson = Base64.getEncoder().encodeToString("{}".getBytes());
    when(request.getHeader(RH_IDENTITY_HEADER)).thenReturn(emptyJson);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    assertPrincipal(filter.getPreAuthenticatedPrincipal(request), null, null);
  }

  @Test
  void testMissingInternalProperty() {
    String missingInternal =
        Base64.getEncoder()
            .encodeToString("{\"identity\":{\"account_number\":\"myaccount\"}}".getBytes());
    doReturn(missingInternal).when(request).getHeader(RH_IDENTITY_HEADER);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    assertPrincipal(filter.getPreAuthenticatedPrincipal(request), "myaccount", null);
  }

  @Test
  void missingOrgInHeaderResultsInNullValueInPrincipal() {
    String missingOrgId =
        Base64.getEncoder()
            .encodeToString(
                "{\"identity\":{\"account_number\":\"myaccount\", \"internal\":{}}}".getBytes());
    doReturn(missingOrgId).when(request).getHeader(RH_IDENTITY_HEADER);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    assertPrincipal(filter.getPreAuthenticatedPrincipal(request), "myaccount", null);
  }

  @Test
  void missingAccountInHeaderResultsInNullValueInPrincipal() {
    //
    String missingAccount =
        Base64.getEncoder()
            .encodeToString("{\"identity\":{\"internal\":{\"org_id\":\"myorg\"}}}".getBytes());
    doReturn(missingAccount).when(request).getHeader(RH_IDENTITY_HEADER);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    assertPrincipal(filter.getPreAuthenticatedPrincipal(request), null, "myorg");
  }

  @Test
  void testRhAssociateHeader() {
    String associate =
        Base64.getEncoder()
            .encodeToString(
                ("{\"identity\":{\"associate\":{\"email\":\"test@example.com\"},"
                        + "\"auth_type\":\"saml-auth\",\"type\": \"Associate\"}}")
                    .getBytes());
    doReturn(associate).when(request).getHeader(RH_IDENTITY_HEADER);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    Object principal = filter.getPreAuthenticatedPrincipal(request);
    assertTrue(principal instanceof RhAssociatePrincipal);
    assertEquals("test@example.com", ((RhAssociatePrincipal) principal).getEmail());
  }

  @Test
  void testX509Header() {
    String associate =
        Base64.getEncoder()
            .encodeToString(
                ("{\"identity\":{\"auth_type\":\"x509\",\"type\":\"X509\",\"x509\":{\"subject_dn\":"
                        + "\"CN=test.example.com\"}}}")
                    .getBytes());
    doReturn(associate).when(request).getHeader(RH_IDENTITY_HEADER);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    Object principal = filter.getPreAuthenticatedPrincipal(request);
    assertTrue(principal instanceof X509Principal);
    assertEquals("CN=test.example.com", ((X509Principal) principal).getSubjectDn());
  }

  @Test
  void testEmptyPskHeaderReturnsNullPrincipal() {
    doReturn(null).when(request).getHeader(RH_PSK_HEADER);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    assertNull(filter.getPreAuthenticatedPrincipal(request));
  }

  @Test
  void testPskHeader() {
    String psk = "c9a98753-2092-4617-b226-5c2653330b3d";
    doReturn(psk).when(request).getHeader(RH_PSK_HEADER);
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    Object principal = filter.getPreAuthenticatedPrincipal(request);
    assertTrue(principal instanceof PskClientPrincipal);
    assertEquals(
        "c9a98753-2092-4617-b226-5c2653330b3d", ((PskClientPrincipal) principal).getPreSharedKey());
  }

  private void assertPrincipal(Object preAuthPrincipal, String expAccountNumber, String expOrgId) {
    assertTrue(preAuthPrincipal instanceof InsightsUserPrincipal);

    InsightsUserPrincipal principal = (InsightsUserPrincipal) preAuthPrincipal;
    assertEquals(expOrgId, principal.getOrgId());
    assertEquals(expAccountNumber, principal.getAccountNumber());
  }
}
