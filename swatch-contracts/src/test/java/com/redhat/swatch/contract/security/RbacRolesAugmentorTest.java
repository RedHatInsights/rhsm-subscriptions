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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.rbac.api.model.Access;
import com.redhat.swatch.clients.rbac.api.model.AccessPagination;
import com.redhat.swatch.clients.rbac.api.resources.AccessApi;
import com.redhat.swatch.clients.rbac.api.resources.ApiException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
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

  @Mock AccessApi rbacApi;

  AuthenticationRequestContext context = Uni.createFrom()::item;

  @BeforeEach
  void setup() {
    augmentor = new RbacRolesAugmentor();
    augmentor.rbacEnabled = true;
    augmentor.accessApi = rbacApi;
  }

  private RbacRolesAugmentor getRbacRolesAugmentor() {
    var augmentor = new RbacRolesAugmentor();
    augmentor.accessApi = rbacApi;
    augmentor.rbacEnabled = true;
    return augmentor;
  }

  private static QuarkusSecurityIdentity securityIdentityForRhIdentityJson(String json) {
    return QuarkusSecurityIdentity.builder()
        .setPrincipal(RhIdentityPrincipal.fromJson(json))
        .build();
  }

  @Test
  void customerRoleGrantedToCustomerHavingAdminRbacRole() throws ApiException {
    var augmentor = getRbacRolesAugmentor();
    when(rbacApi.getPrincipalAccess(any(), any(), any(), any(), any()))
        .thenReturn(
            new AccessPagination().data(List.of(new Access().permission("subscriptions:*:*"))));
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
  void customerRoleGrantedToCustomerHavingReaderRbacRole() throws ApiException {
    var augmentor = getRbacRolesAugmentor();
    when(rbacApi.getPrincipalAccess(any(), any(), any(), any(), any()))
        .thenReturn(
            new AccessPagination()
                .data(List.of(new Access().permission("subscriptions:reports:read"))));
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
  void customerRoleNotGrantedToCustomerNoRbacRole() throws ApiException {
    var augmentor = getRbacRolesAugmentor();
    when(rbacApi.getPrincipalAccess(any(), any(), any(), any(), any()))
        .thenReturn(new AccessPagination().data(List.of()));
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
  void customerRoleNotGrantedWhenRbacCallFails() throws ApiException {
    var augmentor = getRbacRolesAugmentor();
    when(rbacApi.getPrincipalAccess(any(), any(), any(), any(), any()))
        .thenThrow(new ApiException());
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
  void noRbacInteractionWhenRbacNotEnabled() throws ApiException {
    var augmentor = getRbacRolesAugmentor();
    augmentor.rbacEnabled = false;
    var subscriber =
        augmentor
            .augment(
                securityIdentityForRhIdentityJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON), null)
            .map(SecurityIdentity::getRoles)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    verifyNoInteractions(rbacApi);
    subscriber.assertCompleted().assertItem(Set.of());
  }
}
