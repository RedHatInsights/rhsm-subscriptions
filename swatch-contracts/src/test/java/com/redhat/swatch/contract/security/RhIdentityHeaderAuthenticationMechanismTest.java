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

import static org.mockito.Mockito.*;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.QuarkusHttpHeaders;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RhIdentityHeaderAuthenticationMechanismTest {
  @Mock HttpServerRequest request;

  @Mock SecurityIdentity authResponse;

  @Mock IdentityProviderManager identityProviderManager;

  RoutingContext mockRequest(Map<String, String> headerValues) {
    var routingContext = mock(RoutingContext.class);
    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(new QuarkusHttpHeaders().addAll(headerValues));
    return routingContext;
  }

  @Test
  void testIdentityHeaderAuthenticationNotAttemptedWhenHeaderMissing() {
    var auth = new RhIdentityHeaderAuthenticationMechanism();
    var subscriber =
        auth.authenticate(mockRequest(Map.of()), identityProviderManager)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertItem(null);
    verifyNoInteractions(identityProviderManager);
  }

  @Test
  void testIdentityHeaderAuthenticationRequestedWhenHeaderPresent() {
    var auth = new RhIdentityHeaderAuthenticationMechanism();
    when(identityProviderManager.authenticate(any()))
        .thenReturn(Uni.createFrom().item(authResponse));
    var subscriber =
        auth.authenticate(
                mockRequest(Map.of("x-rh-identity", RhIdentityUtils.ASSOCIATE_IDENTITY_HEADER)),
                identityProviderManager)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(authResponse);
    var expectedIdentity =
        RhIdentityPrincipal.builder()
            .identity(
                Identity.builder()
                    .type("Associate")
                    .samlAssertions(SamlAssertions.builder().email("test@example.com").build())
                    .build())
            .build();
    verify(identityProviderManager)
        .authenticate(new RhIdentityAuthenticationRequest(expectedIdentity));
  }

  @Test
  void testIdentityHeaderAuthenticationFailedWhenHeaderMalformed() {
    var auth = new RhIdentityHeaderAuthenticationMechanism();
    var subscriber =
        auth.authenticate(
                mockRequest(Map.of("x-rh-identity", "placeholder")), identityProviderManager)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertFailedWith(AuthenticationFailedException.class);
    verifyNoInteractions(identityProviderManager);
  }
}
