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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.config.Channels.OFFERING_SYNC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.clients.product.api.model.EngineeringProductMap;
import com.redhat.swatch.clients.product.api.model.OperationalProduct;
import com.redhat.swatch.clients.product.api.model.RESTProductTree;
import com.redhat.swatch.clients.product.api.model.SkuEngProduct;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.model.OfferingSyncTask;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.ServiceLevel;
import com.redhat.swatch.contract.repository.Usage;
import com.redhat.swatch.contract.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.contract.test.resources.ProductUseStubService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
@TestProfile(ProductUseStubService.class)
class OfferingSyncServiceTest {

  @InjectMock OfferingRepository repo;
  @InjectMock ProductDenylist denylist;
  @InjectMock CapacityReconciliationService capController;
  @InjectMock OfferingProductTagLookupService offeringProductTagLookupService;
  @Inject @Any InMemoryConnector connector;
  @Inject OfferingSyncService subject;

  InMemorySink<OfferingSyncTask> offeringSyncTaskSink;

  @BeforeEach
  void init() {
    offeringSyncTaskSink = connector.sink(OFFERING_SYNC);
    offeringSyncTaskSink.clear();

    when(denylist.productIdMatches(anyString())).thenReturn(false);
  }

  @Test
  void testSyncOfferingNew() {
    // Given an Offering that is not yet persisted,
    when(repo.findByIdOptional(anyString())).thenReturn(Optional.empty());
    String sku = "MW01485";

    // When syncing the Offering,
    SyncResult result = subject.syncOffering(sku);

    // Then the Offering should be persisted and capacities reconciled.
    assertEquals(SyncResult.FETCHED_AND_SYNCED, result);
    verify(repo).saveOrUpdate(any(OfferingEntity.class));
    verify(capController).enqueueReconcileCapacityForOffering("MW01485");
  }

  @Test
  void testSyncOfferingChanged() {
    // Given an Offering that is different from what is persisted,
    String sku = "MW01485";
    OfferingEntity persisted = new OfferingEntity();
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
    verify(repo).saveOrUpdate(any(OfferingEntity.class));
    verify(capController).enqueueReconcileCapacityForOffering("MW01485");
  }

  @Test
  void testSyncOfferingUnchanged() {
    // Given an Offering that is equal to what is persisted,
    String sku = "MW01485";
    OfferingEntity persisted = new OfferingEntity();
    persisted.setSku(sku);
    persisted.setProductName("OpenShift Container Platform");
    persisted.setDescription("Red Hat OpenShift Container Platform (Hourly)");
    persisted.setProductFamily("OpenShift Enterprise");
    persisted.setChildSkus(Set.of("SVCMW01485"));
    persisted.setProductIds(
        Set.of(
            491, 311, 194, 197, 317, 318, 201, 205, 326, 329, 271, 518, 579, 519, 458, 645, 588,
            408, 290, 473, 479, 240, 603, 604, 185, 546, 608, 69, 70, 610));
    persisted.setServiceLevel(ServiceLevel.PREMIUM);
    persisted.setUsage(Usage.EMPTY);
    persisted.setHasUnlimitedUsage(false);
    persisted.setMetered(false);

    when(repo.findByIdOptional(anyString())).thenReturn(Optional.of(persisted));

    // When syncing the Offering,
    SyncResult result = subject.syncOffering(sku);

    // Then no persisting or capacity reconciliation should happen.
    assertEquals(SyncResult.SKIPPED_MATCHING, result);
    verify(repo, never()).saveOrUpdate(any(OfferingEntity.class));
    verifyNoInteractions(capController);
  }

  @Test
  void testSyncOfferingNoProductIdsShouldPersist() {
    // Given an Offering that has no engineering product ids,
    OfferingEntity offering = new OfferingEntity();
    offering.setSku("MW01484"); // This is an actual Offering that has no engineering product ids

    when(repo.findByIdOptional(anyString())).thenReturn(Optional.empty());

    // When syncing the Offering,
    SyncResult result = subject.syncOffering("MW01484");

    // Then it should still persist, since there are Offerings that we need that have no eng prods,
    assertEquals(SyncResult.FETCHED_AND_SYNCED, result);
    verify(repo).saveOrUpdate(any(OfferingEntity.class));
    // and it should still reconcile capacities.
    verify(capController).enqueueReconcileCapacityForOffering("MW01484");
  }

  @Test
  void testSyncOfferingInDenylist() {
    // Given a marketing SKU listed in denylist,
    when(denylist.productIdMatches(anyString())).thenReturn(true);
    var sku = "MW01485"; // The SKU would normally be successfully retrieved, but is denied

    // Then syncing the offering is rejected, no attempt was made to fetch or store it, and no
    // capacities are reconciled.
    ForbiddenException result =
        assertThrows(ForbiddenException.class, () -> subject.syncOffering(sku));
    assertEquals(SyncResult.SKIPPED_DENYLISTED.description(), result.getMessage());

    verify(denylist).productIdMatches(sku);
    verifyNoInteractions(repo, capController);
  }

  @Test
  void testSyncAllOfferings() {
    // Given the non denylist has a list of SKUs,
    when(repo.findAllDistinctSkus()).thenReturn(Set.of("RH00604F5", "RH0180191"));

    // When a request is made to sync all offerings,
    int numEnqueued = subject.syncAllOfferings();

    // Then the SKUs are enqueud to sync.
    assertEquals(
        2, numEnqueued, "Number of enqueued offerings should match distinct skus in repo.");
    assertEquals(2, offeringSyncTaskSink.received().size());
  }

  @Test
  void testSyncAllOfferingsEmptyWithDenyList() {
    // Given the denylist has no source (that is, no denylist is provided),
    when(denylist.allProducts()).thenReturn(Collections.emptySet());

    // When a request is made to sync all offerings,
    int numEnqueued = subject.syncAllOfferings();

    // Then SKUs are synced.
    assertEquals(0, numEnqueued, "Everything should be synced when no denylist exists.");
    assertEquals(0, offeringSyncTaskSink.received().size());
  }

  @Test
  void testSaveOffering() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    RESTProductTree product =
        new RESTProductTree().addProductsItem(new OperationalProduct().sku("sku").roles(List.of()));
    RESTProductTree[] offerings = new RESTProductTree[] {product};
    EngineeringProductMap engProductMap =
        new EngineeringProductMap().addEntriesItem(new SkuEngProduct().sku("sku"));
    EngineeringProductMap[] engProds = new EngineeringProductMap[] {engProductMap};
    String offeringsJson = objectMapper.writeValueAsString(offerings);
    String engProdJson = objectMapper.writeValueAsString(engProds);
    subject.saveOfferings(offeringsJson, offeringsJson, engProdJson);
    verify(repo).persist(any(OfferingEntity.class));
  }

  private static String read(String testFilename) throws IOException {
    try (InputStream stream =
        OfferingSyncServiceTest.class.getClassLoader().getResourceAsStream(testFilename)) {
      if (stream == null) {
        throw new IOException(testFilename + " not found");
      }
      return new String(stream.readAllBytes());
    }
  }

  @Test
  void testSyncNewOfferingFromUmbMessage() throws IOException {
    when(repo.findByIdOptional(any())).thenReturn(Optional.empty());
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(OfferingEntity.class);
    verify(repo).saveOrUpdate(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
    assertTrue(actual.getValue().isMetered());
    assertFalse(actual.getValue().isMigrationOffering());
  }

  @Test
  void testDoesNotSyncNewOfferingIfAbsentFromDataSource() throws IOException {
    when(repo.findByIdOptional(any())).thenReturn(Optional.empty());
    String readString = read("mocked-product-message.xml").replace("RH0180191", "does-not-exist");
    NotFoundException result =
        assertThrows(NotFoundException.class, () -> subject.syncUmbProductFromXml(readString));
    assertEquals(SyncResult.SKIPPED_NOT_FOUND.description(), result.getMessage());
  }

  @Test
  void testSyncOfferingWithNoChangesFromUmbMessage() throws IOException {
    when(repo.findByIdOptional(any())).thenReturn(Optional.of(createTestOffering()));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    verify(repo, times(1)).findByIdOptional(any());
    verifyNoMoreInteractions(repo);
  }

  @Test
  void testSyncOfferingWithModifiedDerivedSkuFromUmbMessage() throws IOException {
    OfferingEntity testOffering = createTestOffering();
    testOffering.setDerivedSku("new");
    when(repo.findByIdOptional(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(OfferingEntity.class);
    verify(repo).saveOrUpdate(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
  }

  @Test
  void testSyncOfferingWithModifiedChildSkuFromUmbMessage() throws IOException {
    OfferingEntity testOffering = createTestOffering();
    testOffering.setChildSkus(Set.of("stale"));
    when(repo.findByIdOptional(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(OfferingEntity.class);
    verify(repo).saveOrUpdate(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
  }

  @Test
  void testSyncOfferingWithExistingAttributeMissingFromUmbMessage() throws IOException {
    OfferingEntity testOffering = createTestOffering();
    when(repo.findByIdOptional(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(
        read("mocked-product-message.xml").replace("PRODUCT_NAME", "PLACEHOLDER"));
    var actual = ArgumentCaptor.forClass(OfferingEntity.class);
    verify(repo).saveOrUpdate(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
  }

  @Test
  void testSyncOfferingWithChangedProductNameFromUmbMessage() throws IOException {
    OfferingEntity testOffering = createTestOffering();
    testOffering.setProductName("stale");
    when(repo.findByIdOptional(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(OfferingEntity.class);
    verify(repo).saveOrUpdate(actual.capture());
    // this shows that the eng ids were not derived from the product service's definition of the SVC
    // sku
    assertTrue(actual.getValue().getProductIds().isEmpty());
    assertEquals("RHEL Server", actual.getValue().getProductName());
  }

  @Test
  void testQueuesSyncForMatchingOfferingsWhenServiceSkuUmbMessageSynced() throws IOException {
    when(repo.findSkusForChildSku(any())).thenReturn(Stream.of("SKU1", "SKU2"));
    subject.syncUmbProductFromXml(read("mocked-svc-product-message.xml"));
    assertEquals(2, offeringSyncTaskSink.received().size());
  }

  @Test
  void testQueuesSyncForRelatedProductWhenDerivedProductChildSkuUmbMessageSynced()
      throws IOException {
    when(repo.findSkusForDerivedSkus(any())).thenReturn(Stream.of("SKU1", "SKU2"));
    subject.syncUmbProductFromXml(read("mocked-svc-product-message.xml"));
    assertEquals(2, offeringSyncTaskSink.received().size());
  }

  @Test
  void testSyncOfferingRetriesFetchIfFailsToInsert() {
    // Given an Offering that is not yet persisted,
    when(repo.findByIdOptional(anyString()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(createStubProductApiOffering()));
    Mockito.doThrow(new PersistenceException()).when(repo).saveOrUpdate(any());
    String sku = "MW01485";

    // When syncing the Offering
    var result = subject.syncOffering(sku);

    // Then the Offering should already be synced
    assertEquals(SyncResult.SKIPPED_MATCHING, result);
  }

  @Test
  void testSyncOfferingThrowsExceptionIfFailsToInsertAndDoesNotMatchNew() {
    // Given an Offering that is not yet persisted,
    when(repo.findByIdOptional(anyString()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(new OfferingEntity()));
    Mockito.doThrow(new PersistenceException()).when(repo).saveOrUpdate(any());
    String sku = "MW01485";

    // When syncing the Offering an exception is thrown
    assertThrows(PersistenceException.class, () -> subject.syncOffering(sku));
  }

  @Test
  void testSpecialPricingFlagIsPopulated() {
    var sku = "RH02781MO"; // The SKU is mocked to set the special pricing flag.
    when(repo.findByIdOptional(sku)).thenReturn(Optional.empty());
    // When getting the upstream Offering,
    var actual = subject.syncOffering(sku);
    assertEquals(SyncResult.FETCHED_AND_SYNCED, actual);
    verify(repo).saveOrUpdate(argThat(o -> o.getSku().equals(sku) && o.isMigrationOffering()));
  }

  private OfferingEntity createStubProductApiOffering() {
    var expectedOffering = new OfferingEntity();
    expectedOffering.setSku("MW01485");
    expectedOffering.setProductName("OpenShift Container Platform");
    expectedOffering.setDescription("Red Hat OpenShift Container Platform (Hourly)");
    expectedOffering.setProductFamily("OpenShift Enterprise");
    expectedOffering.setChildSkus(Set.of("SVCMW01485"));
    expectedOffering.setProductIds(
        Set.of(
            458, 519, 579, 518, 271, 329, 326, 205, 201, 318, 317, 197, 194, 311, 491, 610, 70, 69,
            608, 546, 185, 604, 603, 240, 479, 473, 290, 408, 588, 645));
    expectedOffering.setServiceLevel(ServiceLevel.PREMIUM);
    expectedOffering.setHasUnlimitedUsage(false);
    expectedOffering.setUsage(Usage.EMPTY);
    expectedOffering.setMetered(false);
    return expectedOffering;
  }

  private static OfferingEntity createTestOffering() {
    return OfferingEntity.builder()
        .sku("RH0180191")
        .productName("RHEL Server")
        .description(
            "Red Hat Enterprise Linux Server, Standard (1-2 sockets) (Up to 4 guests) with Smart Management")
        .productFamily("Red Hat Enterprise Linux")
        .childSkus(Set.of("SVCRH01V4", "SVCMPV4", "SVCRH01"))
        .role("Red Hat Enterprise Linux Server")
        .serviceLevel(ServiceLevel.STANDARD)
        .usage(Usage.PRODUCTION)
        .sockets(2)
        .hasUnlimitedUsage(false)
        .metered(true)
        .build();
  }
}
