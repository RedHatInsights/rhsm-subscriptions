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

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

class RhIdentityIdentityProviderTest {
  @Test
  void testRhIdentityIsPassedThrough() {
    var rhIdentity = new RhIdentityPrincipal();
    rhIdentity.setIdentity(new Identity());
    rhIdentity.getIdentity().setSamlAssertions(new SamlAssertions());
    rhIdentity.getIdentity().setType("Associate");
    rhIdentity.getIdentity().getSamlAssertions().setEmail("test@example.com");
    var identityProvider = new RhIdentityIdentityProvider();
    var subscriber =
        identityProvider
            .authenticate(new RhIdentityAuthenticationRequest(rhIdentity), null)
            .map(SecurityIdentity::getPrincipal)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());
    subscriber.assertCompleted().assertItem(rhIdentity);
  }
}
