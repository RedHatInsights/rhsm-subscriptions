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
package com.redhat.swatch.contract.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RolesAugmentorTest {
  @Inject RhIdentityPrincipalFactory identityFactory;

  private QuarkusSecurityIdentity securityIdentityForRhIdentityJson(String json) {
    try {
      return QuarkusSecurityIdentity.builder().setPrincipal(identityFactory.fromJson(json)).build();
    } catch (IOException e) {
      throw new AuthenticationFailedException(e);
    }
  }

  private QuarkusSecurityIdentity securityIdentityForAnonymous() {
    return QuarkusSecurityIdentity.builder().setAnonymous(true).build();
  }

  @Test
  void testRoleGrantedToRhIdentityWhenEnabled() {
    var augmentor = new RolesAugmentor();
    augmentor.testApisEnabled = true;
    SecurityIdentity x509Identity =
        securityIdentityForRhIdentityJson(RhIdentityUtils.X509_IDENTITY_JSON);
    var subscriber =
        augmentor
            .augment(x509Identity, null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("service", "test"));
  }

  @Test
  void testNoRolesForAnonymous() {
    var augmentor = new RolesAugmentor();
    augmentor.testApisEnabled = false;
    var subscriber =
        augmentor
            .augment(securityIdentityForAnonymous(), null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of());
  }

  @Test
  void testNoTestRoleGrantedToRhIdentityWhenDisabled() {
    var augmentor = new RolesAugmentor();
    augmentor.testApisEnabled = false;
    SecurityIdentity x509Identity =
        securityIdentityForRhIdentityJson(RhIdentityUtils.X509_IDENTITY_JSON);
    var subscriber =
        augmentor
            .augment(x509Identity, null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("service"));
  }

  @Test
  void supportRoleGrantedToRhIdentityWithTypeAssociate() {
    var augmentor = new RolesAugmentor();
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.ASSOCIATE_IDENTITY_JSON), null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("support"));
  }

  @Test
  void serviceRoleGrantedToPsk() {
    var augmentor = new RolesAugmentor();
    var subscriber =
        augmentor
            .augment(
                QuarkusSecurityIdentity.builder().setPrincipal(new PskPrincipal()).build(), null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("service"));
  }

  @Test
  void serviceRoleGrantedToX509() {
    var augmentor = new RolesAugmentor();
    SecurityIdentity x509Identity =
        securityIdentityForRhIdentityJson(RhIdentityUtils.X509_IDENTITY_JSON);
    var subscriber =
        augmentor
            .augment(x509Identity, null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("service"));
  }
}
