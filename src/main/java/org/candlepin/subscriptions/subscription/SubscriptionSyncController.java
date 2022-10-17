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
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.capacity.files.ProductAllowlist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.ReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.umb.CanonicalMessage;
import org.candlepin.subscriptions.umb.UmbSubscription;
import org.candlepin.subscriptions.user.AccountService;
import org.candlepin.subscriptions.util.ApplicationClock;
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

  private static final XmlMapper umbMessageMapper = CanonicalMessage.createMapper();
  private SubscriptionRepository subscriptionRepository;
  private OrgConfigRepository orgRepository;
  private OfferingRepository offeringRepository;
  private SubscriptionService subscriptionService;
  private ApplicationClock clock;
  private CapacityReconciliationController capacityReconciliationController;
  private SubscriptionServiceProperties properties;
  private Timer syncTimer;
  private Timer enqueueAllTimer;
  private KafkaTemplate<String, SyncSubscriptionsTask> syncSubscriptionsByOrgKafkaTemplate;
  private final TagProfile tagProfile;
  private final AccountService accountService;
  private String syncSubscriptionsTopic;
  private final ObjectMapper objectMapper;
  private final ProductAllowlist productAllowlist;

  @Autowired
  public SubscriptionSyncController(
      SubscriptionRepository subscriptionRepository,
      OrgConfigRepository orgRepository,
      OfferingRepository offeringRepository,
      ApplicationClock clock,
      SubscriptionService subscriptionService,
      CapacityReconciliationController capacityReconciliationController,
      SubscriptionServiceProperties properties,
      MeterRegistry meterRegistry,
      KafkaTemplate<String, SyncSubscriptionsTask> syncSubscriptionsByOrgKafkaTemplate,
      ProductAllowlist productAllowlist,
      ObjectMapper objectMapper,
      @Qualifier("syncSubscriptionTasks") TaskQueueProperties props,
      TagProfile tagProfile,
      AccountService accountService) {
    this.subscriptionRepository = subscriptionRepository;
    this.orgRepository = orgRepository;
    this.offeringRepository = offeringRepository;
    this.subscriptionService = subscriptionService;
    this.capacityReconciliationController = capacityReconciliationController;
    this.clock = clock;
    this.properties = properties;
    this.syncTimer = meterRegistry.timer("swatch_subscription_sync_page");
    this.enqueueAllTimer = meterRegistry.timer("swatch_subscription_sync_enqueue_all");
    this.productAllowlist = productAllowlist;
    this.objectMapper = objectMapper;
    this.syncSubscriptionsTopic = props.getTopic();
    this.syncSubscriptionsByOrgKafkaTemplate = syncSubscriptionsByOrgKafkaTemplate;
    this.tagProfile = tagProfile;
    this.accountService = accountService;
  }

  @Transactional
  public void syncSubscription(Subscription subscription) {
    syncSubscription(
        subscription,
        subscriptionRepository.findActiveSubscription(String.valueOf(subscription.getId())));
  }

  @Transactional
  public void syncSubscription(
      Subscription subscription,
      Optional<org.candlepin.subscriptions.db.model.Subscription> subscriptionOptional) {
    final org.candlepin.subscriptions.db.model.Subscription newOrUpdated = convertDto(subscription);
    syncSubscription(newOrUpdated, subscriptionOptional);
  }

  @Transactional
  public void syncSubscription(
      org.candlepin.subscriptions.db.model.Subscription newOrUpdated,
      Optional<org.candlepin.subscriptions.db.model.Subscription> subscriptionOptional) {
    String sku = newOrUpdated.getSku();

    if (!productAllowlist.productIdMatches(sku)) {
      log.debug(
          "Sku {} not on allowlist, skipping subscription sync for subscriptionId: {} in org: {} ",
          sku,
          newOrUpdated.getSubscriptionId(),
          newOrUpdated.getOwnerId());
      return;
    }

    // NOTE: we do not need to check if the offering exists if there is an existing DB record for
    // the subscription that uses that offering
    if (subscriptionOptional.isEmpty() && !offeringRepository.existsById(sku)) {
      log.debug(
          "Sku={} not in Offering repository, skipping subscription sync for subscriptionId={} in org={}",
          sku,
          newOrUpdated.getSubscriptionId(),
          newOrUpdated.getOwnerId());
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
          newOrUpdated.getOwnerId());
      return;
    }
    log.debug("New subscription that will need to be saved={}", newOrUpdated);

    checkForMissingBillingProvider(newOrUpdated);

    if (subscriptionOptional.isPresent()) {
      final org.candlepin.subscriptions.db.model.Subscription existingSubscription =
          subscriptionOptional.get();
      log.debug("Existing subscription in DB={}", existingSubscription);
      if (existingSubscription.equals(newOrUpdated)) {
        return; // we have nothing to do as the DB and the subs service have the same info
      }
      if (existingSubscription.quantityHasChanged(newOrUpdated.getQuantity())) {
        existingSubscription.endSubscription();
        subscriptionRepository.save(existingSubscription);
        final org.candlepin.subscriptions.db.model.Subscription newSub =
            org.candlepin.subscriptions.db.model.Subscription.builder()
                .subscriptionId(existingSubscription.getSubscriptionId())
                .sku(existingSubscription.getSku())
                .ownerId(existingSubscription.getOwnerId())
                .accountNumber(existingSubscription.getAccountNumber())
                .quantity(newOrUpdated.getQuantity())
                .startDate(OffsetDateTime.now())
                .endDate(newOrUpdated.getEndDate())
                .billingProviderId(newOrUpdated.getBillingProviderId())
                .billingAccountId(newOrUpdated.getBillingAccountId())
                .subscriptionNumber(newOrUpdated.getSubscriptionNumber())
                .billingProvider(newOrUpdated.getBillingProvider())
                .build();
        subscriptionRepository.save(newSub);
      } else {
        updateSubscription(newOrUpdated, existingSubscription);
        subscriptionRepository.save(existingSubscription);
      }
      capacityReconciliationController.reconcileCapacityForSubscription(newOrUpdated);
    } else {
      subscriptionRepository.save(newOrUpdated);
      capacityReconciliationController.reconcileCapacityForSubscription(newOrUpdated);
    }
  }

  private void checkForMissingBillingProvider(
      org.candlepin.subscriptions.db.model.Subscription subscription) {
    if (subscription.getBillingProvider() == null
        || subscription.getBillingProvider().equals(BillingProvider.EMPTY)) {
      var offeringOptional = offeringRepository.findById(subscription.getSku());
      if (offeringOptional.isPresent()) {
        var productTag =
            tagProfile.tagForOfferingProductName(offeringOptional.get().getProductName());
        if (tagProfile.isProductPAYGEligible(productTag)) {
          log.warn(
              "PAYG eligible subscription with subscriptionId:{} has no billing provider.",
              subscription.getSubscriptionId());
        }
      }
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
  public void syncSubscription(String subscriptionId) {
    Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
    syncSubscription(subscription);
  }

  void syncSubscriptions(String orgId, int offset, int limit) {
    log.info(
        "Syncing subscriptions for orgId={} with offset={} and limit={} ", orgId, offset, limit);
    Timer.Sample syncTime = Timer.start();

    int pageSize = limit + 1;
    List<Subscription> subscriptions =
        subscriptionService.getSubscriptionsByOrgId(orgId, offset, pageSize);
    int numFetchedSubs = subscriptions.size();
    boolean hasMore = numFetchedSubs >= pageSize;
    log.info(
        "Fetched numFetchedSubs={} for orgId={} from external service.", numFetchedSubs, orgId);

    subscriptions =
        subscriptions.stream().filter(this::shouldSyncSub).collect(Collectors.toUnmodifiableList());
    int numKeptSubs = subscriptions.size();

    subscriptions.forEach(this::syncSubscription);
    if (hasMore) {
      enqueueSubscriptionSync(orgId, offset + limit, limit);
    }
    Duration syncDuration = Duration.ofNanos(syncTime.stop(syncTimer));
    log.info(
        "Fetched numFetchedSubs={} and synced numSyncedSubs={} active/recent subscriptions for orgId={} offset={} limit={} in subSyncedTimeMillis={}",
        numFetchedSubs,
        numKeptSubs,
        orgId,
        offset,
        limit,
        syncDuration.toMillis());
  }

  private boolean shouldSyncSub(Subscription sub) {
    // Reject subs expired long ago, or subs that won't be active quite yet.
    OffsetDateTime now = clock.now();

    Long startDate = sub.getEffectiveStartDate();
    Long endDate = sub.getEffectiveEndDate();

    // Consider any sub with a null effective date as invalid, it could be an upstream data issue.
    // Log this sub's info and skip it.
    if (startDate == null || endDate == null) {
      log.error(
          "subscriptionId={} subscriptionNumber={} for orgId={} has effectiveStartDate={} and "
              + "effectiveEndDate={} (neither should be null). Subscription data will need fixed "
              + "in upstream service. Skipping sync.",
          sub.getId(),
          sub.getSubscriptionNumber(),
          sub.getWebCustomerId(),
          startDate,
          endDate);
      return false;
    }

    long earliestAllowedFutureStartDate =
        now.plus(properties.getIgnoreStartingLaterThan()).toEpochSecond() * 1000;
    long latestAllowedExpiredEndDate =
        now.minus(properties.getIgnoreExpiredOlderThan()).toEpochSecond() * 1000;

    return startDate < earliestAllowedFutureStartDate && endDate > latestAllowedExpiredEndDate;
  }

  private void enqueueSubscriptionSync(String orgId, int offset, int limit) {
    log.debug("Enqueuing subscription sync for orgId={} offset={} limit={}", orgId, offset, limit);
    syncSubscriptionsByOrgKafkaTemplate.send(
        syncSubscriptionsTopic,
        SyncSubscriptionsTask.builder().orgId(orgId).offset(offset).limit(limit).build());
  }

  @Transactional
  public void syncAllSubcriptionsForOrg(String orgId) {
    syncSubscriptions(orgId, 0, properties.getPageSize());
  }

  /**
   * Enqueues all enrolled organizations to sync their subscriptions with the upstream subscription
   * service.
   */
  @Transactional
  public void syncAllSubscriptionsForAllOrgs() {
    Timer.Sample enqueueAllTime = Timer.start();
    orgRepository
        .findSyncEnabledOrgs()
        .forEach(orgId -> enqueueSubscriptionSync(orgId, 0, properties.getPageSize()));
    Duration enqueueAllDuration = Duration.ofNanos(enqueueAllTime.stop(enqueueAllTimer));
    log.info(
        "Enqueued orgs to sync subscriptions from upstream in enqueueTimeMillis={}",
        enqueueAllDuration.toMillis());
  }

  private org.candlepin.subscriptions.db.model.Subscription convertDto(Subscription subscription) {

    return org.candlepin.subscriptions.db.model.Subscription.builder()
        .subscriptionId(String.valueOf(subscription.getId()))
        .subscriptionNumber(subscription.getSubscriptionNumber())
        .sku(SubscriptionDtoUtil.extractSku(subscription))
        .ownerId(subscription.getWebCustomerId().toString())
        .quantity(subscription.getQuantity())
        .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
        .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
        .billingProviderId(SubscriptionDtoUtil.extractBillingProviderId(subscription))
        .billingProvider(SubscriptionDtoUtil.populateBillingProvider(subscription))
        .billingAccountId(SubscriptionDtoUtil.extractBillingAccountId(subscription))
        .build();
  }

  private static org.candlepin.subscriptions.db.model.Subscription convertDto(
      UmbSubscription subscription) {
    return org.candlepin.subscriptions.db.model.Subscription.builder()
        // NOTE: UMB messages don't include subscriptionId
        .subscriptionNumber(subscription.getSubscriptionNumber())
        .sku(subscription.getSku())
        .ownerId(subscription.getWebCustomerId())
        .accountNumber(String.valueOf(subscription.getEbsAccountNumber()))
        .quantity(subscription.getQuantity())
        .startDate(subscription.getEffectiveStartDateInUtc())
        .endDate(subscription.getEffectiveEndDateInUtc())
        // NOTE: UMB messages don't include PAYG identifiers
        .build();
  }

  /** Update all subscription fields that we allow to change */
  protected void updateSubscription(
      org.candlepin.subscriptions.db.model.Subscription newOrUpdated,
      org.candlepin.subscriptions.db.model.Subscription entity) {
    if (newOrUpdated.getEndDate() != null) {
      entity.setEndDate(newOrUpdated.getEndDate());
    }
    entity.setBillingProvider(newOrUpdated.getBillingProvider());
    entity.setBillingAccountId(newOrUpdated.getBillingAccountId());
    entity.setBillingProviderId(newOrUpdated.getBillingProviderId());
  }

  public void saveSubscriptions(String subscriptionsJson, boolean reconcileCapacity) {
    try {
      Subscription[] subscriptions =
          objectMapper.readValue(subscriptionsJson, Subscription[].class);
      Arrays.stream(subscriptions)
          .map(this::convertDto)
          .forEach(
              subscription -> {
                subscriptionRepository.save(subscription);
                if (reconcileCapacity) {
                  capacityReconciliationController.reconcileCapacityForSubscription(subscription);
                }
              });
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error parsing subscriptionsJson", e);
    }
  }

  @Transactional
  public void saveUmbSubscriptionFromXml(String subscriptionXml) throws JsonProcessingException {
    saveUmbSubscription(
        umbMessageMapper
            .readValue(subscriptionXml, org.candlepin.subscriptions.umb.CanonicalMessage.class)
            .getPayload()
            .getSync()
            .getSubscription());
  }

  @Transactional
  public void saveUmbSubscription(UmbSubscription umbSubscription) {
    org.candlepin.subscriptions.db.model.Subscription subscription = convertDto(umbSubscription);
    syncSubscription(
        subscription,
        subscriptionRepository.findBySubscriptionNumber(subscription.getSubscriptionNumber()));
  }

  public void deleteSubscription(String subscriptionId) {
    subscriptionRepository.deleteBySubscriptionId(subscriptionId);
  }

  @Transactional
  public String terminateSubscription(String subscriptionId, OffsetDateTime terminationDate) {
    var subscription =
        subscriptionRepository
            .findActiveSubscription(subscriptionId)
            .orElseThrow(EntityNotFoundException::new);

    var offering =
        offeringRepository
            .findById(subscription.getSku())
            .orElseThrow(EntityNotFoundException::new);

    // Wait until after we are sure there's an offering for this subscription before setting the
    // end date.  We want validation to occur before we start mutating data.
    subscription.setEndDate(terminationDate);

    OffsetDateTime now = OffsetDateTime.now();
    // The calculation returns a whole number, representing the number of complete units
    // between the two temporals. For example, the amount in hours between the times 11:30 and
    // 12:29 will zero hours as it is one minute short of an hour.
    var delta = Math.abs(ChronoUnit.HOURS.between(terminationDate, now));
    var productTag = tagProfile.tagForOfferingProductName(offering.getProductName());
    if (tagProfile.isProductPAYGEligible(productTag) && delta > 0) {
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
    log.info("Starting force sync for orgId: {}", orgId);
    var subscriptions = subscriptionService.getSubscriptionsByOrgId(orgId);

    // Filter out non PAYG subscriptions for faster processing when they are not needed.
    // Slow processing was causing: https://issues.redhat.com/browse/ENT-5083
    if (paygOnly) {
      subscriptions =
          subscriptions.stream()
              .filter(
                  subscription ->
                      SubscriptionDtoUtil.extractBillingProviderId(subscription) != null)
              .collect(Collectors.toList());
    }

    var subscriptionMap =
        getActiveSubscriptionsForOrg(orgId)
            .collect(
                Collectors.toMap(
                    org.candlepin.subscriptions.db.model.Subscription::getSubscriptionId,
                    sub -> sub));

    subscriptions.forEach(
        subscription ->
            syncSubscription(
                subscription,
                Optional.ofNullable(subscriptionMap.get(String.valueOf(subscription.getId())))));

    log.info("Finished force sync for orgId: {}", orgId);
  }

  private Stream<org.candlepin.subscriptions.db.model.Subscription> getActiveSubscriptionsForOrg(
      String orgId) {
    return subscriptionRepository
        .findByOwnerIdAndEndDateAfter(orgId, OffsetDateTime.now())
        .stream();
  }

  @Transactional
  public List<org.candlepin.subscriptions.db.model.Subscription> findSubscriptionsAndSyncIfNeeded(
      String accountNumber,
      Optional<String> orgId,
      Key usageKey,
      OffsetDateTime rangeStart,
      OffsetDateTime rangeEnd,
      boolean paygOnly) {
    Assert.isTrue(Usage._ANY != usageKey.getUsage(), "Usage cannot be _ANY");
    Assert.isTrue(ServiceLevel._ANY != usageKey.getSla(), "Service Level cannot be _ANY");

    String productId = usageKey.getProductId();
    Set<String> productNames = tagProfile.getOfferingProductNamesForTag(productId);
    if (productNames.isEmpty()) {
      log.warn("No product names configured for tag: {}", productId);
      return Collections.emptyList();
    }

    ReportCriteria.ReportCriteriaBuilder reportCriteriaBuilder =
        ReportCriteria.builder()
            .productNames(productNames)
            .serviceLevel(usageKey.getSla())
            // NOTE(khowell) due to an oversight PAYG SKUs don't currently have a usage set -
            // at some point we should use usageKey.getUsage() instead of "_ANY"
            .usage(Usage._ANY)
            .billingProvider(usageKey.getBillingProvider())
            .billingAccountId(usageKey.getBillingAccountId())
            .payg(true)
            .beginning(rangeStart)
            .ending(rangeEnd);

    ReportCriteria subscriptionCriteria =
        orgId
            .map(id -> reportCriteriaBuilder.orgId(id).build())
            .orElseGet(() -> reportCriteriaBuilder.accountNumber(accountNumber).build());

    List<org.candlepin.subscriptions.db.model.Subscription> result =
        subscriptionRepository.findByCriteria(
            subscriptionCriteria, Sort.by(Subscription_.START_DATE).descending());

    if (result.isEmpty()) {
      /* If we are missing the subscription, call out to the RhMarketplaceSubscriptionCollector
      to fetch from Marketplace.  Sync all those subscriptions. Query again. */
      if (orgId.isEmpty()) {
        orgId = Optional.of(accountService.lookupOrgId(accountNumber));
      }
      log.info("Syncing subscriptions for account {} using orgId {}", accountNumber, orgId.get());
      forceSyncSubscriptionsForOrg(orgId.get(), paygOnly);
      result =
          subscriptionRepository.findByCriteria(
              subscriptionCriteria, Sort.by(Subscription_.START_DATE).descending());
    }

    if (result.isEmpty()) {
      log.error(
          "No subscription found for account {} with criteria {}",
          accountNumber,
          subscriptionCriteria);
    }

    return result;
  }
}
