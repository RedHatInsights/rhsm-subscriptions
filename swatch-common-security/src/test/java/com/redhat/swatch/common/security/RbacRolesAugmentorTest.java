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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.clients.rbac.RbacApiException;
import com.redhat.swatch.clients.rbac.RbacService;
import io.getunleash.Unleash;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RbacRolesAugmentorTest {
  RbacRolesAugmentor augmentor;
  RhIdentityPrincipalFactory identityFactory;

  @Mock RbacService rbacService;
  @Mock Unleash unleash;

  AuthenticationRequestContext context = Uni.createFrom()::item;

  @BeforeEach
  void setup() {
    augmentor = new RbacRolesAugmentor();
    augmentor.rbacEnabled = true;
    augmentor.unleash = unleash;
    augmentor.rbacService = rbacService;

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
  void customerRoleGrantedToCustomerHavingAdminRbacRole() throws RbacApiException {
    when(rbacService.getPermissions(any(), any())).thenReturn(List.of("subscriptions:*:*"));
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON), context)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("customer"));
  }

  @Test
  void customerRoleGrantedToCustomerHavingReaderRbacRole() throws RbacApiException {
    when(rbacService.getPermissions(any(), any()))
        .thenReturn(List.of("subscriptions:reports:read"));
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON), context)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of("customer"));
  }

  @Test
  void customerRoleNotGrantedToCustomerNoRbacRole() throws RbacApiException {
    when(rbacService.getPermissions(any(), any())).thenReturn(List.of());
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON), context)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of());
  }

  @Test
  void customerRoleNotGrantedWhenRbacCallFails() throws RbacApiException {
    when(rbacService.getPermissions(any(), any()))
        .thenThrow(new RbacApiException("RBAC unavailable", null));
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON), context)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(Set.of());
  }

  @Test
  void noRbacInteractionWhenRbacNotEnabled() {
    augmentor.rbacEnabled = false;
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON), null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    verifyNoInteractions(rbacService);
    subscriber.assertCompleted().assertItem(Set.of("test", "service", "support", "customer"));
  }
}
