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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
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
  @MockBean CapacityReconciliationController capController;
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
    String sku = "MW01485";

    // When syncing the Offering,
    SyncResult result = subject.syncOffering(sku);

    // Then the Offering should be persisted and capacities reconciled.
    assertEquals(SyncResult.FETCHED_AND_SYNCED, result);
    verify(repo).saveAndFlush(any(Offering.class));
    verify(capController).enqueueReconcileCapacityForOffering("MW01485");
  }

  @Test
  void testSyncOfferingChanged() {
    // Given an Offering that is different from what is persisted,
    String sku = "MW01485";
    Offering persisted = new Offering();
    persisted.setSku(sku);
    persisted.setProductName("Red Hat OpenShift Container Platform (Hourly)");
    persisted.setProductFamily("OpenShift Enterprise");
    persisted.setChildSkus(Set.of("SVCMW01485"));
    persisted.setProductIds(
        Set.of(
            491, 311, 194, 197, 317, 318, 201, 205, 326, 329, 271, 518, 579, 519, 458, 645, 588,
            408, 290, 473, 479, 240, 603, 604, 185, 546, 608, 69, 70, 610));
    persisted.setServiceLevel(ServiceLevel.PREMIUM);
    persisted.setUsage(Usage.EMPTY);

    // When syncing the Offering,
    SyncResult result = subject.syncOffering(sku);

    // Then the Offering should be persisted and capacities reconciled.
    assertEquals(SyncResult.FETCHED_AND_SYNCED, result);
    verify(repo).saveAndFlush(any(Offering.class));
    verify(capController).enqueueReconcileCapacityForOffering("MW01485");
  }

  @Test
  void testSyncOfferingUnchanged() {
    // Given an Offering that is equal to what is persisted,
    String sku = "MW01485";
    Offering persisted = new Offering();
    persisted.setSku(sku);
    persisted.setProductName("Red Hat OpenShift Container Platform (Hourly)");
    persisted.setProductFamily("OpenShift Enterprise");
    persisted.setChildSkus(Set.of("SVCMW01485"));
    persisted.setProductIds(
        Set.of(
            491, 311, 194, 197, 317, 318, 201, 205, 326, 329, 271, 518, 579, 519, 458, 645, 588,
            408, 290, 473, 479, 240, 603, 604, 185, 546, 608, 69, 70, 610));
    persisted.setServiceLevel(ServiceLevel.PREMIUM);
    persisted.setUsage(Usage.EMPTY);

    when(repo.findById(anyString())).thenReturn(Optional.of(persisted));

    // When syncing the Offering,
    SyncResult result = subject.syncOffering(sku);

    // Then no persisting or capacity reconciliation should happen.
    assertEquals(SyncResult.SKIPPED_MATCHING, result);
    verify(repo, never()).saveAndFlush(any(Offering.class));
    verifyNoInteractions(capController);
  }

  @Test
  void testSyncOfferingNoProductIdsShouldPersist() {
    // Given an Offering that has no engineering product ids,
    Offering offering = new Offering();
    offering.setSku("MW01484"); // This is an actual Offering that has no engineering product ids

    when(repo.findById(anyString())).thenReturn(Optional.empty());

    // When syncing the Offering,
    SyncResult result = subject.syncOffering("MW01484");

    // Then it should still persist, since there are Offerings that we need that have no eng prods,
    assertEquals(SyncResult.FETCHED_AND_SYNCED, result);
    verify(repo).saveAndFlush(any(Offering.class));
    // and it should still reconcile capacities.
    verify(capController).enqueueReconcileCapacityForOffering("MW01484");
  }

  @Test
  void testSyncOfferingNotInAllowlist() {
    // Given a marketing SKU not listed in allowlist,
    when(allowlist.productIdMatches(anyString())).thenReturn(false);
    var sku = "MW01485"; // The SKU would normally be successfully retrieved, but is denied

    // When getting the upstream Offering,
    var actual = subject.syncOffering(sku);

    // Then syncing the offering is rejected, no attempt was made to fetch or store it, and no
    // capacities are reconciled.
    assertEquals(
        SyncResult.SKIPPED_NOT_ALLOWLISTED,
        actual,
        "A sku not in the allowlist should not be synced.");
    verify(allowlist).productIdMatches(sku);
    verifyNoInteractions(repo, capController);
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
