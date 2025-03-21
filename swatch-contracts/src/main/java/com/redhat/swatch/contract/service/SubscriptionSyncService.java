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
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.contract.config.ApplicationConfiguration;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.exception.SubscriptionNotFoundException;
import com.redhat.swatch.contract.model.PartnerEntitlementsRequest;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.product.umb.SubscriptionProductStatus;
import com.redhat.swatch.contract.product.umb.UmbSubscription;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.utils.CustomBatchIterator;
import com.redhat.swatch.contract.utils.SubscriptionDtoUtil;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.eclipse.microprofile.faulttolerance.Asynchronous;

@ApplicationScoped
@Slf4j
@AllArgsConstructor
public class SubscriptionSyncService {

  private final SubscriptionRepository subscriptionRepository;
  private final OfferingRepository offeringRepository;
  private final SubscriptionService subscriptionService;
  private final ApplicationClock clock;
  private final CapacityReconciliationService capacityReconciliationService;
  private final OfferingSyncService offeringSyncService;
  private final ContractService contractService;
  private final ApplicationConfiguration properties;
  private final ObjectMapper objectMapper;
  private final ProductDenylist productDenylist;
  private final EntityManager entityManager;

  public void syncSubscription(
      Subscription subscription, Optional<SubscriptionEntity> subscriptionOptional) {
    final SubscriptionEntity newOrUpdated = convertDto(subscription);
    var dtoSku = SubscriptionDtoUtil.extractSku(subscription);
    syncSubscription(dtoSku, newOrUpdated, subscriptionOptional);
  }

  /**
   * Sync a subscription from the subscription service with an internally managed subscription.
   *
   * @param sku the SKU for the subscription
   * @param newOrUpdated a Subscription constructed from a DTO. <strong>This object is not in the
   *     persistence context</strong>
   * @param subscriptionOptional optional existing Subscription. Managed in the persistence context.
   */
  @SuppressWarnings("java:S3776")
  public void syncSubscription(
      String sku,
      SubscriptionEntity newOrUpdated,
      Optional<SubscriptionEntity> subscriptionOptional) {
    if (productDenylist.productIdMatches(sku)) {
      log.debug(
          "Sku {} on denylist, skipping subscription sync for subscriptionId: {} in org: {} ",
          sku,
          newOrUpdated.getSubscriptionId(),
          newOrUpdated.getOrgId());
      return;
    }

    if (!ensureOffering(sku, subscriptionOptional)) {
      log.debug(
          "Sku {} unable to be synced, skipping subscription sync for subscriptionId: {} in org: {}",
          sku,
          newOrUpdated.getSubscriptionId(),
          newOrUpdated.getOrgId());
      return;
    }

    subscriptionOptional.ifPresentOrElse(
        subscription -> newOrUpdated.setOffering(subscription.getOffering()),
        // Set the offering via a proxy object rather than performing a full lookup.  See
        // https://thorben-janssen.com/jpa-getreference/
        () -> newOrUpdated.setOffering(offeringRepository.findById(sku)));

    if (newOrUpdated.getOffering().isMetered()) {
      // New AWS subscriptions may have billing provider info but Azure subscriptions don't
      if (StringUtils.isNotBlank(newOrUpdated.getBillingAccountId())
          && StringUtils.isNotBlank(newOrUpdated.getBillingProviderId())) {
        contractService.createPartnerContract(PartnerEntitlementsRequest.from(newOrUpdated));
      } else if (subscriptionOptional.isPresent()) {
        // Existing records should have billing provider info already stored
        contractService.createPartnerContract(
            PartnerEntitlementsRequest.from(subscriptionOptional.get()));
      } else {
        log.info(
            "Skipping subscription sync for new contract provided subscription with no billing_account_id, contractUMB should sync: {}",
            newOrUpdated);
      }
      return;
    }

    log.debug("Syncing subscription from external service={}", newOrUpdated);

    // UMB doesn't provide all the fields, so if we have an existing DB record, we'll populate from
    // that; otherwise, use the subscription service to fetch missing info
    try {
      enrichMissingFields(newOrUpdated, subscriptionOptional);
    } catch (SubscriptionNotFoundException e) {
      log.warn(
          "Subscription not found in subscription service; unable to save subscriptionNumber={} for orgId={} without a subscription ID",
          newOrUpdated.getSubscriptionNumber(),
          newOrUpdated.getOrgId());
      return;
    }

    checkForMissingRequiredBillingProvider(newOrUpdated);

    // enrich product IDs and measurements onto the incoming subscription record from the offering
    capacityReconciliationService.reconcileCapacityForSubscription(newOrUpdated);
    log.debug("New subscription that will need to be saved={}", newOrUpdated);

    if (subscriptionOptional.isPresent()) {
      final SubscriptionEntity existingSubscription = subscriptionOptional.get();
      log.debug("Existing subscription in DB={}", existingSubscription);
      BillingProvider billingProvider = existingSubscription.getBillingProvider();
      boolean hasBillingProvider =
          Objects.nonNull(billingProvider) && !BillingProvider.EMPTY.equals(billingProvider);
      if (hasBillingProvider && !existingSubscription.getSubscriptionMeasurements().isEmpty()) {
        // NOTE(khowell): longer term, we should query the partnerEntitlement service for this
        // subscription on any attempt to sync, but for now we rely on UMB messages from the IT
        // Partner Entitlement service to update this record outside this process.
        log.info(
            "Skipping sync of subscriptionId={} because it has contract-provided capacity",
            existingSubscription.getSubscriptionId());
        return;
      }
      if (existingSubscription.equals(newOrUpdated)) {
        return; // we have nothing to do as the DB and the subs service have the same info
      }

      /* If the quantity on a subscription has changed, we need to terminate that subscription
       * and create a new subscription with the updated quantity that begins now and ends when
       * the previous subscription used to end.  In the case that the quantity changes multiple
       * times (which should be very rare), we always need to build the new subscription segment
       * off of the current segment.  E.g. If we have A -> A' and the quantity changes again, we
       * need to update A' not A.  We do this via the DESC sort on subscription start date for
       * findByOrgId
       */
      if (existingSubscription.quantityHasChanged(newOrUpdated.getQuantity())) {
        existingSubscription.endSubscription();
        subscriptionRepository.persist(existingSubscription);
        final SubscriptionEntity newSub =
            SubscriptionEntity.builder()
                .subscriptionId(existingSubscription.getSubscriptionId())
                .offering(existingSubscription.getOffering())
                .orgId(existingSubscription.getOrgId())
                .quantity(newOrUpdated.getQuantity())
                .startDate(OffsetDateTime.now())
                .endDate(newOrUpdated.getEndDate())
                .billingProviderId(newOrUpdated.getBillingProviderId())
                .billingAccountId(newOrUpdated.getBillingAccountId())
                .subscriptionNumber(newOrUpdated.getSubscriptionNumber())
                .billingProvider(newOrUpdated.getBillingProvider())
                .build();
        capacityReconciliationService.reconcileCapacityForSubscription(newSub);
        // merge is needed here because the existing subscription might not be found if we only use
        // the subscription number (since primary keys are subscription ID and start date).
        // To be fixed in SWATCH-2801.
        subscriptionRepository.merge(newSub);
      } else {
        updateExistingSubscription(newOrUpdated, existingSubscription);
        subscriptionRepository.persist(existingSubscription);
      }
    } else {
      // Same as above, the newOrUpdated entity might not be in the persistence context, so we need
      // to use the merge operation here as well.
      // To be fixed in SWATCH-2801.
      subscriptionRepository.merge(newOrUpdated);
    }
  }

  private boolean ensureOffering(String sku, Optional<SubscriptionEntity> subscriptionOptional) {
    // NOTE: we do not need to check if the offering exists if there is an existing DB record for
    // the subscription that uses that offering
    if (subscriptionOptional.isEmpty() && offeringRepository.findByIdOptional(sku).isEmpty()) {
      log.debug("Sku={} not in Offering repository, syncing offering.", sku);
      return SyncResult.isSynced(offeringSyncService.syncOffering(sku));
    }
    return true;
  }

  private void checkForMissingRequiredBillingProvider(SubscriptionEntity subscription) {
    BillingProvider billingProvider = subscription.getBillingProvider();
    boolean noBillingProvider =
        Objects.isNull(billingProvider) || !BillingProvider.EMPTY.equals(billingProvider);
    if (noBillingProvider && subscription.getOffering().isMetered()) {
      log.warn(
          "PAYG eligible subscription with subscriptionId:{} and subscription_number:{} has no billing provider.",
          subscription.getSubscriptionId(),
          subscription.getSubscriptionNumber());
    }
  }

  /**
   * Populate a subscription entity with data from the DB if it exists, otherwise enrich with data
   * from RH IT Subscription Service.
   *
   * @param subscription entity to populate with existing data
   * @param optionalSubscription data from the DB, if present
   */
  private void enrichMissingFields(
      SubscriptionEntity subscription, Optional<SubscriptionEntity> optionalSubscription) {
    // We need to pass through existing azure billing info since SearchApi will not have it
    var isAzureSubscription =
        optionalSubscription
            .map(existingSub -> BillingProvider.AZURE.equals(existingSub.getBillingProvider()))
            .orElse(false);
    if (subscription.getSubscriptionId() != null && !isAzureSubscription) {
      // Subscription object already has needed data
      return;
    }
    optionalSubscription
        .or(() -> fetchSubscription(subscription.getSubscriptionNumber()))
        .ifPresent(
            existingData -> {
              if (subscription.getSubscriptionId() == null) {
                subscription.setSubscriptionId(existingData.getSubscriptionId());
              }
              if (subscription.getBillingAccountId() == null) {
                subscription.setBillingAccountId(existingData.getBillingAccountId());
              }
              if (subscription.getBillingProvider() == null) {
                subscription.setBillingProvider(existingData.getBillingProvider());
              }
              if (subscription.getBillingProviderId() == null) {
                subscription.setBillingProviderId(existingData.getBillingProviderId());
              }
            });
  }

  private Optional<? extends SubscriptionEntity> fetchSubscription(String subscriptionNumber) {
    return Optional.of(
        convertDto(subscriptionService.getSubscriptionBySubscriptionNumber(subscriptionNumber)));
  }

  @Transactional
  @Timed("swatch_subscription_reconcile_org")
  public void reconcileSubscriptionsWithSubscriptionService(String orgId, boolean paygOnly) {
    log.info("Syncing subscriptions for orgId={}", orgId);

    var dtos = subscriptionService.getSubscriptionsByOrgId(orgId).stream();

    // Filter out non PAYG subscriptions for faster processing when they are not needed.
    // Slow processing was causing: https://issues.redhat.com/browse/ENT-5083

    if (paygOnly) {
      dtos =
          dtos.filter(
              subscription -> SubscriptionDtoUtil.extractBillingProviderId(subscription) != null);
    }

    var subIdToDtoMap =
        dtos.filter(this::shouldSyncSub)
            .collect(
                Collectors.toMap(
                    dto -> dto.getId().toString(),
                    Function.identity(),
                    (firstMatch, secondMatch) -> firstMatch));

    List<SubscriptionEntity> subEntitiesForDeletion = new ArrayList<>();

    Set<String> seenIds = new HashSet<>(subIdToDtoMap.keySet());

    var batchSize = 1024;
    CustomBatchIterator.batchStreamOf(subscriptionRepository.streamByOrgId(orgId), batchSize)
        .forEach(
            batch -> {
              batch.forEach(
                  subEntity -> {
                    var subId = subEntity.getSubscriptionId();
                    var dto = subIdToDtoMap.remove(subId);

                    // delete from swatch because it didn't appear in the latest list from the
                    // subscription service, or it's in the denylist
                    if (!seenIds.contains(subId)
                        || (productDenylist.productIdMatches(subEntity.getOffering().getSku()))) {
                      subEntitiesForDeletion.add(subEntity);
                      return;
                    }

                    // we've seen a newer version of the sub, but this version doesn't need updates
                    if (dto == null) {
                      return;
                    }

                    syncSubscription(dto, Optional.of(subEntity));
                  });
              subscriptionRepository.flush();
              entityManager.clear();
            });

    // These are additional subs that should be sync'd but weren't previously in the database
    Stream<Subscription> stream = subIdToDtoMap.values().stream();

    CustomBatchIterator.batchStreamOf(stream, batchSize)
        .forEach(
            batch -> {
              batch.forEach(
                  dto -> {
                    // Contract provided subscriptions will have a different start_date than what is
                    // stored in Subscription SearchAPI
                    var existingSubscription =
                        subscriptionRepository
                            .findBySubscriptionNumber(dto.getSubscriptionNumber())
                            .stream()
                            .findFirst();
                    syncSubscription(dto, existingSubscription);
                  });

              subscriptionRepository.flush();
              entityManager.clear();
            });

    if (paygOnly) {
      // don't clean up stale subs, because PAYG-only sync discards/ignores too much data to
      // determine what to delete at this point
      return;
    }

    if (!subEntitiesForDeletion.isEmpty()) {
      log.info("Removing {} stale/incorrect subscription records", subEntitiesForDeletion.size());
    }

    subEntitiesForDeletion.forEach(subscriptionRepository::delete);

    log.info("Finished syncing subscriptions for orgId {}", orgId);
  }

  private boolean shouldSyncSub(Subscription sub) {
    // Reject subs expired long ago, or subs that won't be active quite yet.
    OffsetDateTime now = clock.now();

    Long startDate = sub.getEffectiveStartDate();
    Long endDate = sub.getEffectiveEndDate();

    // Consider any sub with a null effective start date as invalid, it could be an upstream data
    // issue. Log this sub's info and skip it.
    if (startDate == null) {
      log.warn(
          "subscriptionId={} subscriptionNumber={} for orgId={} has effectiveStartDate null (should not be null). Subscription data will need fixing in upstream service. Skipping sync.",
          sub.getId(),
          sub.getSubscriptionNumber(),
          sub.getWebCustomerId());
      return false;
    }

    long earliestAllowedFutureStartDate =
        now.plus(properties.getSubscriptionIgnoreStartingLaterThan()).toInstant().toEpochMilli();
    long latestAllowedExpiredEndDate =
        now.minus(properties.getSubscriptionIgnoreExpiredOlderThan()).toInstant().toEpochMilli();

    return startDate < earliestAllowedFutureStartDate
        && (endDate == null || endDate > latestAllowedExpiredEndDate);
  }

  private SubscriptionEntity convertDto(UmbSubscription subscription) {

    var endDate = subscription.getEffectiveEndDateInUtc();

    /*
     * If a subscription is terminated, set our concept of effective end date to the termination
     * date.
     */

    if (Objects.nonNull(subscription.getProductStatusState())) {
      Optional<SubscriptionProductStatus> status =
          Arrays.stream(subscription.getProductStatusState())
              .filter(x -> "Terminated".equalsIgnoreCase(x.getState()))
              .findFirst();

      boolean isSubscriptionTerminated = status.isPresent();

      /*
       * Note that a status only has a "StartDate", which indicates the start of the corresponding
       * status - this doesn't directly correlate to the StartDate of a subscription.
       */
      if (isSubscriptionTerminated) {
        endDate = UmbSubscription.convertToUtc(status.get().getStartDate());
      }
    }
    // NOTE: we are not setting the offering yet
    return SubscriptionEntity.builder()
        // NOTE: UMB messages don't include subscriptionId
        .subscriptionNumber(subscription.getSubscriptionNumber())
        .orgId(subscription.getWebCustomerId())
        .quantity(subscription.getQuantity())
        .startDate(subscription.getEffectiveStartDateInUtc())
        .endDate(endDate)
        // NOTE: UMB messages don't include PAYG identifiers
        .build();
  }

  private SubscriptionEntity convertDto(Subscription subscription) {
    // Note that we are **not** setting the offering yet!
    return SubscriptionEntity.builder()
        .subscriptionId(String.valueOf(subscription.getId()))
        .subscriptionNumber(subscription.getSubscriptionNumber())
        .offering(
            OfferingEntity.builder().sku(SubscriptionDtoUtil.extractSku(subscription)).build())
        .orgId(subscription.getWebCustomerId().toString())
        .quantity(subscription.getQuantity())
        .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
        .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
        .billingProviderId(SubscriptionDtoUtil.extractBillingProviderId(subscription))
        .billingProvider(SubscriptionDtoUtil.populateBillingProvider(subscription))
        .billingAccountId(SubscriptionDtoUtil.extractBillingAccountId(subscription))
        .build();
  }

  /**
   * Update all subscription fields in an existing SWATCH Subscription that we are allowed to change
   */
  private void updateExistingSubscription(
      SubscriptionEntity newOrUpdated, SubscriptionEntity entity) {
    if (newOrUpdated.getEndDate() != null) {
      entity.setEndDate(newOrUpdated.getEndDate());
    }
    entity.setSubscriptionNumber(newOrUpdated.getSubscriptionNumber());
    entity.setBillingProvider(newOrUpdated.getBillingProvider());
    entity.setBillingAccountId(newOrUpdated.getBillingAccountId());
    entity.setBillingProviderId(newOrUpdated.getBillingProviderId());
    entity.setOrgId(newOrUpdated.getOrgId());
    // recalculate the subscription measurements and product IDs in case those have changed
    capacityReconciliationService.reconcileCapacityForSubscription(entity);
  }

  @Transactional
  public void saveSubscriptions(String subscriptionsJson, boolean reconcileCapacity) {
    try {
      Subscription[] subscriptions =
          objectMapper.readValue(subscriptionsJson, Subscription[].class);
      Arrays.stream(subscriptions)
          .map(this::convertDto)
          .forEach(
              subscription -> {
                if (reconcileCapacity) {
                  determineSubscriptionOffering(subscription);
                  capacityReconciliationService.reconcileCapacityForSubscription(subscription);
                }
                subscriptionRepository.persist(subscription);
              });
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error parsing subscriptionsJson", e);
    }
  }

  private void determineSubscriptionOffering(SubscriptionEntity subscription) {
    if (subscription.getOffering().getSku() != null) {
      // should look up the offering and set it before additional processing
      var offer = offeringRepository.findByIdOptional(subscription.getOffering().getSku());
      if (offer.isPresent()) {
        // should only be one offering per subscription
        subscription.setOffering(offer.get());
      } else {
        throw new BadRequestException("Error offering doesn't exist");
      }
    }
  }

  @Transactional
  public void saveUmbSubscription(UmbSubscription umbSubscription) {
    SubscriptionEntity subscription = convertDto(umbSubscription);
    var subscriptions =
        subscriptionRepository.findBySubscriptionNumber(subscription.getSubscriptionNumber());
    if (subscriptions.size() > 1) {
      log.warn(
          "Skipping UMB message because multiple subscriptions were found for subscriptionNumber={}",
          subscription.getSubscriptionNumber());
    } else {
      syncSubscription(umbSubscription.getSku(), subscription, subscriptions.stream().findFirst());
    }
  }

  @Asynchronous
  @Transactional
  public CompletionStage<Void> forceSyncSubscriptionsForOrgAsync(String orgId) {
    return CompletableFuture.runAsync(() -> forceSyncSubscriptionsForOrg(orgId, false));
  }

  @Transactional
  public void forceSyncSubscriptionsForOrg(String orgId, boolean paygOnly) {
    if (paygOnly && !properties.isEnablePaygSubscriptionForceSync()) {
      log.info("Force sync of payg subscriptions disabled");
      return;
    }
    log.info("Starting force sync for orgId: {}", orgId);
    reconcileSubscriptionsWithSubscriptionService(orgId, paygOnly);
    log.info("Finished force sync for orgId: {}", orgId);
  }

  @Transactional
  public String terminateSubscription(String subscriptionId, OffsetDateTime terminationDate) {
    var subscriptions = subscriptionRepository.findActiveSubscription(subscriptionId);
    if (subscriptions.isEmpty()) {
      throw new EntityNotFoundException(
          String.format(
              "Cannot terminate subscription because no active subscription was found with subscription ID '%s'",
              subscriptionId));
    } else if (subscriptions.size() > 1) {
      throw new ServiceException(
          ErrorCode.UNHANDLED_EXCEPTION,
          Response.Status.INTERNAL_SERVER_ERROR,
          "Multiple active subscription found",
          String.format(
              "Cannot terminate subscription because multiple active subscriptions were found for subscription ID '%s'",
              subscriptionId));
    }

    var subscription = subscriptions.get(0);

    // Wait until after we are sure there's an offering for this subscription before setting the
    // end date.  We want validation to occur before we start mutating data.
    subscription.setEndDate(terminationDate);

    OffsetDateTime now = OffsetDateTime.now();
    // The calculation returns a whole number, representing the number of complete units
    // between the two temporals. For example, the amount in hours between the times 11:30 and
    // 12:29 will zero hours as it is one minute short of an hour.
    var delta = Math.abs(ChronoUnit.HOURS.between(terminationDate, now));
    if (subscription.getOffering().isMetered() && delta > 0) {
      var msg =
          String.format(
              "Subscription %s terminated at %s with out of range termination date %s.",
              subscriptionId, now, terminationDate);
      log.warn(msg);
      return msg;
    }
    return String.format("Subscription %s terminated at %s.", subscriptionId, terminationDate);
  }
}
