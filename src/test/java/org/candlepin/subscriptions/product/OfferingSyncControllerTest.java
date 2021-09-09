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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {OfferingSyncControllerTest.TestProductConfiguration.class})
@ActiveProfiles({"worker", "test"})
class OfferingSyncControllerTest {

  @TestConfiguration
  static class TestProductConfiguration {
    @Bean
    @Qualifier("product")
    @Primary
    public HttpClientProperties productServiceTestProperties() {
      HttpClientProperties props = new HttpClientProperties();
      props.setUseStub(true);
      return props;
    }

    @Bean
    @Primary
    public ProductApiFactory productApiTestFactory(
        @Qualifier("product") HttpClientProperties props) {
      return new ProductApiFactory(props);
    }
  }

  @MockBean OfferingRepository repo;
  @MockBean ProductWhitelist allowlist;
  @MockBean KafkaTemplate<String, OfferingSyncTask> offeringSyncKafkaTemplate;
  @Autowired OfferingSyncController subject;

  @BeforeEach
  void init() {
    when(allowlist.productIdMatches(anyString())).thenReturn(true);
  }

  @Test
  void testSyncOfferingNew() {
    // Given an Offering that is not yet persisted,
    when(repo.findById(anyString())).thenReturn(Optional.empty());

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Set.of(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then the Offering should be persisted.
    verify(repo).save(sku);
  }

  @Test
  void testSyncOfferingChanged() {
    // Given an Offering that is different from what is persisted,
    Offering persisted = new Offering();
    persisted.setSku("RH00003");
    persisted.setProductIds(Set.of(68));
    when(repo.findById(anyString())).thenReturn(Optional.of(persisted));

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Set.of(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then the updated Offering should be persisted.
    verify(repo).save(sku);
  }

  @Test
  void testSyncOfferingUnchanged() {
    // Given an Offering that is equal to what is persisted,
    Offering persisted = new Offering();
    persisted.setSku("RH00003");
    persisted.setProductIds(Set.of(68, 69, 70, 71, 72));
    when(repo.findById(anyString())).thenReturn(Optional.of(persisted));

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Set.of(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then no persisting should happen.
    verify(repo, never()).save(sku);
  }

  @Test
  void testSyncOfferingNoProductIdsShouldPersist() {
    // Given an Offering that has no engineering product ids,
    Offering sku = new Offering();
    sku.setSku("MW01484"); // This is an actual Offering that has no engineering product ids

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then it should still persist, since there are Offerings that we need that have no eng prods.
    verify(repo).save(sku);
  }

  @Test
  void testGetUpstreamOfferingForOcpOffering() {
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
    expected.setProductName("Red Hat OpenShift Container Platform (Hourly)");
    expected.setServiceLevel(ServiceLevel.PREMIUM);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, engProd OIDs, and values.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForNoEngProductOffering() {
    // Given a marketing SKU MW01484 (special for being engProduct-less),
    var sku = "MW01484";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Set.of("SVCMW01484A", "SVCMW01484B"));
    expected.setProductIds(Collections.emptySet());
    expected.setProductFamily("OpenShift Enterprise");
    expected.setProductName("Red Hat OpenShift Dedicated on Customer Cloud Subscription (Hourly)");
    expected.setServiceLevel(ServiceLevel.PREMIUM);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, values, and no engProdIds.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForOfferingWithDerivedSku() {
    // Given a marketing SKU that has a derived SKU,
    var sku = "RH00604F5";
    var expected = new Offering();
    expected.setSku(sku);
    // (For now, Derived SKU and Derived SKU children are included as child SKUs.)
    expected.setChildSkus(Set.of("RH00618F5", "SVCRH00604", "SVCRH00618"));
    // (Neither the parent (as typical) nor the child SKU have eng products. These end up
    //  coming from the derived SKU RH00048.)
    expected.setProductIds(
        Set.of(
            69, 70, 83, 84, 86, 91, 92, 93, 127, 176, 180, 182, 201, 205, 240, 241, 246, 248, 317,
            318, 394, 395, 408, 479, 491, 588));
    expected.setPhysicalSockets(2);
    expected.setVirtualSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName(
        "Red Hat Enterprise Linux Server for SAP HANA for Virtual Datacenters with Smart Management, Premium");
    expected.setServiceLevel(ServiceLevel.PREMIUM);
    // (Usage ends up coming from derived SKU RH00618F5)
    expected.setUsage(Usage.PRODUCTION);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected virtual sockets from derived sku,
    // and engOIDs from the derived sku child.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForOfferingWithRoleAndUsage() {
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
    expected.setPhysicalSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName(
        "Red Hat Enterprise Linux Server, Standard (1-2 sockets) (Up to 4 guests) with Smart Management");
    expected.setServiceLevel(ServiceLevel.STANDARD);
    expected.setUsage(Usage.PRODUCTION);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, values, and engProdIds.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingWithIflAttrCode() {
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
    expected.setPhysicalCores(4); // Because IFL is 1 which gets multiplied by magical constant 4
    expected.setPhysicalSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName("Red Hat Enterprise Linux Developer Workstation, Enterprise");
    expected.setServiceLevel(ServiceLevel.EMPTY); // Because Dev-Enterprise isn't a ServiceLevel yet
    expected.setUsage(Usage.DEVELOPMENT_TEST);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, engProd OIDs, and values.
    assertEquals(expected, actual);
  }

  @Test()
  void testGetUpstreamOfferingNotInAllowlist() {
    // Given a marketing SKU not listed in allowlist,
    when(allowlist.productIdMatches(anyString())).thenReturn(false);
    var sku = "MW01485"; // The SKU would normally be successfully retrieved, but is denied

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku);

    // Then there is no resulting offering.
    assertTrue(actual.isEmpty(), "A sku not in the allowlist should not be returned.");
    verify(allowlist).productIdMatches(sku);
  }

  @Test
  void testGetUpstreamOfferingNotFound() {
    // Given a marketing SKU that doesn't exist upstream,
    var sku = "BOGUS";

    // When attempting to get the upstream Offering,
    var actual = subject.getUpstreamOffering(sku);

    // Then there is no resulting offering.
    assertTrue(actual.isEmpty(), "When a sku doesn't exist upstream, return an empty Optional.");
  }

  /** Valid given MW00330 sku with core value set to 16, physical core value is 16 */
  @Test()
  void testOpenShiftUpSteamProductPhysicalCores() {
    // Given an Openshift SKU that does exist upstream,
    var sku = "MW00330";
    // create file for MW00330

    // When given the result of a physical,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then cores equals 16
    assertEquals(16, actual.getPhysicalCores());
  }

  @Test
  void testSyncAllOfferings() {
    // Given the allowlist has a list of SKUs,
    when(allowlist.allProducts()).thenReturn(Set.of("RH00604F5", "RH0180191"));

    // When a request is made to sync all offerings,
    int numEnqueued = subject.syncAllOfferings();

    // Then the SKUs are enqueud to sync.
    assertEquals(
        2, numEnqueued, "Number of enqueued offerings should match what was given by allowlist.");
    verify(offeringSyncKafkaTemplate, times(2)).send(anyString(), any(OfferingSyncTask.class));
  }

  @Test
  void testSyncAllOfferingsEmptyWithAllowList() {
    // Given the allowlist has no source (that is, no allowlist is provided),
    when(allowlist.allProducts()).thenReturn(Collections.emptySet());

    // When a request is made to sync all offerings,
    int numEnqueued = subject.syncAllOfferings();

    // Then no SKUs are synced.
    assertEquals(0, numEnqueued, "Nothing should be synced when no allowlist exists.");
    verify(offeringSyncKafkaTemplate, never()).send(anyString(), any(OfferingSyncTask.class));
  }
}
