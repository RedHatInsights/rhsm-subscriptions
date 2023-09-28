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
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.AccountServiceInventoryId;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class MetricUsageCollector {

  private static final Logger log = LoggerFactory.getLogger(MetricUsageCollector.class);

  private final AccountServiceInventoryRepository accountServiceInventoryRepository;
  private final ApplicationClock clock;

  private final HostRepository hostRepository;
  private final TallySnapshotRepository snapshotRepository;

  public MetricUsageCollector(
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      ApplicationClock clock,
      HostRepository hostRepository,
      TallySnapshotRepository snapshotRepository) {
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.clock = clock;
    this.hostRepository = hostRepository;
    this.snapshotRepository = snapshotRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateHosts(String orgId, String serviceType, List<Event> events) {

    if (events.isEmpty()) {
      return;
    }

    AccountServiceInventoryId inventoryId =
        AccountServiceInventoryId.builder().orgId(orgId).serviceType(serviceType).build();
    if (!accountServiceInventoryRepository.existsById(inventoryId)) {
      accountServiceInventoryRepository.save(new AccountServiceInventory(inventoryId));
    }

    var hostsByInstanceId =
        hostRepository
            .findAllByOrgIdAndInstanceIdIn(
                orgId, events.stream().map(Event::getInstanceId).collect(Collectors.toSet()))
            .collect(Collectors.toMap(Host::getInstanceId, Function.identity()));

    for (Event event : events) {
      Host host = hostsByInstanceId.getOrDefault(event.getInstanceId(), new Host());

      // Determine if the Event has already been applied to this host and skip it if it is.
      // This is important in the case that we attempt to apply the same Event multiple times.
      if (isEventAlreadyAppliedToHost(host, event)) {
        continue;
      }

      updateInstanceFromEvent(event, host);
      cleanUpHostMeasurements(host, event);
      hostsByInstanceId.put(host.getInstanceId(), host);

      hostRepository.save(host);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void calculateUsage(List<Event> events, AccountUsageCalculationCache calcCache) {
    events.forEach(
        event -> {
          if (calcCache.isEventApplied(event)) {
            return;
          }

          // Rebuild the account calculation for this Event's timestamp.
          AccountUsageCalculation calc =
              calcCache.contains(event)
                  ? calcCache.get(event)
                  : loadHourlyAccountCalculation(event);

          updateUsage(calc, event);
          calcCache.put(event, calc);
        });
  }

  private void updateInstanceFromEvent(Event event, Host instance) {
    // Always process buckets, measurements and set the lastAppliedEventRecordDate
    // on the host.
    Optional.ofNullable(event.getMeasurements())
        .orElse(Collections.emptyList())
        .forEach(
            measurement -> {
              if (!isUsageAlreadyApplied(instance, event)) {
                instance.setMeasurement(measurement.getUom(), measurement.getValue());
              }
              // Every event should be applied to the totals.
              instance.addToMonthlyTotal(
                  event.getTimestamp(),
                  MetricId.fromString(measurement.getUom()),
                  measurement.getValue());
            });
    addBucketsFromEvent(instance, event);
    instance.setLastAppliedEventRecordDate(event.getRecordDate());

    // If the usage was already applied, there's no need to update the
    // metadata from the Event since it will already be in the most up to
    // date state based on the last time it was metered.
    if (isUsageAlreadyApplied(instance, event)) {
      return;
    }

    // Update the Host instance's meta-data
    instance.setOrgId(event.getOrgId());
    instance.setInstanceType(event.getServiceType());
    instance.setInstanceId(event.getInstanceId());
    instance.setDisplayName(event.getInstanceId()); // may be overridden later
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

    instance.setLastSeen(event.getTimestamp());
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
    if (Objects.isNull(hardwareType)) {
      return null;
    }

    return switch (hardwareType) {
      case __EMPTY__ -> null;
      case PHYSICAL -> HostHardwareType.PHYSICAL;
      case VIRTUAL -> HostHardwareType.VIRTUALIZED;
      case CLOUD -> HostHardwareType.CLOUD;
    };
  }

  private HardwareMeasurementType getHardwareMeasurementType(
      HostHardwareType hostHardwareType, String hostCloudProvider) {
    if (hostHardwareType == null) {
      return HardwareMeasurementType.PHYSICAL;
    }
    return switch (hostHardwareType) {
      case CLOUD -> getCloudProvider(hostCloudProvider);
      case VIRTUALIZED -> HardwareMeasurementType.VIRTUAL;
      case PHYSICAL -> HardwareMeasurementType.PHYSICAL;
    };
  }

  private HardwareMeasurementType getCloudProvider(String instanceCloudProvider) {
    if (instanceCloudProvider == null) {
      throw new IllegalArgumentException("Hardware type cloud, but no cloud provider specified");
    }
    return HardwareMeasurementType.fromString(instanceCloudProvider);
  }

  private HardwareMeasurementType getCloudProvider(Event.CloudProvider cloudProvider) {
    if (Objects.isNull(cloudProvider)) {
      return null;
    }

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

  private void addBucketsFromEvent(Host host, Event event) {
    Set<List<Object>> bucketTuples = buildBucketTuples(event);
    bucketTuples.forEach(
        tuple -> {
          String productId = (String) tuple.get(0);
          ServiceLevel slaBucket = (ServiceLevel) tuple.get(1);
          Usage usageBucket = (Usage) tuple.get(2);
          BillingProvider billingProvider = (BillingProvider) tuple.get(3);
          String billingAccountId = (String) tuple.get(4);

          Integer cores =
              Optional.ofNullable(host.getMeasurement(MetricIdUtils.getCores().toString()))
                  .map(Double::intValue)
                  .orElse(null);
          Integer sockets =
              Optional.ofNullable(host.getMeasurement(MetricIdUtils.getSockets().toString()))
                  .map(Double::intValue)
                  .orElse(null);

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
          bucket.setCores(cores);
          bucket.setSockets(sockets);
          host.addBucket(bucket);
        });
  }

  private void updateUsage(AccountUsageCalculation calc, Event event) {
    Set<List<Object>> bucketTuples = buildBucketTuples(event);
    bucketTuples.forEach(
        tuple -> {
          String productId = (String) tuple.get(0);
          ServiceLevel slaBucket = (ServiceLevel) tuple.get(1);
          Usage usageBucket = (Usage) tuple.get(2);
          BillingProvider billingProvider = (BillingProvider) tuple.get(3);
          String billingAccountId = (String) tuple.get(4);

          Key usageKey =
              new Key(productId, slaBucket, usageBucket, billingProvider, billingAccountId);

          HardwareMeasurementType hardwareMeasurementType =
              getHardwareMeasurementType(
                  getHostHardwareType(event.getHardwareType()),
                  getCloudProviderAsString(event.getCloudProvider()));
          event
              .getMeasurements()
              .forEach(
                  m ->
                      calc.addUsage(
                          usageKey,
                          hardwareMeasurementType,
                          MetricId.fromString(m.getUom()),
                          m.getValue()));
        });
  }

  private Set<List<Object>> buildBucketTuples(Event event) {
    // We have multiple SubscriptionDefinitions that can have the same serviceType (OpenShift
    // Cluster).  The SLA and usage for these definitions should be the same (if defined), so we
    // need to collect them all, deduplicate, and then verify via MoreCollectors.toOptional that we
    // have only one or zero choices.
    var subDefinitions = SubscriptionDefinition.findByServiceType(event.getServiceType());
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
    Set<String> productTags = getProductTag(event);
    Set<ServiceLevel> slas = Set.of(effectiveSla, ServiceLevel._ANY);
    Set<Usage> usages = Set.of(effectiveUsage, Usage._ANY);
    Set<BillingProvider> billingProviders = Set.of(effectiveProvider, BillingProvider._ANY);
    Set<String> billingAccountIds = getBillingAccountIds(effectiveBillingAcctId);
    return Sets.cartesianProduct(productTags, slas, usages, billingProviders, billingAccountIds);
  }

  /**
   * Logic to extract the product tag using this order: - From the event productTag field which you
   * have been populated after <a
   * href="https://issues.redhat.com/browse/SWATCH-1928">SWATCH-1928</a> - Last case, using the
   * subscription configuration (the same that was doing before SWATCH-1928).
   */
  private Set<String> getProductTag(Event event) {
    if (event.getProductTag() != null) {
      return event.getProductTag();
    }

    // remain old logic
    String role = Optional.ofNullable(event.getRole()).map(Object::toString).orElse(null);
    return SubscriptionDefinition.getAllProductTagsWithPaygEligibleByRoleOrEngIds(
        role, event.getProductIds(), null);
  }

  private Set<String> getBillingAccountIds(String billingAcctId) {
    Set<String> billingAcctIds = new HashSet<>();
    billingAcctIds.add("_ANY");
    if (billingAcctId != null) {
      billingAcctIds.add(billingAcctId);
    }
    return billingAcctIds;
  }

  /**
   * If the event is the latest known event, update the current measurements based on the Event.
   *
   * @param host the target host.
   * @param event the event measurements that should remain.
   */
  private void cleanUpHostMeasurements(Host host, Event event) {
    // If the last seen date is not recent, we don't do anything. We must support a last seen date
    // that is equal to the event date because, in theory, we could get multiple events that
    // represent the same timestamp.
    if (isUsageAlreadyApplied(host, event)) {
      return;
    }

    List<String> seenMetricIds =
        Optional.ofNullable(event.getMeasurements()).orElse(new LinkedList<>()).stream()
            .map(measurement -> MetricIdUtils.toUpperCaseFormatted(measurement.getUom()))
            .toList();
    List<String> toRemove =
        host.getMeasurements().keySet().stream().filter(k -> !seenMetricIds.contains(k)).toList();
    toRemove.forEach(host.getMeasurements()::remove);
  }

  /**
   * Determines if the Event's usage measurements have already been applied. Usage is considered
   * applied when the Event's timestamp is before the Host's lastSeenDate (last time the event was
   * metered).
   *
   * @param host
   * @param event
   * @return true if the usage was already applied, false otherwise.
   */
  private boolean isUsageAlreadyApplied(Host host, Event event) {
    return Objects.nonNull(host.getLastSeen()) && event.getTimestamp().isBefore(host.getLastSeen());
  }

  private boolean isEventAlreadyAppliedToHost(Host host, Event event) {
    return Objects.nonNull(host.getLastAppliedEventRecordDate())
        && (host.getLastAppliedEventRecordDate().equals(event.getRecordDate())
            || host.getLastAppliedEventRecordDate().isAfter(event.getRecordDate()));
  }

  private AccountUsageCalculation loadHourlyAccountCalculation(Event event) {
    log.debug("Loading hourly usage from snapshots: {}", event);
    Set<String> products =
        SubscriptionDefinition.findByServiceType(event.getServiceType()).stream()
            .map(SubscriptionDefinition::getVariants)
            .flatMap(List::stream)
            .map(Variant::getTag)
            .collect(Collectors.toSet());
    Stream<TallySnapshot> snapshots =
        snapshotRepository.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            event.getOrgId(),
            products,
            Granularity.HOURLY,
            event.getTimestamp(),
            clock.endOfHour(event.getTimestamp()));

    AccountUsageCalculation calc = new AccountUsageCalculation(event.getOrgId());
    snapshots.forEach(
        snap -> {
          Key usageKey = Key.fromTallySnapshot(snap);
          snap.getTallyMeasurements().entrySet().stream()
              // Do not accumulate the TOTAL measurements as the calculation object calculates the
              // totals based on the measurements that are added to it.
              .filter(e -> !HardwareMeasurementType.TOTAL.equals(e.getKey().getMeasurementType()))
              .forEach(
                  entry -> {
                    var measurementKey = entry.getKey();
                    calc.addUsage(
                        usageKey,
                        measurementKey.getMeasurementType(),
                        MetricId.fromString(measurementKey.getMetricId()),
                        entry.getValue());
                  });
        });
    return calc;
  }
}
