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

import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
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
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.candlepin.subscriptions.util.MetricIdUtils;
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

  private final HostRepository hostRepository;

  public MetricUsageCollector(
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      EventController eventController,
      ApplicationClock clock,
      HostRepository hostRepository) {
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.eventController = eventController;
    this.clock = clock;
    this.hostRepository = hostRepository;
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

    Optional<OffsetDateTime> firstEventTimestampInRange =
        eventController.findFirstEventTimestampInRange(
            orgId, serviceType, range.getStartDate(), range.getEndDate());

    if (firstEventTimestampInRange.isEmpty()) {
      log.info("No event metrics to process for service type {} in range: {}", serviceType, range);
      return null;
    }
    OffsetDateTime firstEventTimestamp = firstEventTimestampInRange.get();

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
    Optional<OffsetDateTime> maxLastSeenTimestamp =
        hostRepository.findMaxLastSeenDate(orgId, serviceType);
    for (Host host : accountServiceInventory.getServiceInstances().values()) {
      existingInstances.put(host.getInstanceId(), host);
    }
    OffsetDateTime effectiveStartDateTime;
    OffsetDateTime effectiveEndDateTime;
    boolean isRecalculating;
    /*
    We need to recalculate several things if we are re-tallying, namely monthly totals need to be
    cleared and re-updated for each host record
    Evaluate latest state to determine if we are doing a recalculation.
    This condition serves a dual purpose: First, it validates the presence of untallied events.
    Additionally, it assesses whether the first event timestamp is after the host last seen.
    If this condition holds false, then the effectiveStartDateTime is adjusted from the first day of the month to
    the end of current hour. Otherwise, the start date is same as the beginning of the user start range,
    it extends until the specified passed end date.
     */
    if (maxLastSeenTimestamp.isEmpty()
        || !firstEventTimestamp.isAfter(maxLastSeenTimestamp.get())) {
      int eventLastMonth =
          firstEventTimestamp.getMonth().getValue() - OffsetDateTime.now().getMonth().getValue();
      if (eventLastMonth < 0) {
        effectiveStartDateTime = clock.startOfMonth(firstEventTimestamp);
      } else {
        effectiveStartDateTime = clock.startOfMonth(range.getStartDate());
      }
      effectiveEndDateTime = range.getEndDate();
      log.info(
          "We appear to be retallying; adjusting start and end from [{} : {}] to [{} : {}]",
          range.getStartString(),
          range.getEndString(),
          effectiveStartDateTime,
          effectiveEndDateTime);
      isRecalculating = true;
    } else {
      effectiveStartDateTime = firstEventTimestamp;
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
          Set<String> seenMetricIds = new HashSet<>();
          Host existing = accountServiceInventory.getServiceInstances().get(instanceId);
          Host host = existing == null ? new Host() : existing;
          thisHoursInstances.put(instanceId, host);
          accountServiceInventory.getServiceInstances().put(instanceId, host);

          events.forEach(
              event -> {
                updateInstanceFromEvent(event, host, accountServiceInventory.getServiceType());

                if (event.getMeasurements() != null) {
                  event.getMeasurements().stream()
                      .map(measurement -> MetricIdUtils.toUpperCaseFormatted(measurement.getUom()))
                      .forEach(seenMetricIds::add);
                }
              });

          // clear any measurements that we don't have events for
          Set<String> staleMeasurements =
              host.getMeasurements().keySet().stream()
                  .filter(k -> !seenMetricIds.contains(k))
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
                                (metricId, value) ->
                                    accountUsageCalculation.addUsage(
                                        usageKey,
                                        getHardwareMeasurementType(instance),
                                        MetricId.fromString(metricId),
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

  private void updateInstanceFromEvent(Event event, Host instance, String serviceType) {
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
                  event.getTimestamp(),
                  MetricId.fromString(measurement.getUom()),
                  measurement.getValue());
            });
    addBucketsFromEvent(instance, event, serviceType);
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
    return switch (hardwareType) {
      case __EMPTY__ -> null;
      case PHYSICAL -> HostHardwareType.PHYSICAL;
      case VIRTUAL -> HostHardwareType.VIRTUALIZED;
      case CLOUD -> HostHardwareType.CLOUD;
    };
  }

  private HardwareMeasurementType getHardwareMeasurementType(Host instance) {
    if (instance.getHardwareType() == null) {
      return HardwareMeasurementType.PHYSICAL;
    }
    return switch (instance.getHardwareType()) {
      case CLOUD -> getCloudProvider(instance);
      case VIRTUALIZED -> HardwareMeasurementType.VIRTUAL;
      case PHYSICAL -> HardwareMeasurementType.PHYSICAL;
    };
  }

  private HardwareMeasurementType getCloudProvider(Host instance) {
    if (instance.getCloudProvider() == null) {
      throw new IllegalArgumentException("Hardware type cloud, but no cloud provider specified");
    }
    return HardwareMeasurementType.fromString(instance.getCloudProvider());
  }

  private HardwareMeasurementType getCloudProvider(Event.CloudProvider cloudProvider) {
    return switch (cloudProvider) {
      case __EMPTY__ -> null;
      case AWS -> HardwareMeasurementType.AWS;
      case AZURE -> HardwareMeasurementType.AZURE;
      case ALIBABA -> HardwareMeasurementType.ALIBABA;
      case GOOGLE -> HardwareMeasurementType.GOOGLE;
    };
  }

  private String getCloudProviderAsString(Event.CloudProvider cloudProvider) {
    HardwareMeasurementType hardwareMeasurementType = getCloudProvider(cloudProvider);
    if (hardwareMeasurementType != null) {
      return hardwareMeasurementType.toString();
    }
    return null;
  }

  private BillingProvider getBillingProvider(Event.BillingProvider billingProvider) {
    return switch (billingProvider) {
      case __EMPTY__ -> null;
      case AWS -> BillingProvider.AWS;
      case AZURE -> BillingProvider.AZURE;
      case ORACLE -> BillingProvider.ORACLE;
      case GCP -> BillingProvider.GCP;
      case RED_HAT -> BillingProvider.RED_HAT;
    };
  }

  private void addBucketsFromEvent(Host host, Event event, String serviceType) {
    // We have multiple SubscriptionDefinitions that can have the same serviceType (OpenShift
    // Cluster).  The SLA and usage for these definitions should be the same (if defined), so we
    // need to collect them all, deduplicate, and then verify via MoreCollectors.toOptional that we
    // have only one or zero choices.
    var subDefinitions = SubscriptionDefinition.findByServiceType(serviceType);
    Optional<String> sla =
        subDefinitions.stream()
            .map(x -> x.getDefaults().getSla().toString())
            .distinct()
            .collect(MoreCollectors.toOptional());
    Optional<String> usage =
        subDefinitions.stream()
            .map(x -> x.getDefaults().getUsage().toString())
            .distinct()
            .collect(MoreCollectors.toOptional());

    ServiceLevel effectiveSla =
        Optional.ofNullable(event.getSla())
            .map(Event.Sla::toString)
            .map(ServiceLevel::fromString)
            .orElse(sla.map(ServiceLevel::fromString).orElse(ServiceLevel.EMPTY));
    Usage effectiveUsage =
        Optional.ofNullable(event.getUsage())
            .map(Event.Usage::toString)
            .map(Usage::fromString)
            .orElse(usage.map(Usage::fromString).orElse(Usage.EMPTY));
    BillingProvider effectiveProvider =
        Optional.ofNullable(event.getBillingProvider())
            .map(Event.BillingProvider::toString)
            .map(BillingProvider::fromString)
            .orElse(BillingProvider.RED_HAT);
    String effectiveBillingAcctId =
        Optional.ofNullable(event.getBillingAccountId()).orElse(Optional.empty()).orElse("");
    Set<String> productIds = getProductIds(event);
    Set<ServiceLevel> slas = Set.of(effectiveSla, ServiceLevel._ANY);
    Set<Usage> usages = Set.of(effectiveUsage, Usage._ANY);
    Set<BillingProvider> billingProviders = Set.of(effectiveProvider, BillingProvider._ANY);
    Set<String> billingAccountIds = getBillingAccountIds(effectiveBillingAcctId);

    Set<List<Object>> bucketTuples =
        Sets.cartesianProduct(productIds, slas, usages, billingProviders, billingAccountIds);
    bucketTuples.forEach(
        tuple -> {
          String productId = (String) tuple.get(0);
          ServiceLevel slaBucket = (ServiceLevel) tuple.get(1);
          Usage usageBucket = (Usage) tuple.get(2);
          BillingProvider billingProvider = (BillingProvider) tuple.get(3);
          String billingAccountId = (String) tuple.get(4);
          HostTallyBucket bucket = new HostTallyBucket();
          bucket.setKey(
              new HostBucketKey(
                  host,
                  productId,
                  slaBucket,
                  usageBucket,
                  billingProvider,
                  billingAccountId,
                  false));
          host.addBucket(bucket);
        });
  }

  private Set<String> getProductIds(Event event) {
    Set<String> productIds = new HashSet<>();

    if (Objects.nonNull(event.getRole())) {
      String role = event.getRole().toString();
      SubscriptionDefinition.lookupSubscriptionByRole(role)
          .flatMap(s -> s.findVariantForRole(role).map(Variant::getTag))
          .ifPresent(productIds::add);
    }

    var engIds = Optional.ofNullable(event.getProductIds()).orElse(Collections.emptyList());
    for (String engId : engIds) {
      SubscriptionDefinition.lookupSubscriptionByEngId(engId)
          .flatMap(s -> s.findVariantForEngId(engId).map(Variant::getTag))
          .ifPresent(productIds::add);
    }

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
