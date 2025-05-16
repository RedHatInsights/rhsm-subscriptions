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

import static com.redhat.swatch.contract.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;
import static com.redhat.swatch.contract.security.RhIdentityUtils.CUSTOMER_IDENTITY_HEADER;
import static io.restassured.RestAssured.given;

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportSortV2;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.test.resources.DisableRbacResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DisableRbacResource.class)
class SubscriptionResourceV2Test {
  private static final ProductId RHEL_FOR_X86 = ProductId.fromString("RHEL for x86");
  private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

  @Inject SubscriptionResourceV2 subscriptionResource;
  @InjectMock SubscriptionTableControllerV2 controller;

  @Test
  void testAccessDeniedWhenUserIsNotACustomer() {
    given()
        .queryParams(
            "offset",
            0,
            "limit",
            10,
            "beginning",
            min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "ending",
            max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "usage",
            UsageType.PRODUCTION.toString(),
            "sort",
            SkuCapacityReportSortV2.SKU)
        .header(RH_IDENTITY_HEADER, "not a customer")
        .get(
            String.format(
                "/api/rhsm-subscriptions/v2/subscriptions/products/%s", RHEL_FOR_X86.toString()))
        .then()
        .statusCode(401);
  }

  @Test
  void testAccessAlowed() {
    given()
        .queryParams(
            "offset",
            0,
            "limit",
            10,
            "beginning",
            min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "ending",
            max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "usage",
            UsageType.PRODUCTION.toString(),
            "sort",
            SkuCapacityReportSortV2.SKU)
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get(
            String.format(
                "/api/rhsm-subscriptions/v2/subscriptions/products/%s", RHEL_FOR_X86.toString()))
        .then()
        .statusCode(204);
  }
}
