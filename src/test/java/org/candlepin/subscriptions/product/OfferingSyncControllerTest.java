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
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.product.api.model.EngineeringProductMap;
import org.candlepin.subscriptions.product.api.model.OperationalProduct;
import org.candlepin.subscriptions.product.api.model.RESTProductTree;
import org.candlepin.subscriptions.product.api.model.SkuEngProduct;
import org.candlepin.subscriptions.util.OfferingProductTagLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {OfferingSyncControllerTest.TestProductConfiguration.class})
@ActiveProfiles({"worker", "test"})
class OfferingSyncControllerTest {
  public static Offering createTestOffering() {
    return Offering.builder()
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
  @MockBean ProductDenylist denylist;
  @MockBean CapacityReconciliationController capController;
  @MockBean KafkaTemplate<String, OfferingSyncTask> offeringSyncKafkaTemplate;
  @Autowired OfferingSyncController subject;
  @MockBean OfferingProductTagLookupService offeringProductTagLookupService;

  @BeforeEach
  void init() {
    when(denylist.productIdMatches(anyString())).thenReturn(false);
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
    verify(offeringSyncKafkaTemplate, times(2)).send(anyString(), any(OfferingSyncTask.class));
  }

  @Test
  void testSyncAllOfferingsEmptyWithDenyList() {
    // Given the denylist has no source (that is, no denylist is provided),
    when(denylist.allProducts()).thenReturn(Collections.emptySet());

    // When a request is made to sync all offerings,
    int numEnqueued = subject.syncAllOfferings();

    // Then SKUs are synced.
    assertEquals(0, numEnqueued, "Everything should be synced when no denylist exists.");
    verify(offeringSyncKafkaTemplate, never()).send(anyString(), any(OfferingSyncTask.class));
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
    verify(repo).save(any());
  }

  private static String read(String testFilename) throws IOException {
    try (InputStream stream =
        OfferingSyncControllerTest.class.getClassLoader().getResourceAsStream(testFilename)) {
      if (stream == null) {
        throw new IOException(testFilename + " not found");
      }
      return new String(stream.readAllBytes());
    }
  }

  @Test
  void testSyncNewOfferingFromUmbMessage() throws IOException {
    when(repo.findById(any())).thenReturn(Optional.empty());
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(Offering.class);
    verify(repo).saveAndFlush(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
    assertTrue(actual.getValue().isMetered());
    assertFalse(actual.getValue().isMigrationOffering());
  }

  @Test
  void testDoesNotSyncNewOfferingIfAbsentFromDataSource() throws IOException {
    when(repo.findById(any())).thenReturn(Optional.empty());
    String readString = read("mocked-product-message.xml").replace("RH0180191", "does-not-exist");
    NotFoundException result =
        assertThrows(NotFoundException.class, () -> subject.syncUmbProductFromXml(readString));
    assertEquals(SyncResult.SKIPPED_NOT_FOUND.description(), result.getMessage());
  }

  @Test
  void testSyncOfferingWithNoChangesFromUmbMessage() throws IOException {
    when(repo.findById(any())).thenReturn(Optional.of(createTestOffering()));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    verify(repo, times(1)).findById(any());
    verifyNoMoreInteractions(repo);
  }

  @Test
  void testSyncOfferingWithModifiedDerivedSkuFromUmbMessage() throws IOException {
    Offering testOffering = createTestOffering();
    testOffering.setDerivedSku("new");
    when(repo.findById(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(Offering.class);
    verify(repo).saveAndFlush(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
  }

  @Test
  void testSyncOfferingWithModifiedChildSkuFromUmbMessage() throws IOException {
    Offering testOffering = createTestOffering();
    testOffering.setChildSkus(Set.of("stale"));
    when(repo.findById(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(Offering.class);
    verify(repo).saveAndFlush(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
  }

  @Test
  void testSyncOfferingWithExistingAttributeMissingFromUmbMessage() throws IOException {
    Offering testOffering = createTestOffering();
    when(repo.findById(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(
        read("mocked-product-message.xml").replace("PRODUCT_NAME", "PLACEHOLDER"));
    var actual = ArgumentCaptor.forClass(Offering.class);
    verify(repo).saveAndFlush(actual.capture());
    // this shows that the eng ids were derived from the product service's definition of the SVC sku
    assertEquals(30, actual.getValue().getProductIds().size());
  }

  @Test
  void testSyncOfferingWithChangedProductNameFromUmbMessage() throws IOException {
    Offering testOffering = createTestOffering();
    testOffering.setProductName("stale");
    when(repo.findById(any())).thenReturn(Optional.of(testOffering));
    subject.syncUmbProductFromXml(read("mocked-product-message.xml"));
    var actual = ArgumentCaptor.forClass(Offering.class);
    verify(repo).saveAndFlush(actual.capture());
    // this shows that the eng ids were not derived from the product service's definition of the SVC
    // sku
    assertTrue(actual.getValue().getProductIds().isEmpty());
    assertEquals("RHEL Server", actual.getValue().getProductName());
  }

  @Test
  void testQueuesSyncForMatchingOfferingsWhenServiceSkuUmbMessageSynced() throws IOException {
    when(repo.findSkusForChildSku(any())).thenReturn(Stream.of("SKU1", "SKU2"));
    subject.syncUmbProductFromXml(read("mocked-svc-product-message.xml"));
    verify(offeringSyncKafkaTemplate, times(2)).send(anyString(), any(OfferingSyncTask.class));
  }

  @Test
  void testQueuesSyncForRelatedProductWhenDerivedProductChildSkuUmbMessageSynced()
      throws IOException {
    when(repo.findSkusForDerivedSkus(any())).thenReturn(Stream.of("SKU1", "SKU2"));
    subject.syncUmbProductFromXml(read("mocked-svc-product-message.xml"));
    verify(offeringSyncKafkaTemplate, times(2)).send(anyString(), any(OfferingSyncTask.class));
  }

  @Test
  void testSyncOfferingRetriesFetchIfFailsToInsert() {
    // Given an Offering that is not yet persisted,
    when(repo.findById(anyString()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(createStubProductApiOffering()));
    when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("test"));
    String sku = "MW01485";

    // When syncing the Offering
    var result = subject.syncOffering(sku);

    // Then the Offering should already be synced
    assertEquals(SyncResult.SKIPPED_MATCHING, result);
  }

  @Test
  void testSyncOfferingThrowsExceptionIfFailsToInsertAndDoesNotMatchNew() {
    // Given an Offering that is not yet persisted,
    when(repo.findById(anyString()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(new Offering()));
    when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("test"));
    String sku = "MW01485";

    // When syncing the Offering an exception is thrown
    assertThrows(DataIntegrityViolationException.class, () -> subject.syncOffering(sku));
  }

  @Test
  void testSpecialPricingFlagIsPopulated() {
    var sku = "RH02781MO"; // The SKU is mocked to set the special pricing flag.
    when(repo.findById(sku)).thenReturn(Optional.empty());
    // When getting the upstream Offering,
    var actual = subject.syncOffering(sku);
    assertEquals(SyncResult.FETCHED_AND_SYNCED, actual);
    verify(repo).saveAndFlush(argThat(o -> o.getSku().equals(sku) && o.isMigrationOffering()));
  }

  private Offering createStubProductApiOffering() {
    var expectedOffering = new Offering();
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
}
