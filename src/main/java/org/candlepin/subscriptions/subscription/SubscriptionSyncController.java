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
package org.candlepin.subscriptions.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;
import com.redhat.swatch.configuration.registry.Variant;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription.SubscriptionCompoundId;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.product.OfferingSyncController;
import org.candlepin.subscriptions.product.SyncResult;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.umb.SubscriptionProductStatus;
import org.candlepin.subscriptions.umb.UmbSubscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/** Update subscriptions from subscription service responses. */
@Component
@Slf4j
public class SubscriptionSyncController {
  private final SubscriptionRepository subscriptionRepository;
  private final OrgConfigRepository orgRepository;
  private final OfferingRepository offeringRepository;
  private final SubscriptionService subscriptionService;
  private final ApplicationClock clock;
  private final CapacityReconciliationController capacityReconciliationController;
  private final OfferingSyncController offeringSyncController;
  private final SubscriptionServiceProperties properties;
  private final Timer enqueueAllTimer;
  private final KafkaTemplate<String, SyncSubscriptionsTask> syncSubscriptionsByOrgKafkaTemplate;
  private final String syncSubscriptionsTopic;
  private final ObjectMapper objectMapper;
  private final ProductDenylist productDenylist;
  private final EntityManager entityManager;

  @Autowired
  public SubscriptionSyncController(
      SubscriptionRepository subscriptionRepository,
      OrgConfigRepository orgRepository,
      OfferingRepository offeringRepository,
      ApplicationClock clock,
      SubscriptionService subscriptionService,
      CapacityReconciliationController capacityReconciliationController,
      OfferingSyncController offeringSyncController,
      SubscriptionServiceProperties properties,
      MeterRegistry meterRegistry,
      KafkaTemplate<String, SyncSubscriptionsTask> syncSubscriptionsByOrgKafkaTemplate,
      ProductDenylist productDenylist,
      ObjectMapper objectMapper,
      @Qualifier("syncSubscriptionTasks") TaskQueueProperties props,
      EntityManager entityManager) {
    this.subscriptionRepository = subscriptionRepository;
    this.orgRepository = orgRepository;
    this.offeringRepository = offeringRepository;
    this.subscriptionService = subscriptionService;
    this.capacityReconciliationController = capacityReconciliationController;
    this.offeringSyncController = offeringSyncController;
    this.clock = clock;
    this.properties = properties;
    this.enqueueAllTimer = meterRegistry.timer("swatch_subscription_sync_enqueue_all");
    this.productDenylist = productDenylist;
    this.objectMapper = objectMapper;
    this.syncSubscriptionsTopic = props.getTopic();
    this.syncSubscriptionsByOrgKafkaTemplate = syncSubscriptionsByOrgKafkaTemplate;
    this.entityManager = entityManager;
  }

  @Transactional
  public void syncSubscription(
      Subscription subscription,
      Optional<org.candlepin.subscriptions.db.model.Subscription> subscriptionOptional) {
    final org.candlepin.subscriptions.db.model.Subscription newOrUpdated = convertDto(subscription);
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
  @Transactional
  public void syncSubscription(
      String sku,
      org.candlepin.subscriptions.db.model.Subscription newOrUpdated,
      Optional<org.candlepin.subscriptions.db.model.Subscription> subscriptionOptional) {
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
        () -> newOrUpdated.setOffering(offeringRepository.getReferenceById(sku)));

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
    // enrich product IDs and measurements onto the incoming subscription record from the offering
    capacityReconciliationController.reconcileCapacityForSubscription(newOrUpdated);
    log.debug("New subscription that will need to be saved={}", newOrUpdated);

    checkForMissingBillingProvider(newOrUpdated);

    if (subscriptionOptional.isPresent()) {
      final org.candlepin.subscriptions.db.model.Subscription existingSubscription =
          subscriptionOptional.get();
      log.debug("Existing subscription in DB={}", existingSubscription);
      if (Objects.nonNull(existingSubscription.getBillingProvider())
          && !existingSubscription.getSubscriptionMeasurements().isEmpty()) {
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
      if (existingSubscription.quantityHasChanged(newOrUpdated.getQuantity())) {
        existingSubscription.endSubscription();
        subscriptionRepository.save(existingSubscription);
        final org.candlepin.subscriptions.db.model.Subscription newSub =
            org.candlepin.subscriptions.db.model.Subscription.builder()
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
        capacityReconciliationController.reconcileCapacityForSubscription(newSub);
        subscriptionRepository.save(newSub);
      } else {
        updateExistingSubscription(newOrUpdated, existingSubscription);
        subscriptionRepository.save(existingSubscription);
      }
    } else {
      subscriptionRepository.save(newOrUpdated);
    }
  }

  private boolean ensureOffering(
      String sku,
      Optional<org.candlepin.subscriptions.db.model.Subscription> subscriptionOptional) {
    // NOTE: we do not need to check if the offering exists if there is an existing DB record for
    // the subscription that uses that offering
    if (subscriptionOptional.isEmpty() && !offeringRepository.existsById(sku)) {
      log.debug("Sku={} not in Offering repository, syncing offering.", sku);
      return SyncResult.isSynced(offeringSyncController.syncOffering(sku));
    }
    return true;
  }

  private void checkForMissingBillingProvider(
      org.candlepin.subscriptions.db.model.Subscription subscription) {
    if ((subscription.getBillingProvider() == null
            || subscription.getBillingProvider().equals(BillingProvider.EMPTY))
        && subscription.getOffering().isMetered()) {
      log.warn(
          "PAYG eligible subscription with subscriptionId:{} has no billing provider.",
          subscription.getSubscriptionId());
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
      org.candlepin.subscriptions.db.model.Subscription subscription,
      Optional<org.candlepin.subscriptions.db.model.Subscription> optionalSubscription) {
    if (subscription.getSubscriptionId() != null) {
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

  private Optional<? extends org.candlepin.subscriptions.db.model.Subscription> fetchSubscription(
      String subscriptionNumber) {
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

    var subCompoundIdToDtoMap =
        dtos.filter(this::shouldSyncSub)
            .collect(
                Collectors.toMap(
                    dto -> {
                      OffsetDateTime startDate =
                          clock.dateFromMilliseconds(dto.getEffectiveStartDate());

                      return new SubscriptionCompoundId(dto.getId().toString(), startDate);
                    },
                    Function.identity(),
                    (firstMatch, secondMatch) -> firstMatch));

    List<org.candlepin.subscriptions.db.model.Subscription> subEntitiesForDeletion =
        new ArrayList<>();

    Set<String> seenIds =
        subCompoundIdToDtoMap.keySet().stream()
            .map(SubscriptionCompoundId::getSubscriptionId)
            .collect(Collectors.toSet());

    var batchSize = 1024;
    CustomBatchIterator.batchStreamOf(subscriptionRepository.findByOrgId(orgId), batchSize)
        .forEach(
            batch -> {
              batch.forEach(
                  subEntity -> {
                    var startDate = subEntity.getStartDate();
                    var subId = subEntity.getSubscriptionId();

                    var key = new SubscriptionCompoundId(subId, startDate);

                    var dto = subCompoundIdToDtoMap.remove(key);

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
    Stream<Subscription> stream = subCompoundIdToDtoMap.values().stream();

    CustomBatchIterator.batchStreamOf(stream, batchSize)
        .forEach(
            batch -> {
              batch.forEach(dto -> syncSubscription(dto, Optional.empty()));

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

    subscriptionRepository.deleteAll(subEntitiesForDeletion);

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
        now.plus(properties.getIgnoreStartingLaterThan()).toInstant().toEpochMilli();
    long latestAllowedExpiredEndDate =
        now.minus(properties.getIgnoreExpiredOlderThan()).toInstant().toEpochMilli();

    return startDate < earliestAllowedFutureStartDate
        && (endDate == null || endDate > latestAllowedExpiredEndDate);
  }

  private void enqueueSubscriptionSync(String orgId) {
    log.debug("Enqueuing subscription sync for orgId={}", orgId);
    syncSubscriptionsByOrgKafkaTemplate.send(
        syncSubscriptionsTopic, SyncSubscriptionsTask.builder().orgId(orgId).build());
  }

  /**
   * Enqueues all enrolled organizations to sync their subscriptions with the upstream subscription
   * service.
   */
  @Transactional
  public void syncAllSubscriptionsForAllOrgs() {
    Timer.Sample enqueueAllTime = Timer.start();
    orgRepository.findSyncEnabledOrgs().forEach(this::enqueueSubscriptionSync);
    Duration enqueueAllDuration = Duration.ofNanos(enqueueAllTime.stop(enqueueAllTimer));
    log.info(
        "Enqueued orgs to sync subscriptions from upstream in enqueueTimeMillis={}",
        enqueueAllDuration.toMillis());
  }

  private org.candlepin.subscriptions.db.model.Subscription convertDto(Subscription subscription) {
    // Note that we are **not** setting the offering yet!
    return org.candlepin.subscriptions.db.model.Subscription.builder()
        .subscriptionId(String.valueOf(subscription.getId()))
        .subscriptionNumber(subscription.getSubscriptionNumber())
        .subscriptionProductIds(
            new HashSet<>(Collections.singleton(SubscriptionDtoUtil.extractSku(subscription))))
        .orgId(subscription.getWebCustomerId().toString())
        .quantity(subscription.getQuantity())
        .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
        .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
        .billingProviderId(SubscriptionDtoUtil.extractBillingProviderId(subscription))
        .billingProvider(SubscriptionDtoUtil.populateBillingProvider(subscription))
        .billingAccountId(SubscriptionDtoUtil.extractBillingAccountId(subscription))
        .build();
  }

  private org.candlepin.subscriptions.db.model.Subscription convertDto(
      UmbSubscription subscription) {

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
    return org.candlepin.subscriptions.db.model.Subscription.builder()
        // NOTE: UMB messages don't include subscriptionId
        .subscriptionNumber(subscription.getSubscriptionNumber())
        .orgId(subscription.getWebCustomerId())
        .quantity(subscription.getQuantity())
        .startDate(subscription.getEffectiveStartDateInUtc())
        .endDate(endDate)
        // NOTE: UMB messages don't include PAYG identifiers
        .build();
  }

  /**
   * Update all subscription fields in an existing SWATCH Subscription that we are allowed to change
   */
  protected void updateExistingSubscription(
      org.candlepin.subscriptions.db.model.Subscription newOrUpdated,
      org.candlepin.subscriptions.db.model.Subscription entity) {
    if (newOrUpdated.getEndDate() != null) {
      entity.setEndDate(newOrUpdated.getEndDate());
    }
    entity.setSubscriptionNumber(newOrUpdated.getSubscriptionNumber());
    entity.setBillingProvider(newOrUpdated.getBillingProvider());
    entity.setBillingAccountId(newOrUpdated.getBillingAccountId());
    entity.setBillingProviderId(newOrUpdated.getBillingProviderId());
    entity.setOrgId(newOrUpdated.getOrgId());
    // recalculate the subscription measurements and product IDs in case those have changed
    capacityReconciliationController.reconcileCapacityForSubscription(entity);
  }

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
                  capacityReconciliationController.reconcileCapacityForSubscription(subscription);
                }
                subscriptionRepository.save(subscription);
              });
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error parsing subscriptionsJson", e);
    }
  }

  private void determineSubscriptionOffering(
      org.candlepin.subscriptions.db.model.Subscription subscription) {
    // should look up the offering and set it before additional processing
    var offer =
        offeringRepository.findOfferingBySku(
            subscription.getSubscriptionProductIds().stream()
                .collect(MoreCollectors.toOptional())
                .orElse(null));
    if (Objects.nonNull(offer)) {
      // should only be one offering per subscription
      subscription.setOffering(offer);
    } else {
      throw new BadRequestException("Error offering doesn't exist");
    }
  }

  @Transactional
  public void saveUmbSubscription(UmbSubscription umbSubscription) {
    org.candlepin.subscriptions.db.model.Subscription subscription = convertDto(umbSubscription);

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

  @Transactional
  public String terminateSubscription(String subscriptionId, OffsetDateTime terminationDate) {
    var subscriptions = subscriptionRepository.findActiveSubscription(subscriptionId);
    if (subscriptions.isEmpty()) {
      throw new EntityNotFoundException(
          String.format(
              "Cannot terminate subscription because no active subscription was found with subscription ID '%s'",
              subscriptionId));
    } else if (subscriptions.size() > 1) {
      throw new SubscriptionsException(
          ErrorCode.UNHANDLED_EXCEPTION_ERROR,
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

  @Async
  @Transactional
  public void forceSyncSubscriptionsForOrgAsync(String orgId) {
    forceSyncSubscriptionsForOrg(orgId, false);
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
  public List<org.candlepin.subscriptions.db.model.Subscription> findSubscriptions(
      Optional<String> orgId, Key usageKey, OffsetDateTime rangeStart, OffsetDateTime rangeEnd) {
    Assert.isTrue(Usage._ANY != usageKey.getUsage(), "Usage cannot be _ANY");
    Assert.isTrue(ServiceLevel._ANY != usageKey.getSla(), "Service Level cannot be _ANY");

    String productTag = usageKey.getProductId();
    if (!Variant.isValidProductTag(productTag)) {
      log.warn("No product tag configured: {}", productTag);
      return Collections.emptyList();
    }

    DbReportCriteria.DbReportCriteriaBuilder reportCriteriaBuilder =
        DbReportCriteria.builder()
            .productTag(productTag)
            .serviceLevel(usageKey.getSla())
            // NOTE(khowell) due to an oversight PAYG SKUs don't currently have a usage set -
            // at some point we should use usageKey.getUsage() instead of "_ANY"
            .usage(Usage._ANY)
            .billingProvider(usageKey.getBillingProvider())
            .billingAccountId(usageKey.getBillingAccountId())
            .payg(true)
            .beginning(rangeStart)
            .ending(rangeEnd);

    DbReportCriteria subscriptionCriteria =
        orgId.map(id -> reportCriteriaBuilder.orgId(id).build()).get();

    List<org.candlepin.subscriptions.db.model.Subscription> result =
        subscriptionRepository.findByCriteria(
            subscriptionCriteria, Sort.by(Subscription_.START_DATE).descending());

    if (result.isEmpty()) {
      log.error("No subscription found for orgId {} with criteria {}", orgId, subscriptionCriteria);
    }

    return result;
  }
}
