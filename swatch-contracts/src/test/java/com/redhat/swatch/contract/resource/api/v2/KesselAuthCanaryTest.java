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
package com.redhat.swatch.contract.resource.api.v2;

import static com.redhat.swatch.common.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;
import static com.redhat.swatch.contract.security.RhIdentityUtils.CUSTOMER_IDENTITY_HEADER;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.security.KesselAuthorizationService;
import com.redhat.swatch.common.security.KesselRolesAugmentor;
import com.redhat.swatch.common.security.RhIdentityPrincipal;
import com.redhat.swatch.contract.test.resources.EnableKesselResource;
import io.getunleash.Unleash;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Canary test proving the Kessel auth path works end-to-end. Sends a User identity header to a
 * v2 @RolesAllowed("customer") endpoint and verifies that KesselRolesAugmentor correctly
 * grants/denies the "customer" role via KesselAuthorizationService.
 */
@QuarkusTest
@TestProfile(EnableKesselResource.class)
class KesselAuthCanaryTest {

  private static final String SUBSCRIPTIONS_ENDPOINT =
      "/api/rhsm-subscriptions/v2/subscriptions/products/BASILISK";

  @InjectMock Unleash unleash;
  @InjectMock KesselAuthorizationService kesselService;

  @BeforeEach
  void setup() {
    when(unleash.isEnabled(KesselRolesAugmentor.KESSEL_FLAG)).thenReturn(true);
  }

  @Test
  void kesselGrantsAccess_requestPassesAuth() {
    when(kesselService.getPermissions(any(RhIdentityPrincipal.class)))
        .thenReturn(List.of("subscriptions:*:*"));

    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .queryParam("beginning", OffsetDateTime.now().minusDays(1).toString())
        .queryParam("ending", OffsetDateTime.now().toString())
        .when()
        .get(SUBSCRIPTIONS_ENDPOINT)
        .then()
        .statusCode(not(401))
        .statusCode(not(403));

    verify(kesselService).getPermissions(any(RhIdentityPrincipal.class));
  }

  @Test
  void kesselDeniesAccess_endpointReturns403() {
    when(kesselService.getPermissions(any(RhIdentityPrincipal.class))).thenReturn(List.of());

    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .queryParam("beginning", OffsetDateTime.now().minusDays(1).toString())
        .queryParam("ending", OffsetDateTime.now().toString())
        .when()
        .get(SUBSCRIPTIONS_ENDPOINT)
        .then()
        .statusCode(403);
  }

  @Test
  void kesselFlagOff_fallsBackToRbacV1() {
    when(unleash.isEnabled(KesselRolesAugmentor.KESSEL_FLAG)).thenReturn(false);

    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .queryParam("beginning", OffsetDateTime.now().minusDays(1).toString())
        .queryParam("ending", OffsetDateTime.now().toString())
        .when()
        .get(SUBSCRIPTIONS_ENDPOINT);

    verify(kesselService, never()).getPermissions(any());
  }
}
