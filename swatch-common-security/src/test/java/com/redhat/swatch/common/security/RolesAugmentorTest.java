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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RolesAugmentorTest {

  RolesAugmentor augmentor;
  RhIdentityPrincipalFactory identityFactory;

  @Mock SecurityConfiguration configuration;

  AuthenticationRequestContext context = Uni.createFrom()::item;

  @BeforeEach
  void setup() {
    augmentor = new RolesAugmentor();
    augmentor.configuration = configuration;

    identityFactory = new RhIdentityPrincipalFactory();
    identityFactory.mapper = new ObjectMapper();
  }

  private QuarkusSecurityIdentity securityIdentityForRhIdentityJson(String json) {
    try {
      return QuarkusSecurityIdentity.builder().setPrincipal(identityFactory.fromJson(json)).build();
    } catch (IOException e) {
      throw new AuthenticationFailedException(e);
    }
  }

  @Test
  void shouldGrantAdminRoleToOrgAdminUser() {
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.ORG_ADMIN_IDENTITY_JSON), context)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("admin"));
  }

  @Test
  void shouldNotGrantAdminRoleToNonAdminUser() {
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON), context)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of());
  }
}
