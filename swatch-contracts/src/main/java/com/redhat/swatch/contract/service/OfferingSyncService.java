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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redhat.swatch.clients.product.JsonProductDataSource;
import com.redhat.swatch.clients.product.ProductService;
import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.model.OfferingSyncTask;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.product.UpstreamProductData;
import com.redhat.swatch.contract.product.umb.CanonicalMessage;
import com.redhat.swatch.contract.product.umb.UmbOperationalProduct;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Update {@link OfferingEntity}s from product service responses. */
@ApplicationScoped
@Slf4j
public class OfferingSyncService {

  private static final String SYNC_LOG_TEMPLATE =
      "{} for offeringSku=\"{}\" in offeringSyncTimeMillis={}.";

  private final OfferingRepository offeringRepository;
  private final ProductDenylist productDenylist;
  private final ProductService productService;
  private final CapacityReconciliationService capacityReconciliationService;
  private final Timer syncTimer;
  private final Timer enqueueAllTimer;
  private final MutinyEmitter<OfferingSyncTask> offeringSyncTaskEmitter;
  private final ObjectMapper objectMapper;
  private final XmlMapper umbMessageMapper;
  private final OfferingProductTagLookupService offeringProductTagLookupService;

  @Inject
  public OfferingSyncService(
      OfferingRepository offeringRepository,
      ProductDenylist productDenylist,
      ProductService productService,
      CapacityReconciliationService capacityReconciliationService,
      MeterRegistry meterRegistry,
      @Channel(Channels.OFFERING_SYNC) MutinyEmitter<OfferingSyncTask> offeringSyncTaskEmitter,
      ObjectMapper objectMapper,
      OfferingProductTagLookupService offeringProductTagLookupService) {
    this.offeringRepository = offeringRepository;
    this.productDenylist = productDenylist;
    this.productService = productService;
    this.capacityReconciliationService = capacityReconciliationService;
    this.syncTimer = meterRegistry.timer("swatch_offering_sync");
    this.enqueueAllTimer = meterRegistry.timer("swatch_offering_sync_enqueue_all");
    this.offeringSyncTaskEmitter = offeringSyncTaskEmitter;
    this.objectMapper = objectMapper;
    this.umbMessageMapper = CanonicalMessage.createMapper();
    this.offeringProductTagLookupService = offeringProductTagLookupService;
  }

  /**
   * Fetches the latest upstream version of an offering and updates Swatch's version if different.
   *
   * @param sku the identifier of the marketing operational product
   */
  @Transactional
  public SyncResult syncOffering(String sku) {
    Timer.Sample syncTime = Timer.start();

    if (productDenylist.productIdMatches(sku)) {
      SyncResult result = SyncResult.SKIPPED_DENYLISTED;
      Duration syncDuration = Duration.ofNanos(syncTime.stop(syncTimer));
      log.info(SYNC_LOG_TEMPLATE, result, sku, syncDuration.toMillis());
      throw new ForbiddenException(result.description());
    }

    try {
      SyncResult result =
          getUpstreamOffering(sku).map(this::syncOffering).orElse(SyncResult.SKIPPED_NOT_FOUND);
      Duration syncDuration = Duration.ofNanos(syncTime.stop(syncTimer));
      log.info(SYNC_LOG_TEMPLATE, result, sku, syncDuration.toMillis());
      if (SyncResult.SKIPPED_NOT_FOUND.equals(result)) {
        throw new NotFoundException(result.description());
      }
      return result;
    } catch (RuntimeException ex) {
      SyncResult result = SyncResult.FAILED;
      Duration syncDuration = Duration.ofNanos(syncTime.stop(syncTimer));
      log.warn(SYNC_LOG_TEMPLATE, result, sku, syncDuration.toMillis());
      throw ex;
    }
  }

  /**
   * @param sku the identifier of the marketing operational product
   * @return An Offering with information filled by an upstream service, or empty if the product was
   *     not found.
   */
  private Optional<OfferingEntity> getUpstreamOffering(String sku) {
    log.debug("Retrieving product tree for offeringSku=\"{}\"", sku);
    var offering = UpstreamProductData.offeringFromUpstream(sku, productService);
    discoverProductTagsBySku(offering);
    return offering;
  }

  /**
   * Persists the latest state of an Offering. If no stored Offering matches the SKU, then the
   * Offering is inserted into the datastore. Otherwise, if there are actual changes, the stored
   * Offering with the matching SKU is updated with the given Offering.
   *
   * @param newState the updated Offering
   * @return {@link SyncResult#FETCHED_AND_SYNCED} if upstream offering was stored, or {@link
   *     SyncResult#SKIPPED_MATCHING} if the upstream offering matches what was stored and syncing
   *     was skipped.
   */
  private SyncResult syncOffering(OfferingEntity newState) {
    Optional<OfferingEntity> persistedOffering =
        offeringRepository.findByIdOptional(newState.getSku());
    return syncOffering(newState, persistedOffering);
  }

  private SyncResult syncOffering(
      OfferingEntity newState, Optional<OfferingEntity> persistedOffering) {
    log.debug("New state of offering to save: {}", newState);
    if (alreadySynced(persistedOffering, newState)) {
      return SyncResult.SKIPPED_MATCHING;
    }
    // NOTE(khowell): we need to do the check before persisting the new state, because hibernate
    // reconcile differences (make them equivalent) during persist
    var isCapacityImpacted = offeringChangeImpactsCapacityRecords(newState, persistedOffering);

    // Update to the new entry or create it.
    try {
      offeringRepository.saveOrUpdate(newState);
      offeringRepository.flush();
    } catch (PersistenceException ex) {
      log.debug(
          "Failed to insert offering: {} because of constraint violation. Checking again if it already exists.",
          newState);
      if (alreadySynced(offeringRepository.findByIdOptional(newState.getSku()), newState)) {
        return SyncResult.SKIPPED_MATCHING;
      } else {
        throw ex;
      }
    }

    // Existing capacities need to be updated if certain parts of the offering changed.
    if (isCapacityImpacted) {
      capacityReconciliationService.enqueueReconcileCapacityForOffering(newState.getSku());
      log.info(
          "SKU {} attribute change(s) impact capacity records, reconciliation scheduled",
          newState.getSku());
    } else {
      log.info("SKU {} attribute change(s) did not impact capacity", newState.getSku());
    }

    return SyncResult.FETCHED_AND_SYNCED;
  }

  private boolean offeringChangeImpactsCapacityRecords(
      OfferingEntity newState, Optional<OfferingEntity> persistedOffering) {
    if (persistedOffering.isEmpty()) {
      return true;
    }

    var existingState = persistedOffering.get();

    return !Objects.equals(newState.getCores(), existingState.getCores())
        || !Objects.equals(newState.getHypervisorCores(), existingState.getHypervisorCores())
        || !Objects.equals(newState.getSockets(), existingState.getSockets())
        || !Objects.equals(newState.getHypervisorSockets(), existingState.getHypervisorSockets())
        || !Objects.equals(newState.isHasUnlimitedUsage(), existingState.isHasUnlimitedUsage())
        || !Objects.equals(newState.isMetered(), existingState.isMetered());
  }

  /**
   * Enqueues all offerings listed not in the product denylist to be synced with upstream.
   *
   * @return number of enqueued products
   */
  public int syncAllOfferings() {
    Timer.Sample enqueueTime = Timer.start();

    Set<String> products = offeringRepository.findAllDistinctSkus();
    products.forEach(this::enqueueOfferingSyncTask);

    Duration enqueueDuration = Duration.ofNanos(enqueueTime.stop(enqueueAllTimer));
    int numProducts = products.size();
    log.info(
        "Enqueued numOfferingSyncTasks={} to sync offerings from upstream in enqueueTimeMillis={}",
        numProducts,
        enqueueDuration.toMillis());

    return numProducts;
  }

  // If there is an existing offering in the DB, and it exactly matches the latest upstream
  // version then return true. False means we should sync with the latest upstream version.
  private boolean alreadySynced(Optional<OfferingEntity> persisted, OfferingEntity latest) {
    return persisted
        .map(
            offering ->
                Objects.equals(offering, latest)
                    && Objects.equals(offering.getProductIds(), latest.getProductIds())
                    && Objects.equals(offering.getChildSkus(), latest.getChildSkus()))
        .orElse(false);
  }

  private void enqueueOfferingSyncTask(String sku) {
    offeringSyncTaskEmitter.sendAndAwait(new OfferingSyncTask(sku));
  }

  @Transactional
  public Stream<String> saveOfferings(
      String offeringsJson, String derivedSkuDataJsonArray, String engProdJsonArray) {
    JsonProductDataSource productDataSource =
        new JsonProductDataSource(
            objectMapper, offeringsJson, derivedSkuDataJsonArray, engProdJsonArray);
    productDataSource
        .getTopLevelSkus()
        .map(sku -> enrichUpstreamOfferingData(sku, productDataSource))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(offeringRepository::persist);
    return productDataSource.getTopLevelSkus();
  }

  private Optional<OfferingEntity> enrichUpstreamOfferingData(
      String sku, JsonProductDataSource productDataSource) {
    var offering = UpstreamProductData.offeringFromUpstream(sku, productDataSource);
    discoverProductTagsBySku(offering);
    return offering;
  }

  public void deleteOffering(String sku) {
    offeringRepository.deleteById(sku);
  }

  /**
   * Sync offering state based on a UMB message.
   *
   * <p>See syncRootSku and syncChildSku for more details.
   *
   * @param productXml UMB message for product
   * @return result describing results of sync (for testing purposes)
   */
  @Transactional
  public SyncResult syncUmbProductFromXml(String productXml) throws JsonProcessingException {
    return syncUmbProduct(
        umbMessageMapper
            .readValue(productXml, CanonicalMessage.class)
            .getPayload()
            .getSync()
            .getOperationalProduct());
  }

  private SyncResult syncUmbProduct(UmbOperationalProduct umbOperationalProduct) {
    log.info("Received UMB message for productSku={}", umbOperationalProduct.getSku());
    if (umbOperationalProduct.getSku().startsWith("SVC")) {
      syncChildSku(umbOperationalProduct.getSku());
      return SyncResult.FETCHED_AND_SYNCED;
    } else {
      SyncResult result = syncRootSku(umbOperationalProduct);
      if (result == SyncResult.FETCHED_AND_SYNCED) {
        // we must assume that any SKU we get a message for may be a derived SKU,
        // but we'll check our cache of product data and only actually operate on offerings having
        // the SKU as a derived SKU
        syncDerivedSku(umbOperationalProduct.getSku());
      }
      return result;
    }
  }

  /**
   * Sync the offering state using only the UMB message if possible, otherwise sync the offering
   * from the RHIT product service.
   *
   * @param umbOperationalProduct product definition from a UMB message
   * @see UpstreamProductData#offeringFromUmbData
   */
  private SyncResult syncRootSku(UmbOperationalProduct umbOperationalProduct) {
    Optional<OfferingEntity> existing =
        offeringRepository.findByIdOptional(umbOperationalProduct.getSku());
    Optional<OfferingEntity> newState =
        UpstreamProductData.offeringFromUmbData(
            umbOperationalProduct, existing.orElse(null), productService);
    discoverProductTagsBySku(newState);
    if (newState.isPresent()) {
      return syncOffering(newState.get(), existing);
    } else {
      log.warn(
          "Unable to sync offering from UMB message for sku={}, because product service has no records for it",
          umbOperationalProduct.getSku());
      throw new NotFoundException(SyncResult.SKIPPED_NOT_FOUND.description());
    }
  }

  private void discoverProductTagsBySku(Optional<OfferingEntity> newState) {
    var productTags = offeringProductTagLookupService.discoverProductTagsBySku(newState);
    if (Objects.nonNull(productTags) && Objects.nonNull(productTags.getData())) {
      newState.ifPresent(off -> off.setProductTags(new HashSet<>(productTags.getData())));
    }
  }

  /**
   * Sync all offerings affected by a change in the child SKU.
   *
   * <p>(Future work could reduce the need to sync affected offerings by caching child SKU eng IDs
   * and only queueing offering syncs when there are changes).
   *
   * @param sku child SKU (starts with "SVC" by convention)
   */
  private void syncChildSku(String sku) {
    Set<String> parentSkus =
        offeringRepository.findSkusForChildSku(sku).collect(Collectors.toSet());
    parentSkus.forEach(this::enqueueOfferingSyncTask);
    // NOTE: below, don't simply call parentSkus.forEach(this::syncDerivedSku), as this will cause
    // more DB queries than needed, as implemented below, there is one query, no matter the number
    // of parent SKUs. Calling syncDerivedSku in a loop would cause a separate query for each
    // matching parent SKU
    offeringRepository.findSkusForDerivedSkus(parentSkus).forEach(this::enqueueOfferingSyncTask);
  }

  /**
   * Sync all offerings affected by a derived SKU.
   *
   * <p>(Future work could reduce the need to sync affected offerings by only performing this if
   * derived SKU definition has changes).
   *
   * @param sku SKU that may be a derived SKU
   */
  private void syncDerivedSku(String sku) {
    offeringRepository.findSkusForDerivedSkus(Set.of(sku)).forEach(this::enqueueOfferingSyncTask);
  }
}
