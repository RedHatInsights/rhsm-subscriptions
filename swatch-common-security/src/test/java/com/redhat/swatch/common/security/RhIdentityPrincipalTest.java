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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RhIdentityPrincipalTest {

  @Test
  void testUserTypeHelperMethods() {
    // Test User type
    Identity userIdentity = Identity.builder().type("User").orgId("123").build();
    RhIdentityPrincipal userPrincipal =
        RhIdentityPrincipal.builder().identity(userIdentity).headerValue("test-header").build();

    assertTrue(userPrincipal.isUser());
    assertFalse(userPrincipal.isServiceAccount());
    assertFalse(userPrincipal.isX509());
    assertFalse(userPrincipal.isAssociate());
  }

  @Test
  void testServiceAccountTypeHelperMethods() {
    // Test ServiceAccount type
    Identity serviceAccountIdentity =
        Identity.builder().type("ServiceAccount").orgId("456").build();
    RhIdentityPrincipal serviceAccountPrincipal =
        RhIdentityPrincipal.builder()
            .identity(serviceAccountIdentity)
            .headerValue("test-header")
            .build();

    assertFalse(serviceAccountPrincipal.isUser());
    assertTrue(serviceAccountPrincipal.isServiceAccount());
    assertFalse(serviceAccountPrincipal.isX509());
    assertFalse(serviceAccountPrincipal.isAssociate());
  }

  @Test
  void testX509TypeHelperMethods() {
    // Test X509 type
    X509Properties x509Props = X509Properties.builder().subjectDn("CN=test").build();
    Identity x509Identity = Identity.builder().type("X509").x509(x509Props).build();
    RhIdentityPrincipal x509Principal =
        RhIdentityPrincipal.builder().identity(x509Identity).headerValue("test-header").build();

    assertFalse(x509Principal.isUser());
    assertFalse(x509Principal.isServiceAccount());
    assertTrue(x509Principal.isX509());
    assertFalse(x509Principal.isAssociate());
  }

  @Test
  void testAssociateTypeHelperMethods() {
    // Test Associate type
    SamlAssertions samlAssertions = SamlAssertions.builder().email("test@redhat.com").build();
    Identity associateIdentity =
        Identity.builder().type("Associate").samlAssertions(samlAssertions).build();
    RhIdentityPrincipal associatePrincipal =
        RhIdentityPrincipal.builder()
            .identity(associateIdentity)
            .headerValue("test-header")
            .build();

    assertFalse(associatePrincipal.isUser());
    assertFalse(associatePrincipal.isServiceAccount());
    assertFalse(associatePrincipal.isX509());
    assertTrue(associatePrincipal.isAssociate());
  }

  @Test
  void testGetNameForDifferentTypes() {
    // Test name resolution for different identity types

    // User/ServiceAccount should return orgId
    Identity userIdentity = Identity.builder().type("User").orgId("123").build();
    RhIdentityPrincipal userPrincipal =
        RhIdentityPrincipal.builder().identity(userIdentity).build();
    assertEquals("123", userPrincipal.getName());

    // X509 should return subject DN
    X509Properties x509Props = X509Properties.builder().subjectDn("CN=test,O=RedHat").build();
    Identity x509Identity = Identity.builder().type("X509").x509(x509Props).build();
    RhIdentityPrincipal x509Principal =
        RhIdentityPrincipal.builder().identity(x509Identity).build();
    assertEquals("CN=test,O=RedHat", x509Principal.getName());

    // Associate should return email
    SamlAssertions samlAssertions = SamlAssertions.builder().email("test@redhat.com").build();
    Identity associateIdentity =
        Identity.builder().type("Associate").samlAssertions(samlAssertions).build();
    RhIdentityPrincipal associatePrincipal =
        RhIdentityPrincipal.builder().identity(associateIdentity).build();
    assertEquals("test@redhat.com", associatePrincipal.getName());
  }
}
