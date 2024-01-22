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
package org.candlepin.subscriptions.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.Set;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.umb.ChildProduct;
import org.candlepin.subscriptions.umb.ProductAttribute;
import org.candlepin.subscriptions.umb.ProductRelationship;
import org.candlepin.subscriptions.umb.UmbOperationalProduct;
import org.junit.jupiter.api.Test;

class UpstreamProductDataTest {

  private final ProductService stub = new ProductService(new StubProductApi());

  @Test
  void testOfferingFromUpstreamForOcpOffering() {
    // Given a marketing SKU for OpenShift Container Platform
    var sku = "MW01485";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Set.of("SVCMW01485"));
    expected.setProductIds(
        Set.of(
            69, 70, 185, 194, 197, 201, 205, 240, 271, 290, 311, 317, 318, 326, 329, 408, 458, 473,
            479, 491, 518, 519, 546, 579, 588, 603, 604, 608, 610, 645));
    expected.setProductFamily("OpenShift Enterprise");
    expected.setProductName("OpenShift Container Platform");
    expected.setDescription("Red Hat OpenShift Container Platform (Hourly)");
    expected.setServiceLevel(ServiceLevel.PREMIUM);
    expected.setUsage(Usage.EMPTY);
    expected.setHasUnlimitedUsage(false);
    expected.setMetered(false);

    // When getting the upstream Offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, engProd OIDs, and values.
    assertEquals(expected, actual);
  }

  @Test
  void testOfferingFromUpstreamForNoEngProductOffering() {
    // Given a marketing SKU MW01484 (special for being engProduct-less),
    var sku = "MW01484";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Set.of("SVCMW01484A", "SVCMW01484B"));
    expected.setProductIds(Collections.emptySet());
    expected.setProductFamily("OpenShift Enterprise");
    expected.setProductName("OpenShift Dedicated");
    expected.setDescription("Red Hat OpenShift Dedicated on Customer Cloud Subscription (Hourly)");
    expected.setServiceLevel(ServiceLevel.PREMIUM);
    expected.setUsage(Usage.EMPTY);
    expected.setHasUnlimitedUsage(false);
    expected.setMetered(false);

    // When getting the upstream Offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, values, and no engProdIds.
    assertEquals(expected, actual);
  }

  /** Valid given MW00330 sku with core value set to 16, standard core value is 16 */
  @Test
  void testOfferingFromUpstreamOpenShiftStandardCores() {
    // Given an Openshift SKU that exists upstream,
    var sku = "MW00330";

    // When given the result of a standard,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then cores equals 16
    assertEquals(16, actual.getCores());
  }

  @Test
  void testOfferingFromUpstreamForOfferingWithDerivedSku() {
    // Given a marketing SKU that has a derived SKU,
    var sku = "RH00604F5";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setDerivedSku("RH00618F5");
    // (For now, Derived SKU and Derived SKU children are included as child SKUs.)
    expected.setChildSkus(Set.of("SVCRH00604"));
    // (Neither the parent (as typical) nor the child SKU have eng products. These end up
    //  coming from the derived SKU RH00048.)
    expected.setProductIds(
        Set.of(
            69, 70, 83, 84, 86, 91, 92, 93, 127, 176, 180, 182, 201, 205, 240, 241, 246, 248, 317,
            318, 394, 395, 408, 479, 491, 588));
    // (because there is a derived sku, no standard capacity should be set, only hypervisor
    // capacity. See https://issues.redhat.com/browse/ENT-4301?focusedCommentId=19210665 for
    // details)
    expected.setHypervisorSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName("RHEL for SAP HANA");
    expected.setDescription(
        "Red Hat Enterprise Linux Server for SAP HANA for Virtual "
            + "Datacenters with Smart Management, Premium");
    expected.setServiceLevel(ServiceLevel.PREMIUM);
    // (Usage ends up coming from derived SKU RH00618F5)
    expected.setUsage(Usage.PRODUCTION);
    expected.setHasUnlimitedUsage(false);
    expected.setMetered(false);

    // When getting the upstream Offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then the resulting Offering has the expected hypervisor sockets from derived sku,
    // and engOIDs from the derived sku child.
    assertEquals(expected, actual);
  }

  @Test
  void testOfferingFromUpstreamForOfferingWithRoleAndUsage() {
    // This checks that role and usage are calculated correctly.

    // Given a marketing SKU that has a defined role and usage,
    var sku = "RH0180191";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Set.of("SVCMPV4", "SVCRH01", "SVCRH01V4"));
    expected.setProductIds(
        Set.of(
            69, 70, 84, 86, 91, 92, 93, 94, 127, 133, 176, 180, 182, 201, 205, 240, 246, 271, 272,
            273, 274, 317, 318, 394, 395, 408, 479, 491, 588, 605));
    expected.setRole("Red Hat Enterprise Linux Server");
    expected.setSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName("RHEL Server");
    expected.setDescription(
        "Red Hat Enterprise Linux Server, Standard (1-2 sockets) "
            + "(Up to 4 guests) with Smart Management");
    expected.setServiceLevel(ServiceLevel.STANDARD);
    expected.setUsage(Usage.PRODUCTION);
    expected.setHasUnlimitedUsage(false);
    expected.setMetered(true);

    // When getting the upstream Offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, values, and engProdIds.
    assertEquals(expected, actual);
  }

  @Test
  void testOfferingFromUpstreamWithIflAttrCode() {
    // Given a marketing SKU wiht attribute code "IFL" in its tree (in this case, in SVCMPV4)
    var sku = "RH3413336";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(
        Set.of("SVCEUSRH34", "SVCHPNRH34", "SVCMPV4", "SVCRH34", "SVCRH34V4", "SVCRS", "SVCSFS"));
    expected.setProductIds(
        Set.of(
            68, 69, 70, 71, 83, 84, 85, 86, 90, 91, 92, 93, 132, 133, 172, 176, 179, 180, 190, 201,
            202, 203, 205, 206, 207, 240, 242, 244, 246, 273, 274, 287, 293, 317, 318, 342, 343,
            394, 395, 396, 397, 408, 479, 491, 588));
    expected.setCores(4); // Because IFL is 1 which gets multiplied by magical constant 4
    expected.setSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName("RHEL Developer Workstation");
    expected.setDescription("Red Hat Enterprise Linux Developer Workstation, Enterprise");
    expected.setServiceLevel(ServiceLevel.EMPTY); // Because Dev-Enterprise isn't a ServiceLevel yet
    expected.setUsage(Usage.DEVELOPMENT_TEST);
    expected.setHasUnlimitedUsage(false);
    expected.setMetered(false);

    // When getting the upstream Offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, engProd OIDs, and values.
    assertEquals(expected, actual);
  }

  @Test
  void testOfferingFromUpstreamNotFound() {
    // Given a marketing SKU that doesn't exist upstream,
    var sku = "BOGUS";

    // When attempting to get the upstream Offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub);

    // Then there is no resulting offering.
    assertTrue(actual.isEmpty(), "When a sku doesn't exist upstream, return an empty Optional.");
  }

  @Test
  void testOfferingFromUpstreamOpenShiftUnlimitedCores() {
    // Given an Openshift SKU that has unlimited cores,
    var sku = "MW00210MO";

    // When getting the upstream offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then hasUnlimitedUsage should be true
    assertTrue(actual.isHasUnlimitedUsage());
  }

  @Test
  void testOfferingFromUpstreamOpenShiftUnlimitedSocketLimit() {
    // Given an Openshift SKU that has unlimited cores,
    var sku = "MW00210M1";

    // When getting the upstream offering,
    var actual = UpstreamProductData.offeringFromUpstream(sku, stub).orElseThrow();

    // Then hasUnlimitedUsage should be true
    assertTrue(actual.isHasUnlimitedUsage());
  }

  @Test
  void testOfferingFromUmbMessage() {
    var mockDataSource = mock(ProductDataSource.class);
    // we set only the fields which must match to avoid RHIT product service lookup
    Offering existingData =
        Offering.builder().sku("foo").childSkus(Set.of("child")).derivedSku("derived").build();
    var actual =
        UpstreamProductData.offeringFromUmbData(
                UmbOperationalProduct.builder()
                    .role("role")
                    .sku("foo")
                    .skuDescription("desc")
                    .productRelationship(
                        ProductRelationship.builder()
                            .childProducts(
                                new ChildProduct[] {ChildProduct.builder().sku("child").build()})
                            .build())
                    .attributes(
                        new ProductAttribute[] {
                          ProductAttribute.builder().code("PRODUCT_FAMILY").value("family").build(),
                          ProductAttribute.builder().code("PRODUCT_NAME").value("name").build(),
                          ProductAttribute.builder().code("DERIVED_SKU").value("derived").build(),
                          ProductAttribute.builder().code("SOCKET_LIMIT").value("1").build(),
                          ProductAttribute.builder().code("CORES").value("2").build(),
                          ProductAttribute.builder()
                              .code("SERVICE_TYPE")
                              .value(ServiceLevel.PREMIUM.getValue())
                              .build(),
                          ProductAttribute.builder()
                              .code("USAGE")
                              .value(Usage.PRODUCTION.getValue())
                              .build(),
                        })
                    .build(),
                existingData,
                mockDataSource)
            .orElseThrow();
    // these are the only fields that can be updated by UMB messages without consulting the RHIT
    // product service. (Changes to child SKUs or derived SKU need to consult RHIT product
    // service).
    assertEquals("role", actual.getRole());
    assertEquals("desc", actual.getDescription());
    assertEquals("family", actual.getProductFamily());
    assertEquals("name", actual.getProductName());
    assertEquals(1, actual.getHypervisorSockets());
    assertEquals(2, actual.getHypervisorCores());
    assertEquals(ServiceLevel.PREMIUM, actual.getServiceLevel());
    assertEquals(Usage.PRODUCTION, actual.getUsage());
    verifyNoInteractions(mockDataSource);
  }
}
