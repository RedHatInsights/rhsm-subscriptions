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
package org.candlepin.subscriptions.tally;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.TagMetaData;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Collects instances and tallies based on hourly metrics. */
public class MetricUsageCollector {
  private static final Logger log = LoggerFactory.getLogger(MetricUsageCollector.class);

  private final AccountServiceInventoryRepository accountServiceInventoryRepository;
  private final EventController eventController;
  private final ApplicationClock clock;
  private final TagProfile tagProfile;

  public MetricUsageCollector(
      TagProfile tagProfile,
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      EventController eventController,
      ApplicationClock clock) {
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.eventController = eventController;
    this.clock = clock;
    this.tagProfile = tagProfile;
  }

  @Transactional
  public CollectionResult collect(
      String serviceType, String accountNumber, String orgId, DateRange range) {
    if (!clock.isHourlyRange(range)) {
      throw new IllegalArgumentException(
          String.format(
              "Start and end dates must be at the top of the hour: [%s -> %s]",
              range.getStartString(), range.getEndString()));
    }

    if (!eventController.hasEventsInTimeRange(
        orgId, serviceType, range.getStartDate(), range.getEndDate())) {
      log.info("No event metrics to process for service type {} in range: {}", serviceType, range);
      return null;
    }

    log.info("Event exists for org {} of service type {} in range: {}", orgId, serviceType, range);
    /* load the latest accountServiceInventory state, so we can update host records conveniently */
    AccountServiceInventoryId inventoryId =
        AccountServiceInventoryId.builder().orgId(orgId).serviceType(serviceType).build();
    AccountServiceInventory accountServiceInventory =
        accountServiceInventoryRepository
            .findById(inventoryId)
            .orElse(new AccountServiceInventory(inventoryId));

    if (accountNumber != null) {
      accountServiceInventory.setAccountNumber(accountNumber);
    }
    /*
    Evaluate latest state to determine if we are doing a recalculation and filter to host records for only
    the product profile we're working on
    */
    Map<String, Host> existingInstances = new HashMap<>();
    OffsetDateTime newestInstanceTimestamp = OffsetDateTime.MIN;
    for (Host host : accountServiceInventory.getServiceInstances().values()) {
      existingInstances.put(host.getInstanceId(), host);
      newestInstanceTimestamp =
          newestInstanceTimestamp.isAfter(host.getLastSeen())
              ? newestInstanceTimestamp
              : host.getLastSeen();
    }
    OffsetDateTime effectiveStartDateTime;
    OffsetDateTime effectiveEndDateTime;
    boolean isRecalculating;
    /*
    We need to recalculate several things if we are re-tallying, namely monthly totals need to be
    cleared and re-updated for each host record
     */
    if (newestInstanceTimestamp.isAfter(range.getStartDate())) {
      effectiveStartDateTime = clock.startOfMonth(range.getStartDate());
      effectiveEndDateTime = clock.endOfCurrentHour();
      log.info(
          "We appear to be retallying; adjusting start and end from [{} : {}] to [{} : {}]",
          range.getStartString(),
          range.getEndString(),
          effectiveStartDateTime,
          effectiveEndDateTime);
      isRecalculating = true;
    } else {
      effectiveStartDateTime = range.getStartDate();
      effectiveEndDateTime = range.getEndDate();
      log.info(
          "New tally! Adjusting start and end from [{} : {}] to [{} : {}]",
          range.getStartString(),
          range.getEndString(),
          effectiveStartDateTime,
          effectiveEndDateTime);
      isRecalculating = false;
    }

    if (isRecalculating) {
      log.info("Clearing monthly totals for {} instances", existingInstances.size());
      existingInstances
          .values()
          .forEach(
              instance ->
                  instance.clearMonthlyTotals(effectiveStartDateTime, effectiveEndDateTime));
    }

    Map<OffsetDateTime, AccountUsageCalculation> accountCalcs =
        collectHourlyCalculations(
            accountServiceInventory, effectiveStartDateTime, effectiveEndDateTime);
    accountServiceInventoryRepository.save(accountServiceInventory);

    return new CollectionResult(
        new DateRange(effectiveStartDateTime, effectiveEndDateTime), accountCalcs, isRecalculating);
  }

  private Map<OffsetDateTime, AccountUsageCalculation> collectHourlyCalculations(
      AccountServiceInventory accountServiceInventory,
      OffsetDateTime effectiveStartDateTime,
      OffsetDateTime effectiveEndDateTime) {
    Map<OffsetDateTime, AccountUsageCalculation> accountCalcs = new HashMap<>();
    for (OffsetDateTime offset = effectiveStartDateTime;
        offset.isBefore(effectiveEndDateTime);
        offset = offset.plusHours(1)) {
      AccountUsageCalculation accountUsageCalculation =
          collectHour(accountServiceInventory, offset);

      if (accountUsageCalculation != null) {
        // The associated account number for a calculation has already been determined from the
        // hosts instances (based on the event). Pass that info along if it isn't already known.
        if (!StringUtils.hasText(accountServiceInventory.getAccountNumber())
            && StringUtils.hasText(accountUsageCalculation.getAccount())) {
          accountServiceInventory.setAccountNumber(accountUsageCalculation.getAccount());
        }

        if (!accountUsageCalculation.getKeys().isEmpty()) {
          accountCalcs.put(offset, accountUsageCalculation);
        }
      }
    }
    return accountCalcs;
  }

  @Transactional
  public AccountUsageCalculation collectHour(
      AccountServiceInventory accountServiceInventory, OffsetDateTime startDateTime) {
    Optional<TagMetaData> serviceTypeMeta =
        tagProfile.getTagMetaDataByServiceType(accountServiceInventory.getServiceType());
    OffsetDateTime endDateTime = startDateTime.plusHours(1);

    Map<String, List<Event>> eventToHostMapping =
        eventController
            .fetchEventsInTimeRangeByServiceType(
                accountServiceInventory.getOrgId(),
                accountServiceInventory.getServiceType(),
                startDateTime,
                endDateTime)
            // We group fetched events by instanceId so that we can clear the measurements
            // on first access, if the instance already exists for the accountServiceInventory.
            .collect(Collectors.groupingBy(Event::getInstanceId));

    Map<String, Host> thisHoursInstances = new HashMap<>();
    eventToHostMapping.forEach(
        (instanceId, events) -> {
          Set<Uom> seenUoms = new HashSet<>();
          Host existing = accountServiceInventory.getServiceInstances().get(instanceId);
          Host host = existing == null ? new Host() : existing;
          thisHoursInstances.put(instanceId, host);
          accountServiceInventory.getServiceInstances().put(instanceId, host);

          events.forEach(
              event -> {
                updateInstanceFromEvent(event, host, serviceTypeMeta);

                if (event.getMeasurements() != null) {
                  event.getMeasurements().stream().map(Measurement::getUom).forEach(seenUoms::add);
                }
              });

          // clear any measurements that we don't have events for
          Set<Uom> staleMeasurements =
              host.getMeasurements().keySet().stream()
                  .filter(k -> !seenUoms.contains(k))
                  .collect(Collectors.toSet());
          staleMeasurements.forEach(host.getMeasurements()::remove);
        });
    return tallyCurrentAccountState(accountServiceInventory, thisHoursInstances);
  }

  private AccountUsageCalculation tallyCurrentAccountState(
      AccountServiceInventory accountInventory, Map<String, Host> thisHoursInstances) {
    if (thisHoursInstances.isEmpty()) {
      return null;
    }
    AccountUsageCalculation accountUsageCalculation =
        new AccountUsageCalculation(accountInventory.getOrgId());

    thisHoursInstances
        .values()
        .forEach(
            instance -> {
              instance
                  .getBuckets()
                  .forEach(
                      bucket -> {
                        UsageCalculation.Key usageKey =
                            new UsageCalculation.Key(
                                bucket.getKey().getProductId(),
                                bucket.getKey().getSla(),
                                bucket.getKey().getUsage(),
                                bucket.getKey().getBillingProvider(),
                                bucket.getKey().getBillingAccountId());
                        instance
                            .getMeasurements()
                            .forEach(
                                (uom, value) ->
                                    accountUsageCalculation.addUsage(
                                        usageKey,
                                        getHardwareMeasurementType(instance),
                                        uom,
                                        value));
                      });

              // Pull the account number from the first instance. All instances should have
              // the same account.
              if (Objects.isNull(accountUsageCalculation.getAccount())
                  && StringUtils.hasText(instance.getAccountNumber())) {
                accountUsageCalculation.setAccount(instance.getAccountNumber());
              }
            });
    return accountUsageCalculation;
  }

  private void updateInstanceFromEvent(
      Event event, Host instance, Optional<TagMetaData> serviceTypeMeta) {
    // fields that we expect to always be present
    instance.setAccountNumber(event.getAccountNumber());
    instance.setOrgId(event.getOrgId());
    instance.setInstanceType(event.getServiceType());
    instance.setInstanceId(event.getInstanceId());
    instance.setDisplayName(event.getInstanceId()); // may be overridden later
    instance.setLastSeen(event.getTimestamp());
    instance.setGuest(instance.getHardwareType() == HostHardwareType.VIRTUALIZED);

    // fields that are optional, see update/updateWithTransform method javadocs
    update(instance::setBillingAccountId, event.getBillingAccountId());
    updateWithTransform(
        instance::setBillingProvider,
        Optional.ofNullable(event.getBillingProvider()).orElse(Event.BillingProvider.RED_HAT),
        this::getBillingProvider);
    updateWithTransform(
        instance::setCloudProvider, event.getCloudProvider(), this::getCloudProviderAsString);
    updateWithTransform(
        instance::setHardwareType, event.getHardwareType(), this::getHostHardwareType);
    update(instance::setDisplayName, event.getDisplayName());
    update(instance::setInventoryId, event.getInventoryId());
    update(instance::setHypervisorUuid, event.getHypervisorUuid());
    update(instance::setSubscriptionManagerId, event.getSubscriptionManagerId());
    Optional.ofNullable(event.getMeasurements())
        .orElse(Collections.emptyList())
        .forEach(
            measurement -> {
              instance.setMeasurement(measurement.getUom(), measurement.getValue());
              instance.addToMonthlyTotal(
                  event.getTimestamp(), measurement.getUom(), measurement.getValue());
            });
    addBucketsFromEvent(instance, event, serviceTypeMeta);
  }

  /**
   * Transform and set a value, doing nothing for null values. This method is intended to be used
   * for enum types and complex types.
   *
   * @param setter the setter to invoke when value is non-null
   * @param value the value
   * @param transform function to transform the value to the necessary type
   * @param <T> original type, should be an enum
   * @param <R> type the setter expects
   */
  private <T, R> void updateWithTransform(Consumer<R> setter, T value, Function<T, R> transform) {
    if (value != null) {
      R transformed = transform.apply(value);
      setter.accept(transformed);
    }
  }

  /**
   * Set a value if the value is non-null.
   *
   * <p>Given how we deserialize JSON, here are the possibilities: 1. null if the JSON didn't
   * include the field at all 2. Optional.empty() if the JSON had the field set to null 3.
   * Optional.of($someValue) if the JSON had the field set to a non-null value
   *
   * <p>When the Optional is null, this means the source event does not know about the field, so we
   * do nothing. Otherwise, we update the field
   *
   * @param setter the setter to invoke when value is non-null
   * @param optional null or Optional to be unwrapped
   * @param <T> type the setter expects
   */
  private <T> void update(Consumer<T> setter, Optional<T> optional) {
    Optional.ofNullable(optional).ifPresent(value -> setter.accept(value.orElse(null)));
  }

  private HostHardwareType getHostHardwareType(Event.HardwareType hardwareType) {
    switch (hardwareType) {
      case __EMPTY__:
        return null;
      case PHYSICAL:
        return HostHardwareType.PHYSICAL;
      case VIRTUAL:
        return HostHardwareType.VIRTUALIZED;
      case CLOUD:
        return HostHardwareType.CLOUD;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported hardware type: %s", hardwareType));
    }
  }

  private HardwareMeasurementType getHardwareMeasurementType(Host instance) {
    if (instance.getHardwareType() == null) {
      return HardwareMeasurementType.PHYSICAL;
    }
    switch (instance.getHardwareType()) {
      case CLOUD:
        return getCloudProvider(instance);
      case VIRTUALIZED:
        return HardwareMeasurementType.VIRTUAL;
      case PHYSICAL:
        return HardwareMeasurementType.PHYSICAL;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported hardware type: %s", instance.getHardwareType()));
    }
  }

  private HardwareMeasurementType getCloudProvider(Host instance) {
    if (instance.getCloudProvider() == null) {
      throw new IllegalArgumentException("Hardware type cloud, but no cloud provider specified");
    }
    return HardwareMeasurementType.valueOf(instance.getCloudProvider());
  }

  private HardwareMeasurementType getCloudProvider(Event.CloudProvider cloudProvider) {
    switch (cloudProvider) {
      case __EMPTY__:
        return null;
      case AWS:
        return HardwareMeasurementType.AWS;
      case AZURE:
        return HardwareMeasurementType.AZURE;
      case ALIBABA:
        return HardwareMeasurementType.ALIBABA;
      case GOOGLE:
        return HardwareMeasurementType.GOOGLE;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported value for cloud provider: %s", cloudProvider.value()));
    }
  }

  private String getCloudProviderAsString(Event.CloudProvider cloudProvider) {
    HardwareMeasurementType hardwareMeasurementType = getCloudProvider(cloudProvider);
    if (hardwareMeasurementType != null) {
      return hardwareMeasurementType.toString();
    }
    return null;
  }

  private BillingProvider getBillingProvider(Event.BillingProvider billingProvider) {
    switch (billingProvider) {
      case __EMPTY__:
        return null;
      case AWS:
        return BillingProvider.AWS;
      case AZURE:
        return BillingProvider.AZURE;
      case ORACLE:
        return BillingProvider.ORACLE;
      case GCP:
        return BillingProvider.GCP;
      case RED_HAT:
        return BillingProvider.RED_HAT;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported value for billing provider: %s", billingProvider.value()));
    }
  }

  private void addBucketsFromEvent(Host host, Event event, Optional<TagMetaData> serviceTypeMeta) {
    ServiceLevel effectiveSla =
        Optional.ofNullable(event.getSla())
            .map(Event.Sla::toString)
            .map(ServiceLevel::fromString)
            .orElse(serviceTypeMeta.map(TagMetaData::getDefaultSla).orElse(ServiceLevel.EMPTY));
    Usage effectiveUsage =
        Optional.ofNullable(event.getUsage())
            .map(Event.Usage::toString)
            .map(Usage::fromString)
            .orElse(serviceTypeMeta.map(TagMetaData::getDefaultUsage).orElse(Usage.EMPTY));
    BillingProvider effectiveProvider =
        Optional.ofNullable(event.getBillingProvider())
            .map(Event.BillingProvider::toString)
            .map(BillingProvider::fromString)
            .orElse(BillingProvider.RED_HAT);
    String effectiveBillingAcctId =
        Optional.ofNullable(event.getBillingAccountId()).orElse(Optional.empty()).orElse("");
    Set<String> productIds = getProductIds(event);
    Set<String> billingAcctIds = getBillingAccountIds(effectiveBillingAcctId);

    for (String productId : productIds) {
      for (ServiceLevel sla : Set.of(effectiveSla, ServiceLevel._ANY)) {
        for (Usage usage : Set.of(effectiveUsage, Usage._ANY)) {
          for (BillingProvider billingProvider : Set.of(effectiveProvider, BillingProvider._ANY)) {
            for (String billingAccountId : billingAcctIds) {
              HostTallyBucket bucket = new HostTallyBucket();
              bucket.setKey(
                  new HostBucketKey(
                      host, productId, sla, usage, billingProvider, billingAccountId, false));
              host.addBucket(bucket);
            }
          }
        }
      }
    }
  }

  private Set<String> getProductIds(Event event) {
    Set<String> productIds = new HashSet<>();
    productIds.addAll(tagProfile.getTagsByRole(event.getRole()));

    Optional.ofNullable(event.getProductIds()).orElse(Collections.emptyList()).stream()
        .map(tagProfile::getTagsByEngProduct)
        .filter(Objects::nonNull)
        .forEach(productIds::addAll);

    return productIds;
  }

  private Set<String> getBillingAccountIds(String billingAcctId) {
    Set<String> billingAcctIds = new HashSet<>();
    billingAcctIds.add("_ANY");
    if (billingAcctId != null) {
      billingAcctIds.add(billingAcctId);
    }
    return billingAcctIds;
  }

  @AllArgsConstructor
  @Getter
  public class CollectionResult {
    private DateRange range;
    private Map<OffsetDateTime, AccountUsageCalculation> calculations;
    private boolean wasRecalculated;
  }
}
