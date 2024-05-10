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

import com.google.common.collect.Sets;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollectorFactory;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Collects the max values from all accounts in the inventory. */
@Slf4j
@Component
public class InventoryAccountUsageCollector {
  public static final String HBI_INSTANCE_TYPE = "HBI_HOST";

  private final FactNormalizer factNormalizer;
  private final AccountServiceInventoryRepository accountServiceInventoryRepository;
  private final HostTallyBucketRepository tallyBucketRepository;
  private final HostRepository hostRepository;
  private final EntityManager entityManager;
  private final int culledOffsetDays;
  private final Long hbiReconciliationFlushInterval;
  private final InventorySwatchDataCollator collator;

  @Autowired
  public InventoryAccountUsageCollector(
      FactNormalizer factNormalizer,
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      HostRepository hostRepository,
      EntityManager entityManager,
      HostTallyBucketRepository tallyBucketRepository,
      ApplicationProperties props,
      InventorySwatchDataCollator collator) {
    this.factNormalizer = factNormalizer;
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.collator = collator;
    this.hostRepository = hostRepository;
    this.entityManager = entityManager;
    this.tallyBucketRepository = tallyBucketRepository;
    this.culledOffsetDays = props.getCullingOffsetDays();
    this.hbiReconciliationFlushInterval = props.getHbiReconciliationFlushInterval();
  }

  @Timed("rhsm-subscriptions.tally.inventory.db")
  @Transactional
  public AccountUsageCalculation tally(String orgId) {
    log.info("Running tally via DB for orgId={}", orgId);
    AccountUsageCalculation calculation = new AccountUsageCalculation(orgId);
    try (Stream<AccountBucketTally> tallyStream =
        tallyBucketRepository.tallyHostBuckets(orgId, HBI_INSTANCE_TYPE)) {
      tallyStream.forEach(
          bucketTally -> {
            UsageCalculation usageCalc =
                calculation.getOrCreateCalculation(
                    new Key(
                        bucketTally.getProductId(),
                        bucketTally.getSla(),
                        bucketTally.getUsage(),
                        bucketTally.getBillingProvider(),
                        bucketTally.getBillingAccountId()));
            usageCalc.add(
                bucketTally.getMeasurementType(),
                Optional.ofNullable(bucketTally.getCores()).orElse(0.0),
                Optional.ofNullable(bucketTally.getSockets()).orElse(0.0),
                bucketTally.getInstances());
          });
      return calculation;
    }
  }

  /**
   * Reconcile HBI data with swatch data.
   *
   * <p>This method also performs flushing of the created or updated records using a configured
   * batch size. This enables configurable control over the memory characteristics of system data
   * reconciliation.
   *
   * @param orgId orgId to reconcile
   * @param applicableProducts products to update tally buckets for
   */
  @Transactional
  @Timed("swatch_hbi_system_reconcile")
  public void reconcileSystemDataWithHbi(String orgId, Set<String> applicableProducts) {
    accountServiceInventoryRepository.saveIfDoesNotExist(orgId, HBI_INSTANCE_TYPE);
    List<Host> detachHosts = new ArrayList<>();
    int systemsUpdatedForOrg =
        collator.collateData(
            orgId,
            culledOffsetDays,
            (hbiSystem, swatchSystem, hypervisorData, iterationCount) -> {
              reconcileHbiSystemWithSwatchSystem(
                  hbiSystem, swatchSystem, hypervisorData, applicableProducts, detachHosts);
              if (iterationCount % hbiReconciliationFlushInterval == 0) {
                log.debug("Flushing system changes w/ count={}", iterationCount);
                hostRepository.flush();
                if (Objects.nonNull(swatchSystem) && Objects.nonNull(hbiSystem)) {
                  detachHosts.forEach(entityManager::detach);
                  detachHosts.clear();
                }
              }
            });
    log.info("Reconciled {} records for orgId={}", systemsUpdatedForOrg, orgId);
  }

  /**
   * Create all possible combinations of product, SLA, usage, billing provider, and account ID.
   *
   * @param products productIds
   * @param slas a set of SLAs
   * @param usages a set of usages
   * @param billingProviders a set of BillingProviders
   * @param billingAccountIds a set of billing account IDs
   * @return a set of UsageCalculation.Key representing all possible combinations of the input
   *     parameters
   */
  protected static Set<Key> createKeyCombinations(
      Set<String> products,
      Set<ServiceLevel> slas,
      Set<Usage> usages,
      Set<BillingProvider> billingProviders,
      Set<String> billingAccountIds) {
    Set<List<Object>> usageTuples =
        Sets.cartesianProduct(products, slas, usages, billingProviders, billingAccountIds);
    return usageTuples.stream()
        .map(
            tuple -> {
              String product = (String) tuple.get(0);
              ServiceLevel sla = (ServiceLevel) tuple.get(1);
              Usage usage = (Usage) tuple.get(2);
              BillingProvider billingProvider = (BillingProvider) tuple.get(3);
              String billingAccountId = (String) tuple.get(4);

              return new UsageCalculation.Key(
                  product, sla, usage, billingProvider, billingAccountId);
            })
        .collect(Collectors.toSet());
  }

  public static void populateHostFieldsFromHbi(
      Host host, InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts) {

    if (inventoryHostFacts.getProviderId() != null) {
      // will use the provider ID from HBI
      host.setInstanceId(inventoryHostFacts.getProviderId());
    }

    if (inventoryHostFacts.getInventoryId() != null) {
      host.setInventoryId(inventoryHostFacts.getInventoryId().toString());

      // fallback logic to set the instance ID if and only if the instanceId is not set yet:
      if (host.getInstanceId() == null) {
        // We assume that the instance ID for any given HBI host record is the inventory ID; compare
        // to an OpenShift Cluster from Prometheus data, where we use the cluster ID.
        host.setInstanceId(inventoryHostFacts.getInventoryId().toString());
      }
    }

    host.setInsightsId(inventoryHostFacts.getInsightsId());
    host.setOrgId(inventoryHostFacts.getOrgId());
    host.setDisplayName(inventoryHostFacts.getDisplayName());
    host.setSubscriptionManagerId(inventoryHostFacts.getSubscriptionManagerId());
    host.setGuest(normalizedFacts.isVirtual());
    host.setHypervisorUuid(normalizedFacts.getHypervisorUuid());

    if (normalizedFacts.getCores() != null) {
      host.getMeasurements()
          .put(
              MetricIdUtils.getCores().toUpperCaseFormatted(),
              normalizedFacts.getCores().doubleValue());
    }

    if (normalizedFacts.getSockets() != null) {
      host.getMeasurements()
          .put(
              MetricIdUtils.getSockets().toUpperCaseFormatted(),
              normalizedFacts.getSockets().doubleValue());
    }

    host.setHypervisor(normalizedFacts.isHypervisor());
    host.setUnmappedGuest(normalizedFacts.isVirtual() && normalizedFacts.isHypervisorUnknown());
    host.setCloudProvider(
        normalizedFacts.getCloudProviderType() == null
            ? null
            : normalizedFacts.getCloudProviderType().name());

    host.setLastSeen(inventoryHostFacts.getModifiedOn());
    host.setHardwareType(normalizedFacts.getHardwareType());
  }

  /**
   * Reconciles an HBI system record with a swatch system record.
   *
   * <p>Performs the proper create, update or delete operation based on the state of the HBI record
   * and the state of the swatch record.
   *
   * @param hbiSystem HBI system record, or null
   * @param swatchSystem swatch system record, or null
   * @param orgHostsData container for data gathered from guests, expected to contain an entry for
   *     the hbi system being processed if it is a hypervisor
   * @param applicableProducts set of product tags to process
   */
  protected void reconcileHbiSystemWithSwatchSystem(
      InventoryHostFacts hbiSystem,
      Host swatchSystem,
      OrgHostsData orgHostsData,
      Set<String> applicableProducts,
      List<Host> hosts) {
    log.debug(
        "Reconciling HBI inventoryId={} & swatch inventoryId={} & swatch instanceId={}",
        Optional.ofNullable(hbiSystem).map(InventoryHostFacts::getInventoryId),
        Optional.ofNullable(swatchSystem).map(Host::getInventoryId),
        Optional.ofNullable(swatchSystem).map(Host::getInstanceId));
    boolean isMetered = swatchSystem != null && swatchSystem.isMetered();
    if (hbiSystem == null && swatchSystem == null) {
      log.debug("Unexpected, both HBI & Swatch system records are empty");
    } else if (hbiSystem == null
        && HBI_INSTANCE_TYPE.equalsIgnoreCase(swatchSystem.getInstanceType())) {
      if (!isMetered) {
        log.info(
            "Deleting system w/ inventoryId={} and instanceId={}",
            swatchSystem.getInventoryId(),
            swatchSystem.getInstanceId());
        hostRepository.delete(swatchSystem);
      }
    } else if (hbiSystem != null) {
      NormalizedFacts normalizedFacts =
          factNormalizer.normalize(hbiSystem, orgHostsData, isMetered);
      Set<Key> usageKeys = createHostUsageKeys(applicableProducts, normalizedFacts);
      if (swatchSystem != null) {
        log.debug(
            "Updating system w/ inventoryId={} and instanceId={}",
            hbiSystem.getInventoryId(),
            hbiSystem.getProviderId());
        Host updatedSwatchSystem =
            updateSwatchSystem(hbiSystem, normalizedFacts, swatchSystem, usageKeys);
        hosts.add(updatedSwatchSystem);
      } else {
        log.debug(
            "Creating system w/ inventoryId={} and instanceId={}",
            hbiSystem.getInventoryId(),
            hbiSystem.getProviderId());
        swatchSystem = createSwatchSystem(hbiSystem, normalizedFacts, usageKeys);
        hosts.add(swatchSystem);
      }
      reconcileHypervisorData(normalizedFacts, swatchSystem, orgHostsData, usageKeys);
    }
  }

  private void reconcileHypervisorData(
      NormalizedFacts normalizedFacts, Host system, OrgHostsData orgHostsData, Set<Key> usageKeys) {
    Set<HostBucketKey> seenBucketKeys = new HashSet<>();
    if (system.getHypervisorUuid() != null
        && orgHostsData.hasHypervisorUuid(system.getHypervisorUuid())) {
      // system is a guest w/ known hypervisor, we should add its buckets to hypervisor-guest data
      usageKeys.forEach(
          usageKey -> orgHostsData.addHypervisorKey(system.getHypervisorUuid(), usageKey));
      orgHostsData.incrementGuestCount(system.getHypervisorUuid());
      orgHostsData.collectGuestData(new HashMap<>());
    } else if (system.getSubscriptionManagerId() != null) {
      // this is a potential hypervisor record
      // NOTE: remove is used in order to ensure a placeholder is processed only once
      // (otherwise we end up attempting to apply the buckets to every duplicate hypervisor record).
      Host placeholder = orgHostsData.hypervisorHostMap().remove(system.getSubscriptionManagerId());
      if (placeholder != null) {
        // this system is a hypervisor, transfer buckets & counts to it
        log.debug("Applying buckets and guest-count from orgHostsData.");
        system.setHypervisor(true);
        system.setNumOfGuests(placeholder.getNumOfGuests());
        placeholder
            .getBuckets()
            .forEach(
                bucket -> {
                  if (normalizedFacts.getCores() != null) {
                    bucket.setCores(normalizedFacts.getCores());
                  }
                  if (normalizedFacts.getSockets() != null) {
                    bucket.setSockets(normalizedFacts.getSockets());
                  }
                  system.addBucket(bucket);
                  seenBucketKeys.add(bucket.getKey());
                });
      }
    }
    // remove any buckets for guests no longer present
    // NOTE: asHypervisor=true is used to limit cleanup to buckets added to represent guest
    // subscription requirements (i.e. the bucket was populated from a guest).
    system
        .getBuckets()
        .removeIf(b -> b.getKey().getAsHypervisor() && !seenBucketKeys.contains(b.getKey()));
  }

  private Host createSwatchSystem(
      InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts, Set<Key> usageKeys) {
    Host host = new Host();
    host.setInstanceType(HBI_INSTANCE_TYPE);
    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    applyNonHypervisorBuckets(host, normalizedFacts, usageKeys);
    entityManager.persist(host);
    return host;
  }

  private Set<Key> createHostUsageKeys(Set<String> products, NormalizedFacts facts) {
    return createKeyCombinations(
        products.stream().filter(facts.getProducts()::contains).collect(Collectors.toSet()),
        Set.of(facts.getSla(), ServiceLevel._ANY),
        Set.of(facts.getUsage(), Usage._ANY),
        Set.of(BillingProvider._ANY),
        Set.of("_ANY"));
  }

  private void applyNonHypervisorBuckets(Host host, NormalizedFacts facts, Set<Key> usageKeys) {
    Set<HostBucketKey> seenBucketKeys = new HashSet<>();

    // Calculate for each UsageKey
    // review current implementation of default values, and determine if factnormalizer needs
    // to handle billingAcctId & BillingProvider
    for (Key key : usageKeys) {
      var product = key.getProductId();
      if (!facts.getProducts().contains(product)) {
        continue;
      }
      Optional<HostTallyBucket> appliedBucket =
          ProductUsageCollectorFactory.get(product).buildBucket(key, facts);
      appliedBucket.ifPresent(
          bucket -> {
            // host.addBucket changes bucket.key.hostId, so we do that first; to
            // avoid mutating the item in the set
            host.addBucket(bucket);
            seenBucketKeys.add(bucket.getKey());
          });
    }
    // Remove any *non-hypervisor* keys that weren't seen this time.
    // Hypervisor keys need to be evaluated against hypervisor-guest data and are handled by
    // reconcileHypervisorData
    // NOTE: asHypervisor=false is used to operate solely on buckets for this system (filtering out
    // those added for a guest system).
    host.getBuckets()
        .removeIf(b -> !b.getKey().getAsHypervisor() && !seenBucketKeys.contains(b.getKey()));
  }

  private Host updateSwatchSystem(
      InventoryHostFacts inventoryHostFacts,
      NormalizedFacts normalizedFacts,
      Host host,
      Set<Key> usageKeys) {
    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    applyNonHypervisorBuckets(host, normalizedFacts, usageKeys);
    return entityManager.merge(host);
  }
}
