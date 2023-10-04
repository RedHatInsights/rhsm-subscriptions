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
package com.redhat.swatch.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1.SourcePartnerEnum;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.contract.resource.WireMockResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
class RhPartnerClientIntegrationTest {
  @Inject @RestClient PartnerApi partnerApi;

  @Test
  void smokeTestClientWithTls() throws ApiException {
    // see WireMockResource for setup details, and canned response JSON
    var result =
        partnerApi.getPartnerEntitlements(new QueryPartnerEntitlementV1().rhAccountId("org123"));
    var partnerEntitlements = result.getContent();
    assertNotNull(partnerEntitlements);
    assertEquals(2, partnerEntitlements.size());
    var entitlement = partnerEntitlements.get(0);
    assertEquals("org123", entitlement.getRhAccountId());
    assertEquals(SourcePartnerEnum.AWS_MARKETPLACE, entitlement.getSourcePartner());

    var rhEntitlements = entitlement.getRhEntitlements();
    assertNotNull(rhEntitlements);
    assertNotNull(rhEntitlements.get(0));
    assertEquals("RH000000", rhEntitlements.get(0).getSku());
    assertEquals("123456", rhEntitlements.get(0).getRedHatSubscriptionNumber());
    var purchase = entitlement.getPurchase();
    assertNotNull(purchase);
    assertEquals("1234567890abcdefghijklmno", purchase.getVendorProductCode());
    assertNotNull(purchase.getContracts());
    assertEquals(2, purchase.getContracts().size());
    var contract = purchase.getContracts().get(0);
    assertNotNull(contract);
    assertEquals(OffsetDateTime.parse("2022-09-23T20:07:51.010445Z"), contract.getStartDate());
    assertNotNull(contract.getDimensions());
    assertEquals(2, contract.getDimensions().size());
    var dimension = contract.getDimensions().get(0);
    assertEquals("foobar", dimension.getName());
    assertEquals("1000000", dimension.getValue());
  }
}
